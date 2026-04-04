package org.mental_management_center.mmc.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collection;
import org.hibernate.annotations.CreationTimestamp;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();
    private String authProvider;
    private String providerId;
    @Column(columnDefinition = "TEXT")
    private String adminNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public User() {
    }

    public User(String name, String email, String password, Role initialRole) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.roles.add(initialRole);
    }

    public boolean isAdmin() {
        return roles != null && roles.contains(Role.ADMIN);
    }
    public boolean isColleague() {
        return roles != null && roles.contains(Role.COLLEAGUE);
    }
    public boolean isClient() {
        return roles != null && roles.contains(Role.CLIENT);
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }
    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    public Collection<SimpleGrantedAuthority> getAuthorities() {
        return roles.stream().map(
                role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

}
