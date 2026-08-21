package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mental_management_center.mmc.dto.SharedWallCommentDto;
import org.mental_management_center.mmc.model.Notification;
import org.mental_management_center.mmc.model.SharedWallEntry;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.SharedWallCommentRepository;
import org.mental_management_center.mmc.repository.SharedWallRepository;
import org.mental_management_center.mmc.repository.TherapyAssignmentRepository;
import org.mental_management_center.mmc.service.*;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/api/room/{roomId}/wall")
@RequiredArgsConstructor
public class SharedWallController {

    private final SharedWallService sharedWallService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final JournalCryptoService cryptoService;
    private final SharedWallRepository sharedWallRepository;
    private final NotificationService notificationService;
    private final SharedWallCommentRepository commentRepository;
    private final TherapyAssignmentRepository therapyAssignmentRepository;

    // Універсальний метод перевірки доступу для Стіни
    private boolean hasAccessToWall(User user, UUID assignmentId) {
        if (user.isAdmin()) return true;
        return therapyAssignmentRepository.findById(assignmentId)
                .map(a -> "ACTIVE".equals(a.getStatus()) &&
                        (a.getClient().getId().equals(user.getId()) || a.getTherapist().getId().equals(user.getId())))
                .orElse(false);
    }

    // 1. Збереження повідомлення (з повним збереженням AES-шифрування та додаванням сповіщень)
    @PostMapping("/add")
    @ResponseBody
    public ResponseEntity<Void> addWallEntry(@PathVariable UUID roomId,
                                             @RequestParam(required = false) String content,
                                             @RequestParam(required = false) MultipartFile media,
                                             Principal principal) {

        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();

        // roomId тут фактично є assignmentId (ID договору)
        if (!hasAccessToWall(currentUser, roomId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            // Для методу /fragment та /media використовуйте: throw new SecurityException("Доступ заборонено");
        }

        boolean hasText = content != null && !content.trim().isEmpty();
        boolean hasMedia = media != null && !media.isEmpty();

        if (!hasText && !hasMedia) {
            return ResponseEntity.badRequest().build();
        }

        String mediaFileName = null;
        byte[] encryptedHead = null;
        byte[] encryptedText = null;

        try {
            if (hasMedia) {
                var surgeryResult = fileStorageService.storePrivateTail(media);
                mediaFileName = surgeryResult.tailFileName;
                encryptedHead = cryptoService.encryptBytes(surgeryResult.head);
            }

            if (hasText) {
                encryptedText = cryptoService.encryptAndCompress(content);
            } else {
                encryptedText = cryptoService.encryptAndCompress("[MEDIA_ONLY]");
            }

            // 1. Збереження в базі даних (оригінальний алгоритм без змін)
            sharedWallService.saveMessage(roomId, currentUser.getId(), encryptedText, mediaFileName, encryptedHead);

            // 2. БЛОК СПОВІЩЕНЬ ДЛЯ ПАРТНЕРА ПО ТЕРАПІЇ
            try {
                User roomClient = userService.findById(roomId);
                User recipient = null;

                if (currentUser.getId().equals(roomId)) {
                    // Клієнт додає запис -> сповіщаємо його терапевта
                    recipient = roomClient.getTherapist();
                } else if (currentUser.isTherapist()) {
                    // Терапевт додає запис -> сповіщаємо клієнта кабінету
                    recipient = roomClient;
                }

                if (recipient != null && !recipient.getId().equals(currentUser.getId())) {
                    String messagePreview = hasText
                            ? currentUser.getName() + ": " + content.trim()
                            : currentUser.getName() + " додав(ла) медіафайл на стіну";

                    notificationService.createNotification(
                            recipient,
                            "Нове повідомлення на Спільній стіні",
                            messagePreview,
                            "/therapy/room/" + roomId,
                            Notification.NotificationType.STANDARD
                    );
                }
            } catch (Exception notifEx) {
                log.error("Помилка надсилання сповіщення для стіни: {}", notifEx.getMessage());
                // Вкладений try-catch гарантує, що якщо сповіщення не створиться,
                // основне збереження поста не скасується
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Помилка збереження запису на стіні: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 2. Отримання фрагменту СТІНИ (Вказуємо на новий незалежний шаблон)
    @GetMapping("/fragment")
    public String getWallFragment(@PathVariable UUID roomId,
                                  @RequestParam(defaultValue = "0") int page,
                                  Model model, Principal principal) {

        User currentUser = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        // roomId тут фактично є assignmentId (ID договору)
        if (!hasAccessToWall(currentUser, roomId)) {
            throw new SecurityException("Доступ заборонено");
            // Для методу /fragment та /media використовуйте: throw new SecurityException("Доступ заборонено");
        }

        // Жорстко фіксуємо 5 записів на сторінку
        var messagesPage = sharedWallService.getWallMessages(roomId, PageRequest.of(page, 5));

        model.addAttribute("posts", messagesPage.getContent());
        model.addAttribute("page", messagesPage);
        model.addAttribute("roomId", roomId);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", messagesPage.getTotalPages());
        model.addAttribute("pageSize", 5);
        model.addAttribute("hasMore", messagesPage.hasNext());

        // ПОВЕРТАЄМО НОВИЙ ШАБЛОН:
        return "fragments/shared-wall-form :: wallFeed";
    }

    // 3. ВИДАЛЕННЯ ПОСТУ ЗІ СТІНИ (НОВИЙ МЕТОД)
    @DeleteMapping("/{postId}")
    @ResponseBody
    public ResponseEntity<Void> deleteWallEntry(@PathVariable UUID roomId,
                                                @PathVariable UUID postId,
                                                Principal principal) {
        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();

        SharedWallEntry entry = sharedWallRepository.findById(postId).orElse(null);
        if (entry == null) return ResponseEntity.notFound().build();

        // Тільки автор посту може його видалити
        if (!entry.getAuthorId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        sharedWallRepository.delete(entry);
        return ResponseEntity.ok().build();
    }

    // 4. Отримання форми редагування поста
    @GetMapping("/fragment/edit-form/{postId}")
    public String getEditFormFragment(@PathVariable UUID roomId,
                                      @PathVariable UUID postId,
                                      Model model, Principal principal) {
        User currentUser = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        SharedWallEntry entry = sharedWallRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Пост не знайдено"));

        // Тільки автор посту може його редагувати
        if (!entry.getAuthorId().equals(currentUser.getId())) {
            throw new SecurityException("Доступ заборонено");
        }

        String decryptedContent = cryptoService.decryptAndDecompress(entry.getEncryptedContent());

        model.addAttribute("isEdit", true);
        model.addAttribute("postId", postId);
        model.addAttribute("content", decryptedContent);
        model.addAttribute("post", entry);
        model.addAttribute("roomId", roomId);

        return "fragments/shared-wall-form :: wallForm";
    }

    // 4. Оновлення поста на стіні
    @PostMapping("/{postId}/update")
    @Transactional
    @ResponseBody
    public ResponseEntity<Void> updateWallEntry(@PathVariable UUID roomId,
                                                @PathVariable UUID postId,
                                                @RequestParam("content") String newContent,
                                                @RequestParam(value = "media", required = false) MultipartFile file,
                                                Principal principal) {
        log.info("Оновлення поста {}: roomId={}, postId={}", postId, roomId, postId);

        User currentUser = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        SharedWallEntry entry = sharedWallRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Пост не знайдено"));

        log.info("Автор поста: {}, Поточний користувач: {}", entry.getAuthorId(), currentUser.getId());

        // Тільки автор посту може його оновити
        if (!entry.getAuthorId().equals(currentUser.getId())) {
            log.warn("Доступ заборонено: автор поста {} не дорівнює поточному користувачеві {}", entry.getAuthorId(), currentUser.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String trimmedContent = newContent.trim();
        boolean hasText = !trimmedContent.isEmpty();
        boolean hasMedia = file != null && !file.isEmpty();

        if (!hasText && !hasMedia) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // Оновлення тексту
            if (hasText) {
                byte[] encryptedAndCompressedData = cryptoService.encryptAndCompress(trimmedContent);
                entry.setEncryptedContent(encryptedAndCompressedData);
            } else {
                entry.setEncryptedContent(cryptoService.encryptAndCompress("[MEDIA_ONLY]"));
            }

            // Оновлення медіа
            if (hasMedia) {
                String oldFileName = entry.getMediaFileName();
                log.info("Старе ім'я файлу: {}", oldFileName);

                // Обчислити хеш файлу для дедуплікації
                String fileHash = calculateFileHash(file);
                log.info("Хеш файлу: {}", fileHash);

                // Перевірити, чи існує файл з таким хешем
                java.util.Optional<SharedWallEntry> existingEntry = sharedWallRepository.findFirstByMediaFileHashOrderByCreatedAtDesc(fileHash);

                if (existingEntry.isPresent()) {
                    // Використати існуючий файл
                    String existingFileName = existingEntry.get().getMediaFileName();
                    byte[] existingHead = existingEntry.get().getMediaFileHead();

                    entry.setMediaFileHead(existingHead);
                    entry.setMediaFileName(existingFileName);
                    entry.setMediaFileHash(fileHash);

                    log.info("Використано існуючий файл: {}", existingFileName);
                } else {
                    // Зберегти новий файл
                    FileStorageService.FileSurgeryResult surgery = fileStorageService.storePrivateTail(file);
                    byte[] encryptedHead = cryptoService.encryptBytes(surgery.head);

                    entry.setMediaFileHead(encryptedHead);
                    entry.setMediaFileName(surgery.tailFileName);
                    entry.setMediaFileHash(fileHash);

                    log.info("Збережено новий файл: {}", surgery.tailFileName);
                }
            }

            sharedWallRepository.save(entry);
            log.info("Пост {} успішно оновлено", postId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Помилка оновлення поста {}: {}", postId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 4. Отримання медіа (без змін, працює ідеально)
    @GetMapping("/media/{filename:.+}")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> getWallMedia(@PathVariable("roomId") UUID roomId,
                                                 @PathVariable("filename") String filename,
                                                 Principal principal) {
        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
        // roomId тут фактично є assignmentId (ID договору)
        if (!hasAccessToWall(currentUser, roomId)) {
            throw new SecurityException("Доступ заборонено");
        }

        try {
            log.info("Запит медіа файлу: {}", filename);
            SharedWallEntry entry = sharedWallRepository.findFirstByMediaFileNameOrderByCreatedAtDesc(filename)
                    .orElseThrow(() -> {
                        log.warn("Файл {} не знайдено в базі даних", filename);
                        return new RuntimeException("Файл не знайдено");
                    });

            if (entry.getMediaFileHead() == null) {
                log.warn("MediaFileHead є null для файлу {}", filename);
                return ResponseEntity.notFound().build();
            }

            byte[] decryptedHead = cryptoService.decryptBytes(entry.getMediaFileHead());
            Path filePath = fileStorageService.loadFromPrivate(filename);

            if (!Files.exists(filePath)) {
                log.warn("Файл {} не існує на диску за шляхом: {}", filename, filePath);
                return ResponseEntity.notFound().build();
            }

            log.info("Файл {} успішно знайдено на диску", filename);

            long totalLength = decryptedHead.length + Files.size(filePath);

            Resource dynamicResource = new org.springframework.core.io.AbstractResource() {
                @Override
                public String getDescription() { return "Stream: " + filename; }
                @Override
                public InputStream getInputStream() throws IOException {
                    return new java.io.SequenceInputStream(
                            new java.io.ByteArrayInputStream(decryptedHead),
                            Files.newInputStream(filePath)
                    );
                }
                @Override
                public long contentLength() { return totalLength; }
                @Override
                public boolean exists() { return true; }
            };

            String contentType = MediaTypeFactory.getMediaType(filename)
                    .map(MediaType::toString)
                    .orElse("application/octet-stream");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(dynamicResource);

        } catch (Exception e) {
            log.error("❌ ПОМИЛКА СТРИМІНГУ: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Допоміжний метод для обчислення хешу файлу
    private String calculateFileHash(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = digest.digest();
        return HexFormat.of().formatHex(hashBytes);
    }

    // Отримання фрагмента коментарів (AJAX пагінація)
    @GetMapping("/post/{postId}/comments")
    public String getPostComments(@PathVariable UUID roomId,
                                  @PathVariable UUID postId,
                                  Model model,
                                  Principal principal) {
        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();

        List<SharedWallCommentDto> commentsTree = sharedWallService.getCommentsForPost(postId, currentUser);

        model.addAttribute("comments", commentsTree);
        model.addAttribute("postId", postId);
        model.addAttribute("roomId", roomId);
        return "fragments/shared-wall-form :: commentsFeed";
    }

    @PostMapping("/post/{postId}/comments/add")
    public String addComment(@PathVariable UUID roomId,
                             @PathVariable UUID postId,
                             @RequestParam(required = false) UUID parentId,
                             @RequestParam String content,
                             Model model,
                             Principal principal) {
        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();

        // Зберігаємо
        sharedWallService.addCommentToPost(postId, parentId, currentUser, content);

        // Дістаємо оновлене дерево
        List<SharedWallCommentDto> commentsTree = sharedWallService.getCommentsForPost(postId, currentUser);
        long newCount = commentRepository.countByWallEntryId(postId);

        model.addAttribute("comments", commentsTree);
        model.addAttribute("postId", postId);
        model.addAttribute("roomId", roomId);
        model.addAttribute("newCount", newCount); // 🟢 Передаємо новий лічильник для JS

        return "fragments/shared-wall-form :: commentsFeed";
    }

    @DeleteMapping("/post/{postId}/comments/{commentId}")
    @ResponseBody
    public ResponseEntity<Void> deleteComment(@PathVariable UUID roomId,
                                              @PathVariable UUID postId,
                                              @PathVariable UUID commentId,
                                              Principal principal) {

        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();

        try {
            sharedWallService.deleteComment(commentId, currentUser);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Помилка видалення коментаря: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}