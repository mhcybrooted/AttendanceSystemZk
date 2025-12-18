package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import root.cyb.mh.attendancesystem.model.Shift;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
}
