package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import root.cyb.mh.attendancesystem.model.EmployeeShift;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, Long> {

    @Query("SELECT es FROM EmployeeShift es WHERE es.employee.id = :employeeId AND :date BETWEEN es.startDate AND es.endDate")
    Optional<EmployeeShift> findActiveShiftForEmployee(@Param("employeeId") String employeeId,
            @Param("date") LocalDate date);

    List<EmployeeShift> findByEmployeeId(String employeeId);
}
