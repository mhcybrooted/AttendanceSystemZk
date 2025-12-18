package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import root.cyb.mh.attendancesystem.model.Shift;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.service.ShiftService;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/shifts")
public class ShiftController {

    @Autowired
    private ShiftService shiftService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("shifts", shiftService.getAllShifts());
        model.addAttribute("employees", employeeRepository.findAll());
        model.addAttribute("assignments", shiftService.getAllAssignments());
        model.addAttribute("newShift", new Shift());
        return "shifts";
    }

    @PostMapping("/create")
    public String createShift(@ModelAttribute Shift shift) {
        shiftService.createShift(shift);
        return "redirect:/admin/shifts";
    }

    @GetMapping("/delete/{id}")
    public String deleteShift(@PathVariable Long id) {
        try {
            shiftService.deleteShift(id);
        } catch (Exception e) {
            // Likely foreign key constraint if assigned?
            // Ignore for now or show error
        }
        return "redirect:/admin/shifts";
    }

    @PostMapping("/assign")
    public String assignShift(@RequestParam String employeeId,
            @RequestParam Long shiftId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        shiftService.assignShift(employeeId, shiftId, startDate, endDate);
        return "redirect:/admin/shifts"; // Or redirect to employee details?
    }

    @PostMapping("/assignments/update")
    public String updateAssignment(@RequestParam Long id,
            @RequestParam Long shiftId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        shiftService.updateAssignment(id, shiftId, startDate, endDate);
        return "redirect:/admin/shifts";
    }

    @GetMapping("/assignments/delete/{id}")
    public String deleteAssignment(@PathVariable Long id) {
        shiftService.deleteAssignment(id);
        return "redirect:/admin/shifts";
    }

    // We might want searching assignments. For now, basic implementation.
}
