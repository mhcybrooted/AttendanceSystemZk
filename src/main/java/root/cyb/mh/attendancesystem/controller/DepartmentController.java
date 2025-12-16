package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import root.cyb.mh.attendancesystem.model.Department;
import root.cyb.mh.attendancesystem.repository.DepartmentRepository;

@Controller
public class DepartmentController {

    @Autowired
    private DepartmentRepository departmentRepository;

    @GetMapping("/departments")
    public String departments(Model model) {
        model.addAttribute("departments", departmentRepository.findAll());
        return "departments";
    }

    @PostMapping("/departments")
    public String saveDepartment(@RequestParam(required = false) Long id, @RequestParam String name,
            @RequestParam String description) {
        Department dept;
        if (id != null) {
            dept = departmentRepository.findById(id).orElse(new Department());
        } else {
            dept = new Department();
        }
        dept.setName(name);
        dept.setDescription(description);
        departmentRepository.save(dept);
        return "redirect:/departments";
    }

    @GetMapping("/departments/delete/{id}")
    public String deleteDepartment(@PathVariable Long id) {
        departmentRepository.deleteById(id);
        return "redirect:/departments";
    }
}
