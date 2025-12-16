package root.cyb.mh.attendancesystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    @Id
    private String id; // Corresponds to ZK User ID

    private String name;
    private String cardId;
    private String role;

    @jakarta.persistence.ManyToOne
    private Department department;

    private String email;
    private String password;
    @jakarta.persistence.Column(length = 100000) // Large text for Base64 image
    private String photoBase64;
}
