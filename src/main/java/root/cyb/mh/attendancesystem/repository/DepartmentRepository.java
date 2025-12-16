package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import root.cyb.mh.attendancesystem.model.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
}
