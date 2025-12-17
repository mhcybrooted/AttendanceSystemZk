package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import root.cyb.mh.attendancesystem.service.DataImportExportService;

import java.io.IOException;

@Controller
@RequestMapping("/data")
public class DataManagementController {

    @Autowired
    private DataImportExportService dataService;

    @GetMapping("/export")
    public void exportData(@RequestParam String type, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + type + "_export.csv\"");

        switch (type) {
            case "employees":
                dataService.exportEmployees(response.getWriter());
                break;
            case "departments":
                dataService.exportDepartments(response.getWriter());
                break;
            case "leaves":
                dataService.exportLeaveRequests(response.getWriter());
                break;
            case "devices":
                dataService.exportDevices(response.getWriter());
                break;
            case "settings":
                dataService.exportSettings(response.getWriter());
                break;
            case "users":
                dataService.exportUsers(response.getWriter());
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    @PostMapping("/import")
    public String importData(@RequestParam String type, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return "redirect:/settings?error=emptyfile";
        }

        switch (type) {
            case "employees":
                dataService.importEmployees(file.getInputStream());
                break;
            case "departments":
                dataService.importDepartments(file.getInputStream());
                break;
            case "leaves":
                dataService.importLeaveRequests(file.getInputStream());
                break;
            case "devices":
                dataService.importDevices(file.getInputStream());
                break;
            case "settings":
                dataService.importSettings(file.getInputStream());
                break;
            case "users":
                dataService.importUsers(file.getInputStream());
                break;
            default:
                return "redirect:/settings?error=unknowntype";
        }

        return "redirect:/settings?success=import";
    }
}
