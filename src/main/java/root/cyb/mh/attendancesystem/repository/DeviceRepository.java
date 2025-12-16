package root.cyb.mh.attendancesystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import root.cyb.mh.attendancesystem.model.Device;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    java.util.Optional<Device> findBySerialNumber(String serialNumber);
}
