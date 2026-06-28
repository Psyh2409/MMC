package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.RoleBit; // Наш новий енам
import org.mental_management_center.mmc.model.VerificationToken;
import org.mental_management_center.mmc.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@SuppressWarnings("null")
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final VerificationTokenRepository tokenRepository; // Нове
    private final EmailService emailService; // Нове
    private final ChatMessageRepository chatMessageRepository;
    private final JournalPostRepository journalPostRepository;
    private final RequestRepository requestRepository;
    private final SessionRegistry sessionRegistry;
    private final TherapyNoteRepository therapyNoteRepository;
    private final TherapyAssignmentRepository therapyAssignmentRepository;

    @Transactional
    public void registerNewUser(User user, String confirmPassword, boolean isPendingSpecialist) {

        // 1. Перевірка паролів
        if (!user.getPassword().equals(confirmPassword)) {
            throw new RuntimeException("Паролі не збігаються!");
        }

        if(userRepository.existsByEmail(user.getEmail()))
            throw new RuntimeException("This email address is already taken");

        // 2. Встановлюємо намір стати фахівцем (НАШ НОВИЙ КРОК)
        user.setPendingSpecialist(isPendingSpecialist);

        // 3. Налаштування ролей та провайдера (Базова роль Читач - біт 2 залишається)
        if (!user.hasRole(RoleBit.READER)) {
            user.addRole(RoleBit.READER);
        }

        if (user.getAuthProvider() == null || user.getAuthProvider().isEmpty())
            user.setAuthProvider("LOCAL");

        // 4. Шифрування пароля
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        // 5. ТИМЧАСОВО вимикаємо юзера до підтвердження
        user.setEnabled(false);

        // Зберігаємо юзера, щоб отримати його UUID для токена
        userRepository.save(user);

        // 6. ГЕНЕРУЄМО ТОКЕН (Ініціація)
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken(token, user);
        tokenRepository.save(verificationToken);

        // 7. ВІДПРАВЛЯЄМО ЛИСТ
        emailService.sendVerificationEmail(user.getEmail(), token);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User upsertOAuth2User(String email, String name, String provider, String providerId) {
        return userRepository.findByEmail(email)
                .map(existingUser -> {
                    if ((existingUser.getName() == null || existingUser.getName().isBlank())
                            && name != null && !name.isBlank()) {
                        existingUser.setName(name);
                    }
                    if (existingUser.getAuthProvider() == null || existingUser.getAuthProvider().isBlank()) {
                        existingUser.setAuthProvider(provider);
                    }
                    if (providerId != null && !providerId.isBlank()) {
                        existingUser.setProviderId(providerId);
                    }
                    if (!existingUser.isEnabled()) {
                        issueVerificationToken(existingUser);
                    }
                    return userRepository.save(existingUser);
                })
                .orElseThrow(() -> new OAuth2AuthenticationException("Користувача з email " + email + " не знайдено в системі. Будь ласка, зареєструйтесь спочатку."));
    }

    @Transactional
    public boolean confirmUser(String token) {
        // 1. Шукаємо токен
        VerificationToken verificationToken = tokenRepository.findByToken(token);

        if (verificationToken == null) {
            return false; // Такого ключа немає
        }

        // 2. Перевіряємо термін дії
        if (verificationToken.getExpiryDate().isBefore(java.time.LocalDateTime.now())) {
            return false; // Ключ "прострочений"
        }

        // 3. Активуємо юзера
        User user = verificationToken.getUser();
        user.setEnabled(true);
        userRepository.save(user);

        // 4. Видаляємо використаний токен (гігієна бази даних)
        tokenRepository.delete(verificationToken);

        return true;
    }

    public void promoteToClient(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Der Benutzer wurde nicht gefunden"));

        // Використовуємо наш новий метод додавання біта
        user.addRole(RoleBit.CLIENT);
        userRepository.save(user);
    }

    @SuppressWarnings("null")
    @Transactional
    public void deleteUserById(UUID id, String currentAdminEmail) {
        User userToDelete = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Der Benutzer wurde nicht gefunden"));

        if (userToDelete.getEmail().equals(currentAdminEmail)) {
            throw new RuntimeException("Sie konnen Ihr eigenes Administratorkonto nicht loschen!");
        }

        String emailToInvalidate = userToDelete.getEmail();

        // БЕЗПЕЧНЕ ВИДАЛЕННЯ: спочатку зовнішні ключі (хвости)
        tokenRepository.deleteByUser(userToDelete);
        requestRepository.deleteByUserId(id);
        chatMessageRepository.deleteBySenderIdOrRecipientId(id, id);
        journalPostRepository.deleteByUserId(id);
        therapyNoteRepository.deleteByClientId(id);
        therapyNoteRepository.deleteByTherapistId(id);
        therapyAssignmentRepository.deleteAllAssignmentsRelatedToUser(id);

        // Тільки тепер видаляємо юзера
        userRepository.deleteById(id);

        // Викидаємо примару з мережі
        invalidateUserSessions(emailToInvalidate);
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword, String confirmNewPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Поточний пароль вказано невірно");
        }
        if (!newPassword.equals(confirmNewPassword)) {
            throw new RuntimeException("Нові паролі не збігаються");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new RuntimeException("Новий пароль має відрізнятися від поточного");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void updateProfileDetails(String email, String newName, String newPhone) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setName(newName);
        user.setPhone(newPhone); // 🎯 Зберігаємо телефон
        userRepository.save(user);
    }

    @Transactional
    public void initiatePasswordReset(String email) {
        try {
            userRepository.findByEmail(email).ifPresent(user -> {
                try {
                    user.setPasswordResetToken(UUID.randomUUID().toString());
                    user.setPasswordResetTokenExpiry(java.time.LocalDateTime.now().plusHours(2));
                    userRepository.save(user);
                    logger.info("Generated password reset token for user: {}", email);
                    emailService.sendPasswordResetEmail(user.getEmail(), user.getPasswordResetToken());
                    logger.info("Password reset email sent successfully to: {}", email);
                } catch (Exception e) {
                    logger.error("Failed to process password reset for user {}: {}", email, e.getMessage(), e);
                    throw new RuntimeException("Не вдалося обробити запит на відновлення пароля: " + e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            logger.error("Error in initiatePasswordReset for email {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Не вдалося ініціювати відновлення пароля: " + e.getMessage(), e);
        }
    }

    public boolean isPasswordResetTokenValid(String token) {
        return userRepository.findByPasswordResetToken(token)
                .filter(user -> user.getPasswordResetTokenExpiry() != null
                        && user.getPasswordResetTokenExpiry().isAfter(java.time.LocalDateTime.now()))
                .isPresent();
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmNewPassword) {
        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new RuntimeException("Посилання для відновлення недійсне"));

        if (user.getPasswordResetTokenExpiry() == null
                || user.getPasswordResetTokenExpiry().isBefore(java.time.LocalDateTime.now())) {
            throw new RuntimeException("Термін дії посилання для відновлення минув");
        }
        if (!newPassword.equals(confirmNewPassword)) {
            throw new RuntimeException("Нові паролі не збігаються");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new RuntimeException("Новий пароль має відрізнятися від поточного");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userRepository.save(user);
    }

    private void issueVerificationToken(User user) {
        tokenRepository.findByUser(user).ifPresent(tokenRepository::delete);
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken(token, user);
        tokenRepository.save(verificationToken);
        emailService.sendVerificationEmail(user.getEmail(), token);
    }

    @Transactional
    public void toggleChatStatus(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));
        user.setChatEnabled(!user.isChatEnabled());
        userRepository.save(user);
    }

    @Transactional
    public void toggleCommentsStatus(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));
        user.setCommentsEnabled(!user.isCommentsEnabled());
        userRepository.save(user);
    }

    @Transactional
    public void toggleUserStatus(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        // Міняємо true на false або навпаки
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);

        // Якщо забанили - викидаємо з системи
        if (!user.isEnabled()) {
            invalidateUserSessions(user.getEmail());
        }
    }

    public User findById(UUID id) {
        return userRepository.findById(id).orElse(null);
    }

    public User findAdmin() {
        return userRepository.findRealUsers().stream()
                .filter(User::isAdmin)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Реального адміністратора не знайдено"));
    }

    // ВИДАЛЕНО: findAll() - небезпечний метод, який повертав всіх користувачів без фільтрації
    // Замість цього використовуйте getVisibleUsers(currentUser) для фільтрації тестових/реальних користувачів

    @Transactional
    public void updateAvatar(String email, String avatarFileName) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setAvatarFileName(avatarFileName);
            userRepository.save(user); // ЗАФІКСУВАЛИ В БАЗУ ДАНИХ
        });
    }

    public void invalidateUserSessions(String email) {
        if (email == null) return;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String principalEmail = null;

            if (principal instanceof UserDetails) {
                principalEmail = ((UserDetails) principal).getUsername();
            } else if (principal instanceof org.mental_management_center.mmc.service.OAuth2Principal) {
                principalEmail = ((org.mental_management_center.mmc.service.OAuth2Principal) principal).getName();
            }

            if (email.equals(principalEmail)) {
                for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                    session.expireNow(); // Розриваємо з'єднання
                }
            }
        }
    }

    // У UserService.java
    public List<User> getVisibleUsers(User currentUser) {
        if (currentUser.hasRole(RoleBit.TEST)) {
            // Тестувальник бачить ТІЛЬКИ тестових
            return userRepository.findTestUsers();
        } else {
            // Реальний адмін бачить ТІЛЬКИ реальних
            return userRepository.findRealUsers();
        }
    }

    @Transactional
    public void promoteToSpecialist(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Користувач не знайдений"));

        // Додаємо роль фахівця
        user.addRole(RoleBit.THERAPIST);

        // Прибираємо маркер "очікування"
        user.setPendingSpecialist(false);

        userRepository.save(user);
    }
}
