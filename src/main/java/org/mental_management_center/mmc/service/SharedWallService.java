package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mental_management_center.mmc.dto.SharedWallCommentDto;
import org.mental_management_center.mmc.model.Notification;
import org.mental_management_center.mmc.model.SharedWallComment;
import org.mental_management_center.mmc.model.SharedWallEntry;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.SharedWallCommentRepository;
import org.mental_management_center.mmc.repository.SharedWallRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SharedWallService {

    private final SharedWallRepository sharedWallRepository;
    private final JournalCryptoService cryptoService;
    private final UserRepository userRepository;
    private final SharedWallCommentRepository commentRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    // 1. Метод збереження нового повідомлення
    public void saveMessage(UUID roomId, UUID authorId, byte[] encryptedContent, String mediaFileName, byte[] encryptedMediaHead) {

        if (encryptedContent == null && mediaFileName == null) {
            throw new IllegalArgumentException("Критична помилка: Спроба зберегти порожнє повідомлення в БД");
        }

        SharedWallEntry entry = SharedWallEntry.builder()
                .roomId(roomId)
                .authorId(authorId)
                .encryptedContent(encryptedContent)
                .mediaFileName(mediaFileName)
                .mediaFileHead(encryptedMediaHead)
                .isRead(false)
                .build();

        sharedWallRepository.save(entry);
    }

    // 2. Метод отримання стрічки (з пагінацією, розшифровкою та лічильником)
    @Transactional(readOnly = true)
    public Page<SharedWallEntry> getWallMessages(UUID roomId, Pageable pageable) {
        Page<SharedWallEntry> page = sharedWallRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);

        page.getContent().forEach(entry -> {
            String authorName = userRepository.findById(entry.getAuthorId())
                    .map(User::getName)
                    .orElse("Співрозмовник");
            entry.setAuthorName(authorName);

            // 🟢 ТУТ МИ РАХУЄМО КОМЕНТАРІ ДЛЯ ФРОНТЕНДУ
            long commentsCount = commentRepository.countByWallEntryId(entry.getId());
            entry.setCommentsCount(commentsCount);

            try {
                if (entry.getEncryptedContent() != null && entry.getEncryptedContent().length > 0) {
                    String decrypted = cryptoService.decryptAndDecompress(entry.getEncryptedContent());
                    entry.setContent(decrypted);
                } else {
                    entry.setContent("");
                }
            } catch (Exception e) {
                entry.setContent("[Помилка розшифрування або пошкоджений запис]");
            }
        });

        return page;
    }

    // ==========================================================================
    // МОДУЛЬ КОМЕНТАРІВ СТІНИ
    // ==========================================================================

    /**
     * 1. Збереження нового коментаря (або відповіді, якщо є parentId)
     */
    @Transactional
    public void addCommentToPost(UUID postId, UUID parentId, User author, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Текст коментаря не може бути порожнім");
        }

        SharedWallEntry wallEntry = sharedWallRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Пост на стіні не знайдено"));

        byte[] encryptedContent = cryptoService.encryptAndCompress(content.trim());

        SharedWallComment comment = new SharedWallComment();
        comment.setWallEntry(wallEntry);
        comment.setParentId(parentId); // 🟢 Додано для дерева відповідей
        comment.setAuthorId(author.getId());
        comment.setEncryptedContent(encryptedContent);
        comment.setCreatedAt(LocalDateTime.now());

        commentRepository.save(comment);

        // Автоматичне сповіщення (лишаємо твою логіку без змін)
        try {
            UUID roomId = wallEntry.getRoomId();
            User roomClient = userService.findById(roomId);
            User recipient = null;

            if (author.getId().equals(roomId)) {
                recipient = roomClient.getTherapist();
            } else if (author.isTherapist()) {
                recipient = roomClient;
            }

            if (recipient != null && !recipient.getId().equals(author.getId())) {
                notificationService.createNotification(
                        recipient,
                        "Новий коментар на Спільній стіні",
                        author.getName() + " залишив повідомлення",
                        "/therapy/room/" + roomId,
                        Notification.NotificationType.STANDARD
                );
            }
        } catch (Exception e) {
            log.error("Помилка надсилання сповіщення про коментар: {}", e.getMessage());
        }
    }

    /**
     * 2. Отримання сторінки коментарів
     */
    @Transactional(readOnly = true)
    public List<SharedWallCommentDto> getCommentsForPost(UUID postId, User currentUser) {
        // Дістаємо всі коментарі хронологічно
        List<SharedWallComment> allComments = commentRepository.findByWallEntryIdOrderByCreatedAtAsc(postId);

        java.util.Map<UUID, SharedWallCommentDto> dtoMap = new java.util.LinkedHashMap<>();
        List<SharedWallCommentDto> rootComments = new java.util.ArrayList<>();

        // 1. Дешифруємо і створюємо плоский список DTO
        for (SharedWallComment comment : allComments) {
            String decryptedText = "[Помилка розшифрування]";
            try {
                decryptedText = cryptoService.decryptAndDecompress(comment.getEncryptedContent());
            } catch (Exception e) { log.error("Помилка дешифрування коментаря: {}", e.getMessage()); }

            User author = userService.findById(comment.getAuthorId());
            String authorName = (author != null) ? author.getName() : "Користувач";

            SharedWallCommentDto dto = new SharedWallCommentDto(
                    comment.getId(), postId, comment.getParentId(),
                    comment.getAuthorId(), authorName, decryptedText,
                    comment.getCreatedAt(), comment.getAuthorId().equals(currentUser.getId()),
                    new java.util.ArrayList<>()
            );
            dtoMap.put(dto.getId(), dto);
        }

        // 2. Збираємо дерево (вкладеність)
        for (SharedWallCommentDto dto : dtoMap.values()) {
            if (dto.getParentId() == null) {
                rootComments.add(dto); // Це головний коментар
            } else {
                SharedWallCommentDto parent = dtoMap.get(dto.getParentId());
                if (parent != null) {
                    parent.getReplies().add(dto); // Це відповідь, кладемо її всередину батька
                } else {
                    rootComments.add(dto); // Підстраховка, якщо батька видалили
                }
            }
        }
        return rootComments;
    }
    /**
     * 3. 🟢 Метод видалення коментаря (для кнопки з кошиком)
     */
    @Transactional
    public void deleteComment(UUID commentId, User currentUser) {
        SharedWallComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Коментар не знайдено"));

        if (!comment.getAuthorId().equals(currentUser.getId())) {
            throw new SecurityException("Ви не можете видалити чужий коментар");
        }

        commentRepository.delete(comment);
    }

    /**
     * 4. Редагування коментаря з перевіркою прав та шифруванням
     */
    @Transactional
    public void editComment(UUID commentId, User currentUser, String newContent) {
        if (newContent == null || newContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Текст коментаря не може бути порожнім");
        }

        SharedWallComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Коментар не знайдено"));

        if (!comment.getAuthorId().equals(currentUser.getId())) {
            throw new SecurityException("Ви не можете редагувати чужий коментар");
        }

        byte[] encryptedContent = cryptoService.encryptAndCompress(newContent.trim());
        comment.setEncryptedContent(encryptedContent);
        commentRepository.save(comment);
    }
}