package root.cyb.mh.attendancesystem.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import root.cyb.mh.attendancesystem.model.Device;
import root.cyb.mh.attendancesystem.model.User;
import root.cyb.mh.attendancesystem.repository.DeviceRepository;
import root.cyb.mh.attendancesystem.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner loadData(DeviceRepository deviceRepository, UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            // Devices
            if (deviceRepository.count() == 0) {
                Device device = new Device();
                device.setName("Mb460");
                device.setIpAddress("10.10.15.3");
                device.setPort(4370);
                device.setSerialNumber("QWC5251100143");
                deviceRepository.save(device);
                System.out.println("Pre-loaded device: Mb460 (10.10.15.3)");
            }

            // Users
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setRole("ADMIN");
                userRepository.save(admin);
                System.out.println("Created default ADMIN user: admin / admin123");
            }

            if (userRepository.findByUsername("hr").isEmpty()) {
                User hr = new User();
                hr.setUsername("hr");
                hr.setPassword(passwordEncoder.encode("hr123"));
                hr.setRole("HR");
                userRepository.save(hr);
                System.out.println("Created default HR user: hr / hr123");
            }
        };
    }
}
