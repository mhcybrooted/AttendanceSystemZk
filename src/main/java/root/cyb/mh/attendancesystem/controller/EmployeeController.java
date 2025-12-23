package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.repository.DepartmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

@Controller
@RequestMapping("/employees")
public class EmployeeController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public String listEmployees(Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortField).ascending()
                : Sort.by(sortField).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Employee> employeePage = employeeRepository.findAll(pageable);

        model.addAttribute("employees", employeePage.getContent());
        model.addAttribute("page", employeePage);
        model.addAttribute("newEmployee", new Employee());
        model.addAttribute("departments", departmentRepository.findAll());

        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

        return "employees";
    }

    @PostMapping
    public String saveEmployee(@ModelAttribute Employee employee, @RequestParam(required = false) Long departmentId) {
        Employee employeeToSave = employee;

        // Check if updating existing employee
        if (employee.getId() != null && !employee.getId().isEmpty()) {
            java.util.Optional<Employee> existingOpt = employeeRepository.findById(employee.getId());
            if (existingOpt.isPresent()) {
                Employee existing = existingOpt.get();
                // Update editable fields
                existing.setName(employee.getName());
                existing.setRole(employee.getRole());
                existing.setEmail(employee.getEmail());
                existing.setGuest(employee.isGuest()); // Use correct getter/setter
                existing.setAnnualLeaveQuota(employee.getAnnualLeaveQuota()); // Update Quota
                existing.setJoiningDate(employee.getJoiningDate()); // Update Joining Date
                existing.setJoiningDate(employee.getJoiningDate()); // Update Joining Date
                existing.setMonthlySalary(employee.getMonthlySalary()); // Update Monthly Salary
                existing.setFixedAllowance(employee.getFixedAllowance()); // Update Fixed Allowance
                existing.setBankName(employee.getBankName()); // Update Bank Name
                existing.setAccountNumber(employee.getAccountNumber()); // Update Account Number

                // Update Password only if provided
                if (employee.getUsername() != null && !employee.getUsername().isEmpty()) {
                    existing.setUsername(passwordEncoder.encode(employee.getUsername()));
                }

                // Department logic below will set department on 'existing'
                employeeToSave = existing;
            } else {
                // New Employee with explicit ID (if allowed) or first time save
                // Hash password for new employee
                if (employee.getUsername() != null && !employee.getUsername().isEmpty()) {
                    employee.setUsername(passwordEncoder.encode(employee.getUsername()));
                }
            }
        } else {
            // New Employee (though ID is typically required for this app)
            if (employee.getUsername() != null && !employee.getUsername().isEmpty()) {
                employee.setUsername(passwordEncoder.encode(employee.getUsername()));
            }
        }

        if (departmentId != null) {
            departmentRepository.findById(departmentId).ifPresent(employeeToSave::setDepartment);
        }

        employeeRepository.save(employeeToSave);
        return "redirect:/employees";
    }

    @GetMapping("/delete/{id}")
    public String deleteEmployee(@PathVariable String id) {
        employeeRepository.deleteById(id);
        return "redirect:/employees";
    }

    @PostMapping("/bulk/assign-department")
    public String bulkAssignDepartment(@RequestParam("employeeIds") java.util.List<String> employeeIds,
            @RequestParam("departmentId") Long departmentId) {
        if (employeeIds != null && departmentId != null) {
            root.cyb.mh.attendancesystem.model.Department dept = departmentRepository.findById(departmentId)
                    .orElse(null);
            if (dept != null) {
                java.util.List<Employee> employees = employeeRepository.findAllById(employeeIds);
                for (Employee emp : employees) {
                    emp.setDepartment(dept);
                }
                employeeRepository.saveAll(employees);
            }
        }
        return "redirect:/employees";
    }

}
