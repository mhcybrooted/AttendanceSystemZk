package root.cyb.mh.attendancesystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.model.LeaveRequest;
import root.cyb.mh.attendancesystem.repository.LeaveRequestRepository;

import java.util.List;
import java.util.Optional;

@Service
public class LeaveService {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    public LeaveRequest createRequest(Employee employee, LeaveRequest request) {
        request.setEmployee(employee);
        request.setStatus(LeaveRequest.Status.PENDING);
        return leaveRequestRepository.save(request);
    }

    public List<LeaveRequest> getEmployeeHistory(String employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    public List<LeaveRequest> getAllRequests() {
        return leaveRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<LeaveRequest> findById(Long id) {
        return leaveRequestRepository.findById(id);
    }

    public void updateStatus(Long requestId, LeaveRequest.Status newStatus, String comment, String reviewerRole,
            String reviewerEmail) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Request ID"));

        // HR Logic: Can only update if PENDING
        if ("ROLE_HR".equals(reviewerRole)) {
            if (request.getStatus() != LeaveRequest.Status.PENDING) {
                throw new IllegalStateException("HR cannot modify a request that is already processed.");
            }
        }

        // Admin Logic: Can override anything (no Check)

        request.setStatus(newStatus);
        request.setAdminComment(comment); // Overwrites previous comment if any
        request.setReviewedBy(reviewerEmail + " (" + reviewerRole + ")");

        leaveRequestRepository.save(request);
    }
}
