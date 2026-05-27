package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter @Setter // Використовуємо Lombok на рівні класу для чистоти
public class Request {

    @Id
    @UuidGenerator // Генерує UUID на стороні застосунку
    private UUID id;

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
    private int rolesMask;

    // У класі Request.java
    @Column(columnDefinition = "TEXT")
    private String adminReply; // Тут зберігатиметься твій текст

    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.NEW; // NEW, ANSWERED, CLOSED

    @PrePersist
    private void prePersist(){
        createdAt = LocalDateTime.now();
    }

    public Request(){}

    public boolean hasRole(RoleBit role) {
        return (rolesMask & role.getMask()) != 0;
    }

    public boolean isClientRequest() {
        return hasRole(RoleBit.CLIENT);
    }

    public boolean isGuestRequest() {
        return hasRole(RoleBit.GUEST) && !isClientRequest() && !hasRole(RoleBit.READER);
    }

    public String getRoleLabel() {
        if (hasRole(RoleBit.ADMIN)) {
            return "АДМІН";
        }
        if (hasRole(RoleBit.THERAPIST)) {
            return "ТЕРАПЕВТ";
        }
        if (hasRole(RoleBit.CLIENT)) {
            return "КЛІЄНТ";
        }
        if (hasRole(RoleBit.READER)) {
            return "ЧИТАЧ";
        }
        return "ГІСТЬ";
    }
}
