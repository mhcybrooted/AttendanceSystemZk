package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import root.cyb.mh.attendancesystem.model.AttendanceLog;

import java.time.LocalDateTime;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {
    boolean existsByEmployeeIdAndTimestampAndDeviceId(String employeeId, LocalDateTime timestamp, Long deviceId);

    java.util.List<AttendanceLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    java.util.List<AttendanceLog> findByEmployeeId(String employeeId);
}
