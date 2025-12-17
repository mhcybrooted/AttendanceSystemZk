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
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    private String leaveType; // e.g., Sick, Vacation, Personal

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    // Comment from HR or Admin (especially if rejected)
    @Column(columnDefinition = "TEXT")
    private String adminComment;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Who approved/rejected it? (Optional audit)
    private String reviewedBy;

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }
}
