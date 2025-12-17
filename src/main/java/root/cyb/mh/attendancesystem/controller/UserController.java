package root.cyb.mh.attendancesystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import root.cyb.mh.attendancesystem.model.User;
import root.cyb.mh.attendancesystem.repository.UserRepository;

@Controller
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "users";
    }

    @PostMapping("/add")
    public String addUser(@RequestParam String username, @RequestParam String password, @RequestParam String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return "redirect:/users?error=exists";
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        userRepository.save(user);
        return "redirect:/users?success=added";
    }

    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id, java.security.Principal principal) {
        userRepository.findById(id).ifPresent(user -> {
            if (!user.getUsername().equals(principal.getName())) { // Prevent self-deletion
                userRepository.delete(user);
            }
        });
        return "redirect:/users?success=deleted";
    }
}
