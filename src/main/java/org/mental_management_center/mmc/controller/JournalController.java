package org.mental_management_center.mmc.controller;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    @Transactional
    @GetMapping("/feed")
    public ResponseEntity<List<JournalPost>> getFeed(Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        // Витягуємо сирі зашифровані пости з бази
        List<JournalPost> rawPosts = journalPostRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        // Проганяємо кожен пост через дешифратор і заповнюємо @Transient поле 'content'
        // Проганяємо кожен пост через дешифратор
        List<JournalPost> decryptedPosts = rawPosts.stream().map(post -> {
            String decryptedText = cryptoService.decryptAndDecompress(post.getEncryptedContent());

            // Якщо це наш системний маркер порожнечі — віддаємо фронтенду чисту пустоту
            if ("[MEDIA_ONLY]".equals(decryptedText)) {
                post.setContent("");
            } else {
                post.setContent(decryptedText);
            }

            return post;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(decryptedPosts);
    }

    @GetMapping(value = "/media/{fileName:.+}")
    public ResponseEntity<Resource> getPrivateMedia(@PathVariable String fileName, Principal principal) {
        // 1. Перевірка доступу (залишаємо як є)
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        boolean isOwner = journalPostRepository.existsByUserIdAndMediaFileName(user.getId(), fileName);

        if (!isOwner) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        // 2. Читання файлу
        try {
            Path path = fileStorageService.loadFromPrivate(fileName);
            if (path == null) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(path.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            // 3. БЕЗ ResourceRegion. Просто віддаємо файл цілком.
            // Це гарантовано працює в будь-якому браузері.
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("Помилка при читанні відео: {}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
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
}