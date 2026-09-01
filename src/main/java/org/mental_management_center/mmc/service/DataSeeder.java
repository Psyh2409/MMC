package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.SpecialistApplication;
import org.mental_management_center.mmc.model.TherapyAssignment;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.enums.RoleBit;
import org.mental_management_center.mmc.repository.SpecialistAppRepository;
import org.mental_management_center.mmc.repository.TherapyAssignmentRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@Profile("dev")
@Order(1)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SpecialistAppRepository specialistApplicationRepository;
    private final TherapyAssignmentRepository therapyAssignmentRepository; // <-- 2. Ін'єкція
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        System.out.println("🌱 Запущено DataSeeder: Наповнення тестовими даними...");

        createTestUserIfNotExists("reader@test.com", "ReaderTest", RoleBit.READER.getMask() | RoleBit.TEST.getMask());
        User client = createTestUserIfNotExists("client@test.com", "ClientTest", RoleBit.CLIENT.getMask() | RoleBit.TEST.getMask());
        User admin = createTestUserIfNotExists("admin@test.com", "AdminTest", RoleBit.ADMIN.getMask() | RoleBit.TEST.getMask());
        User therapist = createTestUserIfNotExists("therapist@test.com", "TherapistTest", RoleBit.THERAPIST.getMask() | RoleBit.TEST.getMask());

        // 3. Створення заявки фахівця
        if (specialistApplicationRepository.findByUserId(therapist.getId()).isEmpty()) {
            SpecialistApplication app = SpecialistApplication.builder()
                    .user(therapist)
                    .firstName("Терапевт")
                    .lastName("Тестовий")
                    .middleName("Олександрович")
                    .diplomaSeries("КВ")
                    .diplomaNumber("12345678")
                    .educationLevel("Вища (Магістр)")
                    .specialty("Клінічна психологія")
                    .status("APPROVED")
                    .build();
            specialistApplicationRepository.save(app);
        }

        // 4. Створення активного терапевтичного договору між client та therapist
        if (therapyAssignmentRepository.findByClientId(client.getId()).isEmpty()) {
            TherapyAssignment assignment = TherapyAssignment.builder()
                    .client(client)
                    .therapist(therapist)
                    .approvedByTherapist(therapist) // або approvedByTherapistId залежно від вашої моделі
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            therapyAssignmentRepository.save(assignment);
            System.out.println("✅ Створено активний терапевтичний договір для client@test.com");
        }

        System.out.println("✅ DataSeeder: Завантаження тестових користувачів завершено.");
    }

    private User createTestUserIfNotExists(String email, String password, int mask) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = User.builder()
                    .email(email)
                    .name(email.split("@")[0])
                    .password(passwordEncoder.encode(password))
                    .rolesMask(mask)
                    .enabled(true)
                    .chatEnabled(true)
                    .commentsEnabled(true)
                    .build();
            return userRepository.save(user);
        });
    }
}