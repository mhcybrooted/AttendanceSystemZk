package root.cyb.mh.attendancesystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.model.AttendanceLog;
import root.cyb.mh.attendancesystem.model.Device;
import root.cyb.mh.attendancesystem.repository.AttendanceLogRepository;
import root.cyb.mh.attendancesystem.repository.DeviceRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class AdmsService {

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    private String pendingCommand = null;

    public void queueCommand(String cmd) {
        this.pendingCommand = "C:" + System.currentTimeMillis() + ":" + cmd;
        System.out.println("Queued Command: " + this.pendingCommand);
    }

    public String getPendingCommand() {
        String cmd = this.pendingCommand;
        this.pendingCommand = null; // Clear after fetching
        return cmd != null ? cmd : "OK";
    }

    public String processCdata(String sn, String table, String data) {
        // Find device by SN once
        Long deviceId = 0L;
        Optional<Device> deviceOpt = deviceRepository.findBySerialNumber(sn);
        if (deviceOpt.isPresent()) {
            deviceId = deviceOpt.get().getId();
        } else {
            // Optionally auto-register device here if needed
            System.out.println("Unknown device SN: " + sn);
        }

        if ("attlog".equalsIgnoreCase(table)) {
            System.out.println("Processing ATTLOG data...");
            String[] lines = data.split("\\r?\\n");
            for (String line : lines) {
                if (line.trim().isEmpty())
                    continue;
                parseAndSaveLog(deviceId, line);
            }
            return "OK";
        } else if ("userinfo".equalsIgnoreCase(table)) {
            System.out.println("Processing USERINFO data...");
            String[] lines = data.split("\\r?\\n");
            for (String line : lines) {
                if (line.trim().isEmpty())
                    continue;
                // Some devices send raw "PIN=..." in userinfo, others might send "USER PIN=..."
                String cleanLine = line.startsWith("USER ") ? line.substring(5) : line;
                parseAndSaveUser(cleanLine);
            }
            return "OK";
        } else if ("operlog".equalsIgnoreCase(table)) {
            // Device sends user data mixed in OPERLOG with "USER " prefix
            System.out.println("Processing OPERLOG data...");
            String[] lines = data.split("\\r?\\n");
            for (String line : lines) {
                if (line.startsWith("USER ")) {
                    parseAndSaveUser(line.substring(5)); // Remove "USER " prefix
                } else if (line.startsWith("USERPIC ")) {
                    parseAndSaveUserPic(line.substring(8)); // Remove "USERPIC " prefix
                }
            }
            return "OK";
        } else {
            // System.out.println("Ignored Table: " + table);
            return "OK";
        }
    }

    @Autowired
    private root.cyb.mh.attendancesystem.repository.EmployeeRepository employeeRepository;

    private void parseAndSaveUser(String line) {
        try {
            // Format: PIN=1\tName=John\t...
            // Or Raw: 1\tJohn\t...
            String id = null;
            String name = null;
            String card = null;
            String role = "User";
            String password = null;

            String[] tokens = line.split("\\t");
            if (line.contains("PIN=")) {
                for (String token : tokens) {
                    if (token.startsWith("PIN="))
                        id = token.substring(4);
                    else if (token.startsWith("Name="))
                        name = token.substring(5);
                    else if (token.startsWith("Card="))
                        card = token.substring(5);
                    else if (token.startsWith("Pri=")) {
                        String pri = token.substring(4);
                        if ("14".equals(pri))
                            role = "Admin";
                        else if ("2".equals(pri))
                            role = "Enroller"; // Common ZK role
                        else if ("6".equals(pri))
                            role = "Manager"; // Common ZK role
                        else if ("0".equals(pri))
                            role = "User";
                        else
                            role = "Privilege " + pri; // Fallback
                    } else if (token.startsWith("Passwd="))
                        password = token.substring(7);
                }
            } else {
                // Raw fallback
                if (tokens.length >= 1)
                    id = tokens[0];
                if (tokens.length >= 2)
                    name = tokens[1];
            }

            if (id != null) {
                root.cyb.mh.attendancesystem.model.Employee emp = employeeRepository.findById(id)
                        .orElse(new root.cyb.mh.attendancesystem.model.Employee());
                emp.setId(id);
                if (name != null)
                    emp.setName(name);
                if (card != null)
                    emp.setCardId(card);
                if (role != null)
                    emp.setRole(role);
                if (password != null)
                    emp.setPassword(password);

                employeeRepository.save(emp);
                System.out.println("Saved User: " + id + " (" + name + ")");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseAndSaveUserPic(String line) {
        try {
            // USERPIC PIN=2 FileName=2.jpg Size=11724 Content=/9j/4AA...
            String id = null;
            String content = null;

            String[] tokens = line.split("\\t");
            for (String token : tokens) {
                if (token.startsWith("PIN="))
                    id = token.substring(4);
                else if (token.startsWith("Content="))
                    content = token.substring(8);
            }

            if (id != null && content != null) {
                root.cyb.mh.attendancesystem.model.Employee emp = employeeRepository.findById(id).orElse(null);
                if (emp != null) {
                    emp.setPhotoBase64(content);
                    employeeRepository.save(emp);
                    System.out.println("Saved Photo for User: " + id);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseAndSaveLog(Long deviceId, String line) {
        try {
            // Format received: 3 2025-12-16 18:28:33 0 1 0 0 0 0 0 0 1762
            // Tokens: [0]=ID, [1]=Time, [2]=Status, [3]=VerifyType...

            String employeeId = null;
            String timeStr = null;

            String[] tokens = line.split("\\t");

            if (tokens.length >= 2) {
                // Try parsing as raw tab-separated
                // Check if it's NOT the key-value format (doesn't contain "=")
                if (!line.contains("PIN=") && !line.contains("Time=")) {
                    employeeId = tokens[0].trim();
                    // Timestamp might be in token 1
                    timeStr = tokens[1].trim();
                } else {
                    // Fallback to Key-Value parser
                    for (String token : tokens) {
                        if (token.startsWith("PIN=")) {
                            employeeId = token.substring(4);
                        } else if (token.startsWith("Time=")) {
                            timeStr = token.substring(5);
                        }
                    }
                }
            }

            if (employeeId != null && timeStr != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime timestamp = LocalDateTime.parse(timeStr, formatter);

                if (!attendanceLogRepository.existsByEmployeeIdAndTimestampAndDeviceId(employeeId, timestamp,
                        deviceId)) {
                    AttendanceLog log = new AttendanceLog();
                    log.setEmployeeId(employeeId);
                    log.setTimestamp(timestamp);
                    log.setDeviceId(deviceId);
                    attendanceLogRepository.save(log);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
