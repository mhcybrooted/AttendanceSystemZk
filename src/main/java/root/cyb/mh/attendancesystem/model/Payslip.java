package root.cyb.mh.attendancesystem.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    private String month; // Format: "YYYY-MM"

    private LocalDateTime generatedAt;

    @Enumerated(EnumType.STRING)
    private Status status = Status.DRAFT;

    // Financials
    private Double basicSalary; // The monthly salary at the time of generation
    private Double allowanceAmount; // Fixed Allowance
    private Double bonusAmount; // One-time Bonus
    private Double deductionAmount;
    private Double netSalary;

    // Attendance Summary (Snapshot)
    private int totalWorkingDays;
    private int presentDays;
    private int absentDays; // Unapproved absences
    private int unpaidLeaveDays; // Approved unpaid leaves
    private int paidLeaveDays; // Sick/Casual/Annual

    // Detailed Breakdown
    private Integer lateDays = 0;
    private Double latePenaltyAmount = 0.0;

    // Advance Salary
    private Double advanceSalaryAmount = 0.0;

    public enum Status {
        DRAFT,
        PAID
    }
}
