package org.mental_management_center.mmc.service.audit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//@Component
@Profile("dev")
public class CssAuditService {

    private static final Logger log = LoggerFactory.getLogger(CssAuditService.class);

    // Зберігаємо селектори: ключ - назва класу, значення - об'єкт з інформацією про те, де він знайдений
    private final Map<String, SelectorInfo> selectorMap = new ConcurrentHashMap<>();
    private final Path templatesPath = Paths.get("src/main/resources/templates");
    private final Path cssPath = Paths.get("src/main/resources/static/css");

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=== ЗАПУСК АУДИТУ СТАНДАРТИЗАЦІЇ CSS/HTML ===");
        selectorMap.clear();

        try {
            scanHtmlTemplates(); // Збираємо класи з HTML
            scanCssFiles();      // Збираємо класи з CSS
            performAudit();      // Шукаємо пустишки і рудименти
        } catch (IOException e) {
            log.error("Помилка під час виконання аудиту стилів", e);
        }
    }

    private void scanHtmlTemplates() throws IOException {
        if (!Files.exists(templatesPath)) return;

        Files.walk(templatesPath)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".html"))
                .forEach(path -> {
                    try {
                        String html = Files.readString(path, StandardCharsets.UTF_8);
                        // Вирізаємо HTML коментарі, щоб не шукати класи у закоментованому коді
                        html = html.replaceAll("", "");
                        Document doc = Jsoup.parse(html);

                        doc.select("[class]").forEach(element -> {
                            for (String className : element.classNames()) {
                                // Ігноруємо динаміку Thymeleaf
                                if (className.startsWith("${") || className.startsWith("__")) continue;

                                selectorMap.computeIfAbsent(className, k -> new SelectorInfo())
                                        .htmlFiles.add(path.getFileName().toString());
                            }
                        });
                    } catch (IOException e) {
                        log.error("Не вдалося прочитати HTML шаблон: " + path, e);
                    }
                });
    }

    private void scanCssFiles() throws IOException {
        if (!Files.exists(cssPath)) return;

        // Шукаємо тільки назви класів (слова з крапкою)
        Pattern classPattern = Pattern.compile("\\.([a-zA-Z0-9_-]+)");

        Files.walk(cssPath)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".css"))
                .forEach(path -> {
                    try {
                        String css = Files.readString(path, StandardCharsets.UTF_8);
                        // Вирізаємо CSS коментарі
                        css = css.replaceAll("/\\*[^*]*\\*+([^/*][^*]*\\*+)*/", "");

                        Matcher matcher = classPattern.matcher(css);
                        while (matcher.find()) {
                            String className = matcher.group(1);
                            selectorMap.computeIfAbsent(className, k -> new SelectorInfo())
                                    .cssFiles.add(path.getFileName().toString());
                        }
                    } catch (IOException e) {
                        log.error("Не вдалося прочитати CSS файл: " + path, e);
                    }
                });
    }

    private void performAudit() {
        StringBuilder report = new StringBuilder();
        report.append("\n======================================================\n");
        report.append("           ЗВІТ ПРО РУДИМЕНТИ ТА КОНФЛІКТИ (CSS/HTML)   \n");
        report.append("======================================================\n");

        List<String> htmlOrphans = new ArrayList<>();
        List<String> cssOrphans = new ArrayList<>();
        List<String> cssConflicts = new ArrayList<>(); // ДОДАНО: Список для конфліктів

        selectorMap.forEach((className, info) -> {
            // 1. ПУСТИШКА В HTML
            if (!info.htmlFiles.isEmpty() && info.cssFiles.isEmpty()) {
                htmlOrphans.add(String.format("Клас [%s] є в HTML %s, але В CSS ЙОГО НЕМАЄ!", className, info.htmlFiles));
            }
            // 2. МЕРТВИЙ СТИЛЬ В CSS
            else if (info.htmlFiles.isEmpty() && !info.cssFiles.isEmpty()) {
                cssOrphans.add(String.format("Стиль .%s є в CSS %s, але НІДЕ В HTML НЕ ВИКОРИСТОВУЄТЬСЯ!", className, info.cssFiles));
            }

            // 3. КОНФЛІКТ СТИЛІВ (Клас описаний у кількох файлах CSS одночасно)
            if (info.cssFiles.size() > 1) {
                cssConflicts.add(String.format("Стиль .%s дублюється у декількох файлах: %s", className, info.cssFiles));
            }
        });

        if (!htmlOrphans.isEmpty()) {
            report.append("\n❌ КЛАСИ-ПУСТИШКИ В HTML (не мають стилів):\n");
            htmlOrphans.forEach(line -> report.append("  - ").append(line).append("\n"));
        }

        if (!cssOrphans.isEmpty()) {
            report.append("\n⚠️ МЕРТВІ СТИЛІ В CSS (засмічують пам'ять):\n");
            cssOrphans.forEach(line -> report.append("  - ").append(line).append("\n"));
        }

        // ВИВЕДЕННЯ КОНФЛІКТІВ
        if (!cssConflicts.isEmpty()) {
            report.append("\n⚡ КОНФЛІКТИ СТИЛІВ (Дублювання або перевизначення):\n");
            cssConflicts.forEach(line -> report.append("  - ").append(line).append("\n"));
        }

        if (htmlOrphans.isEmpty() && cssOrphans.isEmpty() && cssConflicts.isEmpty()) {
            report.append("\n✅ ІДЕАЛЬНО: Усі класи синхронізовані. Сміття та конфліктів не знайдено.\n");
        }
        report.append("======================================================\n");

        log.warn(report.toString());
    }

    // Внутрішній клас-замінник для видаленого SelectorDescriptor (всього 2 поля)
    private static class SelectorInfo {
        final Set<String> htmlFiles = new HashSet<>();
        final Set<String> cssFiles = new HashSet<>();
    }
}