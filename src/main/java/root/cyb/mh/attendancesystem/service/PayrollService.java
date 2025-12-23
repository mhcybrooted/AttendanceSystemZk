package root.cyb.mh.attendancesystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.model.*;
import root.cyb.mh.attendancesystem.repository.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PayrollService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private WorkScheduleRepository workScheduleRepository;

    @Autowired
    private PublicHolidayRepository publicHolidayRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @Autowired
    private AdvanceSalaryRepository advanceSalaryRepository;

    public void generatePayrollForMonth(YearMonth yearMonth) {
        // ... (Keep initial setup for configs and dates) ...
        String monthStr = yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate startOfMonth = yearMonth.atDay(1);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();

        List<Employee> employees = employeeRepository.findAll();

        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<PublicHoliday> holidays = publicHolidayRepository.findAll();
        List<LocalDate> monthDates = startOfMonth.datesUntil(endOfMonth.plusDays(1)).collect(Collectors.toList());

        // Data for the month (Global Fetch for performance optimization in bulk)
        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfMonth.atStartOfDay(), endOfMonth.atTime(23, 59, 59));

        List<LeaveRequest> allApprovedLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(LeaveRequest.Status.APPROVED);

        for (Employee emp : employees) {
            // Filter logs and leaves for this specific employee
            List<AttendanceLog> empLogs = allLogs.stream()
                    .filter(l -> l.getEmployeeId().equals(emp.getId()))
                    .collect(Collectors.toList());

            List<LeaveRequest> empLeaves = allApprovedLeaves.stream()
                    .filter(l -> l.getEmployee().getId().equals(emp.getId()))
                    .collect(Collectors.toList());

            calculatePayslip(emp, yearMonth, globalSchedule, holidays, monthDates, empLogs, empLeaves);
        }
    }

    public void createPayslipForEmployee(Employee emp, YearMonth yearMonth) {
        // Need to fetch data for just this employee
        LocalDate startOfMonth = yearMonth.atDay(1);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();
        List<LocalDate> monthDates = startOfMonth.datesUntil(endOfMonth.plusDays(1)).collect(Collectors.toList());

        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<PublicHoliday> holidays = publicHolidayRepository.findAll();

        List<AttendanceLog> empLogs = attendanceLogRepository.findByTimestampBetween(
                startOfMonth.atStartOfDay(), endOfMonth.atTime(23, 59, 59)).stream()
                .filter(l -> l.getEmployeeId().equals(emp.getId()))
                .collect(Collectors.toList());

        List<LeaveRequest> empLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(LeaveRequest.Status.APPROVED).stream()
                .filter(l -> l.getEmployee().getId().equals(emp.getId()))
                .collect(Collectors.toList());

        calculatePayslip(emp, yearMonth, globalSchedule, holidays, monthDates, empLogs, empLeaves);
    }

    private void calculatePayslip(Employee emp, YearMonth yearMonth, WorkSchedule globalSchedule,
            List<PublicHoliday> holidays, List<LocalDate> monthDates, List<AttendanceLog> empLogs,
            List<LeaveRequest> empLeaves) {
        String monthStr = yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate startOfMonth = yearMonth.atDay(1);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();

        if (emp.isGuest())
            return; // Skip guests

        // Skip Future Joiners
        if (emp.getJoiningDate() != null && emp.getJoiningDate().isAfter(endOfMonth)) {
            return;
        }

        // Check if already generated
        Payslip payslip = payslipRepository.findByEmployeeIdAndMonth(emp.getId(), monthStr)
                .orElse(new Payslip());

        // LOCKING: If Paid, do not regenerate
        if (payslip.getId() != null && payslip.getStatus() == Payslip.Status.PAID) {
            return;
        }

        if (payslip.getId() == null) {
            payslip.setEmployee(emp);
            payslip.setMonth(monthStr);
            payslip.setStatus(Payslip.Status.DRAFT);
        }
        payslip.setGeneratedAt(java.time.LocalDateTime.now());

        double monthlySalary = emp.getMonthlySalary() != null ? emp.getMonthlySalary() : 0.0;

        // Calculate Standard Monthly Working Days
        int standardMonthlyWorkingDays = 0;
        for (LocalDate date : monthDates) {
            boolean isWeekend = globalSchedule.getWeekendDays() != null
                    && globalSchedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
            boolean isHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));
            if (!isWeekend && !isHoliday) {
                standardMonthlyWorkingDays++;
            }
        }

        // Counters
        int actualWorkingDays = 0; // Days this employee was eligible and it was a working day
        int presentDays = 0;
        int absentDays = 0;
        int paidLeaveDays = 0;
        int unpaidLeaveDays = 0;

        for (LocalDate date : monthDates) {
            // Skip before joining
            if (emp.getJoiningDate() != null && date.isBefore(emp.getJoiningDate()))
                continue;

            // Check Weekend/Holiday
            boolean isWeekend = globalSchedule.getWeekendDays() != null
                    && globalSchedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
            boolean isHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

            boolean isWorkingDay = !isWeekend && !isHoliday;
            if (isWorkingDay) {
                actualWorkingDays++;
            }

            // Check Leave
            boolean onLeave = false;
            LeaveRequest activeLeave = null;
            for (LeaveRequest lr : empLeaves) {
                if (!date.isBefore(lr.getStartDate()) && !date.isAfter(lr.getEndDate())) {
                    onLeave = true;
                    activeLeave = lr;
                    break;
                }
            }

            // Check Attendance
            boolean isPresent = empLogs.stream()
                    .anyMatch(l -> l.getTimestamp().toLocalDate().equals(date));

            if (isPresent) {
                presentDays++;
            } else if (onLeave && activeLeave != null) {
                // Check Leave Type
                String type = activeLeave.getLeaveType() != null ? activeLeave.getLeaveType().toUpperCase() : "";
                if (type.equals("UNPAID") || type.equals("LWP")) {
                    if (isWorkingDay)
                        unpaidLeaveDays++;
                } else {
                    // Paid Leave
                    if (isWorkingDay)
                        paidLeaveDays++;
                }
            } else {
                // Absent (No Log, No Leave)
                if (isWorkingDay) {
                    absentDays++;
                }
            }
        }

        // Late Penalty Logic
        double penaltyDays = 0.0;
        // Count Late Days (Only first check-in per day)
        long lateCount = 0;

        // Group by Date to find the first check-in of each day
        Map<LocalDate, List<AttendanceLog>> logsByDate = empLogs.stream()
                .collect(Collectors.groupingBy(l -> l.getTimestamp().toLocalDate()));

        for (Map.Entry<LocalDate, List<AttendanceLog>> entry : logsByDate.entrySet()) {
            // Find earliest log for the day
            List<AttendanceLog> dailyLogs = entry.getValue();
            AttendanceLog firstLog = dailyLogs.stream()
                    .min((l1, l2) -> l1.getTimestamp().compareTo(l2.getTimestamp()))
                    .orElse(null);

            if (firstLog != null && globalSchedule.getStartTime() != null) {
                LocalTime checkInTime = firstLog.getTimestamp().toLocalTime();
                LocalTime lateLimit = globalSchedule.getStartTime()
                        .plusMinutes(globalSchedule.getLateToleranceMinutes());
                if (checkInTime.isAfter(lateLimit)) {
                    lateCount++;
                }
            }
        }

        // Apply Penalty (Integer Division Logic: e.g. 3 lates = 1 penalty)
        if (globalSchedule.getLatePenaltyThreshold() != null && globalSchedule.getLatePenaltyThreshold() > 0
                && globalSchedule.getLatePenaltyDeduction() != null && globalSchedule.getLatePenaltyDeduction() > 0) {
            long penaltyCount = lateCount / globalSchedule.getLatePenaltyThreshold();
            penaltyDays = penaltyCount * globalSchedule.getLatePenaltyDeduction();
        }

        // Financials
        double dailyRate;
        String rateBasis = globalSchedule.getDailyRateBasis() != null ? globalSchedule.getDailyRateBasis()
                : "STANDARD_30";

        switch (rateBasis) {
            case "ACTUAL_WORKING_DAYS":
                dailyRate = (standardMonthlyWorkingDays > 0) ? (monthlySalary / standardMonthlyWorkingDays) : 0;
                break;
            case "FIXED_DAYS":
                int fixedDays = (globalSchedule.getDailyRateFixedValue() != null
                        && globalSchedule.getDailyRateFixedValue() > 0)
                                ? globalSchedule.getDailyRateFixedValue()
                                : 30;
                dailyRate = monthlySalary / fixedDays;
                break;
            case "STANDARD_30":
            default:
                dailyRate = monthlySalary / 30.0;
                break;
        }

        // Allowances & Bonuses
        double fixedAllowance = emp.getFixedAllowance() != null ? emp.getFixedAllowance() : 0.0;
        double bonus = payslip.getBonusAmount() != null ? payslip.getBonusAmount() : 0.0;

        // Deductions
        double absentDeduction = (absentDays + unpaidLeaveDays) * dailyRate;
        double latePenaltyAmount = penaltyDays * dailyRate;

        // Advance Salary Logic
        double advanceDeduction = 0.0;
        List<AdvanceSalaryRequest> pendingAdvances = advanceSalaryRepository.findPendingDeductions(emp.getId());
        for (AdvanceSalaryRequest req : pendingAdvances) {
            advanceDeduction += req.getAmount();
            // Mark as deducted to prevent double counting
            req.setDeducted(true);
            req.setStatus(AdvanceSalaryRequest.Status.PAID); // Mark as Paid back
            advanceSalaryRepository.save(req);
        }

        double totalDeductions = absentDeduction + latePenaltyAmount + advanceDeduction;

        // Net Pay Formula
        double netSalary = (monthlySalary + fixedAllowance + bonus) - totalDeductions;

        payslip.setBasicSalary(monthlySalary);
        payslip.setAllowanceAmount(fixedAllowance);
        payslip.setBonusAmount(bonus);
        payslip.setDeductionAmount(Math.round(totalDeductions * 100.0) / 100.0);
        payslip.setNetSalary(Math.round(netSalary * 100.0) / 100.0);
        payslip.setTotalWorkingDays(standardMonthlyWorkingDays);
        payslip.setPresentDays(presentDays);
        payslip.setAbsentDays(absentDays);
        payslip.setUnpaidLeaveDays(unpaidLeaveDays);
        payslip.setPaidLeaveDays(paidLeaveDays);

        // Late Penalty (Persist for UI)
        payslip.setLateDays((int) lateCount);
        payslip.setLatePenaltyAmount(latePenaltyAmount);
        payslip.setAdvanceSalaryAmount(advanceDeduction);

        payslipRepository.save(payslip);
    }
}
