package org.mental_management_center.mmc.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter // Використовуємо Lombok на рівні класу для чистоти
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ці поля заповнюються тільки для ГОСТЕЙ (маска 1)
    private String name;
    private String contact;

    @Column(columnDefinition = "TEXT")
    private String message;

    private LocalDateTime createdAt;

    // Зв'язок із таблицею Users (може бути NULL для анонімів)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Роль того, хто надіслав запит на момент відправки
    private byte rolesMask;

    @PrePersist
    private void prePersist(){
        createdAt = LocalDateTime.now();
    }

    public Request(){}

    // Зручний метод для отримання імені (свого або гостя)
    public String getSenderName() {
        return (user != null) ? user.getName() : name;
    }
}