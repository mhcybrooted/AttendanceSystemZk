package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletResponse;

import root.cyb.mh.attendancesystem.model.*;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.repository.WorkScheduleRepository;
import root.cyb.mh.attendancesystem.service.PdfExportService;
import root.cyb.mh.attendancesystem.service.ReportService;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import root.cyb.mh.attendancesystem.dto.DailyAttendanceDto;
import root.cyb.mh.attendancesystem.dto.MonthlySummaryDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

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
    private root.cyb.mh.attendancesystem.repository.PublicHolidayRepository publicHolidayRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private PdfExportService pdfExportService;

    @Autowired
    private root.cyb.mh.attendancesystem.service.BadgeService badgeService;

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

            // Calculate Badges
            List<String> badges = badgeService.calculateBadges(monthlyReport, null);
            model.addAttribute("badges", badges);
        } else {
            model.addAttribute("daysPresent", 0);
            model.addAttribute("lateCount", 0);
            model.addAttribute("earlyCount", 0);
            model.addAttribute("leaveCount", 0);
            model.addAttribute("badges", new java.util.ArrayList<>());
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

        // 6. Next Holiday Countdown
        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.Optional<root.cyb.mh.attendancesystem.model.PublicHoliday> nextHoliday = publicHolidayRepository
                .findAll().stream()
                .filter(h -> h.getDate().isAfter(today))
                .sorted(java.util.Comparator.comparing(root.cyb.mh.attendancesystem.model.PublicHoliday::getDate))
                .findFirst();

        if (nextHoliday.isPresent()) {
            long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, nextHoliday.get().getDate());
            model.addAttribute("nextHoliday", nextHoliday.get());
            model.addAttribute("daysUntilHoliday", daysUntil);
        } else {
            model.addAttribute("nextHoliday", null);
        }

        // --- INSPIRATION METRICS (Global) ---
        // Fetch Global Data for Leaderboards
        List<Employee> allEmployees = employeeRepository.findAll();
        List<String> guestIds = allEmployees.stream().filter(Employee::isGuest).map(Employee::getId)
                .collect(Collectors.toList());

        // Daily Report for Global Stats
        List<DailyAttendanceDto> dailyReport = reportService
                .getDailyReport(now, null, null, org.springframework.data.domain.PageRequest.of(0, 5000)).getContent();

        // 1. Early Birds
        List<DailyAttendanceDto> earlyBirds = dailyReport.stream()
                .filter(d -> !guestIds.contains(d.getEmployeeId()))
                .filter(d -> d.getInTime() != null)
                .sorted(Comparator.comparing(DailyAttendanceDto::getInTime))
                .limit(5)
                .collect(Collectors.toList());
        model.addAttribute("earlyBirds", earlyBirds);

        // 2. Department Champion
        Map<String, List<DailyAttendanceDto>> byDept = dailyReport.stream()
                .filter(d -> d.getDepartmentName() != null && !d.getDepartmentName().equals("Unassigned"))
                .collect(Collectors.groupingBy(DailyAttendanceDto::getDepartmentName));

        String championDept = "N/A";
        double maxPercent = -1.0;
        for (Map.Entry<String, List<DailyAttendanceDto>> entry : byDept.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("Guest"))
                continue;
            long deptTotal = entry.getValue().size();
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

        // 3. Health Score
        long onTimeCount = dailyReport.stream()
                .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().equals("EARLY LEAVE"))
                .filter(d -> !d.getStatus().contains("LATE"))
                .count();
        long totalPresentCalculated = dailyReport.stream()
                .filter(d -> d.getStatus().contains("PRESENT") || d.getStatus().contains("LATE")
                        || d.getStatus().contains("EARLY"))
                .count();
        int healthScore = totalPresentCalculated > 0 ? (int) ((onTimeCount * 100) / totalPresentCalculated) : 0;
        model.addAttribute("healthScore", healthScore);

        // 4. Punctuality Stars & Streak
        List<Integer> monthList = java.util.Collections.singletonList(now.getMonthValue());
        List<MonthlySummaryDto> monthlyStats = reportService.getMonthlyReport(now.getYear(), monthList, null,
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();

        List<MonthlySummaryDto> punctualityStars = monthlyStats.stream()
                .filter(d -> !guestIds.contains(d.getEmployeeId()))
                .sorted(Comparator.comparingInt(MonthlySummaryDto::getPresentCount).reversed()
                        .thenComparingInt(MonthlySummaryDto::getLateCount))
                .limit(5)
                .collect(Collectors.toList());
        model.addAttribute("punctualityStars", punctualityStars);

        MonthlySummaryDto streakTop = punctualityStars.isEmpty() ? null : punctualityStars.get(0);
        model.addAttribute("streakEmployee", streakTop);

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

    @PostMapping("/employee/profile/upload")
    public String uploadProfilePicture(@RequestParam("file") MultipartFile file, Principal principal) {
        if (!file.isEmpty()) {
            try {
                String uploadDir = "uploads/";
                java.io.File directory = new java.io.File(uploadDir);
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                Path path = Paths.get(uploadDir + fileName);
                Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

                String employeeId = principal.getName();
                Employee employee = employeeRepository.findById(employeeId).orElse(null);
                if (employee != null) {
                    employee.setAvatarPath("/uploads/" + fileName);
                    employeeRepository.save(employee);
                }
            } catch (IOException e) {
                e.printStackTrace(); // Handle error gracefully in real app
            }
        }
        return "redirect:/employee/dashboard";
    }
}
