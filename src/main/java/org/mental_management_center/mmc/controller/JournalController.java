package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mental_management_center.mmc.model.JournalPost;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.JournalPostRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.service.FileStorageService;
import org.mental_management_center.mmc.service.JournalCryptoService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    @GetMapping("/media/{fileName:.+}")
    public ResponseEntity<Resource> getPrivateMedia(@PathVariable("fileName") String fileName, Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        boolean isOwner = journalPostRepository.existsByUserIdAndMediaFileName(user.getId(), fileName);

        if (!isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Path filePath = fileStorageService.loadPrivateFileAsPath(fileName);
            // ДІАГНОСТИКА: Виводимо абсолютний шлях у консоль
            log.info("Щоденник: спроба прочитати файл за шляхом -> {}", filePath.toAbsolutePath());

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                // ДІАГНОСТИКА: Файлу там фізично немає
                log.warn("Щоденник: файл ФІЗИЧНО НЕ ЗНАЙДЕНО на диску -> {}", filePath.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Помилка читання файлу щоденника", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}