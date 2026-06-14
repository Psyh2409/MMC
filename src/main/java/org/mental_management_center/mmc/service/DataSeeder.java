package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.RoleBit;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.ArticleRepository;
import org.mental_management_center.mmc.repository.CategoryTranslationRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Анотація @Profile("dev") гарантує, що цей код НЕ запуститься на сервері (якщо там prod)
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final CategoryTranslationRepository categoryTranslationRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    //./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev" (fot cmd without './')
    @Override
    @Transactional
    public void run(String... args) {
        System.out.println("🌱 Запущено DataSeeder: Наповнення тестовими даними...");
        // Перевіряємо та створюємо кожного окремо. Це найнадійніший варіант.
        createTestUserIfNotExists("reader@test.com", "ReaderTest", RoleBit.READER.getMask() | RoleBit.TEST.getMask());
        createTestUserIfNotExists("client@test.com", "ClientTest", RoleBit.CLIENT.getMask() | RoleBit.TEST.getMask());
        createTestUserIfNotExists("admin@test.com", "AdminTest", RoleBit.ADMIN.getMask() | RoleBit.TEST.getMask());
        System.out.println("✅ DataSeeder: Завантаження тестових користувачів завершено.");
    }

    private void createTestUserIfNotExists(String email, String password, int mask) {
        if (userRepository.findByEmail(email).isPresent()) {
            return; // Юзер вже є, нічого не чіпаємо
        }
        User user = User.builder()
                .email(email)
                .name(email.split("@")[0])
                .password(passwordEncoder.encode(password))
                .rolesMask(mask)
                .enabled(true)
                .chatEnabled(true)
                .commentsEnabled(true)
                .build();
        userRepository.save(user);
    }
}