package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.HttpServletResponse;

import root.cyb.mh.attendancesystem.model.*;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.repository.WorkScheduleRepository;
import root.cyb.mh.attendancesystem.service.PdfExportService;
import root.cyb.mh.attendancesystem.service.ReportService;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class EmployeeDashboardController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    @Autowired
    private WorkScheduleRepository workScheduleRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private PdfExportService pdfExportService;

    @GetMapping("/employee/dashboard")
    public String dashboard(Model model, Principal principal) {
        String employeeId = principal.getName();
        Employee employee = employeeRepository.findById(employeeId).orElse(new Employee());

        // 1. Basic Employee Info
        model.addAttribute("employee", employee);

        // 2. Shift / Schedule Info (Global for now, as per ReportService logic)
        WorkSchedule schedule = workScheduleRepository.findAll().stream().findFirst().orElse(null);
        model.addAttribute("workSchedule", schedule);

        // 3. Recent Logs (Last 10)
        List<AttendanceLog> allLogs = attendanceLogRepository.findByEmployeeId(employeeId);
        List<AttendanceLog> recentLogs = allLogs.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(10)
                .collect(Collectors.toList());
        model.addAttribute("recentLogs", recentLogs);

        // 4. Monthly Stats Calculation
        calculateMonthlyStats(model, allLogs, schedule);

        return "employee-dashboard";
    }

    private void calculateMonthlyStats(Model model, List<AttendanceLog> allLogs, WorkSchedule schedule) {
        LocalDate now = LocalDate.now();
        YearMonth currentYearMonth = YearMonth.from(now);

        // Filter logs for current month
        List<AttendanceLog> monthLogs = allLogs.stream()
                .filter(log -> YearMonth.from(log.getTimestamp()).equals(currentYearMonth))
                .collect(Collectors.toList());

        // Days Present (Count distinct days)
        long daysPresent = monthLogs.stream()
                .map(log -> log.getTimestamp().toLocalDate())
                .distinct()
                .count();
        model.addAttribute("daysPresent", daysPresent);

        // Late & Early Stats
        int lateCount = 0;
        int earlyCount = 0;

        if (schedule != null) {
            for (LocalDate date = currentYearMonth.atDay(1); !date.isAfter(now); date = date.plusDays(1)) {
                LocalDate finalDate = date;
                List<AttendanceLog> dayLogs = monthLogs.stream()
                        .filter(log -> log.getTimestamp().toLocalDate().equals(finalDate))
                        .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                        .collect(Collectors.toList());

                if (!dayLogs.isEmpty()) {
                    // Check In (First Log)
                    LocalTime checkIn = dayLogs.get(0).getTimestamp().toLocalTime();
                    LocalTime startTime = schedule.getStartTime().plusMinutes(schedule.getLateToleranceMinutes()); // Fixed
                                                                                                                   // getter
                    if (checkIn.isAfter(startTime)) {
                        lateCount++;
                    }

                    // Check Out (Last Log) - Logic for Early Departure
                    // Only calculate if we have at least 2 logs (In and Out) or distinct timestamps
                    if (dayLogs.size() > 1) {
                        LocalTime checkOut = dayLogs.get(dayLogs.size() - 1).getTimestamp().toLocalTime();

                        // Calculate allowed earliest departure time (EndTime - Tolerance)
                        LocalTime allowedExitTime = schedule.getEndTime()
                                .minusMinutes(schedule.getEarlyLeaveToleranceMinutes());

                        // If check out is BEFORE Allowed Exit Time
                        if (checkOut.isBefore(allowedExitTime)) {
                            earlyCount++;
                        }
                    }
                }
            }
        }
        model.addAttribute("lateCount", lateCount);
        model.addAttribute("earlyCount", earlyCount);
    }

    @GetMapping("/employee/attendance/history")
    public String attendanceHistory(
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month,
            Model model, Principal principal) {

        String employeeId = principal.getName();

        // Defaults
        if (year == null)
            year = LocalDate.now().getYear();
        if (month == null)
            month = LocalDate.now().getMonthValue();

        // Fetch Data
        root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto historyData = reportService
                .getEmployeeMonthlyReport(employeeId, year, month);

        model.addAttribute("data", historyData);
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("activeLink", "history");

        return "employee-attendance-history";
    }

    @GetMapping("/employee/report/monthly/download")
    public void downloadMonthlyReport(HttpServletResponse response, Principal principal) throws Exception {
        String employeeId = principal.getName();
        // Generate for current month by default
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        // 1. Get Data DTO
        root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto reportData = reportService
                .getEmployeeMonthlyReport(employeeId, year, month);

        if (reportData == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Employee data not found");
            return;
        }

        // 2. Generate PDF
        byte[] pdfBytes = pdfExportService.exportEmployeeMonthlyReport(reportData);

        // 3. Write Response
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=My_Attendance_Report_" + month + "_" + year + ".pdf");
        response.getOutputStream().write(pdfBytes);
    }
}
