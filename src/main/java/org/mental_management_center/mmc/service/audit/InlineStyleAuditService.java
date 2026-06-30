package org.mental_management_center.mmc.service.audit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Windsurf: Сервіс для аудиту інлайн-стилів в HTML шаблонах
 * Знаходить всі style="..." атрибути та аналізує їх використання
 * Запускається автоматично при старті додатку в dev профілі
 */
@Component
@Profile("dev")
public class InlineStyleAuditService {

    private static final Logger log = LoggerFactory.getLogger(InlineStyleAuditService.class);

    // Windsurf: Шлях до HTML шаблонів
    private final Path templatesPath = Paths.get("src/main/resources/templates");
    
    // Windsurf: Шлях для звіту
    private final Path reportPath = Paths.get("inline-style-audit-report.txt");
    
    // Windsurf: Кеш для результатів сканування
    private Map<String, List<InlineStyleInfo>> cachedInlineStyles;

    /**
     * Windsurf: Головний метод аудиту - запускається при старті додатку
     * Сканує HTML файли, знаходить інлайн-стилі та зберігає звіт
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=== ЗАПУСК АУДИТУ ІНЛАЙН-СТИЛІВ ===");

        try {
            cachedInlineStyles = scanInlineStyles();
            saveReportToFile();
        } catch (IOException e) {
            log.error("Помилка під час виконання аудиту інлайн-стилів", e);
        }
    }

    /**
     * Windsurf: Сканує всі HTML шаблони та збирає інлайн-стилі
     * Знаходить всі елементи з атрибутом style
     */
    private Map<String, List<InlineStyleInfo>> scanInlineStyles() throws IOException {
        Map<String, List<InlineStyleInfo>> inlineStylesMap = new TreeMap<>();

        if (!Files.exists(templatesPath)) {
            log.warn("Директорія шаблонів не знайдена: " + templatesPath);
            return inlineStylesMap;
        }

        Files.walk(templatesPath)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".html"))
                .forEach(path -> {
                    try {
                        String html = Files.readString(path, StandardCharsets.UTF_8);
                        // Windsurf: Вирізаємо HTML коментарі, щоб не шукати стилі в закоментованому коді
                        html = html.replaceAll("<!--.*?-->", "");
                        Document doc = Jsoup.parse(html);

                        List<InlineStyleInfo> fileStyles = new ArrayList<>();
                        
                        // Windsurf: Знаходимо всі елементи з атрибутом style
                        doc.select("[style]").forEach(element -> {
                            String styleValue = element.attr("style");
                            if (styleValue != null && !styleValue.trim().isEmpty()) {
                                InlineStyleInfo info = new InlineStyleInfo();
                                info.tagName = element.tagName();
                                info.styleValue = styleValue.trim();
                                info.lineNumber = getLineNumber(element);
                                fileStyles.add(info);
                            }
                        });

                        if (!fileStyles.isEmpty()) {
                            inlineStylesMap.put(path.getFileName().toString(), fileStyles);
                        }
                    } catch (IOException e) {
                        log.error("Не вдалося прочитати HTML шаблон: " + path, e);
                    }
                });

        return inlineStylesMap;
    }

    /**
     * Windsurf: Отримує номер рядка елемента в HTML файлі
     * Це допомагає швидко знайти місце розташування інлайн-стилю
     */
    private int getLineNumber(Element element) {
        try {
            // Jsoup не надає точний номер рядка, тому повертаємо -1
            // В реальному сценарії можна використовувати більш складний парсер
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Windsurf: Зберігає звіт аудиту інлайн-стилів у файл
     * Звіт містить детальну інформацію про кожен знайдений інлайн-стиль
     */
    private void saveReportToFile() throws IOException {
        Map<String, List<InlineStyleInfo>> inlineStylesMap = cachedInlineStyles;

        StringBuilder report = new StringBuilder();
        report.append("\n======================================================\n");
        report.append("           ЗВІТ ПРО ІНЛАЙН-СТИЛІ В HTML ШАБЛОНАХ   \n");
        report.append("======================================================\n");
        report.append("Дата: ").append(new Date()).append("\n");
        report.append("======================================================\n");

        int totalInlineStyles = 0;
        int filesWithInlineStyles = 0;

        for (Map.Entry<String, List<InlineStyleInfo>> entry : inlineStylesMap.entrySet()) {
            String fileName = entry.getKey();
            List<InlineStyleInfo> styles = entry.getValue();
            
            filesWithInlineStyles++;
            totalInlineStyles += styles.size();

            report.append("\n📄 Файл: ").append(fileName).append("\n");
            report.append("   Знайдено інлайн-стилів: ").append(styles.size()).append("\n");
            report.append("   ").append("─".repeat(50)).append("\n");

            for (InlineStyleInfo info : styles) {
                report.append("   • Тег: <").append(info.tagName).append(">\n");
                report.append("     Стиль: \"").append(info.styleValue).append("\"\n");
                if (info.lineNumber > 0) {
                    report.append("     Рядок: ").append(info.lineNumber).append("\n");
                }
                report.append("\n");
            }
        }

        report.append("\n📊 СТАТИСТИКА:\n");
        report.append("  Файлів з інлайн-стилями: ").append(filesWithInlineStyles).append("\n");
        report.append("  Всього знайдено інлайн-стилів: ").append(totalInlineStyles).append("\n");

        if (totalInlineStyles == 0) {
            report.append("\n✅ ВІДМІННО: Інлайн-стилів не знайдено. Проєкт чистий!\n");
        } else {
            report.append("\n⚠️ УВАГА: Знайдено інлайн-стилі. Рекомендується замінити їх на CSS класи.\n");
            report.append("\n💡 Поради:\n");
            report.append("  - Перегляньте кожен інлайн-стиль та вирішіть, чи можна його замінити на CSS клас\n");
            report.append("  - Динамічні стилі (через JavaScript) можуть залишатися\n");
            report.append("  - Специфічні стилі для окремих елементів краще винести в CSS\n");
        }
        report.append("======================================================\n");

        // Windsurf: Записуємо звіт у файл
        Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
        log.info("Звіт аудиту інлайн-стилів збережено у файл: " + reportPath.toAbsolutePath());
        log.warn("Знайдено {} інлайн-стилів у {} файлах", totalInlineStyles, filesWithInlineStyles);
    }

    /**
     * Windsurf: Внутрішній клас для зберігання інформації про інлайн-стиль
     */
    private static class InlineStyleInfo {
        String tagName;      // Назва HTML тегу
        String styleValue;  // Значення атрибуту style
        int lineNumber;     // Номер рядка (якщо вдалося визначити)
    }
}
