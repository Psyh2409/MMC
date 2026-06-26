package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.List;


@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor // Це вилікує помилки створення об'єкта Hibernate
public class User {
    @Id
    @UuidGenerator // Генерує UUID на стороні застосунку
    private UUID id;

    @NotBlank(message = "Ім'я не може бути порожнім")
    private String name;

    @Column(unique = true, nullable = false)
    @Email(message = "Невірний формат Email")
    @NotBlank(message = "Email не може бути порожнім")
    private String email;

    @Column(nullable = false)
    @NotBlank(message = "Пароль не може бути порожнім")
    @Size(min = 8, message = "Пароль має бути не менше 8 символів")
    private String password;

    // Стан акаунта (Активний/Заблокований)
    private boolean enabled = false;

    @Column(name = "chat_enabled", nullable = false)
    private boolean chatEnabled = true;

    @Column(name = "comments_enabled", nullable = false)
    private boolean commentsEnabled = true;

    @Column(name = "roles_mask")
    private int rolesMask = 2; // READER за замовчуванням

    private String authProvider;
    private String providerId;

    @Column(columnDefinition = "TEXT")
    private String adminNotes;

    private String passwordResetToken;

    private LocalDateTime passwordResetTokenExpiry;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "avatar_file_name")
    private String avatarFileName; // Сюди писатиметься унікальний UUID файлу

    // Додай це поле до інших текстових полів у User.java
    @Column(name = "phone", length = 20)
    private String phone;

    // Додаємо поле для відстеження заявки на верифікацію фахівця
    // @Builder.Default гарантує, що при створенні об'єкта через Builder значення буде false, а не null
    @Builder.Default
    @Column(name = "is_pending_specialist", nullable = false, columnDefinition = "boolean default false")
    private boolean pendingSpecialist = false;

    // Зв'язок із заявкою на фахівця. LAZY заощаджує RAM: дані диплома тягнуться з бази тільки тоді, коли ти розгортаєш рядок.
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private SpecialistApplication specialistApplication;

    // --- ЗВ'ЯЗКИ ДЛЯ ПОВНОГО ВИДАЛЕННЯ (Особисті дані клієнта) ---

    // У Request.java поле називається user
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Request> requests = new ArrayList<>();

    // У Comment.java поле називається author
    @OneToMany(mappedBy = "author", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    // Нотатки, які клієнт НАПИСАВ САМ (якщо він автор - вони теж видаляються)
    @OneToMany(mappedBy = "author", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<TherapyNote> authoredNotes = new ArrayList<>();

    // ЗВ'ЯЗКИ БЕЗ ВИДАЛЕННЯ (Для збереження твоїх записів)
    @OneToMany(mappedBy = "client")
    private List<TherapyNote> clientNotes = new ArrayList<>();

    @OneToMany(mappedBy = "therapist")
    private List<TherapyNote> therapistNotes = new ArrayList<>();

    // Твій конструктор (тепер він не заважає Hibernate завдяки @NoArgsConstructor)
    public User(String name, String email, String password, RoleBit initialRole) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.rolesMask = (initialRole != null) ? initialRole.getMask() : 2;
        this.enabled = true;
    }

    // === БЛОК 3: МАГІЯ ВІДВ'ЯЗУВАННЯ НОТАТОК ===
    // Цей метод автоматично спрацьовує за мілісекунду до видалення юзера.
    // Він каже: "Якщо цей юзер був у нотатках, просто забудь про нього, але нотатку залиш".
    @PreRemove
    private void preRemoveNotes() {
        if (authoredNotes != null) {
            for (TherapyNote note : authoredNotes) { note.setAuthor(null); }
        }
        if (clientNotes != null) {
            for (TherapyNote note : clientNotes) { note.setClient(null); }
        }
        if (therapistNotes != null) {
            for (TherapyNote note : therapistNotes) { note.setTherapist(null); }
        }
    }

    // --- ЛОГІКА ПЕРЕВІРКИ РОЛЕЙ ---
    public boolean isTest() { return hasRole(RoleBit.TEST); }
    public boolean isAdmin() { return hasRole(RoleBit.ADMIN); }
    public boolean isTherapist() { return hasRole(RoleBit.THERAPIST); }
    public boolean isClient() { return hasRole(RoleBit.CLIENT); }
    public boolean isReader() { return hasRole(RoleBit.READER); }
    public boolean isGuest() { return hasRole(RoleBit.GUEST); }

    public String getPrimaryRoleName() {
        if (isTest()) {
            return "Test";
        }
        if (isAdmin()) {
            return "ADMIN";
        }
        if (isTherapist()) {
            return "THERAPIST";
        }
        if (isClient()) {
            return "CLIENT";
        }
        if (isReader()) {
            return "READER";
        }
        if (isGuest()) {
            return "GUEST";
        }
        return "UNKNOWN";
    }

    // --- БІТОВІ ОПЕРАЦІЇ ---
    public void addRole(RoleBit role) {
        this.rolesMask |= role.getMask();
    }

    public boolean hasRole(RoleBit role) {
        return (this.rolesMask & role.getMask()) != 0;
    }

    public void removeRole(RoleBit role) {
        this.rolesMask &= ~role.getMask();
    }

    public Collection<SimpleGrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (RoleBit role : RoleBit.values()) {
            if (hasRole(role)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
            }
        }
        return authorities;
    }

    public User orElseThrow() {
        return this;
    }
}
