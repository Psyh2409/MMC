package org.mental_management_center.mmc.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mental_management_center.mmc.model.enums.RoleBit;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.Principal;

@Component
public class TestUserProtectionInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public TestUserProtectionInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Нас цікавлять тільки методи, які щось створюють, змінюють або видаляють
        String method = request.getMethod();
        if (method.equalsIgnoreCase("POST") ||
                method.equalsIgnoreCase("PUT") ||
                method.equalsIgnoreCase("DELETE")) {

            Principal principal = request.getUserPrincipal();
            if (principal != null) {
                User user = userService.findByEmail(principal.getName()).orElse(null);

                // ОСЬ ТВОЯ ГЛОБАЛЬНА ПЕРЕВІРКА НА 128 БІТ
                if (user != null && user.hasRole(RoleBit.TEST)) {
                    throw new AccessDeniedException("Тестовим користувачам заборонено змінювати дані в системі.");
                }
            }
        }
        // GET-запити (перегляд сторінок) та реальні юзери проходять без перешкод
        return true;
    }
}