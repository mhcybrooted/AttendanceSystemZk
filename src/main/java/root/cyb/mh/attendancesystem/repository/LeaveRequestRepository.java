package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import root.cyb.mh.attendancesystem.model.LeaveRequest;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    // For Employee: View their own history
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(String employeeId);

    // For HR/Admin: View all (could filter by Pending later)
    List<LeaveRequest> findAllByOrderByCreatedAtDesc();

    // Find pending requests
    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveRequest.Status status);
}
