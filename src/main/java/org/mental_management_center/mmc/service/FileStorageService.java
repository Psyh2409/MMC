package org.mental_management_center.mmc.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path publicStorageLocation;
    private final Path privateStorageLocation; // НОВА ПАПКА ДЛЯ ЩОДЕННИКА

    // Додаємо новий шлях (через app.journal-upload-dir або дефолтний)
    public FileStorageService(
            @Value("${app.upload-dir:./uploads/articles}") String publicDir,
            @Value("${app.journal-upload-dir:./uploads/journal_private}") String privateDir) {

        this.publicStorageLocation = Paths.get(publicDir).toAbsolutePath().normalize();
        this.privateStorageLocation = Paths.get(privateDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.publicStorageLocation);
            Files.createDirectories(this.privateStorageLocation); // Створюємо захищену папку
        } catch (Exception ex) {
            throw new RuntimeException("Не вдалося створити директорії для завантаження.", ex);
        }
    }

    // Твій існуючий метод storeFile() залишається БЕЗ ЗМІН (він використовує publicStorageLocation)
    // ...

    // 1. НОВИЙ МЕТОД: Збереження ТІЛЬКИ в приватну папку
    public String storePrivateFile(MultipartFile file) {
        String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        try {
            if (originalFileName.contains("..") || file.isEmpty()) {
                throw new IllegalArgumentException("Некоректний файл.");
            }
            String fileExtension = "";
            int extensionIndex = originalFileName.lastIndexOf(".");
            if (extensionIndex >= 0) fileExtension = originalFileName.substring(extensionIndex);

            String uniqueFileName = UUID.randomUUID().toString() + fileExtension;

            // УВАГА: Зберігаємо в privateStorageLocation
            Path targetLocation = this.privateStorageLocation.resolve(uniqueFileName);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }
            return uniqueFileName;
        } catch (IOException ex) {
            throw new RuntimeException("Не вдалося зберегти приватний файл", ex);
        }
    }

    // 2. НОВИЙ МЕТОД: Читання з приватної папки
    public Path loadPrivateFileAsPath(String fileName) {
        return this.privateStorageLocation.resolve(fileName).normalize();
    }

    @PostConstruct
    public void init() {
        try {
            // Автоматично створюємо папку на диску сервера, якщо її ще немає
            Files.createDirectories(this.publicStorageLocation);
        } catch (IOException ex) {
            throw new RuntimeException("Не вдалося створити директорію для завантажених файлів.", ex);
        }
    }

    /**
     * Зберігає файл на диск і повертає його унікальне ім'я для запису в БД.
     */
    public String storeFile(MultipartFile file) {
        // Очищаємо ім'я файлу від можливих зайвих символів шляху
        String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));

        try {
            // Безпека: перевіряємо, чи ім'я файлу не містить хакерських спроб виходу з папки (напр., ../../)
            if (originalFileName.contains("..")) {
                throw new IllegalArgumentException("Помилка! Ім'я файлу містить недозволений шлях: " + originalFileName);
            }

            if (file.isEmpty()) {
                throw new IllegalArgumentException("Не вдалося зберегти порожній файл: " + originalFileName);
            }

            // Валідація типів: дозволяємо ТІЛЬКИ зображення або відео
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
                throw new IllegalArgumentException("Недозволений тип файлу! Дозволено лише зображення та відео.");
            }

            // Витягуємо оригінальне розширення файлу (наприклад, .png або .mp4)
            String fileExtension = "";
            int extensionIndex = originalFileName.lastIndexOf(".");
            if (extensionIndex >= 0) {
                fileExtension = originalFileName.substring(extensionIndex);
            }

            // Генеруємо абсолютно унікальне ім'я файлу через UUID
            String uniqueFileName = UUID.randomUUID().toString() + fileExtension;

            // Формуємо фінальний шлях на диску
            Path targetLocation = this.publicStorageLocation.resolve(uniqueFileName);

            // Безпечно копіюємо байти файлу в цільову папку
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            // Повертаємо суто унікальне ім'я файлу (наприклад: "a1b2c3d4-....png")
            // Саме це ім'я ми запишемо в колонку image_path таблиці статей
            return uniqueFileName;

        } catch (IOException ex) {
            throw new RuntimeException("Не вдалося зберегти файл " + originalFileName + ". Спробуйте ще раз!", ex);
        }
    }

    /**
     * Повертає повний Path до файлу на диску для подальшого стрімінгу користувачу
     */
    public Path loadFileAsPath(String fileName) {
        return this.publicStorageLocation.resolve(fileName).normalize();
    }
}