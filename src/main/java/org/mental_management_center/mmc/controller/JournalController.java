package org.mental_management_center.mmc.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mental_management_center.mmc.model.JournalPost;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.JournalPostRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.service.FileStorageService;
import org.mental_management_center.mmc.service.JournalCryptoService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.security.web.access.AuthorizationManagerWebInvocationPrivilegeEvaluator;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
public class JournalController {

    private final JournalPostRepository journalPostRepository;
    private final UserRepository userRepository;
    private final JournalCryptoService cryptoService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    // Припускаємо, що твій сервіс збереження медіа називається саме так
    // private final FileStorageService fileStorageService;

    // 1. СТВОРЕННЯ НОВОГО ПОСТА (Шифрування + збереження)
    @PostMapping("/create")
    public ResponseEntity<?> createPost(
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "media", required = false) MultipartFile mediaFile,
            Principal principal) {

        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // Перевіряємо наявність контенту
        boolean hasText = content != null && !content.trim().isEmpty();
        boolean hasMedia = mediaFile != null && !mediaFile.isEmpty();

        // Якщо немає ні тексту, ні файлу — тільки тоді це помилка
        if (!hasText && !hasMedia) {
            return ResponseEntity.badRequest().body("Запис щоденника не може бути порожнім.");
        }

        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

            // Якщо тексту немає, шифруємо системний маркер, щоб обійти NOT NULL у базі
            byte[] encryptedData = cryptoService.encryptAndCompress(hasText ? content.trim() : "[MEDIA_ONLY]");
            // Обробка медіафайлу через твій сервіс зберігання
            String savedFileName = null;
            if (hasMedia) {
                // Тепер файл реально копіюється на диск, а метод повертає його унікальний UUID
                savedFileName = fileStorageService.storePrivateFile(mediaFile);
            }

            // Будуємо об'єкт поста з унікальним UUID-іменем файлу
            JournalPost post = JournalPost.builder()
                    .userId(user.getId())
                    .encryptedContent(encryptedData)
                    .mediaFileName(savedFileName) // Сюди піде "a1b2c3d4-....jpg"
                    .createdAt(LocalDateTime.now())
                    .build();

            journalPostRepository.save(post);
            log.info("Створено новий зашифрований пост для користувача: {}", user.getId());

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Помилка створення поста щоденника", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Не вдалося зберегти запис: " + e.getMessage());
        }
    }

    // 2. ОТРИМАННЯ СТРІЧКИ ЩОДЕННИКА (Дешифрування при читанні)
    // 1. Повернення готового HTML-фрагменту всієї стрічки
    @Transactional(readOnly = true)
    @GetMapping("/feed")
    public ModelAndView getFeed(Principal principal) {
        User currentUser = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        List<JournalPost> posts = journalPostRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());

        // Дешифруємо контент кожного запису перед рендерингом фрагмента
        posts.forEach(post -> {
            String decrypted = cryptoService.decryptAndDecompress(post.getEncryptedContent());
            post.setContent(decrypted);
        });

        ModelAndView mav = new ModelAndView("fragments/journal-form :: journalFeed");
        mav.addObject("posts", posts);

        posts.forEach(p -> log.info("POST ID: {}, MEDIA: {}", p.getId(), p.getMediaFileName()));

        return mav;
    }


    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deletePost(@PathVariable UUID id, Principal principal) {
        // 1. Знаходимо пост
        JournalPost post = journalPostRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пост не знайдено"));

        // 2. Перевірка власника
        User currentUser = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        if (!post.getUserId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 3. Зберігаємо ім'я файлу (якщо є), щоб видалити його ПІСЛЯ видалення поста
        String fileName = post.getMediaFileName();

        // 4. Видаляємо пост з бази
        journalPostRepository.delete(post);

        // 5. Логіка видалення медіа
        if (fileName != null) {
            // Перевіряємо, чи залишились ще посилання на цей файл в інших постах
            long usageCount = journalPostRepository.countUsage(fileName);

            if (usageCount == 0) {
                // Якщо 0 — файл "сирота", видаляємо фізично
                // Тут ми маємо бути впевнені, з якої папки видаляти.
                // Якщо ми не знаємо, з якої саме, можна спробувати видалити з обох.
                fileStorageService.deletePrivateFile(fileName);
                log.info("🗑️ Файл {} видалено фізично, бо посилань більше немає.", fileName);
            }
        }

        return ResponseEntity.ok().build();
    }

    // 2. Повернення готового HTML-фрагменту форми для конкретного поста
    @Transactional(readOnly = true)
    @GetMapping("/fragment/edit-form/{id}")
    public ModelAndView getEditFormFragment(@PathVariable UUID id, Principal principal) {
        JournalPost post = journalPostRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пост не знайдено"));

        User currentUser = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        if (!post.getUserId().equals(currentUser.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ заборонено");
        }

        String decryptedContent = cryptoService.decryptAndDecompress(post.getEncryptedContent());

        ModelAndView mav = new ModelAndView("fragments/journal-form :: journalForm");
        mav.addObject("isEdit", true);
        mav.addObject("postId", id);
        mav.addObject("content", decryptedContent);
        // ВАЖЛИВО: додаємо об'єкт post, щоб шаблон бачив назву медіафайлу
        mav.addObject("post", post);
        return mav;
    }

    // 3. Збереження оновленого поста (MultipartForm для підтримки нових файлів)
    @PostMapping("/{id}/update")
    @Transactional
    public ResponseEntity<Void> updatePost(@PathVariable UUID id,
                                           @RequestParam("content") String newContent,
                                           @RequestParam(value = "media", required = false) MultipartFile file,
                                           Principal principal) {
        JournalPost post = journalPostRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пост не знайдено"));

        User currentUser = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        if (!post.getUserId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String trimmedContent = newContent.trim();
        if (trimmedContent.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        byte[] encryptedAndCompressedData = cryptoService.encryptAndCompress(trimmedContent);
        post.setEncryptedContent(encryptedAndCompressedData);

        if (file != null && !file.isEmpty()) {
            String oldFileName = post.getMediaFileName();
            try {
                String newFileName = fileStorageService.storePrivateFile(file);
                post.setMediaFileName(newFileName);

                if (oldFileName != null) {
                    long usageCount = journalPostRepository.countUsage(oldFileName);
                    if (usageCount <= 1) {
                        fileStorageService.deletePrivateFile(oldFileName);
                    }
                }
            } catch (Exception e) {
                log.error("Помилка оновлення медіафайлу для поста {}", id, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }

        journalPostRepository.save(post);
        log.info("📝 Пост {} успішно оновлено через стабільний SSR-інтерфейс", id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/media/{filename:.+}")
    public ResponseEntity<Resource> getMedia(@PathVariable String filename) {
        Path filePath = fileStorageService.findFileAnywhere(filename);

        if (filePath == null || !Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            // Визначаємо тип контенту через стандартний Java-метод
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

}