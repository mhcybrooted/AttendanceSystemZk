package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import root.cyb.mh.attendancesystem.service.ReportService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Controller
public class ReportController {

        @Autowired
        private ReportService reportService;

        @Autowired
        private root.cyb.mh.attendancesystem.repository.DepartmentRepository departmentRepository;

        @GetMapping("/reports")
        public String reports(@RequestParam(required = false) LocalDate date,
                        @RequestParam(required = false) Long departmentId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "name") String sortField,
                        @RequestParam(defaultValue = "asc") String sortDir,
                        Model model) {
                if (date == null) {
                        date = LocalDate.now();
                }

                // Create Pageable
                // Note: We sort in memory for now because the service returns a Page of DTOs
                // built from a List,
                // and the DTOs are not Entities. The Service handles "Paginate First, Then
                // Calculate".
                // However, standard Spring Pageable sorting passed to repo works on *Entities*.
                // Here, our service manually slices. So passing 'sort' to the service's
                // Pageable
                // might not effect the final list order if we don't apply it manually or if we
                // rely on the sublist.
                // Given the service logic:
                // 1. Fetches ALL employees (filtered by dept).
                // 2. Slices them (applied page/size).
                // 3. Generates report for that slice.
                // So the "Page" contains only those employees.
                // If we want to sort by *Employee Name* (which is default), we should sort the
                // Employee LIST before slicing.
                // If implementation is 'findAll' then stream, we can sort there.
                // But we didn't add sorting to the service logic yet (it slices a raw list).
                // For now, let's pass unsorted Pageable to get the slice, and assume default ID
                // order or whatever findAll returns.
                // IMPROVEMENT: The service should sort the 'allFilteredEmployees' list before
                // slicing if we want consistent paging.
                // But I will stick to the basic pagination integration first.

                org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page,
                                size);

                org.springframework.data.domain.Page<root.cyb.mh.attendancesystem.dto.DailyAttendanceDto> reportPage = reportService
                                .getDailyReport(date, departmentId, pageable);

                // Sorting the *current page* content (DTOs)
                List<root.cyb.mh.attendancesystem.dto.DailyAttendanceDto> reportContent = new java.util.ArrayList<>(
                                reportPage.getContent());

                // ... (Existing comparator logic) ...
                java.util.Comparator<root.cyb.mh.attendancesystem.dto.DailyAttendanceDto> comparator = null;
                switch (sortField) {
                        case "department":
                                comparator = java.util.Comparator.comparing(
                                                dto -> dto.getDepartmentName() != null ? dto.getDepartmentName() : "");
                                break;
                        case "inTime":
                                comparator = java.util.Comparator.comparing(
                                                dto -> dto.getInTime() != null ? dto.getInTime() : LocalTime.MAX);
                                break;
                        case "outTime":
                                comparator = java.util.Comparator.comparing(
                                                dto -> dto.getOutTime() != null ? dto.getOutTime() : LocalTime.MIN);
                                break;
                        case "status":
                                comparator = java.util.Comparator.comparing(
                                                root.cyb.mh.attendancesystem.dto.DailyAttendanceDto::getStatus);
                                break;
                        case "name":
                        default:
                                comparator = java.util.Comparator.comparing(
                                                root.cyb.mh.attendancesystem.dto.DailyAttendanceDto::getEmployeeName);
                                break;
                }

                if ("desc".equalsIgnoreCase(sortDir)) {
                        comparator = comparator.reversed();
                }

                if (comparator != null) {
                        reportContent.sort(comparator);
                }

                // Re-wrap in PageImpl?
                // Ideally we shouldn't sort *after* paging if we want global sort, but with
                // "Paginate Employees First",
                // we are sorting the *result set of that page*. This is acceptable for "View by
                // Page" especially if default order is consistent.
                // But strictly speaking, we should sort employees first.
                // I will display the reportPage directly but replacing content with sorted
                // content is hard on a Page object without creating new.
                // Let's just update the list reference if possible? No, Page is immutable-ish.
                // Actually, let's just pass `reportContent` as "report" and pass `reportPage`
                // as "page".

                model.addAttribute("report", reportContent);
                model.addAttribute("page", reportPage);

                model.addAttribute("date", date);
                model.addAttribute("departments", departmentRepository.findAll());
                model.addAttribute("selectedDeptId", departmentId);

                model.addAttribute("sortField", sortField);
                model.addAttribute("sortDir", sortDir);
                model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

                return "reports";
        }

        @GetMapping("/reports/weekly")
        public String weeklyReports(@RequestParam(required = false) LocalDate date,
                        @RequestParam(required = false) Long departmentId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "name") String sortField,
                        @RequestParam(defaultValue = "asc") String sortDir,
                        Model model) {
                if (date == null) {
                        date = LocalDate.now();
                }
                // Adjust to start of week (Monday)
                LocalDate startOfWeek = date
                                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

                // Generate headers (Mon 12, Tue 13...)
                List<String> headers = new java.util.ArrayList<>();
                LocalDate current = startOfWeek;
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("EEE dd");
                for (int i = 0; i < 7; i++) {
                        headers.add(current.format(formatter));
                        current = current.plusDays(1);
                }

                org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page,
                                size);
                org.springframework.data.domain.Page<root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto> reportPage = reportService
                                .getWeeklyReport(startOfWeek, departmentId, pageable);

                List<root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto> reportContent = new java.util.ArrayList<>(
                                reportPage.getContent());

                // Sorting
                java.util.Comparator<root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto> comparator = null;
                switch (sortField) {
                        case "present":
                                comparator = java.util.Comparator.comparingInt(
                                                root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto::getPresentCount);
                                break;
                        case "absent":
                                comparator = java.util.Comparator.comparingInt(
                                                root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto::getAbsentCount);
                                break;
                        case "late":
                                comparator = java.util.Comparator.comparingInt(
                                                root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto::getLateCount);
                                break;
                        case "early":
                                comparator = java.util.Comparator.comparingInt(
                                                root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto::getEarlyLeaveCount);
                                break;
                        case "leave":
                                comparator = java.util.Comparator.comparingInt(
                                                root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto::getLeaveCount);
                                break;
                        case "name":
                        default:
                                comparator = java.util.Comparator.comparing(
                                                root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto::getEmployeeName);
                                break;
                }

                if ("desc".equalsIgnoreCase(sortDir)) {
                        comparator = comparator.reversed();
                }
                if (comparator != null) {
                        reportContent.sort(comparator);
                }

                model.addAttribute("startOfWeek", startOfWeek);
                model.addAttribute("headers", headers);
                model.addAttribute("weekDates",
                                startOfWeek.datesUntil(startOfWeek.plusDays(7))
                                                .collect(java.util.stream.Collectors.toList()));

                model.addAttribute("departments", departmentRepository.findAll());
                model.addAttribute("selectedDept", departmentId);
                model.addAttribute("report", reportContent);
                model.addAttribute("page", reportPage);

                model.addAttribute("sortField", sortField);
                model.addAttribute("sortDir", sortDir);
                model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

                return "reports-weekly";
        }

        @GetMapping("/reports/weekly/{employeeId}")
        public String employeeWeeklyReport(@org.springframework.web.bind.annotation.PathVariable String employeeId,
                        @RequestParam(required = false) LocalDate date,
                        Model model) {
                if (date == null)
                        date = LocalDate.now();
                LocalDate startOfWeek = date
                                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

                model.addAttribute("report", reportService.getEmployeeWeeklyReport(employeeId, startOfWeek));
                return "reports-employee-weekly";
        }

        @GetMapping("/reports/monthly")
        public String monthlyReports(@RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Long departmentId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "name") String sortField,
                        @RequestParam(defaultValue = "asc") String sortDir,
                        Model model) {
                if (year == null || month == null) {
                        LocalDate now = LocalDate.now();
                        year = now.getYear();
                        month = now.getMonthValue();
                }

                org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page,
                                size);
                org.springframework.data.domain.Page<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> reportPage = reportService
                                .getMonthlyReport(year,
                                                month, departmentId, pageable);

                List<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> reportContent = new java.util.ArrayList<>(
                                reportPage.getContent());

                // Sorting
                java.util.Comparator<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> comparator = null;
                switch (sortField) {
                        case "department":
                                comparator = java.util.Comparator.comparing(
                                                dto -> dto.getDepartmentName() != null ? dto.getDepartmentName() : "");
                                break;
                        case "present":
                                comparator = java.util.Comparator.comparingInt(
                                                root.cyb.mh.attendancesystem.dto.MonthlySummaryDto::getPresentCount);
                                break;
                        case "absent":
                                comparator = java.util.Comparator.comparingInt(
                                                root.cyb.mh.attendancesystem.dto.MonthlySummaryDto::getAbsentCount);
                                break;
                        case "late":
                                comparator = java.util.Comparator.comparingInt(
                                                root.cyb.mh.attendancesystem.dto.MonthlySummaryDto::getLateCount);
                                break;
                        case "early":
                                comparator = java.util.Comparator.comparingInt(
                                                root.cyb.mh.attendancesystem.dto.MonthlySummaryDto::getEarlyLeaveCount);
                                break;
                        case "leave":
                                comparator = java.util.Comparator.comparingInt(
                                                root.cyb.mh.attendancesystem.dto.MonthlySummaryDto::getLeaveCount);
                                break;
                        case "name":
                        default:
                                comparator = java.util.Comparator.comparing(
                                                root.cyb.mh.attendancesystem.dto.MonthlySummaryDto::getEmployeeName);
                                break;
                }

                if ("desc".equalsIgnoreCase(sortDir)) {
                        comparator = comparator.reversed();
                }
                if (comparator != null) {
                        reportContent.sort(comparator);
                }

                model.addAttribute("selectedYear", year);
                model.addAttribute("selectedMonth", month);
                model.addAttribute("departments", departmentRepository.findAll());
                model.addAttribute("selectedDept", departmentId);

                model.addAttribute("report", reportContent);
                model.addAttribute("page", reportPage);

                model.addAttribute("sortField", sortField);
                model.addAttribute("sortDir", sortDir);
                model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

                return "reports-monthly";
        }

        @GetMapping("/reports/monthly/{employeeId}")
        public String employeeMonthlyReport(@org.springframework.web.bind.annotation.PathVariable String employeeId,
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) String period,
                        Model model) {

                LocalDate now = LocalDate.now();

                if (period != null && !period.isEmpty()) {
                        LocalDate startDate;
                        LocalDate endDate = now;

                        if ("3M".equals(period)) {
                                startDate = now.minusMonths(2).withDayOfMonth(1);
                        } else if ("6M".equals(period)) {
                                startDate = now.minusMonths(5).withDayOfMonth(1);
                        } else if ("1Y".equals(period)) {
                                startDate = now.minusMonths(11).withDayOfMonth(1);
                        } else {
                                // Fallback or Specific Month handled below if period logic gets complex
                                startDate = now.withDayOfMonth(1);
                        }

                        model.addAttribute("rangeReport",
                                        reportService.getEmployeeRangeReport(employeeId, startDate, endDate));
                        model.addAttribute("selectedPeriod", period);
                } else {
                        if (year == null || month == null) {
                                year = now.getYear();
                                month = now.getMonthValue();
                        }
                        model.addAttribute("report", reportService.getEmployeeMonthlyReport(employeeId, year, month));
                        model.addAttribute("selectedYear", year);
                        model.addAttribute("selectedMonth", month);
                }

                return "reports-employee-monthly";
        }

        @Autowired
        private root.cyb.mh.attendancesystem.service.PdfExportService pdfExportService;

        @GetMapping("/reports/daily/pdf")
        public org.springframework.http.ResponseEntity<byte[]> downloadDailyReportPdf(
                        @RequestParam(required = false) LocalDate date,
                        @RequestParam(required = false) Long departmentId)
                        throws java.io.IOException, com.lowagie.text.DocumentException {
                if (date == null)
                        date = LocalDate.now();

                List<root.cyb.mh.attendancesystem.dto.DailyAttendanceDto> report = reportService.getDailyReport(date,
                                departmentId, org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
                String deptName = "All Departments";
                if (departmentId != null) {
                        deptName = departmentRepository.findById(departmentId)
                                        .map(root.cyb.mh.attendancesystem.model.Department::getName).orElse("Unknown");
                }

                byte[] pdfBytes = pdfExportService.exportDailyReport(report, date, deptName);

                return org.springframework.http.ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=daily_report_" + date + "_"
                                                                + System.currentTimeMillis() + ".pdf")
                                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                                .body(pdfBytes);
        }

        @GetMapping("/reports/weekly/pdf")
        public org.springframework.http.ResponseEntity<byte[]> downloadWeeklyReportPdf(
                        @RequestParam(required = false) LocalDate date,
                        @RequestParam(required = false) Long departmentId)
                        throws java.io.IOException, com.lowagie.text.DocumentException {
                if (date == null)
                        date = LocalDate.now();
                LocalDate startOfWeek = date
                                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

                List<root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto> report = reportService.getWeeklyReport(
                                startOfWeek,
                                departmentId, org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
                String deptName = "All Departments";
                if (departmentId != null) {
                        deptName = departmentRepository.findById(departmentId)
                                        .map(root.cyb.mh.attendancesystem.model.Department::getName).orElse("Unknown");
                }

                byte[] pdfBytes = pdfExportService.exportWeeklyReport(report, startOfWeek, deptName);

                return org.springframework.http.ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=weekly_report_" + startOfWeek + "_"
                                                                + System.currentTimeMillis() + ".pdf")
                                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                                .body(pdfBytes);
        }

        @GetMapping("/reports/monthly/pdf")
        public org.springframework.http.ResponseEntity<byte[]> downloadMonthlyReportPdf(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Long departmentId)
                        throws java.io.IOException, com.lowagie.text.DocumentException {
                if (year == null || month == null) {
                        LocalDate now = LocalDate.now();
                        year = now.getYear();
                        month = now.getMonthValue();
                }

                List<root.cyb.mh.attendancesystem.dto.MonthlySummaryDto> report = reportService.getMonthlyReport(year,
                                month,
                                departmentId, org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
                String deptName = "All Departments";
                if (departmentId != null) {
                        deptName = departmentRepository.findById(departmentId)
                                        .map(root.cyb.mh.attendancesystem.model.Department::getName).orElse("Unknown");
                }

                byte[] pdfBytes = pdfExportService.exportMonthlyReport(report, year, month, deptName);

                return org.springframework.http.ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=monthly_report_" + month + "_" + year + "_"
                                                                + System.currentTimeMillis() + ".pdf")
                                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                                .body(pdfBytes);
        }

        @GetMapping("/reports/monthly/{employeeId}/pdf")
        public org.springframework.http.ResponseEntity<byte[]> downloadEmployeeMonthlyReportPdf(
                        @org.springframework.web.bind.annotation.PathVariable String employeeId,
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) String period)
                        throws java.io.IOException, com.lowagie.text.DocumentException {

                byte[] pdfBytes;
                String filename;
                LocalDate now = LocalDate.now();

                if (period != null && !period.isEmpty()) {
                        LocalDate startDate;
                        LocalDate endDate = now;
                        if ("3M".equals(period)) {
                                startDate = now.minusMonths(2).withDayOfMonth(1);
                        } else if ("6M".equals(period)) {
                                startDate = now.minusMonths(5).withDayOfMonth(1);
                        } else if ("1Y".equals(period)) {
                                startDate = now.minusMonths(11).withDayOfMonth(1);
                        } else {
                                startDate = now.withDayOfMonth(1);
                        }

                        root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto rangeReport = reportService
                                        .getEmployeeRangeReport(employeeId, startDate, endDate);
                        pdfBytes = pdfExportService.exportEmployeeRangeReport(rangeReport);
                        filename = "employee_report_" + employeeId + "_" + period + "_" + System.currentTimeMillis()
                                        + ".pdf";
                } else {
                        if (year == null || month == null) {
                                year = now.getYear();
                                month = now.getMonthValue();
                        }
                        root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto report = reportService
                                        .getEmployeeMonthlyReport(employeeId, year, month);
                        pdfBytes = pdfExportService.exportEmployeeMonthlyReport(report);
                        filename = "employee_report_" + employeeId + "_" + month + "_" + year + "_"
                                        + System.currentTimeMillis() + ".pdf";
                }

                return org.springframework.http.ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=" + filename)
                                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                                .body(pdfBytes);
        }
}
