package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.TherapyNote;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.TherapyNoteRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.service.FileStorageService;
import org.mental_management_center.mmc.service.TherapyNoteService;
import org.mental_management_center.mmc.service.TherapyRoomService;
import org.mental_management_center.mmc.service.UserService;
import org.mental_management_center.mmc.web.form.PasswordChangeForm;
import org.mental_management_center.mmc.web.form.ProfileUpdateForm;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Controller
public class UserProfileController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final TherapyNoteService therapyNoteService;
    private final TherapyNoteRepository therapyNoteRepository;
    private final TherapyRoomService therapyRoomService;
    private final FileStorageService fileStorageService;



    public UserProfileController(UserService userService, UserRepository userRepository, TherapyNoteService therapyNoteService, TherapyNoteRepository therapyNoteRepository, TherapyRoomService therapyRoomService, FileStorageService fileStorageService) {
        this.userService = userService;
        this.userRepository = userRepository;
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

            // КРОК 1: Створюємо конфігурацію пагінації вручну для першої сторінки (0 сторінка, 2 елементи)
            Pageable pageable = PageRequest.of(
                    0, 10, Sort.by("createdAt").descending());

            // Запитуємо порційні дані у сервісу
            Page<TherapyNote> notesPage = therapyNoteService.getNotesByAuthor(user.getId(), pageable);

            // Передаємо чистий список для вашого th:each="note : ${myNotes}"
            model.addAttribute("myNotes", notesPage.getContent());

            // Передаємо лічильники для кнопок фронтенду
            model.addAttribute("currentNotesPage", notesPage.getNumber());
            model.addAttribute("totalNotesPages", notesPage.getTotalPages());
            model.addAttribute("hasMoreNotes", notesPage.hasNext());

            // 2. Логіка запрошення (сповіщення)
            if (user.isClient()) {
                model.addAttribute("therapyRoomUrl", "/therapy/room/" + user.getId());
                boolean isSessionActive = therapyRoomService.isRoomActive(user.getId());
                model.addAttribute("hasInvitation", isSessionActive);
            }

            if (!model.containsAttribute("profileUpdateForm")) {
                ProfileUpdateForm profileUpdateForm = new ProfileUpdateForm();
                profileUpdateForm.setName(user.getName());
                profileUpdateForm.setPhone(user.getPhone());
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

            // КРИТИЧНИЙ ФІКС ПАГІНАЦІЇ: Ініціалізуємо початкову порцію нотаток (0 сторінка, по 2 елементи)
            Pageable pageable = PageRequest.of(
                    0, 10, Sort.by("createdAt").descending());

            Page<TherapyNote> notesPage = therapyNoteService.getNotesByAuthor(user.getId(), pageable);

            // Передаємо чистий список та лічильники сторінок, щоб Thymeleaf не видавав лінкових помилок
            model.addAttribute("myNotes", notesPage.getContent());
            model.addAttribute("currentNotesPage", notesPage.getNumber());
            model.addAttribute("totalNotesPages", notesPage.getTotalPages());
            model.addAttribute("hasMoreNotes", notesPage.hasNext());

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
            userService.updateProfileDetails(principal.getName(), profileUpdateForm.getName(), profileUpdateForm.getPhone());

            userService.findByEmail(principal.getName()).ifPresent(user -> {
                model.addAttribute("user", user);
                ProfileUpdateForm refreshedForm = new ProfileUpdateForm();
                refreshedForm.setName(user.getName());
                refreshedForm.setPhone(user.getPhone());
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

    // Ендпоінт для видалення терапевтичної нотатки
    @PostMapping("/profile/notes/delete/{id}")
    public String deleteTherapyNote(@PathVariable UUID id, Principal principal) {
        // Викликаємо сервіс для видалення
        therapyNoteService.deleteNote(id);

        // Повертаємо користувача назад на вкладку нотаток у профілі
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

    @GetMapping("/profile/notes/fragment")
    public ModelAndView getNotesFragment(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Principal principal) {

        User user = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Pageable pageable = PageRequest.of(
                page, size, Sort.by("createdAt").descending());

        Page<TherapyNote> notesPage = therapyNoteService.getNotesByAuthor(user.getId(), pageable);

        // Повертаємо шматок сторінки "notesList", який ми намітили в profile.html
        ModelAndView mav = new ModelAndView("profile :: notesList");
        mav.addObject("myNotes", notesPage.getContent());
        mav.addObject("currentNotesPage", notesPage.getNumber());
        mav.addObject("totalNotesPages", notesPage.getTotalPages());
        mav.addObject("hasMoreNotes", notesPage.hasNext());
        mav.addObject("user", user);

        return mav;
    }
}
