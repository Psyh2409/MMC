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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        try {
            scanHtmlTemplates();
            scanCssFiles();
            performAudit();
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

            doc.select("[class]").forEach(element -> {
                element.classNames().forEach(className -> addHtmlUsage("." + className, fileName));
            });
            doc.select("[id]").forEach(element -> {
                addHtmlUsage("#" + element.id(), fileName);
            });

            String htmlContent = Files.readString(htmlFile);
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
            cssContent = cssContent.replaceAll("/\\*[\\s\\S]*?\\*/", "");

            Pattern cssBlockPattern = Pattern.compile("(?s)(@media[^{]*?\\{)?\\s*([^{]+?)\\s*\\{\\s*(.*?)\\s*\\}(?=\\s*(?:@media|[.#a-zA-Z*]|$))");
            Matcher blockMatcher = cssBlockPattern.matcher(cssContent);

            while (blockMatcher.find()) {
                String mediaQuery = blockMatcher.group(1);
                String selectorsRaw = blockMatcher.group(2);
                String propertiesRaw = blockMatcher.group(3);

                Map<String, String> properties = parseProperties(propertiesRaw);

                // ОНОВЛЕНИЙ Regex: Збирає базовий клас РАЗОМ із його псевдокласами (:hover, [disabled], тощо)
                Pattern classOrIdPattern = Pattern.compile("[.#][a-zA-Z0-9_-]+[^\\s,{>+~]*");
                Matcher classOrIdMatcher = classOrIdPattern.matcher(selectorsRaw);

                while (classOrIdMatcher.find()) {
                    String fullSelector = classOrIdMatcher.group();

                    StyleDefinition definition = new StyleDefinition(cssFile.getFileName().toString(), mediaQuery, properties);
                    selectorMap.computeIfAbsent(fullSelector, k -> new SelectorDescriptor(k))
                            .getDefinitions()
                            .add(definition);
                }
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
        auditReport.append("\n--- CSS/HTML Audit Summary ---\n");

        List<String> missingStyles = new ArrayList<>();
        List<String> unusedStyles = new ArrayList<>();
        List<String> styleConflicts = new ArrayList<>();

        selectorMap.forEach((selectorName, descriptor) -> {
            boolean hasCssDefinition = descriptor.getDefinitions() != null && !descriptor.getDefinitions().isEmpty();

            // Знаходимо БАЗОВИЙ селектор для перевірки HTML (відкидаємо :hover, :focus, [type="text"] тощо)
            String baseSelector = selectorName.replaceAll("[:\\[].*", "");
            SelectorDescriptor baseDescriptor = selectorMap.get(baseSelector);

            // Селектор вважається використаним, якщо його базова форма є в HTML
            boolean hasHtmlUsage = baseDescriptor != null && baseDescriptor.getHtmlUsage() != null && !baseDescriptor.getHtmlUsage().isEmpty();

            // 1. Пошук відсутніх стилів (тільки для базових селекторів)
            if (selectorName.equals(baseSelector) && hasHtmlUsage && !hasCssDefinition) {
                missingStyles.add(selectorName + " (used in: " + String.join(", ", descriptor.getHtmlUsage().keySet()) + ")");
            }
            // 2. Пошук невикористаних стилів
            else if (hasCssDefinition && !hasHtmlUsage) {
                unusedStyles.add(selectorName + " (defined in: " + descriptor.getDefinitions().stream().map(StyleDefinition::getSourceFile).collect(Collectors.joining(", ")) + ")");
            }

            // 3. Пошук конфліктів стилів (Тепер :hover не б'ється зі звичайним станом)
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

        if (!missingStyles.isEmpty()) {
            auditReport.append("\n--- Missing Styles (Used in HTML but not defined in CSS) ---\n");
            missingStyles.forEach(s -> auditReport.append("- ").append(s).append("\n"));
        }

        if (!unusedStyles.isEmpty()) {
            auditReport.append("\n--- Unused Styles (Defined in CSS but not used in HTML) ---\n");
            unusedStyles.forEach(s -> auditReport.append("- ").append(s).append("\n"));
        }

        if (!styleConflicts.isEmpty()) {
            auditReport.append("\n--- Style Conflicts (Same property defined differently for the same selector) ---\n");
            styleConflicts.forEach(s -> auditReport.append("- ").append(s).append("\n"));
        }

        if (missingStyles.isEmpty() && unusedStyles.isEmpty() && styleConflicts.isEmpty()) {
            auditReport.append("\n--- No CSS/HTML audit issues found. ---\n");
        }
        log.warn(auditReport.toString());
    }
}