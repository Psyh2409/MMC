package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mental_management_center.mmc.model.SharedWallEntry;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.SharedWallRepository;
import org.mental_management_center.mmc.service.FileStorageService;
import org.mental_management_center.mmc.service.JournalCryptoService;
import org.mental_management_center.mmc.service.SharedWallService;
import org.mental_management_center.mmc.service.UserService;
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
import java.security.Principal;
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

    // 1. Збереження повідомлення
    @PostMapping("/add")
    @ResponseBody
    public ResponseEntity<Void> addWallEntry(@PathVariable UUID roomId,
                                             @RequestParam(required = false) String content,
                                             @RequestParam(required = false) MultipartFile media,
                                             Principal principal) {

        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();

        if (!currentUser.getId().equals(roomId)) {
            boolean isClientVisible = userService.getVisibleUsers(currentUser).stream()
                    .anyMatch(u -> u.getId().equals(roomId));
            if (!isClientVisible) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
                encryptedText = cryptoService.encryptAndCompress(hasText ? content.trim() : "[MEDIA_ONLY]");
            }

            sharedWallService.saveMessage(roomId, currentUser.getId(), encryptedText, mediaFileName, encryptedHead);
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

        if (!currentUser.getId().equals(roomId)) {
            boolean isClientVisible = userService.getVisibleUsers(currentUser).stream()
                    .anyMatch(u -> u.getId().equals(roomId));

            if (!isClientVisible) {
                throw new SecurityException("Доступ заборонено: спроба перетину середовищ!");
            }
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

    // 4. Отримання медіа (без змін, працює ідеально)
    @GetMapping("/media/{filename:.+}")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> getWallMedia(@PathVariable("roomId") UUID roomId,
                                                 @PathVariable("filename") String filename,
                                                 Principal principal) {
        // ... (Твій існуючий код методу getWallMedia залишається 100% таким самим, як був)
        // Я його не дублюю, щоб зекономити місце, просто залиш його тут.
        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
        if (!currentUser.getId().equals(roomId)) {
            boolean isClientVisible = userService.getVisibleUsers(currentUser).stream()
                    .anyMatch(u -> u.getId().equals(roomId));
            if (!isClientVisible) throw new SecurityException("Доступ заборонено!");
        }

        try {
            SharedWallEntry entry = sharedWallRepository.findFirstByMediaFileNameOrderByCreatedAtDesc(filename)
                    .orElseThrow(() -> new RuntimeException("Файл не знайдено"));

            if (entry.getMediaFileHead() == null) return ResponseEntity.notFound().build();

            byte[] decryptedHead = cryptoService.decryptBytes(entry.getMediaFileHead());
            Path filePath = fileStorageService.loadFromPrivate(filename);

            if (!Files.exists(filePath)) return ResponseEntity.notFound().build();

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
}