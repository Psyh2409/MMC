package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.TherapyNote;
import org.mental_management_center.mmc.repository.TherapyNoteRepository;
import org.mental_management_center.mmc.service.FileStorageService;
import org.mental_management_center.mmc.service.TherapyNoteService;
import org.mental_management_center.mmc.service.TherapyRoomService;
import org.mental_management_center.mmc.service.UserService;
import org.mental_management_center.mmc.web.form.PasswordChangeForm;
import org.mental_management_center.mmc.web.form.ProfileUpdateForm;
import jakarta.validation.Valid;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Controller
public class UserProfileController {

    private final UserService userService;
    private final TherapyNoteService therapyNoteService;
    private final TherapyNoteRepository therapyNoteRepository;
    private final TherapyRoomService therapyRoomService;
    private final FileStorageService fileStorageService;



    public UserProfileController(UserService userService, TherapyNoteService therapyNoteService, TherapyNoteRepository therapyNoteRepository, TherapyRoomService therapyRoomService, FileStorageService fileStorageService) {
        this.userService = userService;
        this.therapyNoteService = therapyNoteService;
        this.therapyNoteRepository = therapyNoteRepository;
        this.therapyRoomService = therapyRoomService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/profile")
    public String showProfile(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";

        userService.findByEmail(principal.getName()).ifPresent(user -> {
            model.addAttribute("user", user);

            // 1. Отримуємо нотатки, де цей користувач є автором
            model.addAttribute("myNotes", therapyNoteService.getNotesByAuthor(user.getId()));

            // 2. Логіка запрошення (сповіщення)
            if (user.isClient()) {
                // Клієнт бачить кнопку переходу у свій кабінет
                model.addAttribute("therapyRoomUrl", "/therapy/room/" + user.getId());
                // ПИТАЄМО БЕКЕНД: Терапевт уже відкрив сесію для цього клієнта?
                boolean isSessionActive = therapyRoomService.isRoomActive(user.getId());

                model.addAttribute("hasInvitation", isSessionActive);
            }

            if (!model.containsAttribute("profileUpdateForm")) {
                ProfileUpdateForm profileUpdateForm = new ProfileUpdateForm();
                profileUpdateForm.setName(user.getName());
                model.addAttribute("profileUpdateForm", profileUpdateForm);
            }
        });

        if (!model.containsAttribute("passwordChangeForm")) {
            model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        }
        return "profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@Valid @ModelAttribute("passwordChangeForm") PasswordChangeForm passwordChangeForm,
                                 BindingResult result,
                                 Principal principal,
                                 Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        userService.findByEmail(principal.getName()).ifPresent(user -> model.addAttribute("user", user));

        if (result.hasErrors()) {
            return "profile";
        }

        try {
            userService.changePassword(
                    principal.getName(),
                    passwordChangeForm.getCurrentPassword(),
                    passwordChangeForm.getNewPassword(),
                    passwordChangeForm.getConfirmNewPassword()
            );
            model.addAttribute("passwordSuccess", "Пароль успішно змінено.");
            model.addAttribute("passwordChangeForm", new PasswordChangeForm());
            return "profile";
        } catch (RuntimeException e) {
            model.addAttribute("passwordError", e.getMessage());
            return "profile";
        }
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("profileUpdateForm") ProfileUpdateForm profileUpdateForm,
                                BindingResult result,
                                Principal principal,
                                Model model) {
        if (principal == null) return "redirect:/login";

        userService.findByEmail(principal.getName()).ifPresent(user -> {
            model.addAttribute("user", user);
            model.addAttribute("myNotes", therapyNoteService.getNotesByAuthor(user.getId()));
            if (user.isClient()) {
                model.addAttribute("therapyRoomUrl", "/therapy/room/" + user.getId());
                boolean isSessionActive = therapyRoomService.isRoomActive(user.getId());

                model.addAttribute("hasInvitation", isSessionActive);
            }
            });

        if (!model.containsAttribute("passwordChangeForm")) {
            model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        }

        if (result.hasErrors()) return "profile";

        try {
            userService.updateProfileName(principal.getName(), profileUpdateForm.getName());
            userService.findByEmail(principal.getName()).ifPresent(user -> {
                model.addAttribute("user", user);
                ProfileUpdateForm refreshedForm = new ProfileUpdateForm();
                refreshedForm.setName(user.getName());
                model.addAttribute("profileUpdateForm", refreshedForm);
            });
            model.addAttribute("profileSuccess", "Профіль успішно оновлено.");
            return "profile";
        } catch (RuntimeException e) {
            model.addAttribute("profileError", e.getMessage());
            return "profile";
        }
    }

    @PostMapping("/profile/notes/{id}/edit")
    public String editTherapyNote(@PathVariable("id") UUID noteId,
                                  @RequestParam("content") String newContent) {

        // 1. Знаходимо нотатку в базі за її ID
        TherapyNote note = therapyNoteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("Нотатку не знайдено"));

        // 2. Оновлюємо текст нотатки
        note.setContent(newContent);

        // 3. Зберігаємо оновлену нотатку назад у базу
        therapyNoteRepository.save(note);

        // 4. Повертаємо користувача назад на сторінку профілю
        return "redirect:/profile";
    }

    // 3. НОВИЙ АСИНХРОННИЙ ЕНДПОЇНТ ДЛЯ ЗАВАНТАЖЕННЯ АВАТАРА
    @PostMapping("/api/profile/avatar")
    @ResponseBody
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @RequestParam("avatar") MultipartFile file, Principal principal) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Файл порожній"));
        }

        try {
            // 1. Зберігаємо файл на диск
            String uniqueName = fileStorageService.storeFile(file);

            // 2. ВИПРАВЛЕНО: Записуємо назву файлу безпосередньо в базу даних через сервіс
            userService.updateAvatar(principal.getName(), uniqueName);

            // 3. Повертаємо адресу файлу для фронтенду
            return ResponseEntity.ok(Map.of(
                    "url", "/api/media/" + uniqueName,
                    "fileName", uniqueName
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getLocalizedMessage()));
        }
    }
}
