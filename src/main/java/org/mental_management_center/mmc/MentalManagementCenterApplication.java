package org.mental_management_center.mmc;

import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.RoleBit;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MentalManagementCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(MentalManagementCenterApplication.class, args);
    }

    @Bean
    public CommandLineRunner createTestUser(UserService userService) {
        return args -> {
            try {
                // Створюємо тестового користувача для тестування відновлення пароля
                User testUser = new User();
                testUser.setName("Test User");
                testUser.setEmail("test@example.com");
                testUser.setPassword("password123");
                testUser.setAuthProvider("LOCAL");
                testUser.addRole(RoleBit.READER);
                testUser.setEnabled(true); // Вмикаємо одразу для тестування

                userService.registerNewUser(testUser, "password123");
                System.out.println("Тестовий користувач створений: test@example.com");
            } catch (Exception e) {
                System.out.println("Тестовий користувач вже існує або помилка: " + e.getMessage());
            }
        };
    }
}
