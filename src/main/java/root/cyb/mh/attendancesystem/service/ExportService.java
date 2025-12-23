package root.cyb.mh.attendancesystem.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.dto.DailyAttendanceDto;
import root.cyb.mh.attendancesystem.dto.MonthlySummaryDto;
import root.cyb.mh.attendancesystem.dto.WeeklyAttendanceDto;
import root.cyb.mh.attendancesystem.dto.EmployeeMonthlyDetailDto;
import root.cyb.mh.attendancesystem.dto.EmployeeRangeReportDto;
import root.cyb.mh.attendancesystem.dto.EmployeeWeeklyDetailDto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ExportService {

    // --- Daily Report ---

    public byte[] exportDailyExcel(List<DailyAttendanceDto> report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Daily Report");

            Row headerRow = sheet.createRow(0);
            String[] columns = { "Employee ID", "Name", "Department", "In Time", "Out Time", "Status" };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            int rowIdx = 1;
            for (DailyAttendanceDto dto : report) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getEmployeeId());
                row.createCell(1).setCellValue(dto.getEmployeeName());
                row.createCell(2).setCellValue(dto.getDepartmentName());
                row.createCell(3).setCellValue(dto.getInTime() != null ? dto.getInTime().toString() : "-");
                row.createCell(4).setCellValue(dto.getOutTime() != null ? dto.getOutTime().toString() : "-");
                row.createCell(5).setCellValue(dto.getStatus());
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportDailyCsv(List<DailyAttendanceDto> report) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(out)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader("Employee ID", "Name", "Department", "In Time", "Out Time", "Status")
                    .build();

            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (DailyAttendanceDto dto : report) {
                    printer.printRecord(
                            dto.getEmployeeId(),
                            dto.getEmployeeName(),
                            dto.getDepartmentName(),
                            dto.getInTime() != null ? dto.getInTime().toString() : "-",
                            dto.getOutTime() != null ? dto.getOutTime().toString() : "-",
                            dto.getStatus());
                }
            }
            return out.toByteArray();
        }
    }

    // --- Weekly Report ---

    public byte[] exportWeeklyExcel(List<WeeklyAttendanceDto> report, LocalDate startOfWeek) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Weekly Report");

            Row headerRow = sheet.createRow(0);
            // Dynamic headers for days
            String[] fixedHeaders = { "Employee ID", "Name", "Department" };
            String[] statsHeaders = { "Present", "Absent", "Late", "Early", "Leave" };

            int colIdx = 0;
            CellStyle boldStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            boldStyle.setFont(font);

            for (String h : fixedHeaders) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(h);
                cell.setCellStyle(boldStyle);
            }

            for (int i = 0; i < 7; i++) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(startOfWeek.plusDays(i).getDayOfWeek().toString().substring(0, 3));
                cell.setCellStyle(boldStyle);
            }

            for (String h : statsHeaders) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(h);
                cell.setCellStyle(boldStyle);
            }

            int rowIdx = 1;
            for (WeeklyAttendanceDto dto : report) {
                Row row = sheet.createRow(rowIdx++);
                colIdx = 0;
                row.createCell(colIdx++).setCellValue(dto.getEmployeeId());
                row.createCell(colIdx++).setCellValue(dto.getEmployeeName());
                row.createCell(colIdx++).setCellValue(dto.getDepartmentName());

                Map<LocalDate, String> dailyStatus = dto.getDailyStatus();
                for (int i = 0; i < 7; i++) {
                    String status = dailyStatus.getOrDefault(startOfWeek.plusDays(i), "-");
                    // Simplify status for Excel similar to PDF (P, A, etc or full word?) - Full
                    // word is better for Excel
                    // actually let's stick to short codes if it's too long, but Excel has space.
                    // Let's use short codes for readability in grid
                    String code = "-";
                    if (status.contains("PRESENT"))
                        code = "P";
                    else if (status.contains("ABSENT"))
                        code = "A";
                    else if (status.contains("WEEKEND"))
                        code = "W";
                    else if (status.contains("HOLIDAY"))
                        code = "H";
                    else if (status.contains("LATE"))
                        code = "L";
                    else if (status.contains("EARLY"))
                        code = "E";
                    else if (status.contains("LEAVE"))
                        code = "LV";

                    row.createCell(colIdx++).setCellValue(code);
                }

                row.createCell(colIdx++).setCellValue(dto.getPresentCount());
                row.createCell(colIdx++).setCellValue(dto.getAbsentCount());
                row.createCell(colIdx++).setCellValue(dto.getLateCount());
                row.createCell(colIdx++).setCellValue(dto.getEarlyLeaveCount());
                row.createCell(colIdx++).setCellValue(dto.getLeaveCount());
            }

            for (int i = 0; i < colIdx; i++)
                sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportWeeklyCsv(List<WeeklyAttendanceDto> report, LocalDate startOfWeek) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(out)) {

            // Build header list
            java.util.List<String> headers = new java.util.ArrayList<>();
            headers.add("Employee ID");
            headers.add("Name");
            headers.add("Department");
            for (int i = 0; i < 7; i++)
                headers.add(startOfWeek.plusDays(i).toString());
            headers.add("Present");
            headers.add("Absent");
            headers.add("Late");
            headers.add("Early");
            headers.add("Leave");

            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build();

            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (WeeklyAttendanceDto dto : report) {
                    java.util.List<Object> record = new java.util.ArrayList<>();
                    record.add(dto.getEmployeeId());
                    record.add(dto.getEmployeeName());
                    record.add(dto.getDepartmentName());

                    Map<LocalDate, String> dailyStatus = dto.getDailyStatus();
                    for (int i = 0; i < 7; i++) {
                        record.add(dailyStatus.getOrDefault(startOfWeek.plusDays(i), "-"));
                    }

                    record.add(dto.getPresentCount());
                    record.add(dto.getAbsentCount());
                    record.add(dto.getLateCount());
                    record.add(dto.getEarlyLeaveCount());
                    record.add(dto.getLeaveCount());

                    printer.printRecord(record);
                }
            }
            return out.toByteArray();
        }
    }

    // --- Monthly Report ---

    public byte[] exportMonthlyExcel(List<MonthlySummaryDto> report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Monthly Report");

            Row headerRow = sheet.createRow(0);
            String[] columns = { "ID", "Name", "Department", "Period", "Present", "Absent", "Late", "Early",
                    "Total Leave",
                    "Paid Leave", "Unpaid Leave" };

            CellStyle boldStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            boldStyle.setFont(font);

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(boldStyle);
            }

            int rowIdx = 1;
            for (MonthlySummaryDto dto : report) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(dto.getEmployeeId());
                row.createCell(col++).setCellValue(dto.getEmployeeName());
                row.createCell(col++).setCellValue(dto.getDepartmentName());
                // Add Period
                String period = java.time.Month.of(dto.getMonth()).toString() + "-" + dto.getYear();
                row.createCell(col++).setCellValue(period);

                row.createCell(col++).setCellValue(dto.getPresentCount());
                row.createCell(col++).setCellValue(dto.getAbsentCount());
                row.createCell(col++).setCellValue(dto.getLateCount());
                row.createCell(col++).setCellValue(dto.getEarlyLeaveCount());
                row.createCell(col++).setCellValue(dto.getLeaveCount());
                row.createCell(col++).setCellValue(dto.getPaidLeaveCount());
                row.createCell(col++).setCellValue(dto.getUnpaidLeaveCount());
            }

            for (int i = 0; i < columns.length; i++)
                sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportMonthlyCsv(List<MonthlySummaryDto> report) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(out)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader("ID", "Name", "Department", "Period", "Present", "Absent", "Late", "Early",
                            "Total Leave",
                            "Paid Leave", "Unpaid Leave")
                    .build();

            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (MonthlySummaryDto dto : report) {
                    String period = java.time.Month.of(dto.getMonth()).toString() + "-" + dto.getYear();
                    printer.printRecord(
                            dto.getEmployeeId(),
                            dto.getEmployeeName(),
                            dto.getDepartmentName(),
                            period,
                            dto.getPresentCount(),
                            dto.getAbsentCount(),
                            dto.getLateCount(),
                            dto.getEarlyLeaveCount(),
                            dto.getLeaveCount(),
                            dto.getPaidLeaveCount(),
                            dto.getUnpaidLeaveCount());
                }
            }
            return out.toByteArray();
        }
    }

    // --- Employee Detail (No Range for now, simple monthly detail) ---
    // Can expand if needed
    // --- Single Employee Weekly Detail ---

    public byte[] exportEmployeeWeeklyDetailExcel(EmployeeWeeklyDetailDto report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Employee Weekly Report");

            // Info Header
            Row infoRow = sheet.createRow(0);
            infoRow.createCell(0)
                    .setCellValue("Employee: " + report.getEmployeeName() + " (" + report.getEmployeeId() + ")");

            Row deptRow = sheet.createRow(1);
            deptRow.createCell(0).setCellValue("Department: " + report.getDepartmentName());

            Row headerRow = sheet.createRow(3);
            String[] columns = { "Date", "Day", "In Time", "Out Time", "Late", "Early", "Status" };

            CellStyle boldStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            boldStyle.setFont(font);

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(boldStyle);
            }

            int rowIdx = 4;
            for (EmployeeWeeklyDetailDto.DailyDetail day : report.getDailyDetails()) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(day.getDate().toString());
                row.createCell(col++).setCellValue(day.getDayOfWeek().toString());
                row.createCell(col++).setCellValue(day.getInTime() != null ? day.getInTime().toString() : "-");
                row.createCell(col++).setCellValue(day.getOutTime() != null ? day.getOutTime().toString() : "-");
                row.createCell(col++)
                        .setCellValue(day.getLateDurationMinutes() > 0 ? day.getLateDurationMinutes() + " min" : "-");
                row.createCell(col++).setCellValue(
                        day.getEarlyLeaveDurationMinutes() > 0 ? day.getEarlyLeaveDurationMinutes() + " min" : "-");
                row.createCell(col++).setCellValue(day.getStatus());
            }

            // Summary Row
            rowIdx++;
            Row summaryRow = sheet.createRow(rowIdx);
            summaryRow.createCell(0).setCellValue("Summary:");
            summaryRow.createCell(1).setCellValue("P: " + report.getTotalPresent() + ", A: " + report.getTotalAbsent() +
                    ", L: " + report.getTotalLates() + ", E: " + report.getTotalEarlyLeaves() +
                    ", LV: " + report.getTotalLeaves());

            for (int i = 0; i < columns.length; i++)
                sheet.autoSizeColumn(i);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportEmployeeWeeklyDetailCsv(EmployeeWeeklyDetailDto report) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); PrintWriter writer = new PrintWriter(out)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader("Date", "Day", "In Time", "Out Time", "Late", "Early", "Status")
                    .build();

            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                // Info as comment or first rows? CSV usually raw data. Let's keep it raw data
                // table.
                for (EmployeeWeeklyDetailDto.DailyDetail day : report.getDailyDetails()) {
                    printer.printRecord(
                            day.getDate(),
                            day.getDayOfWeek(),
                            day.getInTime() != null ? day.getInTime() : "-",
                            day.getOutTime() != null ? day.getOutTime() : "-",
                            day.getLateDurationMinutes() > 0 ? day.getLateDurationMinutes() + " min" : "-",
                            day.getEarlyLeaveDurationMinutes() > 0 ? day.getEarlyLeaveDurationMinutes() + " min" : "-",
                            day.getStatus());
                }
            }
            return out.toByteArray();
        }
    }

    // --- Single Employee Monthly Detail (and Range) ---

    public byte[] exportEmployeeMonthlyDetailExcel(EmployeeMonthlyDetailDto report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Monthly Sheet");
            // Create style here to pass
            CellStyle bold = workbook.createCellStyle();
            Font f = workbook.createFont();
            f.setBold(true);
            bold.setFont(f);

            createMonthlyDetailSheet(sheet, report, bold);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportEmployeeRangeReportExcel(EmployeeRangeReportDto report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Create Styles Helper
            CellStyle boldStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            boldStyle.setFont(font);

            // Summary Sheet
            Sheet summarySheet = workbook.createSheet("Overall Summary");
            Row r0 = summarySheet.createRow(0);
            r0.createCell(0).setCellValue("Report for: " + report.getEmployeeName());
            Row r1 = summarySheet.createRow(1);
            r1.createCell(0).setCellValue("Period: " + report.getStartDate() + " to " + report.getEndDate());

            Row r3 = summarySheet.createRow(3);
            r3.createCell(0).setCellValue("Total Present: " + report.getTotalPresent());
            Row r4 = summarySheet.createRow(4);
            r4.createCell(0).setCellValue("Total Absent: " + report.getTotalAbsent());
            Row r5 = summarySheet.createRow(5);
            r5.createCell(0).setCellValue("Total Leaves: " + report.getTotalLeaves());

            // Individual Sheets for months
            int sheetCounter = 1;
            for (EmployeeMonthlyDetailDto monthly : report.getMonthlyReports()) {
                // Safe Sheet Name
                String safeName = monthly.getMonth() + "-" + monthly.getYear();
                // Ensure uniqueness if for some reason duplicates exist (though unlikely with
                // current logic)
                if (workbook.getSheet(safeName) != null) {
                    safeName = safeName + " (" + sheetCounter++ + ")";
                }

                // Create sheet with safe name
                Sheet mSheet = null;
                try {
                    mSheet = workbook.createSheet(safeName);
                } catch (IllegalArgumentException e) {
                    // Fallback for invalid chars
                    mSheet = workbook.createSheet("Month-" + sheetCounter++);
                }

                createMonthlyDetailSheet(mSheet, monthly, boldStyle);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void createMonthlyDetailSheet(Sheet sheet, EmployeeMonthlyDetailDto report, CellStyle boldStyle) {
        // Reuse passed style
        if (boldStyle == null) {
            boldStyle = sheet.getWorkbook().createCellStyle();
            Font f = sheet.getWorkbook().createFont();
            f.setBold(true);
            boldStyle.setFont(f);
        }

        Row h1 = sheet.createRow(0);
        h1.createCell(0).setCellValue("Month: " + report.getMonth() + "/" + report.getYear());

        Row headerRow = sheet.createRow(2);
        String[] columns = { "Date", "Day", "In Time", "Out Time", "Late", "Early", "Status" };

        for (int i = 0; i < columns.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(columns[i]);
            c.setCellStyle(boldStyle);
        }

        int idx = 3;
        for (EmployeeWeeklyDetailDto.DailyDetail day : report.getDailyDetails()) {
            Row row = sheet.createRow(idx++);
            int c = 0;
            row.createCell(c++).setCellValue(day.getDate().toString());
            row.createCell(c++).setCellValue(day.getDayOfWeek().toString());
            row.createCell(c++).setCellValue(day.getInTime() != null ? day.getInTime().toString() : "-");
            row.createCell(c++).setCellValue(day.getOutTime() != null ? day.getOutTime().toString() : "-");
            row.createCell(c++)
                    .setCellValue(day.getLateDurationMinutes() > 0 ? day.getLateDurationMinutes() + " min" : "-");
            row.createCell(c++).setCellValue(
                    day.getEarlyLeaveDurationMinutes() > 0 ? day.getEarlyLeaveDurationMinutes() + " min" : "-");
            row.createCell(c++).setCellValue(day.getStatus());
        }
        for (int i = 0; i < columns.length; i++)
            sheet.autoSizeColumn(i);
    }

    public byte[] exportEmployeeMonthlyDetailCsv(EmployeeMonthlyDetailDto report) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); PrintWriter writer = new PrintWriter(out)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader("Date", "Day", "In Time", "Out Time", "Late", "Early", "Status")
                    .build();
            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (EmployeeWeeklyDetailDto.DailyDetail day : report.getDailyDetails()) {
                    printer.printRecord(
                            day.getDate(), day.getDayOfWeek(),
                            day.getInTime() != null ? day.getInTime() : "-",
                            day.getOutTime() != null ? day.getOutTime() : "-",
                            day.getLateDurationMinutes() > 0 ? day.getLateDurationMinutes() + " min" : "-",
                            day.getEarlyLeaveDurationMinutes() > 0 ? day.getEarlyLeaveDurationMinutes() + " min" : "-",
                            day.getStatus());
                }
            }
            return out.toByteArray();
        }
    }

    public byte[] exportEmployeeRangeReportCsv(EmployeeRangeReportDto report) throws IOException {
        // Flatten all months into one CSV
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); PrintWriter writer = new PrintWriter(out)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader("Month", "Year", "Date", "Day", "In Time", "Out Time", "Late", "Early", "Status")
                    .build();
            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (EmployeeMonthlyDetailDto monthly : report.getMonthlyReports()) {
                    for (EmployeeWeeklyDetailDto.DailyDetail day : monthly.getDailyDetails()) {
                        printer.printRecord(
                                monthly.getMonth(), monthly.getYear(),
                                day.getDate(), day.getDayOfWeek(),
                                day.getInTime() != null ? day.getInTime() : "-",
                                day.getOutTime() != null ? day.getOutTime() : "-",
                                day.getLateDurationMinutes() > 0 ? day.getLateDurationMinutes() + " min" : "-",
                                day.getEarlyLeaveDurationMinutes() > 0 ? day.getEarlyLeaveDurationMinutes() + " min"
                                        : "-",
                                day.getStatus());
                    }
                }
            }
            return out.toByteArray();
        }
    }

    public byte[] exportBankAdviceExcel(List<root.cyb.mh.attendancesystem.model.Payslip> slips) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Bank Advice");

            Row headerRow = sheet.createRow(0);
            String[] columns = { "Employee ID", "Name", "Bank Name", "Account Number", "Net Salary", "Payment Ref" };

            CellStyle boldStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            boldStyle.setFont(font);

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(boldStyle);
            }

            int rowIdx = 1;
            for (root.cyb.mh.attendancesystem.model.Payslip p : slips) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(p.getEmployee().getId());
                row.createCell(col++).setCellValue(p.getEmployee().getName());
                row.createCell(col++).setCellValue(p.getEmployee() != null ? p.getEmployee().getBankName() : "");
                row.createCell(col++).setCellValue(p.getEmployee() != null ? p.getEmployee().getAccountNumber() : "");
                row.createCell(col++).setCellValue(p.getNetSalary() != null ? p.getNetSalary() : 0.0);
                row.createCell(col++).setCellValue("Salary " + p.getMonth());
            }

            for (int i = 0; i < columns.length; i++)
                sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        }
    }
}
