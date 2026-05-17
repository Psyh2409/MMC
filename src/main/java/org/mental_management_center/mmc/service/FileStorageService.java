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

    private final Path fileStorageLocation;

    // Зчитуємо шлях із нашого application.yaml. Якщо порожньо — беремо дефолт
    public FileStorageService(@Value("${app.upload-dir:./uploads/articles}") String uploadDir) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            // Автоматично створюємо папку на диску сервера, якщо її ще немає
            Files.createDirectories(this.fileStorageLocation);
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
            Path targetLocation = this.fileStorageLocation.resolve(uniqueFileName);

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
        return this.fileStorageLocation.resolve(fileName).normalize();
    }
}