package org.mental_management_center.mmc.service;

import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.RoleBit; // Наш новий енам
import org.mental_management_center.mmc.model.VerificationToken;
import org.mental_management_center.mmc.repository.ChatMessageRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.repository.JournalPostRepository;
import org.mental_management_center.mmc.repository.VerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@SuppressWarnings("null")
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final VerificationTokenRepository tokenRepository; // Нове
    private final EmailService emailService; // Нове
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private JournalPostRepository journalPostRepository;

    // Оновлений конструктор — Spring сам підставить сюди всі залежності
    public UserService(UserRepository userRepository,
                       BCryptPasswordEncoder passwordEncoder,
                       VerificationTokenRepository tokenRepository,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
    }

        @Transactional // Додай цю анотацію, щоб якщо лист не відправиться, юзер не зберігся (відкат)
        public void registerNewUser(User user, String confirmPassword) {

            // 1. Твоя існуюча перевірка паролів
            if (!user.getPassword().equals(confirmPassword)) {
                throw new RuntimeException("Паролі не збігаються!");
            }

            if(userRepository.existsByEmail(user.getEmail()))
                throw new RuntimeException("This email address is already taken");

            // 2. Налаштування ролей та провайдера
            if (!user.hasRole(RoleBit.READER)) {
                user.addRole(RoleBit.READER);
            }

            if (user.getAuthProvider() == null || user.getAuthProvider().isEmpty())
                user.setAuthProvider("LOCAL");

            // 3. Шифрування пароля
            String encodedPassword = passwordEncoder.encode(user.getPassword());
            user.setPassword(encodedPassword);

            // 4. ТИМЧАСОВО вимикаємо юзера до підтвердження
            user.setEnabled(false);

            // Зберігаємо юзера, щоб отримати його ID для токена
            userRepository.save(user);

            // 5. ГЕНЕРУЄМО ТОКЕН (Ініціація)
            String token = UUID.randomUUID().toString();
            VerificationToken verificationToken = new VerificationToken(token, user);
            tokenRepository.save(verificationToken);

            // 6. ВІДПРАВЛЯЄМО ЛИСТ
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
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(email);
                    user.setName((name == null || name.isBlank()) ? email : name);

                    // Додаємо базові ролі для нового соц-користувача
                    user.addRole(RoleBit.GUEST);
                    user.addRole(RoleBit.READER);

                    user.setAuthProvider(provider);
                    user.setProviderId(providerId);
                    user.setEnabled(false);
                    user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    User savedUser = userRepository.save(user);
                    issueVerificationToken(savedUser);
                    return savedUser;
                });
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
        // 1. Твоя перевірка існування користувача
        User userToDelete = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Der Benutzer wurde nicht gefunden"));

        // 2. Твій захист від видалення власного адмін-акаунту
        if (userToDelete.getEmail().equals(currentAdminEmail)) {
            throw new RuntimeException("Sie konnen Ihr eigenes Administratorkonto nicht loschen!");
        }

        // 3. Безпечне очищення повідомлень чату за UUID
        chatMessageRepository.deleteBySenderIdOrRecipientId(id, id);

        // 4. Безпечне очищення зашифрованих постів журналу за UUID
        journalPostRepository.deleteByUserId(id);

        // 5. Твоє фінальне видалення користувача з бази даних
        userRepository.deleteById(id);
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
    }

    public User findById(UUID id) {
        return userRepository.findById(id).orElse(null);
    }

    public User findAdmin() {
        return userRepository.findAll().stream()
                .filter(User::isAdmin)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Адміністратора не знайдено"));
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional
    public void updateAvatar(String email, String avatarFileName) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setAvatarFileName(avatarFileName);
            userRepository.save(user); // ЗАФІКСУВАЛИ В БАЗУ ДАНИХ
        });
    }
}
