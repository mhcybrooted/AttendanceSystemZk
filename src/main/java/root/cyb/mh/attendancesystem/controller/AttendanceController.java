package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import root.cyb.mh.attendancesystem.model.Device;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.DeviceRepository;
// import root.cyb.mh.attendancesystem.service.SyncService;

@Controller
@RequestMapping("/")
public class AttendanceController {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    // @Autowired
    // private SyncService syncService;

    @GetMapping
    public String index() {
        return "redirect:/attendance";
    }

    @GetMapping("/devices")
    public String devices(Model model) {
        model.addAttribute("devices", deviceRepository.findAll());
        model.addAttribute("newDevice", new Device());
        return "device-status";
    }

    @PostMapping("/devices")
    public String addDevice(@ModelAttribute Device device) {
        deviceRepository.save(device);
        return "redirect:/devices";
    }

    @PostMapping("/sync")
    public String manualSync() {
        // syncService.syncAllDevices();
        return "redirect:/devices";
    }

    @Autowired
    private root.cyb.mh.attendancesystem.service.AdmsService admsService;

    @PostMapping("/devices/download")
    public String downloadLogs() {
        // Queue command to download all logs
        // Command: DATA QUERY ATTLOG StartTime=2000-01-01 EndTime=2099-12-31
        // Simplified: DATA QUERY ATTLOG
        admsService.queueCommand("DATA QUERY ATTLOG");
        return "redirect:/devices";
    }

    @PostMapping("/devices/download-users")
    public String downloadUsers() {
        admsService.queueCommand("DATA QUERY USERINFO");
        return "redirect:/devices";
    }

    @Autowired
    private root.cyb.mh.attendancesystem.repository.EmployeeRepository employeeRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.DepartmentRepository departmentRepository;

    @GetMapping("/attendance")
    public String attendance(@RequestParam(required = false) Long departmentId, Model model) {
        if (departmentId != null) {
            // Filter logic: Find employees in dept, then find logs for those employees
            java.util.List<String> employeeIds = employeeRepository.findAll().stream()
                    .filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(departmentId))
                    .map(root.cyb.mh.attendancesystem.model.Employee::getId)
                    .collect(java.util.stream.Collectors.toList());

            model.addAttribute("logs", attendanceLogRepository.findAll().stream()
                    .filter(log -> employeeIds.contains(log.getEmployeeId()))
                    .collect(java.util.stream.Collectors.toList()));
            model.addAttribute("selectedDeptId", departmentId);
        } else {
            model.addAttribute("logs", attendanceLogRepository.findAll());
        }

        model.addAttribute("departments", departmentRepository.findAll());

        // Create a map of ID -> Name for easy lookup in view
        java.util.Map<String, String> employeeMap = new java.util.HashMap<>();
        for (root.cyb.mh.attendancesystem.model.Employee emp : employeeRepository.findAll()) {
            employeeMap.put(emp.getId(), emp.getName());
        }
        model.addAttribute("employeeMap", employeeMap);

        return "attendance";
    }
}
