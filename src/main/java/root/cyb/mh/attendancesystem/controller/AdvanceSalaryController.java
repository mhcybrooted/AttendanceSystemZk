package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.repository.AdvanceSalaryRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.repository.WorkScheduleRepository;

import java.time.LocalDate;
import java.util.List;

@Controller
public class AdvanceSalaryController {

    @Autowired
    private AdvanceSalaryRepository advanceSalaryRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private WorkScheduleRepository workScheduleRepository;

    // --- Employee Actions ---

    @PostMapping("/advance/request")
    public String requestAdvance(@RequestParam String employeeId,
            @RequestParam Double amount,
            @RequestParam String reason) {

        Employee emp = employeeRepository.findById(employeeId).orElse(null);
        if (emp != null) {
            AdvanceSalaryRequest req = new AdvanceSalaryRequest();
            req.setEmployee(emp);
            req.setAmount(amount);
            req.setReason(reason);
            // Default Status PENDING
            advanceSalaryRepository.save(req);
        }
        return "redirect:/employee/dashboard?advanceRequested";
    }

    // --- Admin Actions ---

    @GetMapping("/admin/advance-requests")
    public String viewAdvanceRequests(Model model) {
        List<AdvanceSalaryRequest> pending = advanceSalaryRepository
                .findByStatusOrderByCreatedAtDesc(AdvanceSalaryRequest.Status.PENDING);
        List<AdvanceSalaryRequest> history = advanceSalaryRepository.findAllByOrderByCreatedAtDesc();
        // In real app, might want to filter history or paginate

        model.addAttribute("pendingRequests", pending);
        model.addAttribute("allRequests", history);
        return "admin-advance-requests";
    }

    @PostMapping("/admin/advance/approve")
    public String approveRequest(@RequestParam Long id, @RequestParam(required = false) String comment) {
        AdvanceSalaryRequest req = advanceSalaryRepository.findById(id).orElse(null);
        if (req != null) {
            req.setStatus(AdvanceSalaryRequest.Status.APPROVED);
            req.setAdminComment(comment);
            advanceSalaryRepository.save(req);
        }
        return "redirect:/admin/advance-requests";
    }

    @PostMapping("/admin/advance/reject")
    public String rejectRequest(@RequestParam Long id, @RequestParam(required = false) String comment) {
        AdvanceSalaryRequest req = advanceSalaryRepository.findById(id).orElse(null);
        if (req != null) {
            req.setStatus(AdvanceSalaryRequest.Status.REJECTED);
            req.setAdminComment(comment);
            advanceSalaryRepository.save(req);
        }
        return "redirect:/admin/advance-requests";
    }
}
