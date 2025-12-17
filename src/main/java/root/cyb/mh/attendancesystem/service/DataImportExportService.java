package root.cyb.mh.attendancesystem.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.model.*;
import root.cyb.mh.attendancesystem.repository.*;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class DataImportExportService {

    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private WorkScheduleRepository workScheduleRepository;
    @Autowired
    private UserRepository userRepository;

    // --- EXPORT METODS ---

    public void exportEmployees(PrintWriter writer) throws IOException {
        CSVPrinter printer = new CSVPrinter(writer,
                CSVFormat.DEFAULT.withHeader("ID", "Name", "DepartmentID", "CardID"));
        for (Employee emp : employeeRepository.findAll()) {
            printer.printRecord(emp.getId(), emp.getName(),
                    emp.getDepartment() != null ? emp.getDepartment().getId() : "", emp.getCardId());
        }
        printer.flush();
    }

    public void exportDepartments(PrintWriter writer) throws IOException {
        CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("ID", "Name"));
        for (Department dept : departmentRepository.findAll()) {
            printer.printRecord(dept.getId(), dept.getName());
        }
        printer.flush();
    }

    public void exportLeaveRequests(PrintWriter writer) throws IOException {
        CSVPrinter printer = new CSVPrinter(writer,
                CSVFormat.DEFAULT.withHeader("ID", "EmployeeID", "StartDate", "EndDate", "Reason", "Status"));
        for (LeaveRequest lr : leaveRequestRepository.findAll()) {
            printer.printRecord(lr.getId(), lr.getEmployee().getId(), lr.getStartDate(), lr.getEndDate(),
                    lr.getReason(), lr.getStatus());
        }
        printer.flush();
    }

    public void exportDevices(PrintWriter writer) throws IOException {
        CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("ID", "Name", "IP", "Port", "Serial"));
        for (Device d : deviceRepository.findAll()) {
            printer.printRecord(d.getId(), d.getName(), d.getIpAddress(), d.getPort(), d.getSerialNumber());
        }
        printer.flush();
    }

    public void exportSettings(PrintWriter writer) throws IOException {
        CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("ID", "StartTime", "EndTime",
                "LateTolerance", "EarlyTolerance", "Weekends"));
        for (WorkSchedule ws : workScheduleRepository.findAll()) {
            printer.printRecord(ws.getId(), ws.getStartTime(), ws.getEndTime(), ws.getLateToleranceMinutes(),
                    ws.getEarlyLeaveToleranceMinutes(), ws.getWeekendDays());
        }
        printer.flush();
    }

    public void exportUsers(PrintWriter writer) throws IOException {
        CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("ID", "Username", "Role"));
        for (User u : userRepository.findAll()) {
            printer.printRecord(u.getId(), u.getUsername(), u.getRole());
        }
        printer.flush();
    }

    // --- IMPORT METHODS ---

    public void importEmployees(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
        for (CSVRecord record : records) {
            String id = record.get("ID");
            Employee emp = employeeRepository.findById(id).orElse(new Employee());
            emp.setId(id);
            emp.setName(record.get("Name"));
            String deptId = record.get("DepartmentID");
            if (deptId != null && !deptId.isEmpty()) {
                departmentRepository.findById(Long.parseLong(deptId)).ifPresent(emp::setDepartment);
            }
            if (record.isMapped("CardID"))
                emp.setCardId(record.get("CardID"));
            employeeRepository.save(emp);
        }
    }

    public void importDepartments(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
        for (CSVRecord record : records) {
            Department dept = new Department();
            if (record.isMapped("ID") && !record.get("ID").isEmpty()) {
                Long id = Long.parseLong(record.get("ID"));
                if (departmentRepository.existsById(id))
                    dept.setId(id);
            }
            dept.setName(record.get("Name"));
            departmentRepository.save(dept);
        }
    }

    public void importLeaveRequests(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
        for (CSVRecord record : records) {
            LeaveRequest lr = new LeaveRequest();
            // Assuming new imports, or update if logic matches. Here simple insert for
            // simplicity/demo
            // or match by ID if present
            if (record.isMapped("ID") && !record.get("ID").isEmpty()) {
                Long id = Long.parseLong(record.get("ID"));
                leaveRequestRepository.findById(id).ifPresent(found -> lr.setId(found.getId()));
            }

            String empId = record.get("EmployeeID");
            employeeRepository.findById(empId).ifPresent(lr::setEmployee);

            lr.setStartDate(LocalDate.parse(record.get("StartDate")));
            lr.setEndDate(LocalDate.parse(record.get("EndDate")));
            lr.setReason(record.get("Reason"));
            if (record.isMapped("Status")) {
                lr.setStatus(LeaveRequest.Status.valueOf(record.get("Status")));
            } else {
                lr.setStatus(LeaveRequest.Status.PENDING);
            }
            leaveRequestRepository.save(lr);
        }
    }

    public void importDevices(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
        for (CSVRecord record : records) {
            Device d = new Device();
            if (record.isMapped("ID") && !record.get("ID").isEmpty()) {
                Long id = Long.parseLong(record.get("ID"));
                deviceRepository.findById(id).ifPresent(found -> d.setId(found.getId()));
            }
            d.setName(record.get("Name"));
            d.setIpAddress(record.get("IP"));
            d.setPort(Integer.parseInt(record.get("Port")));
            if (record.isMapped("Serial"))
                d.setSerialNumber(record.get("Serial"));
            deviceRepository.save(d);
        }
    }

    public void importSettings(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
        for (CSVRecord record : records) {
            WorkSchedule ws = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
            ws.setStartTime(LocalTime.parse(record.get("StartTime")));
            ws.setEndTime(LocalTime.parse(record.get("EndTime")));
            ws.setLateToleranceMinutes(Integer.parseInt(record.get("LateTolerance")));
            ws.setEarlyLeaveToleranceMinutes(Integer.parseInt(record.get("EarlyTolerance")));
            ws.setWeekendDays(record.get("Weekends"));
            workScheduleRepository.save(ws);
        }
    }

    public void importUsers(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
        for (CSVRecord record : records) {
            // Only update existing or add new if username unique
            String username = record.get("Username");
            User user = userRepository.findByUsername(username).orElse(new User());
            user.setUsername(username);

            if (record.isMapped("Role"))
                user.setRole(record.get("Role"));

            // Password not imported for security, or assumed handled otherwise.
            // If new user, might need default password.
            if (user.getId() == null) {
                user.setPassword("{noop}123456"); // Default/Temp password
            }

            userRepository.save(user);
        }
    }

}
