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

    public void generatePayrollForMonth(YearMonth yearMonth) {
        String monthStr = yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate startOfMonth = yearMonth.atDay(1);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();
        List<LocalDate> monthDates = startOfMonth.datesUntil(endOfMonth.plusDays(1)).collect(Collectors.toList());

        // Configs
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<PublicHoliday> holidays = publicHolidayRepository.findAll();

        // Data for the month
        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfMonth.atStartOfDay(), endOfMonth.atTime(23, 59, 59));

        List<LeaveRequest> approvedLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(LeaveRequest.Status.APPROVED).stream()
                .filter(l -> !l.getStartDate().isAfter(endOfMonth) && !l.getEndDate().isBefore(startOfMonth))
                .collect(Collectors.toList());

        List<Employee> employees = employeeRepository.findAll();

        // Calculate Standard Working Days for the Month (Global, ignoring individual
        // joining dates)
        int standardMonthlyWorkingDays = 0;
        for (LocalDate date : monthDates) {
            boolean isWeekend = globalSchedule.getWeekendDays() != null
                    && globalSchedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
            boolean isHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));
            if (!isWeekend && !isHoliday) {
                standardMonthlyWorkingDays++;
            }
        }

        for (Employee emp : employees) {
            if (emp.isGuest())
                continue; // Skip guests

            // Skip Future Joiners
            if (emp.getJoiningDate() != null && emp.getJoiningDate().isAfter(endOfMonth)) {
                continue;
            }

            // Check if already generated
            Payslip payslip = payslipRepository.findByEmployeeIdAndMonth(emp.getId(), monthStr)
                    .orElse(new Payslip());

            // LOCKING: If Paid, do not regenerate
            if (payslip.getId() != null && payslip.getStatus() == Payslip.Status.PAID) {
                continue;
            }

            if (payslip.getId() == null) {
                payslip.setEmployee(emp);
                payslip.setMonth(monthStr);
                payslip.setStatus(Payslip.Status.DRAFT);
            }
            payslip.setGeneratedAt(java.time.LocalDateTime.now());

            double monthlySalary = emp.getMonthlySalary() != null ? emp.getMonthlySalary() : 0.0;

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
                for (LeaveRequest lr : approvedLeaves) {
                    if (lr.getEmployee().getId().equals(emp.getId())
                            && !date.isBefore(lr.getStartDate()) && !date.isAfter(lr.getEndDate())) {
                        onLeave = true;
                        activeLeave = lr;
                        break;
                    }
                }

                // Check Attendance
                boolean isPresent = allLogs.stream()
                        .anyMatch(l -> l.getEmployeeId().equals(emp.getId())
                                && l.getTimestamp().toLocalDate().equals(date));

                if (isPresent) {
                    presentDays++;
                    // Note: If present on a holiday/weekend, we count it as present,
                    // but distinct from "Total Working Days" calculation denominator usually.
                    // For deduction logic: if present, no deduction.
                } else if (onLeave && activeLeave != null) {
                    // Check Leave Type
                    String type = activeLeave.getLeaveType() != null ? activeLeave.getLeaveType().toUpperCase() : "";
                    if (type.equals("UNPAID") || type.equals("LWP")) {
                        if (isWorkingDay)
                            unpaidLeaveDays++; // Only deduct if it was a working day? or all days?
                        // User formula: "Absent Days * Daily Rate". Daily rate = Salary / Working Days.
                        // Usually Unpaid leave on a working day is deducted.
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
            WorkSchedule schedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());

            // Count Late Days (Only first check-in per day)
            long lateCount = 0;

            // Filter logs for this employee
            List<AttendanceLog> employeeLogs = allLogs.stream()
                    .filter(l -> l.getEmployeeId().equals(emp.getId()) && l.getTimestamp() != null)
                    .collect(Collectors.toList());

            // Group by Date to find the first check-in of each day
            Map<LocalDate, List<AttendanceLog>> logsByDate = employeeLogs.stream()
                    .collect(Collectors.groupingBy(l -> l.getTimestamp().toLocalDate()));

            for (Map.Entry<LocalDate, List<AttendanceLog>> entry : logsByDate.entrySet()) {
                // Find earliest log for the day
                List<AttendanceLog> dailyLogs = entry.getValue();
                AttendanceLog firstLog = dailyLogs.stream()
                        .min((l1, l2) -> l1.getTimestamp().compareTo(l2.getTimestamp()))
                        .orElse(null);

                if (firstLog != null && schedule.getStartTime() != null) {
                    LocalTime checkInTime = firstLog.getTimestamp().toLocalTime();
                    LocalTime lateLimit = schedule.getStartTime().plusMinutes(schedule.getLateToleranceMinutes());
                    if (checkInTime.isAfter(lateLimit)) {
                        lateCount++;
                    }
                }
            }

            // Apply Penalty (Integer Division Logic: e.g. 3 lates = 1 penalty)
            if (schedule.getLatePenaltyThreshold() != null && schedule.getLatePenaltyThreshold() > 0
                    && schedule.getLatePenaltyDeduction() != null && schedule.getLatePenaltyDeduction() > 0) {
                long penaltyCount = lateCount / schedule.getLatePenaltyThreshold();
                penaltyDays = penaltyCount * schedule.getLatePenaltyDeduction();
            }

            // Financials
            double dailyRate = monthlySalary / 30.0; // Standard 30 days
            if (standardMonthlyWorkingDays > 0) {
                dailyRate = monthlySalary / standardMonthlyWorkingDays;
            }

            // Pro-Rata for New Joiners
            double eligibleSalary = monthlySalary;
            if (emp.getJoiningDate() != null) {
                // The existing pro-rata logic for baseSalary was:
                // baseSalary = monthlySalary * ((double) actualWorkingDays /
                // standardMonthlyWorkingDays);
                // We will integrate this into the final net salary calculation by deducting for
                // absent/unpaid days.
            }

            // Allowances & Bonuses
            double fixedAllowance = emp.getFixedAllowance() != null ? emp.getFixedAllowance() : 0.0;
            double bonus = payslip.getBonusAmount() != null ? payslip.getBonusAmount() : 0.0;

            // Deductions
            double absentDeduction = (absentDays + unpaidLeaveDays) * dailyRate; // Combine absent and unpaid leave
            double latePenaltyAmount = penaltyDays * dailyRate;
            double totalDeductions = absentDeduction + latePenaltyAmount;

            // Net Pay Formula
            double netSalary = (monthlySalary + fixedAllowance + bonus) - totalDeductions;

            payslip.setBasicSalary(monthlySalary);
            payslip.setAllowanceAmount(fixedAllowance);
            payslip.setBonusAmount(bonus);
            payslip.setDeductionAmount(Math.round(totalDeductions * 100.0) / 100.0);
            payslip.setNetSalary(Math.round(netSalary * 100.0) / 100.0);
            payslip.setTotalWorkingDays(standardMonthlyWorkingDays); // This is global standard, not employee specific
                                                                     // eligible days
            payslip.setPresentDays(presentDays);
            payslip.setAbsentDays(absentDays);
            payslip.setUnpaidLeaveDays(unpaidLeaveDays);
            payslip.setPaidLeaveDays(paidLeaveDays);

            // Late Penalty (Persist for UI)
            payslip.setLateDays((int) lateCount);
            payslip.setLatePenaltyAmount(latePenaltyAmount);

            payslipRepository.save(payslip);
        }
    }
}
