package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "verification_tokens")
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    private String token;

    @Setter
    @OneToOne(targetEntity = User.class, fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    private LocalDateTime expiryDate;

    // Порожній конструктор (вимога Hibernate)
    public VerificationToken() {
    }

    // ТОЙ САМИЙ КОНСТРУКТОР для твого UserService
    public VerificationToken(String token, User user) {
        this.token = token;
        this.user = user;
        this.expiryDate = LocalDateTime.now().plusHours(24); // Токен живе добу
    }

    // Геттери та сеттери (або анотація @Data, якщо використовуєш Lombok)
    public Long getId() { return id; }
    public String getToken() { return token; }

    public User getUser() { return user; }

    public LocalDateTime getExpiryDate() { return expiryDate; }
}