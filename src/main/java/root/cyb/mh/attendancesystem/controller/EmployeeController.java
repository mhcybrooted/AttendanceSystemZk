package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.repository.DepartmentRepository; // Added import

@Controller
@RequestMapping("/employees")
public class EmployeeController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository; // Injected DepartmentRepository

    @GetMapping
    public String listEmployees(Model model) {
        model.addAttribute("employees", employeeRepository.findAll());
        model.addAttribute("newEmployee", new Employee());
        model.addAttribute("departments", departmentRepository.findAll()); // Added for dropdown
        return "employees";
    }

    @PostMapping
    public String saveEmployee(@ModelAttribute Employee employee, @RequestParam(required = false) Long departmentId) {
        if (departmentId != null) {
            departmentRepository.findById(departmentId).ifPresent(employee::setDepartment);
        }
        employeeRepository.save(employee);
        return "redirect:/employees";
    }

    @GetMapping("/delete/{id}")
    public String deleteEmployee(@PathVariable String id) {
        employeeRepository.deleteById(id);
        return "redirect:/employees";
    }
}
