package root.cyb.mh.attendancesystem.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.model.Device;

@Service
public class SyncService {

    // Run every 5 minutes
    @Scheduled(fixedRate = 300000)
    public void syncAllDevices() {
        // Push Protocol implementation: Devices push data to AdmsController.
        // Scheduled sync is not required for data fetching.
    }

    public void syncDevice(Device device) {
        // Logic moved to AdmsController (Push)
    }
}
