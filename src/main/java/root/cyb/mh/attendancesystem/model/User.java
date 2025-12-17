package root.cyb.mh.attendancesystem.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "app_users") // 'users' is often a reserved keyword in DBs
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // "ADMIN", "HR"
}
