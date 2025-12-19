package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import root.cyb.mh.attendancesystem.model.WorkSchedule;
import root.cyb.mh.attendancesystem.repository.WorkScheduleRepository;
import root.cyb.mh.attendancesystem.repository.DeviceRepository;
import java.util.List;

@Controller
public class SettingsController {

    @Autowired
    private WorkScheduleRepository workScheduleRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.PublicHolidayRepository publicHolidayRepository;

    @GetMapping("/settings")
    public String settings(Model model) {
        // We assume only one global schedule for now. Get the first one or create it.
        WorkSchedule schedule = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        if (schedule.getId() == null) {
            workScheduleRepository.save(schedule); // Initialize if empty
        }
        model.addAttribute("schedule", schedule);
        model.addAttribute("holidays",
                publicHolidayRepository.findAll(org.springframework.data.domain.Sort.by("date")));
        return "settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@ModelAttribute WorkSchedule schedule,
            @RequestParam(required = false) List<Integer> weekendDaysList) {
        WorkSchedule existing = workScheduleRepository.findAll().stream().findFirst().orElse(new WorkSchedule());
        if (schedule.getStartTime() != null)
            existing.setStartTime(schedule.getStartTime());
        if (schedule.getEndTime() != null)
            existing.setEndTime(schedule.getEndTime());
        existing.setLateToleranceMinutes(schedule.getLateToleranceMinutes());
        existing.setEarlyLeaveToleranceMinutes(schedule.getEarlyLeaveToleranceMinutes());
        // Save Default Annual Leave Quota
        existing.setDefaultAnnualLeaveQuota(schedule.getDefaultAnnualLeaveQuota());

        // Convert list [6, 7] to string "6,7"
        if (weekendDaysList != null) {
            existing.setWeekendDays(
                    String.join(",", weekendDaysList.stream().map(String::valueOf).toArray(String[]::new)));
        } else {
            existing.setWeekendDays("");
        }

        workScheduleRepository.save(existing);
        return "redirect:/settings?success";
    }

    @PostMapping("/settings/holidays/add")
    public String addHoliday(@RequestParam String name, @RequestParam java.time.LocalDate date) {
        root.cyb.mh.attendancesystem.model.PublicHoliday holiday = new root.cyb.mh.attendancesystem.model.PublicHoliday();
        holiday.setName(name);
        holiday.setDate(date);
        publicHolidayRepository.save(holiday);
        return "redirect:/settings";
    }

    @GetMapping("/settings/holidays/delete")
    public String deleteHoliday(@RequestParam Long id) {
        publicHolidayRepository.deleteById(id);
        return "redirect:/settings";
    }

    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private root.cyb.mh.attendancesystem.repository.AttendanceLogRepository attendanceLogRepository;
    @Autowired
    private root.cyb.mh.attendancesystem.repository.EmployeeRepository employeeRepository;

    @PostMapping("/settings/generate-demo-data")
    public String generateDemoData() {
        java.time.LocalDate today = java.time.LocalDate.now();
        Long deviceId = deviceRepository.count() > 0 ? deviceRepository.findAll().get(0).getId() : 1L;

        // Ensure employees exist
        createDemoEmployee("101", "Alice OnTime");
        createDemoEmployee("102", "Bob Late");
        createDemoEmployee("103", "Charlie Early");
        createDemoEmployee("104", "Dave LateEarly");
        createDemoEmployee("105", "Eve Absent");
        // 106 will be on leave if we add leave request, for now just skip logs

        // 1. Alice: On Time (09:00 - 18:00)
        createLog("101", today.atTime(8, 55), deviceId);
        createLog("101", today.atTime(18, 5), deviceId);

        // 2. Bob: Late (09:30 - 18:05)
        createLog("102", today.atTime(9, 30), deviceId);
        createLog("102", today.atTime(18, 5), deviceId);

        // 3. Charlie: Early (08:50 - 17:00)
        createLog("103", today.atTime(8, 50), deviceId);
        createLog("103", today.atTime(17, 0), deviceId);

        // 4. Dave: Late & Early (09:45 - 16:30)
        createLog("104", today.atTime(9, 45), deviceId);
        createLog("104", today.atTime(16, 30), deviceId);

        return "redirect:/settings?success";
    }

    private void createDemoEmployee(String id, String name) {
        if (!employeeRepository.existsById(id)) {
            root.cyb.mh.attendancesystem.model.Employee emp = new root.cyb.mh.attendancesystem.model.Employee();
            emp.setId(id);
            emp.setName(name);
            emp.setRole("User");
            employeeRepository.save(emp);
        }
    }

    private void createLog(String empId, java.time.LocalDateTime ts, Long deviceId) {
        if (!attendanceLogRepository.existsByEmployeeIdAndTimestampAndDeviceId(empId, ts, deviceId)) {
            root.cyb.mh.attendancesystem.model.AttendanceLog log = new root.cyb.mh.attendancesystem.model.AttendanceLog();
            log.setEmployeeId(empId);
            log.setTimestamp(ts);
            log.setDeviceId(deviceId);
            attendanceLogRepository.save(log);
        }
    }
}
