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

/**
 * Windsurf: Сервіс для аудиту CSS класів - знаходить невикористані стилі, дублікати та конфлікти
 * Запускається автоматично при старті додатку в dev профілі
 */
@Component
@Profile("dev")
public class CssAuditService {

    private static final Logger log = LoggerFactory.getLogger(CssAuditService.class);

    // Windsurf: Зберігаємо селектори: ключ - назва класу, значення - об'єкт з інформацією про те, де він знайдений
    private final Map<String, SelectorInfo> selectorMap = new ConcurrentHashMap<>();
    private final Path templatesPath = Paths.get("src/main/resources/templates");
    private final Path cssPath = Paths.get("src/main/resources/static/css");

    // Windsurf: Шляхи для звітів
    private final Path reportPath = Paths.get("css-audit-report.txt");

    /**
     * Windsurf: Головний метод аудиту - запускається при старті додатку
     * Збирає класи з HTML та CSS, аналізує конфлікти та зберігає звіт
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=== ЗАПУСК АУДИТУ СТАНДАРТИЗАЦІЇ CSS/HTML ===");
        selectorMap.clear();

        try {
            scanHtmlTemplates(); // Збираємо класи з HTML
            scanCssFiles();      // Збираємо класи з CSS
            performAudit();      // Шукаємо пустишки і рудименти
            saveReportToFile();  // Windsurf: Зберігаємо звіт у файл
        } catch (IOException e) {
            log.error("Помилка під час виконання аудиту стилів", e);
        }
    }

    /**
     * Windsurf: Сканує всі HTML шаблони та збирає використані CSS класи
     * Ігнорує Thymeleaf динамічні класи (починаються з ${ або __)
     */
    private void scanHtmlTemplates() throws IOException {
        if (!Files.exists(templatesPath)) return;

        Files.walk(templatesPath)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".html"))
                .forEach(path -> {
                    try {
                        String html = Files.readString(path, StandardCharsets.UTF_8);
                        // Windsurf: Вирізаємо HTML коментарі, щоб не шукати класи у закоментованому коді
                        html = html.replaceAll("<!--.*?-->", "");
                        Document doc = Jsoup.parse(html);

                        doc.select("[class]").forEach(element -> {
                            for (String className : element.classNames()) {
                                // Windsurf: Ігноруємо динаміку Thymeleaf
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

    /**
     * Windsurf: Сканує всі CSS файли та збирає визначені класи
     * Ігнорує CSS коментарі та шукає тільки класи (слова з крапкою)
     */
    private void scanCssFiles() throws IOException {
        if (!Files.exists(cssPath)) return;

        // Windsurf: Шукаємо тільки назви класів (слова з крапкою)
        Pattern classPattern = Pattern.compile("\\.([a-zA-Z0-9_-]+)");

        Files.walk(cssPath)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".css"))
                .forEach(path -> {
                    try {
                        String css = Files.readString(path, StandardCharsets.UTF_8);
                        // Windsurf: Вирізаємо CSS коментарі
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

    /**
     * Windsurf: Аналізує зібрані дані та шукає проблеми:
     * 1. Класи-пустишки в HTML (є в HTML, але немає в CSS)
     * 2. Мертві стилі в CSS (є в CSS, але не використовуються в HTML)
     * 3. Конфлікти стилів (один клас визначений в кількох CSS файлах)
     */
    private void performAudit() {
        StringBuilder report = new StringBuilder();
        report.append("\n======================================================\n");
        report.append("           ЗВІТ ПРО РУДИМЕНТИ ТА КОНФЛІКТИ (CSS/HTML)   \n");
        report.append("======================================================\n");
        report.append("Дата: ").append(new Date()).append("\n");
        report.append("======================================================\n");

        List<String> htmlOrphans = new ArrayList<>();
        List<String> cssOrphans = new ArrayList<>();
        List<String> cssConflicts = new ArrayList<>();
        List<String> cssDuplicates = new ArrayList<>(); // Windsurf: Додано для дублікатів

        selectorMap.forEach((className, info) -> {
            // Windsurf: 1. ПУСТИШКА В HTML (клас використовується, але стилів немає)
            if (!info.htmlFiles.isEmpty() && info.cssFiles.isEmpty()) {
                htmlOrphans.add(String.format("Клас [%s] є в HTML %s, але В CSS ЙОГО НЕМАЄ!", className, info.htmlFiles));
            }
            // Windsurf: 2. МЕРТВИЙ СТИЛЬ В CSS (стиль визначений, але не використовується)
            else if (info.htmlFiles.isEmpty() && !info.cssFiles.isEmpty()) {
                cssOrphans.add(String.format("Стиль .%s є в CSS %s, але НІДЕ В HTML НЕ ВИКОРИСТОВУЄТЬСЯ!", className, info.cssFiles));
            }

            // Windsurf: 3. КОНФЛІКТ СТИЛІВ (клас описаний у кількох CSS файлах одночасно)
            if (info.cssFiles.size() > 1) {
                cssConflicts.add(String.format("Стиль .%s дублюється у декількох файлах: %s", className, info.cssFiles));
            }

            // Windsurf: 4. ПОТЕНЦІЙНІ ДУБЛІКАТИ (клас використовується в багатьох HTML файлах - можливо треба уніфікувати)
            if (info.htmlFiles.size() > 5) {
                cssDuplicates.add(String.format("Клас [%s] використовується в багатьох HTML файлах (%d): %s",
                    className, info.htmlFiles.size(), info.htmlFiles));
            }
        });

        // Windsurf: Сортуємо для кращої читабельності
        Collections.sort(htmlOrphans);
        Collections.sort(cssOrphans);
        Collections.sort(cssConflicts);

        if (!htmlOrphans.isEmpty()) {
            report.append("\n❌ КЛАСИ-ПУСТИШКИ В HTML (не мають стилів) - ").append(htmlOrphans.size()).append(":\n");
            htmlOrphans.forEach(line -> report.append("  - ").append(line).append("\n"));
        }

        if (!cssOrphans.isEmpty()) {
            report.append("\n⚠️ МЕРТВІ СТИЛІ В CSS (засмічують пам'ять) - ").append(cssOrphans.size()).append(":\n");
            cssOrphans.forEach(line -> report.append("  - ").append(line).append("\n"));
        }

        // Windsurf: ВИВЕДЕННЯ КОНФЛІКТІВ
        if (!cssConflicts.isEmpty()) {
            report.append("\n⚡ КОНФЛІКТИ СТИЛІВ (Дублювання або перевизначення) - ").append(cssConflicts.size()).append(":\n");
            cssConflicts.forEach(line -> report.append("  - ").append(line).append("\n"));
        }

        // Windsurf: ВИВЕДЕННЯ ПОТЕНЦІЙНИХ ДУБЛІКАТІВ
        if (!cssDuplicates.isEmpty()) {
            report.append("\n🔍 ПОТЕНЦІЙНІ ДУБЛІКАТИ (використовуються в багатьох місцях) - ").append(cssDuplicates.size()).append(":\n");
            cssDuplicates.forEach(line -> report.append("  - ").append(line).append("\n"));
        }

        // Windsurf: Статистика
        report.append("\n📊 СТАТИСТИКА:\n");
        report.append("  Всього знайдено класів: ").append(selectorMap.size()).append("\n");
        report.append("  Класи-пустишки в HTML: ").append(htmlOrphans.size()).append("\n");
        report.append("  Мертві стилі в CSS: ").append(cssOrphans.size()).append("\n");
        report.append("  Конфлікти стилів: ").append(cssConflicts.size()).append("\n");
        report.append("  Потенційні дублікати: ").append(cssDuplicates.size()).append("\n");

        if (htmlOrphans.isEmpty() && cssOrphans.isEmpty() && cssConflicts.isEmpty()) {
            report.append("\n✅ ІДЕАЛЬНО: Усі класи синхронізовані. Сміття та конфліктів не знайдено.\n");
        }
        report.append("======================================================\n");

        log.warn(report.toString());
    }

    /**
     * Windsurf: Зберігає звіт аудиту у файл css-audit-report.txt в корені проєкту
     * Це дозволяє вам переглядати звіт в будь-який час без перезапуску додатку
     */
    private void saveReportToFile() throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("\n======================================================\n");
        report.append("           ЗВІТ ПРО РУДИМЕНТИ ТА КОНФЛІКТИ (CSS/HTML)   \n");
        report.append("======================================================\n");
        report.append("Дата: ").append(new Date()).append("\n");
        report.append("======================================================\n");

        List<String> htmlOrphans = new ArrayList<>();
        List<String> cssOrphans = new ArrayList<>();
        List<String> cssConflicts = new ArrayList<>();
        List<String> cssDuplicates = new ArrayList<>();

        selectorMap.forEach((className, info) -> {
            if (!info.htmlFiles.isEmpty() && info.cssFiles.isEmpty()) {
                htmlOrphans.add(String.format("Клас [%s] є в HTML %s, але В CSS ЙОГО НЕМАЄ!", className, info.htmlFiles));
            } else if (info.htmlFiles.isEmpty() && !info.cssFiles.isEmpty()) {
                cssOrphans.add(String.format("Стиль .%s є в CSS %s, але НІДЕ В HTML НЕ ВИКОРИСТОВУЄТЬСЯ!", className, info.cssFiles));
            }
            if (info.cssFiles.size() > 1) {
                cssConflicts.add(String.format("Стиль .%s дублюється у декількох файлах: %s", className, info.cssFiles));
            }
            if (info.htmlFiles.size() > 5) {
                cssDuplicates.add(String.format("Клас [%s] використовується в багатьох HTML файлах (%d): %s",
                    className, info.htmlFiles.size(), info.htmlFiles));
            }
        });

        Collections.sort(htmlOrphans);
        Collections.sort(cssOrphans);
        Collections.sort(cssConflicts);

        if (!htmlOrphans.isEmpty()) {
            report.append("\n❌ КЛАСИ-ПУСТИШКИ В HTML (не мають стилів) - ").append(htmlOrphans.size()).append(":\n");
            htmlOrphans.forEach(line -> report.append("  - ").append(line).append("\n"));
        }

        if (!cssOrphans.isEmpty()) {
            report.append("\n⚠️ МЕРТВІ СТИЛІ В CSS (засмічують пам'ять) - ").append(cssOrphans.size()).append(":\n");
            cssOrphans.forEach(line -> report.append("  - ").append(line).append("\n"));
        }

        if (!cssConflicts.isEmpty()) {
            report.append("\n⚡ КОНФЛІКТИ СТИЛІВ (Дублювання або перевизначення) - ").append(cssConflicts.size()).append(":\n");
            cssConflicts.forEach(line -> report.append("  - ").append(line).append("\n"));
        }

        if (!cssDuplicates.isEmpty()) {
            report.append("\n🔍 ПОТЕНЦІЙНІ ДУБЛІКАТИ (використовуються в багатьох місцях) - ").append(cssDuplicates.size()).append(":\n");
            cssDuplicates.forEach(line -> report.append("  - ").append(line).append("\n"));
        }

        report.append("\n📊 СТАТИСТИКА:\n");
        report.append("  Всього знайдено класів: ").append(selectorMap.size()).append("\n");
        report.append("  Класи-пустишки в HTML: ").append(htmlOrphans.size()).append("\n");
        report.append("  Мертві стилі в CSS: ").append(cssOrphans.size()).append("\n");
        report.append("  Конфлікти стилів: ").append(cssConflicts.size()).append("\n");
        report.append("  Потенційні дублікати: ").append(cssDuplicates.size()).append("\n");

        if (htmlOrphans.isEmpty() && cssOrphans.isEmpty() && cssConflicts.isEmpty()) {
            report.append("\n✅ ІДЕАЛЬНО: Усі класи синхронізовані. Сміття та конфліктів не знайдено.\n");
        }
        report.append("======================================================\n");

        // Windsurf: Записуємо звіт у файл
        Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
        log.info("Звіт аудиту збережено у файл: " + reportPath.toAbsolutePath());
    }

    /**
     * Windsurf: Внутрішній клас для зберігання інформації про CSS селектор
     * Зберігає списки HTML та CSS файлів, де використовується клас
     */
    private static class SelectorInfo {
        final Set<String> htmlFiles = new HashSet<>(); // Windsurf: HTML файли, де використовується клас
        final Set<String> cssFiles = new HashSet<>();   // Windsurf: CSS файли, де визначено клас
    }
}