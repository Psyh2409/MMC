package org.mental_masochistic_club.mmc.model;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;

@Entity
@Table(name="users")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank(message = "Der Name darf nicht leer sein")
    private String name;
    @Column(unique = true, nullable = false)
    @Email(message = "Das E-Mail-Format ist nicht korrekt")
    @NotBlank(message = "Das E-Mail darf nicht leer sein")
    private String email;
    @NotBlank(message = "Das Passwort darf nicht leer sein")
    @Size(min = 8, message = "Das Psswort muss mindestens acht Zeichen lang sein")
    private String password;

    @Column(nullable = false)
    private String role;

    public User() {
    }

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(this.role);
    }
}
