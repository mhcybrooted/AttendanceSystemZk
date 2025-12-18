package root.cyb.mh.attendancesystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.model.Employee;
import root.cyb.mh.attendancesystem.model.EmployeeShift;
import root.cyb.mh.attendancesystem.model.Shift;
import root.cyb.mh.attendancesystem.repository.EmployeeRepository;
import root.cyb.mh.attendancesystem.repository.EmployeeShiftRepository;
import root.cyb.mh.attendancesystem.repository.ShiftRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ShiftService {

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private EmployeeShiftRepository employeeShiftRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    public List<Shift> getAllShifts() {
        return shiftRepository.findAll();
    }

    public Shift createShift(Shift shift) {
        return shiftRepository.save(shift);
    }

    public void deleteShift(Long id) {
        shiftRepository.deleteById(id);
    }

    public EmployeeShift assignShift(String employeeId, Long shiftId, LocalDate startDate, LocalDate endDate) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new RuntimeException("Shift not found"));

        // Basic validation: Check if startDate is before endDate
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start Date must be before End Date");
        }

        EmployeeShift assignment = new EmployeeShift();
        assignment.setEmployee(employee);
        assignment.setShift(shift);
        assignment.setStartDate(startDate);
        assignment.setEndDate(endDate);

        return employeeShiftRepository.save(assignment);
    }

    public EmployeeShift getAssignment(Long id) {
        return employeeShiftRepository.findById(id).orElse(null);
    }

    public void updateAssignment(Long id, Long shiftId, LocalDate startDate, LocalDate endDate) {
        EmployeeShift assignment = employeeShiftRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));

        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new RuntimeException("Shift not found"));

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start Date must be before End Date");
        }

        assignment.setShift(shift);
        assignment.setStartDate(startDate);
        assignment.setEndDate(endDate);
        employeeShiftRepository.save(assignment);
    }

    public void deleteAssignment(Long assignmentId) {
        employeeShiftRepository.deleteById(assignmentId);
    }

    public List<EmployeeShift> getAllAssignments() {
        return employeeShiftRepository.findAll();
    }

    public List<EmployeeShift> getEmployeeShiftHistory(String employeeId) {
        return employeeShiftRepository.findByEmployeeId(employeeId);
    }

    /**
     * Finds the specific shift for an employee on a given date.
     * Returns null if no specific shift is assigned (fallback to global schedule).
     */
    public Shift getShiftForDate(String employeeId, LocalDate date) {
        Optional<EmployeeShift> assignment = employeeShiftRepository.findActiveShiftForEmployee(employeeId, date);
        return assignment.map(EmployeeShift::getShift).orElse(null);
    }
}
