package org.mental_management_center.mmc.service;

import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.RoleBit; // Наш новий енам
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void registerNewUser(User user, String confirmPassword) {
        // 1. НАЙВАЖЛИВІШЕ: Перевірка на співпадіння паролів
        // Порівнюємо "чисті" рядки перед кодуванням
        if (!user.getPassword().equals(confirmPassword)) {
            throw new RuntimeException("Паролі не збігаються! Будь ласка, спробуйте ще раз.");
        }

        if(userRepository.existsByEmail(user.getEmail()))
            throw new RuntimeException("This email address is already taken");

        // Якщо маска порожня (хоча дефолт 2), або ми хочемо явно додати READER
        if (!user.hasRole(RoleBit.READER)) {
            user.addRole(RoleBit.READER);
        }

        if (user.getAuthProvider() == null || user.getAuthProvider().isEmpty())
            user.setAuthProvider("LOCAL");

        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        userRepository.save(user);
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