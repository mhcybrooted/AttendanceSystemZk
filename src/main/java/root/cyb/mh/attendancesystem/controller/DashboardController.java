package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import root.cyb.mh.attendancesystem.dto.DailyAttendanceDto;
import root.cyb.mh.attendancesystem.model.AttendanceLog;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.DepartmentRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.service.ReportService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import root.cyb.mh.attendancesystem.dto.MonthlySummaryDto;

@Controller
public class DashboardController {

        @Autowired
        private EmployeeRepository employeeRepository;

        @Autowired
        private DepartmentRepository departmentRepository;

        @Autowired
        private AttendanceLogRepository attendanceLogRepository;

        @Autowired
        private root.cyb.mh.attendancesystem.repository.LeaveRequestRepository leaveRequestRepository;

        @Autowired
        private ReportService reportService;

        @GetMapping({ "/", "/dashboard" })
        public String dashboard(Model model) {
                LocalDate today = LocalDate.now();

                // Fetch All Employees
                List<root.cyb.mh.attendancesystem.model.Employee> allEmployees = employeeRepository.findAll();

                // Identify Guests
                List<String> guestIds = allEmployees.stream()
                                .filter(root.cyb.mh.attendancesystem.model.Employee::isGuest)
                                .map(root.cyb.mh.attendancesystem.model.Employee::getId)
                                .collect(Collectors.toList());

                // Count Stats (Excluding Guests)
                long totalEmployees = allEmployees.size() - guestIds.size();
                long totalDepartments = departmentRepository.count();

                // Today's Attendance Stats (Present/Late/Early)
                List<DailyAttendanceDto> dailyReport = reportService
                                .getDailyReport(today, null, null,
                                                org.springframework.data.domain.PageRequest.of(0, 5000))
                                .getContent();

                // Filter out guests from daily report
                long presentCount = dailyReport.stream()
                                .filter(d -> !guestIds.contains(d.getEmployeeId()))
                                .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().contains("LATE")
                                                || d.getStatus().contains("EARLY"))
                                .count();

                // Recent Activity (Last 5 logs) - Keep showing all logs, or filter?
                // User said "Today's Status will skip if employee is guest".
                // Logs are "Recent Activity", likely okay to show anyone who punched.
                // But to be consistent with "excluding guests", maybe we should filter too?
                // The requirement was specific to "Today's Status" (the counters).
                // I will keep Recent Activity as is (raw logs) unless requested.
                List<AttendanceLog> recentLogs = attendanceLogRepository.findByTimestampBetween(
                                today.atStartOfDay(), today.atTime(LocalTime.MAX))
                                .stream()
                                .sorted(Comparator.comparing(AttendanceLog::getTimestamp).reversed())
                                .limit(5)
                                .collect(Collectors.toList());

                recentLogs.forEach(log -> {
                        employeeRepository.findById(log.getEmployeeId()).ifPresent(emp -> {
                        });
                });

                // Leaves Today (Excluding Guests)
                List<root.cyb.mh.attendancesystem.model.LeaveRequest> todayLeaves = leaveRequestRepository
                                .findByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatus(
                                                today, today,
                                                root.cyb.mh.attendancesystem.model.LeaveRequest.Status.APPROVED);

                long leaveCount = todayLeaves.stream()
                                .filter(l -> !guestIds.contains(l.getEmployee().getId()))
                                .count();

                // Late Count (Excluding Guests)
                long lateCount = dailyReport.stream()
                                .filter(d -> !guestIds.contains(d.getEmployeeId()))
                                .filter(d -> d.getStatus().contains("LATE"))
                                .count();

                // --- INSPIRATION METRICS ---

                // 1. Early Birds (Today's first 5 arrivals)
                // Filter present/late/early, exclude guests, sort by inTime
                List<DailyAttendanceDto> earlyBirds = dailyReport.stream()
                                .filter(d -> !guestIds.contains(d.getEmployeeId()))
                                .filter(d -> d.getInTime() != null)
                                .sorted(Comparator.comparing(DailyAttendanceDto::getInTime))
                                .limit(5)
                                .collect(Collectors.toList());
                model.addAttribute("earlyBirds", earlyBirds);

                // 2. Department Champion (Dept with highest Present %)
                // Group by Dept Name
                Map<String, List<DailyAttendanceDto>> byDept = dailyReport.stream()
                                .filter(d -> d.getDepartmentName() != null
                                                && !d.getDepartmentName().equals("Unassigned"))
                                .collect(Collectors.groupingBy(DailyAttendanceDto::getDepartmentName));

                String championDept = "N/A";
                double maxPercent = -1.0;

                for (Map.Entry<String, List<DailyAttendanceDto>> entry : byDept.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase("Guest"))
                                continue; // Explicitly skip Guest dept

                        long deptTotal = entry.getValue().size(); // All employees in dept
                        if (deptTotal == 0)
                                continue;

                        long deptPresent = entry.getValue().stream()
                                        .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().contains("LATE")
                                                        || d.getStatus().contains("EARLY"))
                                        .count();

                        double percent = (double) deptPresent / deptTotal;
                        if (percent > maxPercent) {
                                maxPercent = percent;
                                championDept = entry.getKey();
                        }
                }

                if (maxPercent <= 0) {
                        championDept = "No Data";
                        maxPercent = 0;
                }
                model.addAttribute("championDept", championDept);
                model.addAttribute("championPercent", Math.round(maxPercent * 100));

                // 3. Attendance Health Score (Company Wide On-Time %)
                // On Time = Present AND NOT Late
                long onTimeCount = dailyReport.stream()
                                // guest filtered already by service
                                .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().equals("EARLY LEAVE")) // Strictly
                                                                                                                       // "PRESENT"
                                                                                                                       // or
                                                                                                                       // "EARLY"
                                                                                                                       // (as
                                                                                                                       // long
                                                                                                                       // as
                                                                                                                       // not
                                                                                                                       // late)
                                .filter(d -> !d.getStatus().contains("LATE"))
                                .count();

                // Base for % is Total Present (or Total Employees? Usually Health is of those
                // who came, how many were on time? Or of all content?)
                // Let's do % of Present Employees who were On Time.
                long totalPresentCalculated = dailyReport.stream()
                                .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().contains("LATE")
                                                || d.getStatus().contains("EARLY"))
                                .count();

                int healthScore = totalPresentCalculated > 0 ? (int) ((onTimeCount * 100) / totalPresentCalculated) : 0;
                model.addAttribute("healthScore", healthScore);

                // 4. Punctuality Stars & Streak (From Monthly Report)
                // Fetch current month report
                List<Integer> monthList = java.util.Collections.singletonList(today.getMonthValue());
                List<MonthlySummaryDto> monthlyStats = reportService.getMonthlyReport(today.getYear(), monthList, null,
                                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();

                // Sort by Present Count DESC, Late Count ASC
                List<MonthlySummaryDto> punctualityStars = monthlyStats.stream()
                                // ReportService might include guests? Check getMonthlyReport logic.
                                // I don't recall explicit guest filter in getMonthlyReport.
                                // I should filter guests here to be safe.
                                .filter(d -> !guestIds.contains(d.getEmployeeId()))
                                .sorted(Comparator.comparingInt(MonthlySummaryDto::getPresentCount).reversed()
                                                .thenComparingInt(MonthlySummaryDto::getLateCount))
                                .limit(5)
                                .collect(Collectors.toList());
                model.addAttribute("punctualityStars", punctualityStars);

                // Streak / On Fire: Top Employee by Present Count
                MonthlySummaryDto streakTop = punctualityStars.isEmpty() ? null : punctualityStars.get(0);
                model.addAttribute("streakEmployee", streakTop);

                // Status Breakdown
                long absentCount = totalEmployees - presentCount - leaveCount;
                if (absentCount < 0)
                        absentCount = 0;

                model.addAttribute("totalEmployees", totalEmployees);
                model.addAttribute("totalDepartments", totalDepartments);
                model.addAttribute("presentCount", presentCount);
                model.addAttribute("absentCount", absentCount);
                model.addAttribute("leaveCount", leaveCount);
                model.addAttribute("lateCount", lateCount);
                model.addAttribute("recentLogs", recentLogs);

                // Chart 1: Today's Status (Donut)
                java.util.List<Long> statusChartData = java.util.Arrays.asList(presentCount, leaveCount,
                                absentCount);
                model.addAttribute("statusChartData", statusChartData);

                model.addAttribute("employeeMap", allEmployees.stream()
                                .collect(Collectors.toMap(root.cyb.mh.attendancesystem.model.Employee::getId,
                                                root.cyb.mh.attendancesystem.model.Employee::getName)));

                return "dashboard";
        }
}
