
package org.mental_management_center.mmc.service.audit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Profile("dev")
public class CssAuditService {

    private static final Logger log = LoggerFactory.getLogger(CssAuditService.class);

    private final Map<String, SelectorDescriptor> selectorMap = new ConcurrentHashMap<>();
    private final Path templatesPath = Paths.get("src/main/resources/templates");
    private final Path cssPath = Paths.get("src/main/resources/static/css");

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Starting CSS/HTML audit...");
        // КРИТИЧНИЙ ФІКС 1: Очищуємо кеш перед скануванням, щоб вбити фантомів від DevTools
        selectorMap.clear();

        try {
            scanHtmlTemplates();
            scanCssFiles();
            performAudit();
            refactorAllHtmlComponents();
            log.info("CSS/HTML audit finished.");
        } catch (IOException e) {
            log.error("Error during CSS/HTML audit", e);
        }
        log.info("CSS/HTML audit finished.");
    }

    private void scanHtmlTemplates() throws IOException {
        if (!Files.exists(templatesPath)) return;
        Files.walk(templatesPath)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".html"))
                .forEach(this::processHtmlFile);
    }

    private void processHtmlFile(Path htmlFile) {
        try {
            File input = htmlFile.toFile();
            Document doc = Jsoup.parse(input, "UTF-8");
            String fileName = htmlFile.getFileName().toString();

            // Збираємо тільки класи. Jsoup автоматично ігнорує HTML-коментарі.
            doc.select("[class]").forEach(element -> {
                element.classNames().forEach(className -> addHtmlUsage("." + className, fileName));
            });

            // КРИТИЧНИЙ ФІКС 2: Вимикаємо збір ID (#), бо вони засмічують CSS-аудит (їх місце в JS/Бекенді)
            /*
            doc.select("[id]").forEach(element -> {
                addHtmlUsage("#" + element.id(), fileName);
            });
            */

            String htmlContent = Files.readString(htmlFile);

            // КРИТИЧНИЙ ФІКС 3: Вирізаємо HTML-коментарі з сирого тексту ПЕРЕД тим, як натравлювати Regex.
            // Інакше Regex знайде старий код, який ти просто закоментував.
            htmlContent = htmlContent.replaceAll("<!--[\\s\\S]*?-->", "");

            Pattern thymeleafClassAttributePattern = Pattern.compile("th:(?:class|classappend)=\"([^\"]*?)\"");
            Matcher thymeleafMatcher = thymeleafClassAttributePattern.matcher(htmlContent);

            while (thymeleafMatcher.find()) {
                String thymeleafExpression = thymeleafMatcher.group(1);
                Pattern quotedStringPattern = Pattern.compile("'(.*?)'|\"(.*?)\"");
                Matcher quotedStringMatcher = quotedStringPattern.matcher(thymeleafExpression);
                while (quotedStringMatcher.find()) {
                    String classNamesRaw = quotedStringMatcher.group(1) != null ? quotedStringMatcher.group(1) : quotedStringMatcher.group(2);
                    if (classNamesRaw != null && !classNamesRaw.isEmpty()) {
                        Arrays.stream(classNamesRaw.split("\\s+"))
                                .filter(s -> !s.isEmpty())
                                .filter(className -> className.matches("^[a-z0-9-]+$"))
                                .forEach(className -> addHtmlUsage("." + className, fileName));
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error processing HTML file {}: {}", htmlFile, e.getMessage());
        }
    }

    private void addHtmlUsage(String selector, String fileName) {
        selectorMap.computeIfAbsent(selector, k -> new SelectorDescriptor(k))
                .getHtmlUsage()
                .merge(fileName, 1, Integer::sum);
    }

    private void scanCssFiles() throws IOException {
        if (!Files.exists(cssPath)) return;
        Files.walk(cssPath)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".css"))
                .forEach(this::processCssFile);
    }

    private void processCssFile(Path cssFile) {
        try {
            String cssContent = Files.readString(cssFile);
            // Видаляємо коментарі
            cssContent = cssContent.replaceAll("/\\*[\\s\\S]*?\\*/", "");

            int i = 0;
            String currentMediaQuery = null;

            while (i < cssContent.length()) {
                int nextOpen = cssContent.indexOf("{", i);
                if (nextOpen == -1) break;

                String beforeBrace = cssContent.substring(i, nextOpen).trim();

                // Якщо перед дужкою є @media (або @supports), фіксуємо контекст
                if (beforeBrace.contains("@media") || beforeBrace.contains("@supports")) {
                    int atIndex = beforeBrace.contains("@media") ? beforeBrace.lastIndexOf("@media") : beforeBrace.lastIndexOf("@supports");
                    currentMediaQuery = beforeBrace.substring(atIndex).trim();
                    i = nextOpen + 1; // Заходимо всередину блоку
                    continue;
                }

                String selectorsRaw = beforeBrace;

                // Якщо в цьому проміжку є закриваюча дужка '}', ми могли вийти з @media
                int lastCloseBefore = selectorsRaw.lastIndexOf("}");
                if (lastCloseBefore != -1) {
                    selectorsRaw = selectorsRaw.substring(lastCloseBefore + 1).trim();

                    // Математична перевірка: чи ми вийшли з @media?
                    String fullTextBefore = cssContent.substring(0, nextOpen);
                    int openCount = fullTextBefore.length() - fullTextBefore.replace("{", "").length();
                    int closeCount = fullTextBefore.length() - fullTextBefore.replace("}", "").length();

                    // Якщо всі відкриті дужки закриті, ми в глобальній області
                    if (openCount == closeCount) {
                        currentMediaQuery = null;
                    }
                }

                int nextClose = cssContent.indexOf("}", nextOpen);
                if (nextClose == -1) break; // Захист від битого CSS

                String propertiesRaw = cssContent.substring(nextOpen + 1, nextClose).trim();
                Map<String, String> properties = parseProperties(propertiesRaw);

                if (!selectorsRaw.isEmpty()) {
                    String[] selectors = selectorsRaw.split(",");
                    for (String rawSel : selectors) {
                        String fullSelector = rawSel.trim();

                        // Ігноруємо чисті теги (body, p) без класів/id або порожні рядки
                        if (fullSelector.isEmpty() || !fullSelector.matches(".*[.#].*")) {
                            continue;
                        }

                        StyleDefinition definition = new StyleDefinition(cssFile.getFileName().toString(), currentMediaQuery, properties);
                        selectorMap.computeIfAbsent(fullSelector, k -> new SelectorDescriptor(k))
                                .getDefinitions()
                                .add(definition);
                    }
                }

                i = nextClose + 1;
            }
        } catch (IOException e) {
            log.error("Error processing CSS file {}: {}", cssFile, e.getMessage());
        }
    }

    private Map<String, String> parseProperties(String propertiesRaw) {
        Map<String, String> properties = new HashMap<>();
        Pattern propertyPattern = Pattern.compile("([^:]+?)\\s*:\\s*([^;]+?);");
        Matcher propertyMatcher = propertyPattern.matcher(propertiesRaw);
        while (propertyMatcher.find()) {
            properties.put(propertyMatcher.group(1).trim(), propertyMatcher.group(2).trim());
        }
        return properties;
    }

    private void performAudit() {
        StringBuilder auditReport = new StringBuilder();
        auditReport.append("\n--- CSS/HTML Audit Summary (Grouped & Sorted) ---\n");

        // Використовуємо TreeMap для автоматичного сортування імен файлів за алфавітом
        Map<String, List<String>> missingByPage = new java.util.TreeMap<>();
        // Використовуємо TreeMap для сортування ключів (файлів) за алфавітом
        Map<String, List<String>> unusedStylesByFile = new TreeMap<>();
        List<String> styleConflicts = new ArrayList<>();
        List<String> orphansForRescue = new ArrayList<>();

        selectorMap.forEach((selectorName, descriptor) -> {
            boolean hasCssDefinition = descriptor.getDefinitions() != null && !descriptor.getDefinitions().isEmpty();

            // Знаходимо БАЗОВИЙ селектор для перевірки HTML
            String baseSelector = selectorName.replaceAll("[:\\[].*", "");
            SelectorDescriptor baseDescriptor = selectorMap.get(baseSelector);

            // Селектор вважається використаним, якщо його базова форма є в HTML
            boolean hasHtmlUsage = baseDescriptor != null && baseDescriptor.getHtmlUsage() != null && !baseDescriptor.getHtmlUsage().isEmpty();

            // 1. Пошук відсутніх стилів (групуємо за сторінками)
            if (selectorName.equals(baseSelector) && hasHtmlUsage && !hasCssDefinition) {
                orphansForRescue.add(selectorName);
                descriptor.getHtmlUsage().keySet().forEach(fileName ->
                        missingByPage.computeIfAbsent(fileName, k -> new ArrayList<>()).add(selectorName)
                );
            }
            // 2. Пошук невикористаних стилів
            else if (hasCssDefinition && !hasHtmlUsage) {
                descriptor.getDefinitions().forEach(def -> {
                    unusedStylesByFile.computeIfAbsent(def.getSourceFile(), k -> new ArrayList<>())
                            .add(selectorName);
                });
            }
            // 3. Пошук конфліктів стилів (групування за Media Query залишаємо без змін)
            if (hasCssDefinition && descriptor.getDefinitions().size() > 1) {
                Map<String, List<StyleDefinition>> definitionsByMediaQuery = descriptor.getDefinitions().stream()
                        .collect(Collectors.groupingBy(def -> def.getMediaQuery() != null ? def.getMediaQuery() : "no-media-query"));

                definitionsByMediaQuery.forEach((mediaQuery, defs) -> {
                    if (defs.size() > 1) {
                        Map<String, List<String>> propertyValues = new HashMap<>();
                        for (StyleDefinition definition : defs) {
                            definition.getProperties().forEach((propName, propValue) ->
                                    propertyValues.computeIfAbsent(propName, k -> new ArrayList<>()).add(propValue)
                            );
                        }

                        propertyValues.forEach((propName, values) -> {
                            if (values.stream().distinct().count() > 1) {
                                String conflictDetail = String.format("Conflict for selector '%s' property '%s'. Values: %s. Defined in: %s",
                                        selectorName, propName,
                                        values.stream().distinct().collect(Collectors.joining(", ")),
                                        defs.stream().map(StyleDefinition::getSourceFile).collect(Collectors.joining(", "))
                                );
                                styleConflicts.add(conflictDetail);
                            }
                        });
                    }
                });
            }
        });

        // --- ФОРМУВАННЯ СОРТОВАНОГО ЗВІТУ ---

        if (!missingByPage.isEmpty()) {
            auditReport.append("\n[!!!] MISSING STYLES BY TEMPLATE (HTML needs these):\n");
            missingByPage.forEach((fileName, selectors) -> {
                auditReport.append("📄 ").append(fileName.toUpperCase()).append(":\n");
                selectors.stream().sorted().forEach(s -> auditReport.append("   - ").append(s).append("\n"));
            });
        }

        if (!unusedStylesByFile.isEmpty()) {
            auditReport.append("\n--- Unused Styles (Grouped by file) ---\n");
            unusedStylesByFile.forEach((fileName, selectors) -> {
                auditReport.append("📁 ").append(fileName).append(":\n");
                // Сортуємо селектори всередині файлу, прибираємо дублікати (distinct)
                selectors.stream().distinct().sorted().forEach(s ->
                        auditReport.append("   - ").append(s).append("\n")
                );
            });
        }

        if (!styleConflicts.isEmpty()) {
            auditReport.append("\n--- Style Conflicts (Sorted) ---\n");
            styleConflicts.stream().sorted().forEach(s -> auditReport.append("- ").append(s).append("\n"));
        }

        if (missingByPage.isEmpty() && unusedStylesByFile.isEmpty() && styleConflicts.isEmpty()) {
            auditReport.append("\n--- No CSS/HTML audit issues found. ---\n");
        }

        log.warn(auditReport.toString());

        if (!orphansForRescue.isEmpty()) {
            runArcheologyMission(orphansForRescue);
        }
    }

    private void runArcheologyMission(List<String> orphans) {
        // ШЛЯХ ДО ТВОГО СТАРОГО ФАЙЛУ (перевір, чи він правильний)
        Path archivePath = Paths.get("src/main/resources/static/css/style_old.txt");
        if (!Files.exists(archivePath)) {
            log.info("Археологічний файл {} не знайдено. Пропускаю рятувальну місію.", archivePath.getFileName());
            return;
        }

        try {
            String archiveContent = Files.readString(archivePath);
            archiveContent = archiveContent.replaceAll("/\\*[\\s\\S]*?\\*/", ""); // Чистимо коментарі

            StringBuilder rescuedCode = new StringBuilder();
            rescuedCode.append("/* === ВРЯТОВАНІ КЛАСИ ДЛЯ РЕФАКТОРИНГУ === */\n");
            rescuedCode.append("/* Цей файл згенеровано автоматично. Не підключай його до HTML напряму. */\n\n");

            int i = 0;
            int rescuedCount = 0;

            while (i < archiveContent.length()) {
                int nextOpen = archiveContent.indexOf("{", i);
                if (nextOpen == -1) break;

                String beforeBrace = archiveContent.substring(i, nextOpen).trim();
                int nextClose = archiveContent.indexOf("}", nextOpen);
                if (nextClose == -1) break;

                String blockContent = archiveContent.substring(nextOpen + 1, nextClose).trim();

                // Шукаємо, чи є в цьому старому селекторі хтось із наших сиріт
                for (String orphan : orphans) {
                    // Використовуємо регулярку, щоб знайти точний збіг слова (щоб .badge не захопив .badge-client)
                    if (beforeBrace.matches(".*(?:^|\\s|,)" + Pattern.quote(orphan) + "(?:$|\\s|,|:|>|\\+).*")) {
                        rescuedCode.append(beforeBrace).append(" {\n")
                                .append("    ").append(blockContent).append("\n")
                                .append("}\n\n");
                        rescuedCount++;
                        break; // Знайшли сироту — забираємо блок і йдемо далі
                    }
                }
                i = nextClose + 1;
            }

            // Зберігаємо врятований код у новий файл
            Path outputPath = Paths.get("src/main/resources/static/css/rescued-orphans.css");
            Files.write(
                    outputPath,
                    rescuedCode.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            log.warn("Археологічна місія завершена! Врятовано {} блоків коду. Шукай файл 'rescued-orphans.css'.", rescuedCount);

        } catch (IOException e) {
            log.error("Помилка під час археологічної місії", e);
        }
    }

    /**
     * Спеціальний аудит для підготовки до уніфікації кнопок.
     * Виводить список усіх класів кнопок чисто в консоль без логер-сміття.
     */
    private void printButtonClassesAudit() {
        System.out.println("\n=======================================================");
        System.out.println("=== АУДИТ КНОПОК (ПІДГОТОВКА ДО btn-primary) ===");
        System.out.println("=======================================================");

        // Використовуємо TreeSet для автоматичного сортування за алфавітом
        java.util.Set<String> buttonClasses = new java.util.TreeSet<>();

        // Проходимо по всіх знайдених селекторах у системі
        for (SelectorDescriptor descriptor : selectorMap.values()) {
            // Беремо тільки ті, що реально знайдені в HTML шаблонах
            if (descriptor.getHtmlUsage() != null && !descriptor.getHtmlUsage().isEmpty()) {
                String selector = descriptor.getSelectorName();

                // Фільтруємо: шукаємо .btn або .button
                if (selector.toLowerCase().contains("btn") || selector.toLowerCase().contains("button")) {
                    buttonClasses.add(selector);
                }
            }
        }

        System.out.println("Знайдено унікальних класів кнопок у HTML: " + buttonClasses.size());
        for (String btnClass : buttonClasses) {
            System.out.println(" - " + btnClass);
        }
        System.out.println("=======================================================\n");
    }

    /**
     * Глобальний авторефакторинг HTML: підпорядкування всього сайту атомарним токенам MMC.
     */
    private void refactorAllHtmlComponents() {
        System.out.println("\n=======================================================");
        System.out.println("=== СТАРТ ГЛОБАЛЬНОЇ СТАНДАРТИЗАЦІЇ MMC (10 ФАЙЛІВ) ===");
        System.out.println("=======================================================");

        java.util.Map<String, String> classMap = new java.util.HashMap<>();

        // 1. PILLAR: BUTTONS (Зафіксовано)
        String[][] buttonRules = {
                {"submit-button", "btn-primary"}, {"submit-button-primary", "btn-primary"},
                {"btn-publish-article", "btn-primary"}, {"btn-action", "btn-primary"},
                {"btn-action-email", "btn-primary"}, {"btn-action-sms", "btn-primary"},
                {"admin-save-button", "btn-primary"}, {"manifesto-button", "btn-primary"},
                {"btn-dashboard-link", "btn-primary"}, {"admin-secondary-button", "btn-outline"},
                {"btn-cancel-reply", "btn-outline"}, {"btn-reply", "btn-outline"},
                {"submit-button-soft", "btn-outline"}, {"btn-link", "btn-outline"},
                {"btn-comm-ban", "btn-primary btn-danger"}, {"btn-chat-ban", "btn-primary btn-danger"},
                {"btn-delete-small", "btn-primary btn-danger"}, {"btn-chat-ok", "btn-primary btn-success"},
                {"btn-comm-ok", "btn-primary btn-success"}, {"btn-site-ok", "btn-primary btn-success"}
        };
        for (String[] r : buttonRules) classMap.put(r[0], r[1]);

        // --- 2. PILLAR: CARDS & SURFACES (РОЗДІЛЕНО НА ДВА СТАНДАРТИ) ---

        // А. Базові картки сайту -> отримують .card-primary (Тло: bg-surface)
        String[] primaryCards = {"note-card", "comment-form-box", "article-callout"};
        for (String c : primaryCards) classMap.put(c, "card-primary");

        // Б. Акцентовані блоки та CTA -> отримують .card-elevated (Тло: bg-elevated)
        // Вони мають виринати над контентом одразу, навіть без ховеру
        String[] elevatedCards = {"session-invitation-card", "cta-panel", "cta-panel-light"};
        for (String c : elevatedCards) classMap.put(c, "card-elevated");

        // 3. PILLAR: FORMS (Поля вводу та контейнери)
        String[] formGroups = {"article-form-section", "reply-form-container", "comment-form-box"};
        for (String f : formGroups) classMap.put(f, "form-group");

        // 4. PILLAR: TYPOGRAPHY (Заголовки та підписи)
        String[] headingsLg = {"section-subtitle", "comments-title", "sidebar-heading", "manifesto-title", "notes-section-title", "article-section-title"};
        for (String h : headingsLg) classMap.put(h, "heading-lg");

        String[] textMuted = {"article-date", "note-date", "status-text-muted", "footer-copy"};
        for (String t : textMuted) classMap.put(t, "text-muted");

        // 5. PILLAR: LAYOUT & NAVIGATION (Обгортки дій)
        String[] actionGroups = {"dashboard-actions", "contact-actions", "profile-actions", "manifesto-actions", "filter-buttons"};
        for (String a : actionGroups) classMap.put(a, "action-group");

        // --- 6. PILLAR: NAVIGATION (Хедери, футери, сайдбари) ---
        // Замість мільйону унікальних посилань у футері — один стандарт навігаційного лінка
        String[] navLinks = {"footer-link-action", "footer-action-text", "home-service-link", "steps-cta-link", "read-more"};
        for (String n : navLinks) classMap.put(n, "nav-link");

        // Уніфікуємо вертикальне меню (наприклад, у сайдбарі профілю чи адмінки)
        String[] navVerticals = {"sidebar-nav", "chat-tabs"};
        for (String nv : navVerticals) classMap.put(nv, "nav-vertical");


        // --- 7. PILLAR: LAYOUT & CONTAINERS (Глобальна квантова сітка) ---
        // Зводимо всі унікальні обгортки контенту до єдиного системного контейнера
        String[] layouts = {"chat-wrapper", "notes-container", "manifesto-container", "comments-section", "article-section-spaced", "issue-steps"};
        for (String l : layouts) classMap.put(l, "layout-container");


        // --- 8. PILLAR: TABLES & LISTS (Адмін-панелі та списки запитів) ---
        // Замість специфічних класів для таблиць робимо загальносистемний table-main
        String[] tables = {"row-priority-client", "articles-list", "comments-list", "notes-list"};
        for (String t : tables) classMap.put(t, "layout-stack");

        // --- 9. PILLAR: CONTENT SECTIONS (Забутий блок) ---
        // Усі унікальні обгортки тексту зводимо до єдиного стандарту
        String[] contents = {"article-content", "article-body", "issue-content", "steps-content", "note-content", "manifesto-content"};
        for (String c : contents) classMap.put(c, "content-section");

        // --- 10. ДОДАТКОВІ КНОПКИ ТА ГРУПИ ---
        classMap.put("btn-edit-small", "btn-outline");
        classMap.put("btn-copy-uuid", "btn-outline");
        classMap.put("btn-group-nowrap", "action-group");
        classMap.put("dashboard-cta", "card-elevated");

        // --- 11. ІКОНКИ ---
        classMap.put("icon-large", "icon");

        // --- 12. ФОРМИ: ЗНИЩЕННЯ ДУБЛІКАТІВ ---
        classMap.put("form-control", "form-input");
        classMap.put("form-control-textarea", "form-textarea");

        java.util.regex.Pattern classPattern = java.util.regex.Pattern.compile("class=\"([^\"]+)\"");

        try {
            java.nio.file.Files.walk(templatesPath)
                    .filter(path -> path.toString().endsWith(".html"))
                    .forEach(path -> {
                        try {
                            String content = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
                            java.util.regex.Matcher matcher = classPattern.matcher(content);
                            StringBuilder updatedContent = new StringBuilder();
                            boolean fileChanged = false;

                            while (matcher.find()) {
                                String originalClasses = matcher.group(1);
                                String[] individualClasses = originalClasses.split("\\s+");
                                java.util.Set<String> newClassesList = new java.util.LinkedHashSet<>();
                                boolean attributeChanged = false;

                                for (String cssClass : individualClasses) {
                                    if (classMap.containsKey(cssClass)) {
                                        newClassesList.addAll(java.util.Arrays.asList(classMap.get(cssClass).split("\\s+")));
                                        attributeChanged = true;
                                    } else {
                                        newClassesList.add(cssClass);
                                    }
                                }

                                if (attributeChanged) {
                                    fileChanged = true;
                                    String replacement = "class=\"" + String.join(" ", newClassesList) + "\"";
                                    matcher.appendReplacement(updatedContent, java.util.regex.Matcher.quoteReplacement(replacement));
                                } else {
                                    matcher.appendReplacement(updatedContent, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
                                }
                            }
                            matcher.appendTail(updatedContent);

                            if (fileChanged) {
                                java.nio.file.Files.writeString(path, updatedContent.toString(), java.nio.charset.StandardCharsets.UTF_8);
                                System.out.println("✅ СТАНДАРТИЗОВАНО: " + path.getFileName());
                            }

                        } catch (java.io.IOException e) {
                            System.out.println("❌ Помилка файлу: " + path.getFileName());
                        }
                    });
        } catch (java.io.IOException e) {
            System.out.println("Помилка доступу до папки шаблонів");
        }
        System.out.println("=== ГЛОБАЛЬНА СТАНДАРТИЗОВАНА СИСТЕМА ПРИЙНЯТА ===");
    }

    public void extractInlineScripts() {
        try {
            Files.walk(templatesPath)
                    .filter(p -> p.toString().endsWith(".html"))
                    .forEach(path -> {
                        try {
                            Document doc = Jsoup.parse(path.toFile(), "UTF-8");
                            // Знаходимо всі <script>, у яких немає атрибута src (тобто інлайнові)
                            doc.select("script:not([src])").forEach(script -> {
                                String scriptContent = script.html().trim();
                                if (scriptContent.isEmpty()) return;

                                // Генеруємо ім'я файлу (напр: journal.js)
                                String baseName = path.getFileName().toString().replace(".html", "");
                                String jsFileName = baseName + ".js";
                                Path jsPath = Paths.get("src/main/resources/static/js/pages/", jsFileName);

                                // Створюємо папку, якщо треба
                                try {
                                    Files.createDirectories(jsPath.getParent());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }

                                // Записуємо в окремий файл
                                try {
                                    Files.writeString(jsPath, scriptContent);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }

                                // Логуємо для тебе
                                System.out.println("✅ Екстраговано: " + jsFileName);
                                System.out.println("   Шаблон: " + path.getFileName());
                                if (scriptContent.contains("[[")) {
                                    System.out.println("   ⚠️ УВАГА: Знайдено Thymeleaf-вирази '[[...]]'. Потрібен ручний рефакторинг!");
                                }
                            });
                        } catch (IOException e) {
                            log.error("Помилка обробки шаблону: " + path, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Помилка читання папок", e);
        }
    }
}