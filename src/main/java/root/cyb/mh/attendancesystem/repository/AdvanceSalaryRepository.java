package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import root.cyb.mh.attendancesystem.model.AdvanceSalaryRequest;

import java.util.List;

public interface AdvanceSalaryRepository extends JpaRepository<AdvanceSalaryRequest, Long> {

    List<AdvanceSalaryRequest> findByEmployeeId(String employeeId);

    List<AdvanceSalaryRequest> findByStatus(AdvanceSalaryRequest.Status status);

    List<AdvanceSalaryRequest> findAllByOrderByCreatedAtDesc();

    List<AdvanceSalaryRequest> findByStatusOrderByCreatedAtDesc(AdvanceSalaryRequest.Status status);

    // Find approved and not yet deducted requests for a specific employee
    @Query("SELECT r FROM AdvanceSalaryRequest r WHERE r.employee.id = :empId AND r.status = 'APPROVED' AND r.isDeducted = false")
    List<AdvanceSalaryRequest> findPendingDeductions(@Param("empId") String employeeId);
}
