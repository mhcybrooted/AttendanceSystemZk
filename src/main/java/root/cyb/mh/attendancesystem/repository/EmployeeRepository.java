package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import root.cyb.mh.attendancesystem.model.Employee;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, String> {
}
