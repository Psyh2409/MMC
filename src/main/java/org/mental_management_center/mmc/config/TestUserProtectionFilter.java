package org.mental_management_center.mmc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TestUserProtectionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Перевіряємо, чи користувач авторизований і чи має він роль TEST
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_TEST"))) {
            String method = request.getMethod();
            String path = request.getRequestURI();

            // Дозволяємо лише безпечні GET-запити та вихід з акаунту
            if (!method.equals("GET") && !path.startsWith("/logout")) {
                // Жорстко блокуємо запит на рівні фільтра (403 Forbidden)
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Тестовий акаунт має права лише на перегляд. Зміна даних заборонена.");
                return;
            }
        }

        // Якщо все добре, передаємо запит далі по ланцюжку
        filterChain.doFilter(request, response);
    }
}