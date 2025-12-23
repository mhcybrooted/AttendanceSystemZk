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
public class AdvanceSalaryRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    private Double amount;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private LocalDate requestDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(columnDefinition = "TEXT")
    private String adminComment;

    // Tracks if this advance has been paid out/deducted from a payslip
    private boolean isDeducted = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        PAID // Alternatively used to mark as processed/deducted
    }
}
