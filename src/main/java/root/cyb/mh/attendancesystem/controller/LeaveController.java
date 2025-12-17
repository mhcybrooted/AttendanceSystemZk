package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.model.LeaveRequest;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.service.LeaveService;

import java.security.Principal;
import java.util.Collection;
import java.util.List;

@Controller
@RequestMapping("/leave")
public class LeaveController {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private EmployeeRepository employeeRepository;

    // --- Employee Endpoints ---

    @GetMapping("/employee")
    public String employeeLeavePage(Model model, Principal principal) {
        String employeeId = principal.getName();

        // 1. History
        List<LeaveRequest> history = leaveService.getEmployeeHistory(employeeId);
        model.addAttribute("history", history);
        model.addAttribute("newRequest", new LeaveRequest());
        model.addAttribute("activeLink", "leave");

        return "employee-leave";
    }

    @PostMapping("/employee/apply")
    public String applyForLeave(@ModelAttribute LeaveRequest leaveRequest, Principal principal) {
        String employeeId = principal.getName();
        Employee employee = employeeRepository.findById(employeeId).orElse(null);

        if (employee != null) {
            leaveService.createRequest(employee, leaveRequest);
        }
        return "redirect:/leave/employee";
    }

    // --- Admin / HR Endpoints ---

    @GetMapping("/manage")
    public String manageLeavePage(Model model) {
        // Fetch all requests (could filter by pending in UI or via param)
        List<LeaveRequest> allRequests = leaveService.getAllRequests();
        model.addAttribute("requests", allRequests);
        model.addAttribute("activeLink", "leave-manage");
        return "admin-leave-requests";
    }

    @PostMapping("/manage/update")
    public String updateStatus(@RequestParam Long id,
            @RequestParam String status,
            @RequestParam(required = false) String comment,
            Authentication authentication) {

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(r -> r.startsWith("ROLE_"))
                .findFirst().orElse("ROLE_USER");

        String reviewerEmail = authentication.getName();

        try {
            leaveService.updateStatus(id, LeaveRequest.Status.valueOf(status), comment, role, reviewerEmail);
        } catch (IllegalStateException e) {
            // Flash error message if HR tries to edit non-pending (TODO: Add flash
            // attributes)
            return "redirect:/leave/manage?error=" + e.getMessage();
        }

        return "redirect:/leave/manage";
    }
}
