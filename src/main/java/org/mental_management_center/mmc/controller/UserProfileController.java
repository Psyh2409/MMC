package org.mental_management_center.mmc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mental_management_center.mmc.dto.NotificationDto;
import org.mental_management_center.mmc.dto.UserActivityDto;
import org.mental_management_center.mmc.model.*;
import org.mental_management_center.mmc.repository.*;
import org.mental_management_center.mmc.service.*;
import org.mental_management_center.mmc.web.form.PasswordChangeForm;
import org.mental_management_center.mmc.web.form.ProfileUpdateForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final TherapyNoteService therapyNoteService;
    private final TherapyNoteRepository therapyNoteRepository;
    private final TherapyRoomService therapyRoomService;
    private final FileStorageService fileStorageService;
    private final ChatMessageRepository chatMessageRepository;
    private final TherapyAssignmentRepository therapyAssignmentRepository;
    private final JournalPostRepository journalPostRepository;
    private final RequestService requestService;
    private final SpecialistAppRepository specialistAppRepository;
    private final UserActivityService userActivityService;
    private final EmailService emailService;
    private final CommentRepository commentRepository;
    private final NotificationService notificationService;

    @GetMapping("/profile")
    public String showProfile(
            @RequestParam(defaultValue = "0") int activityPage,
            Model model,
            Principal principal) {
        if (principal == null) return "redirect:/login";

        log.info("DEBUG: showProfile called for user: {}", principal.getName());

        userService.findByEmail(principal.getName()).ifPresent(user -> {
            log.info("DEBUG: User found: {}, ID: {}, isClient: {}", user.getName(), user.getId(), user.isClient());
            model.addAttribute("user", user);

            // КРОК 1: Створюємо конфігурацію пагінації вручну
            Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

            log.info("DEBUG: Before getPersonalClientNotes");
            // Запитуємо порційні дані у сервісу
            Page<TherapyNote> notesPage = therapyNoteService.getPersonalClientNotes(user.getId(), pageable);
            log.info("DEBUG: After getPersonalClientNotes, notes count: {}", notesPage.getContent().size());
            // Передаємо чистий список для вашого th:each="note : ${myNotes}"
            model.addAttribute("myNotes", notesPage.getContent());

            // Передаємо лічильники для кнопок фронтенду
            model.addAttribute("currentNotesPage", notesPage.getNumber());
            model.addAttribute("totalNotesPages", notesPage.getTotalPages());
            model.addAttribute("hasMoreNotes", notesPage.hasNext());

            // 2. Логіка запрошення (сповіщення)
            if (user.isClient()) {
                log.info("DEBUG: User is client, setting therapy room URL");
                model.addAttribute("therapyRoomUrl", "/therapy/room/" + user.getId());
                boolean isSessionActive = therapyRoomService.isRoomActive(user.getId());
                model.addAttribute("hasInvitation", isSessionActive);

                // Windsurf: Знаходимо активних терапевтів клієнта для кнопки "Профіль фахівця"
                List<TherapyAssignment> activeAssignments = therapyAssignmentRepository.findActiveByClientId(user.getId());
                if (!activeAssignments.isEmpty()) {
                    // Беремо першого терапевта для кнопки (або можна показати список)
                    model.addAttribute("activeAssignments", activeAssignments);
                }
            }

            // 3. Форма оновлення профілю
            if (!model.containsAttribute("profileUpdateForm")) {
                log.info("DEBUG: Creating profileUpdateForm");
                ProfileUpdateForm profileUpdateForm = new ProfileUpdateForm();
                profileUpdateForm.setName(user.getName());
                profileUpdateForm.setPhone(user.getPhone());
                // 🎯 ФІКС: Зчитуємо прапорець з бази даних
                profileUpdateForm.setEmailNotificationsEnabled(user.isEmailNotificationsEnabled());
                model.addAttribute("profileUpdateForm", profileUpdateForm);
            }

            // 4. БЛОК ЄДЕБО (ТЕПЕР В ПРАВИЛЬНОМУ МІСЦІ) ---
            log.info("DEBUG: Before specialistAppRepository.findByUserId");
            Optional<SpecialistApplication> app = specialistAppRepository.findByUserId(user.getId());
            model.addAttribute("specialistApp", app.orElse(null));
            log.info("DEBUG: After specialistAppRepository.findByUserId");

            // 5. ВИТЯГУЄМО АКТИВНІСТЬ КОРИСТУВАЧА (ТЕПЕР УПРАВИЛЬНОМУ БЛОЦІ)
            log.info("DEBUG: Before populateActivityModel");
            populateActivityModel(user, activityPage, model);
            log.info("DEBUG: After populateActivityModel");
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

                // Windsurf: Знаходимо активних терапевтів клієнта для кнопки "Профіль фахівця"
                List<TherapyAssignment> activeAssignments = therapyAssignmentRepository.findActiveByClientId(user.getId());
                if (!activeAssignments.isEmpty()) {
                    // Беремо першого терапевта для кнопки (або можна показати список)
                    model.addAttribute("myTherapist", activeAssignments.get(0).getTherapist());
                }
            }
        });

        if (!model.containsAttribute("passwordChangeForm")) {
            model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        }

        if (result.hasErrors()) return "profile";

        try {
            userService.updateProfileDetails(
                    principal.getName(),
                    profileUpdateForm.getName(),
                    profileUpdateForm.getPhone(),
                    profileUpdateForm.isEmailNotificationsEnabled());

            userService.findByEmail(principal.getName()).ifPresent(user -> {
                model.addAttribute("user", user);
                ProfileUpdateForm refreshedForm = new ProfileUpdateForm();
                refreshedForm.setName(user.getName());
                refreshedForm.setPhone(user.getPhone());
                // 🎯 ФІКС: Оновлюємо форму актуальним станом з бази
                refreshedForm.setEmailNotificationsEnabled(user.isEmailNotificationsEnabled());
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

    @Transactional
    @PostMapping("/profile/request-deactivation")
    public String requestDeactivation(Principal principal, RedirectAttributes redirectAttributes) {

        // Знаходимо користувача, який зараз онлайн
        User currentUser = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Der Benutzer wurde nicht gefunden"));
        UUID userId = currentUser.getId();

        // 1. Рахуємо дані, які зв'язані безпосередньо в User.java
        int commentsCount = currentUser.getComments().size();
        int authoredNotesCount = currentUser.getAuthoredNotes().size();

        // 2. Рахуємо дані через репозиторії (бо вони прив'язані тільки по UUID)
        long chatCount = chatMessageRepository.countBySenderId(userId);
        long journalCount = journalPostRepository.countByUserId(userId);

        long totalActivity = commentsCount + authoredNotesCount + chatCount + journalCount;

        // 3. Терапевтичний бар'єр: якщо активність є, не даємо відправити заявку
        if (totalActivity > 0) {
            String message = String.format(
                    "Вам необхідно власноруч видалити свої дані: коментарів (%d), записів у щоденнику (%d), повідомлень у чаті (%d), нотаток (%d). Тільки після цього можна відправити запит.",
                    commentsCount, journalCount, chatCount, authoredNotesCount
            );
            redirectAttributes.addFlashAttribute("errorMessage", message);
            return "redirect:/profile";
        }

        // 4. Якщо все чисто — створюємо системний запит для адміна
        Request deactivationRequest = new Request();
        deactivationRequest.setUser(currentUser);
        deactivationRequest.setMessage("Користувач власноруч очистив свої дані і просить деактивувати акаунт.");
        requestService.save(deactivationRequest, principal);

        redirectAttributes.addFlashAttribute("successMessage", "Ваш профіль повністю очищено. Заявку на деактивацію відправлено адміністратору.");
        return "redirect:/profile";
    }

    // Приватний метод для уникнення дублювання
    private void populateActivityModel(User user, int page, Model model) {
        Pageable pageable = PageRequest.of(page, 10);
        Page<UserActivityDto> activityPageData = userActivityService.getUserActivitiesPaged(user, pageable);

        model.addAttribute("activities", activityPageData.getContent());
        model.addAttribute("currentActivityPage", activityPageData.getNumber());
        model.addAttribute("activityPage", activityPageData.getNumber()); // Додано аліас для зручності
        model.addAttribute("totalActivityPages", activityPageData.getTotalPages());
    }

    @GetMapping("/profile/activity/feed")
    public String getActivityFeed(
            @RequestParam(defaultValue = "0") int page,
            Model model,
            Principal principal) {

        User user = userService.findByEmail(principal.getName()).orElseThrow();

        // Викликаємо той самий приватний метод
        populateActivityModel(user, page, model);

        return "profile :: activityFeed";
    }

    // Пряме видалення
    @Transactional
    @PostMapping("/profile/activity/delete")
    public String deleteActivity(
            @RequestParam UUID id,
            @RequestParam String type,
            @RequestParam(defaultValue = "0") int page,
            Principal principal) {

        if (principal == null) return "redirect:/login";

        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
        deleteUserActivityItem(id, type, currentUser);

        // Перенаправляємо на ту ж сторінку пагінації
        return "redirect:/profile?activityPage=" + page + "#activity-tab";
    }

    // Експорт на пошту та видалення
    @Transactional
    @PostMapping("/profile/activity/export-delete")
    public String exportAndDeleteActivity(
            @RequestParam UUID id,
            @RequestParam String type,
            @RequestParam(defaultValue = "0") int page,
            Principal principal) {

        if (principal == null) return "redirect:/login";

        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();

        exportActivityToEmail(id, type, currentUser);
        deleteUserActivityItem(id, type, currentUser);

        // Перенаправляємо на ту ж сторінку пагінації
        return "redirect:/profile?activityPage=" + page + "#activity-tab";
    }

    // --- ПРИВАТНІ ДОПОМІЖНІ МЕТОДИ ---

    private void exportActivityToEmail(UUID id, String type, User currentUser) {
        String title = "Особистий запис";
        String content = "";
        String typeLabel = type;
        String upperType = type != null ? type.toUpperCase() : "";

        // 1. ОБРОБКА ЧАТІВ (Публічний та Приватний)
        if (upperType.contains("CHAT")) {
            ChatMessage msg = chatMessageRepository.findById(id).orElse(null);
            if (msg != null && msg.getSenderId().equals(currentUser.getId())) {
                content = msg.getContent();
                String dateStr = msg.getTimestamp() != null
                        ? msg.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                        : "";
                title = "Повідомлення у чаті від " + dateStr;
                typeLabel = upperType.contains("PRIVATE") ? "Приватне повідомлення" : "Повідомлення у публічному чаті";
            }
        }
        // 2. ОБРОБКА КОМЕНТАРІВ ПІД СТАТТЯМИ
        else if (upperType.contains("COMMENT")) {
            Comment comment = commentRepository.findById(id).orElse(null);
            if (comment != null && comment.getAuthor() != null && comment.getAuthor().getId().equals(currentUser.getId())) {
                content = comment.getContent();
                title = (comment.getArticle() != null) ? comment.getArticle().getTitle() : "Коментар до статті";
                typeLabel = "Коментар під статтею";
            }
        }
        // 3. ОБРОБКА НОТАТОК (Усі можливі варіації назв типів)
        else if (upperType.contains("NOTE") || upperType.contains("THERAPY") || upperType.contains("JOURNAL")) {
            TherapyNote note = therapyNoteRepository.findById(id).orElse(null);
            if (note != null) {
                boolean isClient = note.getClient() != null && note.getClient().getId().equals(currentUser.getId());
                boolean isTherapist = note.getTherapist() != null && note.getTherapist().getId().equals(currentUser.getId());

                if (isClient || isTherapist) {
                    content = note.getContent();
                    String dateStr = note.getCreatedAt() != null
                            ? note.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                            : "";
                    title = "Нотатка з терапії від " + dateStr;
                    typeLabel = "Нотатка з терапії";
                }
            }
        }

        if (content != null && !content.isBlank()) {
            emailService.sendActivityExport(currentUser.getEmail(), typeLabel, title, content);
        }
    }

    private void deleteUserActivityItem(UUID id, String type, User currentUser) {
        String upperType = type != null ? type.toUpperCase() : "";

        // 1. ВИДАЛЕННЯ ЧАТІВ
        if (upperType.contains("CHAT")) {
            ChatMessage msg = chatMessageRepository.findById(id).orElse(null);
            if (msg != null && msg.getSenderId().equals(currentUser.getId())) {
                chatMessageRepository.delete(msg);
            }
        }
        // 2. ВИДАЛЕННЯ КОМЕНТАРІВ
        else if (upperType.contains("COMMENT")) {
            Comment comment = commentRepository.findById(id).orElse(null);
            if (comment != null && comment.getAuthor() != null && comment.getAuthor().getId().equals(currentUser.getId())) {
                commentRepository.delete(comment);
            }
        }
        // 3. ВИДАЛЕННЯ НОТАТОК
        else if (upperType.contains("NOTE") || upperType.contains("THERAPY") || upperType.contains("JOURNAL")) {
            TherapyNote note = therapyNoteRepository.findById(id).orElse(null);
            if (note != null) {
                boolean isClient = note.getClient() != null && note.getClient().getId().equals(currentUser.getId());
                boolean isTherapist = note.getTherapist() != null && note.getTherapist().getId().equals(currentUser.getId());

                if (isClient || isTherapist) {
                    therapyNoteRepository.delete(note);
                }
            }
        }
    }

    // Фонове збереження стану чекбокса Email-сповіщень (AJAX)
    @PostMapping("/api/profile/email-notifications")
    @ResponseBody
    public ResponseEntity<Void> toggleEmailNotifications(@RequestParam boolean enabled, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        userRepository.findByEmail(principal.getName()).ifPresent(user -> {
            user.setEmailNotificationsEnabled(enabled);
            userRepository.save(user);
        });

        return ResponseEntity.ok().build();
    }

    @GetMapping("/profile/notifications-feed")
    public String getNotificationsFeed(Principal principal,
                                       @RequestParam(defaultValue = "0") int page,
                                       Model model) {

        // Отримуємо користувача універсальним способом (як для Form Login, так і для OAuth2)
        User user = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        // Витягуємо сторінку з історією
        Page<NotificationDto.Item> notificationsPage = notificationService.getAllUserNotificationsPage(user, page, 10);

        model.addAttribute("notificationsPage", notificationsPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", notificationsPage.getTotalPages());

        // Повертаємо виключно HTML-фрагмент
        return "fragments/notifications-tab :: notificationsFeed";
    }
}
