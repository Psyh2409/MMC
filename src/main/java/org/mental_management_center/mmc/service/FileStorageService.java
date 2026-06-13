package org.mental_management_center.mmc.service;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

@Slf4j
@Service
public class FileStorageService {

    // Оголошуємо два чітких фінальних поля для розділення сховищ
    private final Path publicStorageLocation;
    private final Path privateStorageLocation;

    public FileStorageService(
            @Value("${app.upload-dir:./uploads/articles}") String publicDir,
            @Value("${app.journal-upload-dir:./uploads/journal_private}") String privateDir) {

        this.publicStorageLocation = Paths.get(publicDir).toAbsolutePath().normalize();
        this.privateStorageLocation = Paths.get(privateDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.publicStorageLocation);
            Files.createDirectories(this.privateStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Не вдалося створити директорії для завантаження.", ex);
        }
    }

    /**
     * ПУБЛІЧНЕ СХОВИЩЕ (Статті, Аватари) - Версія 2.0 з SHA-256 дедуплікацією
     */
    public String storeFile(MultipartFile file) {
        return saveFileWithHash(file, this.publicStorageLocation, "Публічний (Статті)");
    }

    /**
     * ПРИВАТНЕ СХОВИЩЕ (🔒 Щоденник рефлексії) - Версія 2.0 з SHA-256 дедуплікацією
     */
    public String storePrivateFile(MultipartFile file) {
        return saveFileWithHash(file, this.privateStorageLocation, "Приватний (Щоденник)");
    }

    /**
     * Перенесений приватний утилітний метод, щоб не дублювати логіку хешування в коді
     */
    private String saveFileWithHash(MultipartFile file, Path targetFolder, String storageType) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Не вдалося зберегти порожній файл.");
        }

        String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        if (originalFileName.contains("..")) {
            throw new IllegalArgumentException("Помилка! Ім'я файлу містить недозволений шлях: " + originalFileName);
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
            throw new IllegalArgumentException("Недозволений тип файлу! Дозволено лише зображення та відео.");
        }

        try {
            // 1. Обчислюємо SHA-256 хеш
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String fileHash = hexString.toString();

            // 2. Виділяємо розширення
            String fileExtension = "";
            int extensionIndex = originalFileName.lastIndexOf(".");
            if (extensionIndex >= 0) {
                fileExtension = originalFileName.substring(extensionIndex);
            }

            String uniqueHashFileName = fileHash + fileExtension;
            Path targetLocation = targetFolder.resolve(uniqueHashFileName);

            // 3. Перевірка на дублікат на диску
            if (Files.exists(targetLocation)) {
                log.info("ℹ️ Дедуплікація [{}]: Файл з хешем {} вже існує на диску. Пропускаємо копіювання.", storageType, fileHash);
                return uniqueHashFileName;
            }

            // 4. Копіюємо контент
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("✅ [{}] Збережено новий унікальний файл: {}", storageType, uniqueHashFileName);
            return uniqueHashFileName;

        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Помилка ініціалізації SHA-256", ex);
        } catch (IOException ex) {
            throw new RuntimeException("Не вдалося зберегти файл " + originalFileName, ex);
        }
    }

    // Для щоденника (завжди бере з приватної)
    public Path loadFromPrivate(String fileName) {
        return this.privateStorageLocation.resolve(fileName).normalize();
    }

    // Для статей/аватарів (завжди бере з публічної)
    public Path loadFromPublic(String fileName) {
        return this.publicStorageLocation.resolve(fileName).normalize();
    }

    public Path findExistingFile(String fileName) {
        Path privatePath = privateStorageLocation.resolve(fileName);
        if (Files.exists(privatePath)) {
            return privatePath;
        }

        Path publicPath = publicStorageLocation.resolve(fileName);
        if (Files.exists(publicPath)) {
            return publicPath;
        }

        return null; // Файл не знайдено ніде
    }

    public Path findFileAnywhere(String fileName) {
        // 1. Спробуй знайти в приватній папці
        Path privatePath = privateStorageLocation.resolve(fileName).normalize();
        if (Files.exists(privatePath)) {
            return privatePath;
        }

        // 2. Якщо там немає, шукай у публічній
        Path publicPath = publicStorageLocation.resolve(fileName).normalize();
        if (Files.exists(publicPath)) {
            return publicPath;
        }

        return null; // Файлу немає ніде
    }

    // Видаляємо тільки з приватного сховища (для щоденника)
    public void deletePrivateFile(String fileName) {
        try {
            Files.deleteIfExists(privateStorageLocation.resolve(fileName));
            log.info("🗑️ Приватний файл видалено: {}", fileName);
        } catch (IOException e) {
            log.error("Помилка видалення приватного файлу: {}", fileName, e);
        }
    }

    // Видаляємо тільки з публічного (для статей)
    public void deletePublicFile(String fileName) {
        try {
            Files.deleteIfExists(publicStorageLocation.resolve(fileName));
            log.info("🗑️ Публічний файл видалено: {}", fileName);
        } catch (IOException e) {
            log.error("Помилка видалення публічного файлу: {}", fileName, e);
        }
    }

    // Допоміжний клас для повернення результату (голова + ім'я хвоста)
    public static class FileSurgeryResult {
        public final byte[] head;
        public final String tailFileName;

        public FileSurgeryResult(byte[] head, String tailFileName) {
            this.head = head;
            this.tailFileName = tailFileName;
        }
    }

    public FileSurgeryResult storePrivateTail(MultipartFile file) throws IOException {
        byte[] head = new byte[4096];
        InputStream inputStream = file.getInputStream();

        // 1. Відкушуємо голову
        int bytesRead = inputStream.read(head);
        if (bytesRead < 4096 && bytesRead != -1) {
            head = java.util.Arrays.copyOf(head, bytesRead);
        } else if (bytesRead == -1) {
            head = new byte[0];
        }

        // 2. Тимчасовий файл для обчислення хешу
        Path tempFile = Files.createTempFile(privateStorageLocation, "temp_tail_", ".tmp");
        String tailHash;

        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (java.io.OutputStream os = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
            }
            tailHash = java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            Files.deleteIfExists(tempFile);
            throw new RuntimeException("SHA-256 не підтримується", e);
        } finally {
            inputStream.close();
        }

        // --- ОСЬ ТУТ ЗМІНА: Зберігаємо рідне розширення контенту ---
        String originalFilename = org.springframework.util.StringUtils.cleanPath(java.util.Objects.requireNonNull(file.getOriginalFilename()));
        String ext = "";
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx >= 0) {
            ext = originalFilename.substring(dotIdx).toLowerCase(); // отримаємо наприклад ".mp4" або ".jpg"
        }

        String uniqueFileName = tailHash + ext;
        Path targetLocation = privateStorageLocation.resolve(uniqueFileName);

        // 3. Дедуплікація
        if (Files.exists(targetLocation)) {
            log.info("ℹ️ Дедуплікація: Хвіст {} вже існує.", uniqueFileName);
            Files.deleteIfExists(tempFile);
        } else {
            log.info("✅ Збережено новий унікальний хвіст: {}", uniqueFileName);
            Files.move(tempFile, targetLocation, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        return new FileSurgeryResult(head, uniqueFileName);
    }
}