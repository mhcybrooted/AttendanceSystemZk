package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import root.cyb.mh.attendancesystem.service.AdmsService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/iclock")
public class AdmsController {

    @Autowired
    private AdmsService admsService;

    // Handshake
    @GetMapping("/cdata")
    public String handshake(HttpServletRequest request) {
        return "OK";
    }

    // Data Push
    @PostMapping("/cdata")
    public String receiveData(@RequestParam(required = false) String SN,
            @RequestParam(required = false) String table,
            @RequestBody String body,
            HttpServletRequest request) {
        System.out.println("ADMS Push: SN=" + SN + ", Table=" + table);
        return admsService.processCdata(SN, table, body);
    }

    // Command Request (Device asks "Any commands for me?")
    @GetMapping("/getrequest")
    public String getRequest(@RequestParam String SN) {
        return admsService.getPendingCommand();
    }

    // Registry check
    @GetMapping("/registry")
    public String registry(@RequestParam String SN) {
        return "RegistryCode=QWC5251100143"; // Or whatever logic
    }
}
