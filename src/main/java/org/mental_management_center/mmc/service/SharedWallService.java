package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.SharedWallEntry;
import org.mental_management_center.mmc.repository.SharedWallRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SharedWallService {

    private final SharedWallRepository sharedWallRepository;
    private final JournalCryptoService cryptoService; // Інжектимо твій існуючий сервіс!

    // 1. Метод збереження нового повідомлення
    public void saveMessage(UUID roomId, UUID authorId, byte[] encryptedContent, String mediaFileName, byte[] encryptedMediaHead) {

        // ДУБЛЮЮЧА ЖОРСТКА ПЕРЕВІРКА БЕЗПЕКИ
        // Якщо немає ні зашифрованого тексту, ні імені файлу — це аномалія, запис заборонено
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

    // 2. Метод отримання стрічки (з пагінацією та розшифровкою)
    @Transactional(readOnly = true)
    public Page<SharedWallEntry> getWallMessages(UUID roomId, Pageable pageable) {
        Page<SharedWallEntry> page = sharedWallRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);

        // Проходимо по кожному повідомленню і розшифровуємо його для фронтенду
        page.getContent().forEach(entry -> {
            try {
                // Перевіряємо, чи є що розшифровувати
                if (entry.getEncryptedContent() != null && entry.getEncryptedContent().length > 0) {
                    String decrypted = cryptoService.decryptAndDecompress(entry.getEncryptedContent());
                    entry.setContent(decrypted);
                } else {
                    entry.setContent(""); // Заглушка для порожніх/медіа записів
                }
            } catch (Exception e) {
                // Якщо якийсь старий тестовий запис "побитий", він не покладе всю стіну
                entry.setContent("[Помилка розшифрування або пошкоджений запис]");
            }
        });

        return page;
    }
}
