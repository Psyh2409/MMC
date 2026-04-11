package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.repository.ArticleRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
@RequiredArgsConstructor // Lombok сам створить конструктор для репозиторію
public class ArticleService {

    private final ArticleRepository articleRepository;

    public List<Article> findAll() {
        return articleRepository.findAll();
    }

    public Article findById(UUID id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Статтю не знайдено"));
    }

    public void saveArticle(Article article, String title, String desc, String text) {
        try {
            article.setTitleBytes(title.getBytes(StandardCharsets.UTF_8));
            article.setDescriptionBytes(desc.getBytes(StandardCharsets.UTF_8));
            article.setContentBytes(compress(text));
            articleRepository.save(article);
        } catch (IOException e) {
            // Якщо стискання не вдалося — логуємо і кидаємо помилку далі
            throw new RuntimeException("Помилка при збереженні статті: " + article.getId(), e);
        }
    }

    public static byte[] compress(String str) throws IOException {
        if (str == null || str.isEmpty()) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    public static String decompress(byte[] compressed) {
        if (compressed == null || compressed.length == 0) return null;
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gis.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Помилка читання контенту";
        }
    }
}
