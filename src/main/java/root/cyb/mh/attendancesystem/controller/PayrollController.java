package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import root.cyb.mh.attendancesystem.model.Payslip;
import root.cyb.mh.attendancesystem.repository.PayslipRepository;
import root.cyb.mh.attendancesystem.service.PayrollService;

import java.security.Principal;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class PayrollController {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayslipRepository payslipRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.DepartmentRepository departmentRepository;

    // Phase 2: Monthly Summary Dashboard
    @GetMapping("/payroll")
    public String payrollDashboard(Model model) {
        // Fetch all payslips
        List<Payslip> allSlips = payslipRepository.findAll();

        // Group by Month to create summaries
        Map<String, List<Payslip>> groupedByMonth = allSlips.stream()
                .collect(Collectors.groupingBy(Payslip::getMonth));

        List<root.cyb.mh.attendancesystem.dto.PayrollMonthlySummaryDto> summaries = new ArrayList<>();

        // Sort keys (months) descending
        List<String> sortedMonths = new ArrayList<>(groupedByMonth.keySet());
        sortedMonths.sort(Collections.reverseOrder());

        for (String month : sortedMonths) {
            summaries.add(
                    new root.cyb.mh.attendancesystem.dto.PayrollMonthlySummaryDto(month, groupedByMonth.get(month)));
        }

        model.addAttribute("summaries", summaries);
        model.addAttribute("activeLink", "payroll");
        return "admin-payroll-dashboard";
    }

    // Phase 2: Monthly Details View
    @GetMapping("/payroll/details/{month}")
    public String payrollDetails(@PathVariable String month,
            @RequestParam(required = false) List<Long> departmentIds,
            Model model) {

        List<Payslip> slips = payslipRepository.findByMonth(month);

        // Filter by Department
        if (departmentIds != null && !departmentIds.isEmpty()) {
            slips = slips.stream()
                    .filter(s -> s.getEmployee().getDepartment() != null
                            && departmentIds.contains(s.getEmployee().getDepartment().getId()))
                    .collect(Collectors.toList());
        }

        model.addAttribute("month", month);
        model.addAttribute("payslips", slips);
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("selectedDeptIds", departmentIds);
        model.addAttribute("activeLink", "payroll");

        return "admin-payroll-details";
    }

    // Phase 2: Update Status (Mark Paid)
    @PostMapping("/payroll/status/update")
    public String updateStatus(@RequestParam Long payslipId, @RequestParam String status) {
        Payslip slip = payslipRepository.findById(payslipId).orElse(null);
        if (slip != null) {
            slip.setStatus(Payslip.Status.valueOf(status));
            payslipRepository.save(slip);
        }
        // Redirect back to details page
        return "redirect:/payroll/details/" + (slip != null ? slip.getMonth() : "");
    }

    // Phase 2: Bulk Mark Paid
    @PostMapping("/payroll/status/bulk-paid")
    public String bulkMarkPaid(@RequestParam String month) {
        List<Payslip> slips = payslipRepository.findByMonth(month);
        for (Payslip slip : slips) {
            if (slip.getStatus() == Payslip.Status.DRAFT) {
                slip.setStatus(Payslip.Status.PAID);
                payslipRepository.save(slip);
            }
        }
        return "redirect:/payroll/details/" + month;
    }

    // Trigger Generation (Updated redirect)
    @PostMapping("/payroll/run")
    public String runPayroll(@RequestParam String month) { // format yyyy-mm
        YearMonth yearMonth = YearMonth.parse(month);
        payrollService.generatePayrollForMonth(yearMonth);
        return "redirect:/payroll";
    }

    // Phase 4: Update Bonus
    @PostMapping("/payroll/bonus/update")
    public String updateBonus(@RequestParam Long payslipId, @RequestParam Double amount) {
        Payslip slip = payslipRepository.findById(payslipId).orElse(null);
        if (slip != null && slip.getStatus() == Payslip.Status.DRAFT) {
            slip.setBonusAmount(amount);

            // Recalculate Net
            double allowance = slip.getAllowanceAmount() != null ? slip.getAllowanceAmount() : 0.0;
            double deductions = slip.getDeductionAmount() != null ? slip.getDeductionAmount() : 0.0;
            double basic = slip.getBasicSalary() != null ? slip.getBasicSalary() : 0.0;

            double net = (basic + allowance + amount) - deductions;

            slip.setNetSalary(Math.round(net * 100.0) / 100.0);
            payslipRepository.save(slip);
        }
        return "redirect:/payroll/details/" + (slip != null ? slip.getMonth() : "");
    }

    // Employee View
    @GetMapping("/payroll/delete/{id}")
    public String deletePayslip(@PathVariable Long id,
            @RequestHeader(value = "Referer", required = false) String referer) {
        payslipRepository.deleteById(id);
        return "redirect:" + (referer != null ? referer : "/payroll");
    }

    @GetMapping("/employee/payroll")
    public String myPayroll(Model model, Principal principal) {
        String employeeId = principal.getName();
        List<Payslip> myPayslips = payslipRepository.findByEmployeeIdOrderByMonthDesc(employeeId);
        model.addAttribute("payslips", myPayslips);
        model.addAttribute("activeLink", "payroll");
        return "employee-payroll";
    }
}
