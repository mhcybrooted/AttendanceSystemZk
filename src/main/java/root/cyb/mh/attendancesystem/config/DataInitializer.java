package root.cyb.mh.attendancesystem.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import root.cyb.mh.attendancesystem.model.Device;
import root.cyb.mh.attendancesystem.repository.DeviceRepository;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner loadData(DeviceRepository deviceRepository) {
        return args -> {
            if (deviceRepository.count() == 0) {
                Device device = new Device();
                device.setName("Mb460");
                device.setIpAddress("10.10.15.3");
                device.setPort(4370);
                device.setSerialNumber("QWC5251100143");
                deviceRepository.save(device);
                System.out.println("Pre-loaded device: Mb460 (10.10.15.3)");
            }
        };
    }
}
