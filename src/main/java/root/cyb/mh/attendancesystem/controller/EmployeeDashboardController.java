package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import root.cyb.mh.attendancesystem.model.AttendanceLog;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Controller
public class EmployeeDashboardController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    @GetMapping("/employee/dashboard")
    public String dashboard(Model model, Principal principal) {
        String employeeId = principal.getName();
        Employee employee = employeeRepository.findById(employeeId).orElse(new Employee());

        // Recent Logs
        LocalDate today = LocalDate.now();
        List<AttendanceLog> logs = attendanceLogRepository.findByEmployeeId(employeeId).stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(10)
                .toList();

        model.addAttribute("employee", employee);
        model.addAttribute("recentLogs", logs);

        return "employee-dashboard";
    }
}
