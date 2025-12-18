package root.cyb.mh.attendancesystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.dto.DailyAttendanceDto;
import root.cyb.mh.attendancesystem.model.AttendanceLog;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.model.WorkSchedule;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.repository.WorkScheduleRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    @Autowired
    private WorkScheduleRepository workScheduleRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.PublicHolidayRepository publicHolidayRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private ShiftService shiftService;

    public Page<DailyAttendanceDto> getDailyReport(LocalDate date, Long departmentId, Pageable pageable) {
        List<DailyAttendanceDto> report = new ArrayList<>();

        // Get Work Schedule
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());

        // Get Employees (Filter by Dept if provided)
        List<Employee> allFilteredEmployees;
        if (departmentId != null) {
            allFilteredEmployees = employeeRepository.findAll().stream()
                    .filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        } else {
            allFilteredEmployees = employeeRepository.findAll();
        }

        // Manual Pagination of the list
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allFilteredEmployees.size());
        List<Employee> employees = new ArrayList<>();
        if (start <= allFilteredEmployees.size()) {
            employees = allFilteredEmployees.subList(start, end);
        }

        // Get All Logs for the Date
        List<AttendanceLog> logs = attendanceLogRepository.findAll().stream()
                .filter(log -> log.getTimestamp().toLocalDate().equals(date))
                .collect(Collectors.toList());

        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();

        // Get Approved Leaves for the Date
        List<root.cyb.mh.attendancesystem.model.LeaveRequest> approvedLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED)
                .stream()
                .filter(l -> !date.isBefore(l.getStartDate()) && !date.isAfter(l.getEndDate()))
                .collect(Collectors.toList());

        for (Employee emp : employees) {
            DailyAttendanceDto dto = new DailyAttendanceDto();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getName());
            dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");

            WorkSchedule schedule = resolveSchedule(emp.getId(), date, globalSchedule);

            // Filter logs for this employee
            List<AttendanceLog> empLogs = logs.stream()
                    .filter(log -> log.getEmployeeId().equals(emp.getId()))
                    .sorted(Comparator.comparing(AttendanceLog::getTimestamp))
                    .collect(Collectors.toList());

            boolean isWeekend = schedule.getWeekendDays() != null
                    && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
            boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

            if (isWeekend || isPublicHoliday) {
                dto.setStatus("WEEKEND/HOLIDAY");
                dto.setStatusColor("secondary"); // Gray
                // If they came anyway, count as Present for daily view too
                if (!empLogs.isEmpty()) {
                    dto.setStatus("PRESENT (HOLIDAY)");
                    dto.setStatusColor("success");
                    LocalTime inTime = empLogs.get(0).getTimestamp().toLocalTime();
                    LocalTime outTime = empLogs.get(empLogs.size() - 1).getTimestamp().toLocalTime();
                    dto.setInTime(inTime);
                    dto.setOutTime(outTime);
                }
            } else if (empLogs.isEmpty()) {
                // Check for Leave
                boolean onLeave = approvedLeaves.stream().anyMatch(l -> l.getEmployee().getId().equals(emp.getId()));
                if (onLeave) {
                    root.cyb.mh.attendancesystem.model.LeaveRequest leave = approvedLeaves.stream()
                            .filter(l -> l.getEmployee().getId().equals(emp.getId()))
                            .findFirst().orElse(null);
                    String type = leave != null && leave.getLeaveType() != null ? leave.getLeaveType().toUpperCase()
                            : "";
                    dto.setStatus(!type.isEmpty() ? type + " LEAVE" : "ON LEAVE");
                    dto.setStatusColor("info"); // Blue
                } else {
                    dto.setStatus("ABSENT");
                    dto.setStatusColor("danger");
                }
            } else {
                LocalTime inTime = empLogs.get(0).getTimestamp().toLocalTime();
                LocalTime outTime = empLogs.get(empLogs.size() - 1).getTimestamp().toLocalTime();

                dto.setInTime(inTime);
                dto.setOutTime(outTime);

                // Check Status
                LocalTime lateThreshold = schedule.getStartTime().plusMinutes(schedule.getLateToleranceMinutes());
                LocalTime earlyThreshold = schedule.getEndTime().minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

                boolean isLate = inTime.isAfter(lateThreshold);
                boolean isEarly = outTime.isBefore(earlyThreshold);

                if (isLate && isEarly) {
                    dto.setStatus("LATE & EARLY LEAVE");
                    dto.setStatusColor("warning"); // Orange-ish
                } else if (isLate) {
                    dto.setStatus("LATE ENTRY");
                    dto.setStatusColor("warning");
                } else if (isEarly) {
                    dto.setStatus("EARLY LEAVE");
                    dto.setStatusColor("info"); // Light blue
                } else {
                    dto.setStatus("PRESENT");
                    dto.setStatusColor("success");
                }
            }
            report.add(dto);
        }

        return new PageImpl<>(report, pageable, allFilteredEmployees.size());
    }

    public Page<root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto> getWeeklyReport(LocalDate startOfWeek,
            Long departmentId, Pageable pageable) {
        List<root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto> report = new ArrayList<>();

        // Ensure start date works
        if (startOfWeek == null)
            startOfWeek = LocalDate.now()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        LocalDate endOfWeek = startOfWeek.plusDays(6);
        List<LocalDate> weekDates = startOfWeek.datesUntil(endOfWeek.plusDays(1)).collect(Collectors.toList());

        // Configs
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();

        List<Employee> allFilteredEmployees;
        if (departmentId != null) {
            allFilteredEmployees = employeeRepository.findAll().stream()
                    .filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        } else {
            allFilteredEmployees = employeeRepository.findAll();
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allFilteredEmployees.size());
        List<Employee> employees = new ArrayList<>();
        if (start <= allFilteredEmployees.size()) {
            employees = allFilteredEmployees.subList(start, end);
        }

        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfWeek.atStartOfDay(), endOfWeek.atTime(LocalTime.MAX));

        // Get Approved Leaves overlapping the week
        List<root.cyb.mh.attendancesystem.model.LeaveRequest> allLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

        for (Employee emp : employees) {
            root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto dto = new root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getName());
            dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");
            dto.setDailyStatus(new java.util.LinkedHashMap<>());

            int present = 0, absent = 0, late = 0, early = 0, leave = 0;

            for (LocalDate date : weekDates) {
                WorkSchedule schedule = resolveSchedule(emp.getId(), date, globalSchedule);

                // Check if Holiday/Weekend
                boolean isWeekend = schedule.getWeekendDays() != null
                        && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
                boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

                String status = "";

                // Get logs for this emp & date
                List<AttendanceLog> dailyLogs = allLogs.stream()
                        .filter(l -> l.getEmployeeId().equals(emp.getId())
                                && l.getTimestamp().toLocalDate().equals(date))
                        .sorted(Comparator.comparing(AttendanceLog::getTimestamp))
                        .collect(Collectors.toList());

                if (isWeekend || isPublicHoliday) {
                    status = "WEEKEND"; // or HOLIDAY
                    // If they came anyway, count as Present
                    if (!dailyLogs.isEmpty()) {
                        status = "PRESENT";
                        present++;

                        LocalTime inTime = dailyLogs.get(0).getTimestamp().toLocalTime();
                        LocalTime outTime = dailyLogs.get(dailyLogs.size() - 1).getTimestamp().toLocalTime();

                        LocalTime lateThreshold = schedule.getStartTime()
                                .plusMinutes(schedule.getLateToleranceMinutes());
                        LocalTime earlyThreshold = schedule.getEndTime()
                                .minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

                        if (inTime.isAfter(lateThreshold)) {
                            status = "LATE";
                            late++;
                        }
                        if (outTime.isBefore(earlyThreshold)) {
                            if (status.equals("LATE"))
                                status = "LATE_EARLY";
                            else
                                status = "EARLY";
                            early++;
                        }
                    }
                } else if (dailyLogs.isEmpty()) {
                    // Check Leave
                    boolean onLeave = isEmployeeOnLeave(emp.getId(), date, allLeaves);
                    if (onLeave) {
                        status = "LEAVE";
                        // Don't increment absent/present count for leave? Or separate count?
                        // But for Summary PDF we might want to know.
                        // Let's rely on string status for now.
                        leave++;
                    } else {
                        status = "ABSENT";
                        absent++;
                    }
                } else {
                    status = "PRESENT";
                    present++;

                    LocalTime inTime = dailyLogs.get(0).getTimestamp().toLocalTime();
                    LocalTime outTime = dailyLogs.get(dailyLogs.size() - 1).getTimestamp().toLocalTime();

                    LocalTime lateThreshold = schedule.getStartTime().plusMinutes(schedule.getLateToleranceMinutes());
                    LocalTime earlyThreshold = schedule.getEndTime()
                            .minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

                    if (inTime.isAfter(lateThreshold)) {
                        status = "LATE"; // Simplified for grid
                        late++;
                    }
                    if (outTime.isBefore(earlyThreshold)) {
                        if (status.equals("LATE"))
                            status = "LATE_EARLY";
                        else
                            status = "EARLY";
                        early++;
                    }
                }
                dto.getDailyStatus().put(date, status);
            }
            dto.setPresentCount(present);
            dto.setAbsentCount(absent);
            dto.setLateCount(late);
            dto.setEarlyLeaveCount(early);
            dto.setLeaveCount(leave);
            report.add(dto);
        }
        return new PageImpl<>(report, pageable, allFilteredEmployees.size());
    }

    public root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto getEmployeeWeeklyReport(String employeeId,
            LocalDate startOfWeek) {
        root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto dto = new root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto();

        // Find Employee
        Employee emp = employeeRepository.findById(employeeId).orElse(null);
        if (emp == null)
            return null;

        dto.setEmployeeName(emp.getName());
        dto.setEmployeeId(emp.getId());
        dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");

        // Dates
        if (startOfWeek == null)
            startOfWeek = LocalDate.now()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        LocalDate endOfWeek = startOfWeek.plusDays(6);
        dto.setStartOfWeek(startOfWeek);
        dto.setEndOfWeek(endOfWeek);

        List<LocalDate> weekDates = startOfWeek.datesUntil(endOfWeek.plusDays(1)).collect(Collectors.toList());

        // Configs
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();
        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfWeek.atStartOfDay(), endOfWeek.atTime(LocalTime.MAX));

        List<root.cyb.mh.attendancesystem.model.LeaveRequest> allLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

        List<root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail> details = new ArrayList<>();
        int present = 0, absent = 0, late = 0, early = 0, leaves = 0;

        for (LocalDate date : weekDates) {
            WorkSchedule schedule = resolveSchedule(emp.getId(), date, globalSchedule);
            root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail daily = new root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail();
            daily.setDate(date);
            daily.setDayOfWeek(date.getDayOfWeek().name());

            // Check keys
            boolean isWeekend = schedule.getWeekendDays() != null
                    && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
            boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

            // Logs
            List<AttendanceLog> dailyLogs = allLogs.stream()
                    .filter(l -> l.getEmployeeId().equals(emp.getId())
                            && l.getTimestamp().toLocalDate().equals(date))
                    .sorted(Comparator.comparing(AttendanceLog::getTimestamp))
                    .collect(Collectors.toList());

            String status = "";
            String color = "";

            if (isWeekend || isPublicHoliday) {
                status = "WEEKEND";
                color = "secondary";
                if (isPublicHoliday)
                    status = "HOLIDAY";

                if (!dailyLogs.isEmpty()) {
                    status = "PRESENT (" + status + ")";
                    color = "success";
                    present++;

                    // Calc timings
                    processTimings(daily, dailyLogs, schedule);
                    // Check late/early but don't strictly flag as 'LATE' stats if it's a holiday,
                    // unless we want to track overtime strictness. Let's just show times.
                }
            } else if (dailyLogs.isEmpty()) {
                boolean onLeave = isEmployeeOnLeave(emp.getId(), date, allLeaves);
                if (onLeave) {
                    status = "LEAVE";
                    color = "info";
                    leaves++;
                } else {
                    status = "ABSENT";
                    color = "danger";
                    absent++;
                }
            } else {
                status = "PRESENT";
                color = "success";
                present++;

                processTimings(daily, dailyLogs, schedule);

                if (daily.getLateDurationMinutes() > 0) {
                    late++;
                    if (status.equals("PRESENT"))
                        status = "LATE";
                }
                if (daily.getEarlyLeaveDurationMinutes() > 0) {
                    early++;
                    if (status.equals("PRESENT"))
                        status = "EARLY";
                    else if (status.equals("LATE"))
                        status = "LATE & EARLY";
                }

                // Colors for issues
                if (status.contains("LATE") || status.contains("EARLY")) {
                    if (status.contains("&"))
                        color = "warning"; // Late & Early
                    else if (status.contains("LATE"))
                        color = "warning";
                    else
                        color = "info"; // Early
                }
            }

            daily.setStatus(status);
            daily.setStatusColor(color);
            details.add(daily);
        }

        dto.setDailyDetails(details);
        dto.setTotalPresent(present);
        dto.setTotalAbsent(absent);
        dto.setTotalLates(late);
        dto.setTotalEarlyLeaves(early);

        return dto;
    }

    private void processTimings(root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail daily,
            List<AttendanceLog> logs, WorkSchedule schedule) {
        LocalTime inTime = logs.get(0).getTimestamp().toLocalTime();
        LocalTime outTime = logs.get(logs.size() - 1).getTimestamp().toLocalTime();

        daily.setInTime(inTime);
        daily.setOutTime(outTime);

        // Thresholds
        LocalTime lateThreshold = schedule.getStartTime().plusMinutes(schedule.getLateToleranceMinutes());
        LocalTime earlyThreshold = schedule.getEndTime().minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

        if (inTime.isAfter(lateThreshold)) {
            java.time.Duration diff = java.time.Duration.between(schedule.getStartTime(), inTime);
            // We count lateness from the Start Time, not the threshold
            daily.setLateDurationMinutes(diff.toMinutes());
        }

        if (outTime.isBefore(earlyThreshold)) {
            java.time.Duration diff = java.time.Duration.between(outTime, schedule.getEndTime());
            daily.setEarlyLeaveDurationMinutes(diff.toMinutes());
        }
    }

    public Page<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> getMonthlyReport(int year, int month,
            Long departmentId, Pageable pageable) {
        List<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> report = new ArrayList<>();

        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());

        List<LocalDate> monthDates = startOfMonth.datesUntil(endOfMonth.plusDays(1)).collect(Collectors.toList());

        // Configs
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();
        int defaultQuota = globalSchedule.getDefaultAnnualLeaveQuota() != null
                ? globalSchedule.getDefaultAnnualLeaveQuota()
                : 12;

        // Filter Employees
        List<Employee> allFilteredEmployees;
        if (departmentId != null) {
            allFilteredEmployees = employeeRepository.findAll().stream()
                    .filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        } else {
            allFilteredEmployees = employeeRepository.findAll();
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allFilteredEmployees.size());
        List<Employee> employees = new ArrayList<>();
        if (start <= allFilteredEmployees.size()) {
            employees = allFilteredEmployees.subList(start, end);
        }

        // Fetch Logs
        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfMonth.atStartOfDay(), endOfMonth.atTime(LocalTime.MAX));

        List<root.cyb.mh.attendancesystem.model.LeaveRequest> allLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

        for (Employee emp : employees) {
            root.cyb.mh.attendancesystem.dto.MonthlySummaryDto dto = new root.cyb.mh.attendancesystem.dto.MonthlySummaryDto();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getName());
            dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");

            int present = 0, absent = 0, late = 0, early = 0, leave = 0;
            int paidLeave = 0, unpaidLeave = 0;

            // Calculate Remaining Quota at Start of Month
            int effectiveQuota = emp.getEffectiveQuota(defaultQuota);
            int leavesTakenBefore = countYearlyLeavesBeforeMonth(emp.getId(), year, month, allLeaves);
            int remainingQuota = Math.max(0, effectiveQuota - leavesTakenBefore);

            for (LocalDate date : monthDates) {
                WorkSchedule schedule = resolveSchedule(emp.getId(), date, globalSchedule);

                // Priority 1: Check Leave FIRST overrides everything (Logs, Weekend, etc.)
                boolean onLeave = isEmployeeOnLeave(emp.getId(), date, allLeaves);
                if (onLeave) {
                    leave++;
                    if (remainingQuota > 0) {
                        paidLeave++;
                        remainingQuota--;
                    } else {
                        unpaidLeave++;
                    }
                    continue; // Skip further processing for this day
                }

                // Check keys
                boolean isWeekend = schedule.getWeekendDays() != null
                        && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
                boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

                // Logs for day
                List<AttendanceLog> dailyLogs = allLogs.stream()
                        .filter(l -> l.getEmployeeId().equals(emp.getId())
                                && l.getTimestamp().toLocalDate().equals(date))
                        .sorted(Comparator.comparing(AttendanceLog::getTimestamp))
                        .collect(Collectors.toList());

                if (!dailyLogs.isEmpty()) {
                    // Priority 2: PRESENT (Logs exist and NOT on Leave)
                    present++;

                    // Only count Late/Early if it is NOT a weekend/holiday (Working Day)
                    if (!isWeekend && !isPublicHoliday) {
                        LocalTime inTime = dailyLogs.get(0).getTimestamp().toLocalTime();
                        LocalTime outTime = dailyLogs.get(dailyLogs.size() - 1).getTimestamp().toLocalTime();

                        LocalTime lateThreshold = schedule.getStartTime()
                                .plusMinutes(schedule.getLateToleranceMinutes());
                        LocalTime earlyThreshold = schedule.getEndTime()
                                .minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

                        if (inTime.isAfter(lateThreshold)) {
                            late++;
                        }
                        if (outTime.isBefore(earlyThreshold)) {
                            early++;
                        }
                    }
                } else {
                    // Priority 3: NO LOGS
                    if (isWeekend || isPublicHoliday) {
                        // WEEKEND/HOLIDAY
                        // Do nothing, just off
                    } else {
                        // ABSENT
                        absent++;
                    }
                }
            }

            dto.setPresentCount(present);
            dto.setAbsentCount(absent);
            dto.setLateCount(late);
            dto.setEarlyLeaveCount(early);
            dto.setLeaveCount(leave);
            dto.setPaidLeaveCount(paidLeave);
            dto.setUnpaidLeaveCount(unpaidLeave);
            report.add(dto);
        }

        return new PageImpl<>(report, pageable, allFilteredEmployees.size());
    }

    public root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto getEmployeeMonthlyReport(String employeeId,
            int year, int month) {
        root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto dto = new root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto();

        // Find Employee
        Employee emp = employeeRepository.findById(employeeId).orElse(null);
        if (emp == null)
            return null;

        dto.setEmployeeName(emp.getName());
        dto.setEmployeeId(emp.getId());
        dto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");
        dto.setYear(year);
        dto.setMonth(month);

        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());

        List<LocalDate> monthDates = startOfMonth.datesUntil(endOfMonth.plusDays(1)).collect(Collectors.toList());

        // Configs
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        List<root.cyb.mh.attendancesystem.model.PublicHoliday> holidays = publicHolidayRepository.findAll();
        int defaultQuota = globalSchedule.getDefaultAnnualLeaveQuota() != null
                ? globalSchedule.getDefaultAnnualLeaveQuota()
                : 12;

        List<AttendanceLog> allLogs = attendanceLogRepository.findByTimestampBetween(
                startOfMonth.atStartOfDay(), endOfMonth.atTime(LocalTime.MAX));

        List<root.cyb.mh.attendancesystem.model.LeaveRequest> allLeaves = leaveRequestRepository
                .findByStatusOrderByCreatedAtDesc(root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

        List<root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail> details = new ArrayList<>();
        int present = 0, absent = 0, late = 0, early = 0, leaves = 0;
        int paidLeaves = 0, unpaidLeaves = 0;

        // Calculate Remaining Quota
        int effectiveQuota = emp.getEffectiveQuota(defaultQuota);
        int leavesTakenBefore = countYearlyLeavesBeforeMonth(emp.getId(), year, month, allLeaves);
        int remainingQuota = Math.max(0, effectiveQuota - leavesTakenBefore);

        for (LocalDate date : monthDates) {
            WorkSchedule schedule = resolveSchedule(emp.getId(), date, globalSchedule);

            root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail daily = new root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto.DailyDetail();
            daily.setDate(date);
            daily.setDayOfWeek(date.getDayOfWeek().name());

            // Priority 1: Check Leave FIRST overrides everything
            boolean onLeave = isEmployeeOnLeave(emp.getId(), date, allLeaves);
            if (onLeave) {
                leaves++;
                String leaveType = remainingQuota > 0 ? "PAID LEAVE" : "UNPAID LEAVE";
                if (remainingQuota > 0) {
                    paidLeaves++;
                    remainingQuota--;
                } else {
                    unpaidLeaves++;
                }

                daily.setStatus(leaveType);
                daily.setStatusColor("info");
                details.add(daily);
                continue; // Skip further processing for this day
            }

            // Check keys
            boolean isWeekend = schedule.getWeekendDays() != null
                    && schedule.getWeekendDays().contains(String.valueOf(date.getDayOfWeek().getValue()));
            boolean isPublicHoliday = holidays.stream().anyMatch(h -> h.getDate().equals(date));

            // Logs
            List<AttendanceLog> dailyLogs = allLogs.stream()
                    .filter(l -> l.getEmployeeId().equals(emp.getId())
                            && l.getTimestamp().toLocalDate().equals(date))
                    .sorted(Comparator.comparing(AttendanceLog::getTimestamp))
                    .collect(Collectors.toList());

            String status = "";
            String color = "";

            if (!dailyLogs.isEmpty()) {
                // Priority 2: PRESENT
                present++;
                status = "PRESENT";
                color = "success";

                processTimings(daily, dailyLogs, schedule); // calculates late/early minutes

                if (isWeekend || isPublicHoliday) {
                    status = "PRESENT (" + (isPublicHoliday ? "HOLIDAY" : "WEEKEND") + ")";
                } else {
                    // Working Day: Update Counters and Status Label for deviations
                    if (daily.getLateDurationMinutes() > 0) {
                        late++;
                        status = "LATE";
                    }
                    if (daily.getEarlyLeaveDurationMinutes() > 0) {
                        early++;
                        if (status.equals("PRESENT"))
                            status = "EARLY";
                        else if (status.equals("LATE"))
                            status = "LATE & EARLY";
                    }

                    if (status.contains("LATE") || status.contains("EARLY")) {
                        if (status.contains("&"))
                            color = "warning";
                        else if (status.contains("LATE"))
                            color = "warning";
                        else
                            color = "info";
                    }
                }
            } else {
                // Priority 3: NO LOGS
                if (isWeekend || isPublicHoliday) {
                    // WEEKEND/HOLIDAY
                    status = isPublicHoliday ? "HOLIDAY" : "WEEKEND";
                    color = "secondary";
                } else {
                    // Priority 4: ABSENT
                    status = "ABSENT";
                    color = "danger";
                    absent++;
                }
            }

            daily.setStatus(status);
            daily.setStatusColor(color);
            details.add(daily);
        }

        dto.setDailyDetails(details);
        dto.setTotalPresent(present);
        dto.setTotalAbsent(absent);
        dto.setTotalLates(late);
        dto.setTotalEarlyLeaves(early);
        dto.setTotalLeaves(leaves);
        dto.setPaidLeavesCount(paidLeaves);
        dto.setUnpaidLeavesCount(unpaidLeaves);

        return dto;
    }

    private int countYearlyLeavesBeforeMonth(String employeeId, int year, int month,
            List<root.cyb.mh.attendancesystem.model.LeaveRequest> allLeaves) {
        LocalDate startOfYear = LocalDate.of(year, 1, 1);
        LocalDate startOfMonth = LocalDate.of(year, month, 1);

        return (int) allLeaves.stream()
                .filter(l -> l.getEmployee().getId().equals(employeeId))
                // Filter leaves that end after start of year and start before start of month
                .filter(l -> !l.getEndDate().isBefore(startOfYear) && l.getStartDate().isBefore(startOfMonth))
                .mapToLong(l -> {
                    // Calculate intersection with [StartOfYear, StartOfMonth)
                    LocalDate s = l.getStartDate().isBefore(startOfYear) ? startOfYear : l.getStartDate();
                    LocalDate e = l.getEndDate().isAfter(startOfMonth.minusDays(1)) ? startOfMonth.minusDays(1)
                            : l.getEndDate();

                    if (s.isAfter(e))
                        return 0;

                    return java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1;
                })
                .sum();
    }

    private boolean isEmployeeOnLeave(String employeeId, LocalDate date,
            List<root.cyb.mh.attendancesystem.model.LeaveRequest> leaves) {
        return leaves.stream()
                .anyMatch(l -> l.getEmployee().getId().equals(employeeId)
                        && !date.isBefore(l.getStartDate())
                        && !date.isAfter(l.getEndDate()));
    }

    public root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto getEmployeeRangeReport(
            String employeeId, LocalDate startDate, LocalDate endDate) {

        root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto rangeDto = new root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto();
        rangeDto.setEmployeeId(employeeId);
        rangeDto.setStartDate(startDate);
        rangeDto.setEndDate(endDate);

        // Find Employee for basic info
        Employee emp = employeeRepository.findById(employeeId).orElse(null);
        if (emp != null) {
            rangeDto.setEmployeeName(emp.getName());
            rangeDto.setDepartmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned");
        }

        List<root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto> monthlyReports = new ArrayList<>();

        LocalDate current = startDate.withDayOfMonth(1);
        LocalDate endLoop = endDate.withDayOfMonth(1); // Compare months

        while (!current.isAfter(endLoop)) {
            int y = current.getYear();
            int m = current.getMonthValue();

            root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto monthDto = getEmployeeMonthlyReport(employeeId, y,
                    m);
            if (monthDto != null) {
                monthlyReports.add(monthDto);

                // Aggregate Stats
                rangeDto.setTotalPresent(rangeDto.getTotalPresent() + monthDto.getTotalPresent());
                rangeDto.setTotalAbsent(rangeDto.getTotalAbsent() + monthDto.getTotalAbsent());
                rangeDto.setTotalLates(rangeDto.getTotalLates() + monthDto.getTotalLates());
                rangeDto.setTotalEarlyLeaves(rangeDto.getTotalEarlyLeaves() + monthDto.getTotalEarlyLeaves());
                rangeDto.setTotalLeaves(rangeDto.getTotalLeaves() + monthDto.getTotalLeaves());
                rangeDto.setTotalPaidLeaves(rangeDto.getTotalPaidLeaves() + monthDto.getPaidLeavesCount());
                rangeDto.setTotalUnpaidLeaves(rangeDto.getTotalUnpaidLeaves() + monthDto.getUnpaidLeavesCount());
            }

            current = current.plusMonths(1);
        }

        rangeDto.setMonthlyReports(monthlyReports);
        return rangeDto;
    }

    public WorkSchedule resolveSchedule(String employeeId, LocalDate date, WorkSchedule globalDefault) {
        root.cyb.mh.attendancesystem.model.Shift specificShift = shiftService.getShiftForDate(employeeId, date);
        if (specificShift == null) {
            return globalDefault;
        }
        WorkSchedule effective = new WorkSchedule();
        effective.setStartTime(specificShift.getStartTime());
        effective.setEndTime(specificShift.getEndTime());
        effective.setLateToleranceMinutes(specificShift.getLateToleranceMinutes());
        effective.setEarlyLeaveToleranceMinutes(specificShift.getEarlyLeaveToleranceMinutes());
        effective.setWeekendDays(globalDefault.getWeekendDays());
        effective.setDefaultAnnualLeaveQuota(globalDefault.getDefaultAnnualLeaveQuota());
        return effective;
    }
}
