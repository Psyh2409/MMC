package org.mental_management_center.mmc.service;

import org.mental_management_center.mmc.model.SpecialistApplication;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.SpecialistAppRepository;
import org.mental_management_center.mmc.web.form.EdeboVerificationForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SpecialistService {

    private final SpecialistAppRepository repository;
    private final UserService userService;

    public SpecialistService(SpecialistAppRepository repository, UserService userService) {
        this.repository = repository;
        this.userService = userService;
    }

    @Transactional
    public void submitApplication(User user, EdeboVerificationForm form) {
        // Перевіряємо, чи юзер вже не подав заявку
        if (repository.findByUserId(user.getId()).isPresent()) {
            throw new IllegalStateException("Ви вже подали заявку на верифікацію.");
        }

        SpecialistApplication application = SpecialistApplication.builder()
                .user(user)
                .educationLevel(form.getEducationLevel())
                .diplomaSeries(form.getDiplomaSeries())
                .diplomaNumber(form.getDiplomaNumber())
                .lastName(form.getLastName())
                .firstName(form.getFirstName())
                .middleName(form.getMiddleName())
                .noMiddleName(form.isNoMiddleName())
                .status("PENDING")
                .build();

        repository.save(application);
    }

    @Transactional
    public void approveVerification(UUID userId) {
        // 1. Знаходимо заявку
        SpecialistApplication app = repository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Заявка не знайдена"));

        // 2. Змінюємо статус заявки
        app.setStatus("APPROVED");
        repository.save(app);

        // 3. Викликаємо сервіс користувача для зміни ролі (використовуємо метод з UserService)
        userService.promoteToSpecialist(userId);
    }
}