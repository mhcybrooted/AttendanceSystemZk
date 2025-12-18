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
    private root.cyb.mh.attendancesystem.repository.LeaveRequestRepository leaveRequestRepository;

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

        // 2. Shift / Schedule Info (Today's Effective Schedule)
        WorkSchedule globalSchedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        WorkSchedule todaySchedule = reportService.resolveSchedule(employeeId, LocalDate.now(), globalSchedule);
        model.addAttribute("workSchedule", todaySchedule);

        // 3. Recent Logs (Last 10)
        List<AttendanceLog> allLogs = attendanceLogRepository.findByEmployeeId(employeeId);
        List<AttendanceLog> recentLogs = allLogs.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(10)
                .collect(Collectors.toList());
        model.addAttribute("recentLogs", recentLogs);

        // 4. Monthly Stats (Using ReportService for Dynamic Logic)
        LocalDate now = LocalDate.now();
        root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto monthlyReport = reportService
                .getEmployeeMonthlyReport(
                        employeeId, now.getYear(), now.getMonthValue());

        if (monthlyReport != null) {
            model.addAttribute("daysPresent", monthlyReport.getTotalPresent());
            model.addAttribute("lateCount", monthlyReport.getTotalLates());
            model.addAttribute("earlyCount", monthlyReport.getTotalEarlyLeaves());
            model.addAttribute("leaveCount", monthlyReport.getTotalLeaves());
        } else {
            model.addAttribute("daysPresent", 0);
            model.addAttribute("lateCount", 0);
            model.addAttribute("earlyCount", 0);
            model.addAttribute("leaveCount", 0);
        }

        // 5. Annual Quota Stats (Using Range Report for Year)
        int defaultQuota = globalSchedule.getDefaultAnnualLeaveQuota() != null
                ? globalSchedule.getDefaultAnnualLeaveQuota()
                : 12;
        int quota = employee.getEffectiveQuota(defaultQuota);

        LocalDate startOfYear = LocalDate.of(now.getYear(), 1, 1);
        LocalDate endOfYear = LocalDate.of(now.getYear(), 12, 31);

        root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto annualReport = reportService.getEmployeeRangeReport(
                employeeId, startOfYear, endOfYear);

        int totalTaken = annualReport != null ? annualReport.getTotalLeaves() : 0;
        // Logic for paid/unpaid split is in RangeReport? Yes, totalPaidLeaves
        int paidTaken = annualReport != null ? annualReport.getTotalPaidLeaves() : 0;
        int unpaidTaken = annualReport != null ? annualReport.getTotalUnpaidLeaves() : 0;

        model.addAttribute("annualQuota", quota);
        model.addAttribute("yearlyLeavesTaken", totalTaken);
        model.addAttribute("paidLeavesTaken", paidTaken);
        model.addAttribute("unpaidLeavesTaken", unpaidTaken);

        return "employee-dashboard";
    }

    @GetMapping("/employee/attendance/history")
    public String attendanceHistory(
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month,
            Model model, Principal principal) {

        String employeeId = principal.getName();
        LocalDate now = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;

        // Determine Dates based on Period
        if ("3M".equals(period)) {
            endDate = now;
            startDate = now.minusMonths(2).withDayOfMonth(1); // Current + prev 2
        } else if ("6M".equals(period)) {
            endDate = now;
            startDate = now.minusMonths(5).withDayOfMonth(1);
        } else if ("1Y".equals(period)) {
            endDate = now;
            startDate = now.minusMonths(11).withDayOfMonth(1);
        } else {
            // Specific Month (Default)
            if (year == null)
                year = now.getYear();
            if (month == null)
                month = now.getMonthValue();

            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
            period = "MONTH"; // Default marker
        }

        // Fetch Range Data
        root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto rangeData = reportService
                .getEmployeeRangeReport(employeeId, startDate, endDate);

        model.addAttribute("data", rangeData);
        model.addAttribute("selectedPeriod", period);
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("activeLink", "history");

        return "employee-attendance-history";
    }

    @GetMapping("/employee/report/monthly/download")
    public void downloadAttendanceReport(
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month,
            HttpServletResponse response, Principal principal) throws Exception {

        String employeeId = principal.getName();
        LocalDate now = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;

        // Determine Dates (Same logic as View)
        if ("3M".equals(period)) {
            endDate = now;
            startDate = now.minusMonths(2).withDayOfMonth(1);
        } else if ("6M".equals(period)) {
            endDate = now;
            startDate = now.minusMonths(5).withDayOfMonth(1);
        } else if ("1Y".equals(period)) {
            endDate = now;
            startDate = now.minusMonths(11).withDayOfMonth(1);
        } else {
            // Specific Month (Default)
            if (year == null)
                year = now.getYear();
            if (month == null)
                month = now.getMonthValue();

            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
            period = "MONTH_" + month + "_" + year;
        }

        // 1. Get Range Data
        root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto reportData = reportService
                .getEmployeeRangeReport(employeeId, startDate, endDate);

        if (reportData == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Employee data not found");
            return;
        }

        // 2. Generate PDF using Range Export
        // Note: Even for a single month, we now use the Range logic (List of 1 report)
        // via exportEmployeeRangeReport
        // or we could keep the old one. But Range logic is cleaner as it handles the
        // list loop.
        byte[] pdfBytes = pdfExportService.exportEmployeeRangeReport(reportData);

        // 3. Write Response
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=Attendance_Report_" + period + "_" + System.currentTimeMillis() + ".pdf");
        response.getOutputStream().write(pdfBytes);
    }
}
