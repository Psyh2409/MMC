package org.mental_management_center.mmc.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.mental_management_center.mmc.model.CategoryTranslation;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.CategoryTranslationRepository;
import org.mental_management_center.mmc.service.MyUserDetails;
import org.mental_management_center.mmc.service.OAuth2Principal;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.stereotype.Controller;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalModelAttributes {

    private final UserService userService;

    // ДОДАНО: Підключаємо наш новий репозиторій словника
    private final CategoryTranslationRepository categoryTranslationRepository;

    // ОНОВЛЕНО: Конструктор тепер приймає CategoryTranslationRepository
    public GlobalModelAttributes(UserService userService, CategoryTranslationRepository categoryTranslationRepository) {
        this.userService = userService;
        this.categoryTranslationRepository = categoryTranslationRepository;
    }

    // ОНОВЛЕНО: Тепер меню будується динамічно з бази даних
    @ModelAttribute("allCategoriesMap")
    public Map<String, String> allCategoriesMap(HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/")) {
            return Collections.emptyMap(); //якщо це метод категорій)
        }
        return categoryTranslationRepository.findAll().stream()
                .collect(Collectors.toMap(
                        CategoryTranslation::getCategorySlug,
                        CategoryTranslation::getDisplayName
                ));
    }

    // ====================================================================================
    // Твій метод для користувача лишається АБСОЛЮТНО без змін
    // ====================================================================================
    @ModelAttribute("currentUserDisplayName")
    public String currentUserDisplayName(Authentication authentication, HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/")) {
            return null; // (або return java.util.Collections.emptyList(); якщо це метод категорій)
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof String principalName && "anonymousUser".equals(principalName)) {
            return null;
        }
        if (principal instanceof MyUserDetails userDetails) {
            return userDetails.getFirstName();
        }
        if (principal instanceof OAuth2Principal oauth2Principal) {
            return oauth2Principal.getFirstName();
        }
        if (principal instanceof OidcUser oidcUser) {
            String name = oidcUser.getFullName();
            if (oidcUser.getEmail() != null && !oidcUser.getEmail().isBlank()) {
                String persistedName = userService.findByEmail(oidcUser.getEmail())
                        .map(User::getName)
                        .orElse(null);
                if (persistedName != null && !persistedName.isBlank()) {
                    return persistedName;
                }
            }
            if (name != null && !name.isBlank()) {
                return name;
            }
            name = oidcUser.getGivenName();
            if (name != null && !name.isBlank()) {
                return name;
            }
            if (oidcUser.getEmail() != null && !oidcUser.getEmail().isBlank()) {
                return userService.findByEmail(oidcUser.getEmail())
                        .map(User::getName)
                        .orElse(oidcUser.getEmail());
            }
        }

        return userService.findByEmail(authentication.getName())
                .map(User::getName)
                .orElse(authentication.getName());
    }
}