package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MediaEndpointController {

    private final FileStorageService fileStorageService;

    @GetMapping("/api/media/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws IOException {
        // ... (твій код)
        try {
            // Отримуємо безпечний шлях до файлу на диску через наш сервіс
            Path file = fileStorageService.loadFromPublic(filename);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                // Визначаємо тип контенту (зображення чи відео) для браузера
                String contentType = filename.toLowerCase().endsWith(".mp4") ? "video/mp4" : "image/jpeg";

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/api/media/upload")
    public ResponseEntity<Map<String, String>> uploadMediaAsync(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Файл порожній"));
        }

        try {
            // Зберігаємо файл на диск через твій FileStorageService і отримуємо UUID ім'я
            String uniqueName = fileStorageService.storeFile(file);

            // Формуємо чистий шлях до нашого ж ендпоінту serveFile
            String fileUrl = "/api/media/" + uniqueName;

            // Повертаємо JSON з адресою для фронтенду
            return ResponseEntity.ok(Map.of(
                    "url", fileUrl,
                    "filename", uniqueName
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}