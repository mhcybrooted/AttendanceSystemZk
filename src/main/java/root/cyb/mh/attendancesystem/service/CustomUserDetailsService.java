package root.cyb.mh.attendancesystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.model.User;
import root.cyb.mh.attendancesystem.repository.UserRepository;

import java.util.Collections;
import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private root.cyb.mh.attendancesystem.repository.EmployeeRepository employeeRepository;

    @Override
    public UserDetails loadUserByUsername(String usernameOrId) throws UsernameNotFoundException {
        // 1. Try to find as standard User (Admin/HR)
        java.util.Optional<User> userOpt = userRepository.findByUsername(usernameOrId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            return new org.springframework.security.core.userdetails.User(
                    user.getUsername(),
                    user.getPassword(),
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        }

        // 2. Try to find as Employee (Login ID = Employee.id)
        java.util.Optional<root.cyb.mh.attendancesystem.model.Employee> empOpt = employeeRepository
                .findById(usernameOrId);
        if (empOpt.isPresent()) {
            root.cyb.mh.attendancesystem.model.Employee emp = empOpt.get();
            // Password is the 'username' field which we hashed
            if (emp.getUsername() == null) {
                throw new UsernameNotFoundException("Employee has no login configured");
            }
            return new org.springframework.security.core.userdetails.User(
                    emp.getId(), // Principal Name is ID
                    emp.getUsername(), // Password is the hashed 'username' field
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
        }

        throw new UsernameNotFoundException("User/Employee not found: " + usernameOrId);
    }
}
