package org.mental_management_center.mmc.service;

import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.RoleBit; // Наш новий енам
import org.mental_management_center.mmc.model.VerificationToken;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.repository.VerificationTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final VerificationTokenRepository tokenRepository; // Нове
    private final EmailService emailService; // Нове

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
            String token = java.util.UUID.randomUUID().toString();
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
                    user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    return userRepository.save(user);
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

    public void promoteToClient(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Der Benutzer wurde nicht gefunden"));

        // Використовуємо наш новий метод додавання біта
        user.addRole(RoleBit.CLIENT);
        userRepository.save(user);
    }

    public void deleteUserById(Long id, String currentAdminEmail) {
        User userToDelete = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Der Benutzer wurde nicht gefunden"));
        if (userToDelete.getEmail().equals(currentAdminEmail)) {
            throw new RuntimeException("Sie konnen Ihr eigenes Administratorkonto nicht loschen!");
        }
        userRepository.deleteById(id);
    }

    public void toggleUserStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        // Міняємо true на false або навпаки
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }
}