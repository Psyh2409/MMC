package org.mental_management_center.mmc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ZombieSessionFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public ZombieSessionFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Ігноруємо статику, щоб не робити зайвих запитів у БД при завантаженні картинок чи CSS
        if (path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {

            String email = authentication.getName();

            // Якщо сесія є, але юзера фізично немає в базі
            if (userRepository.findByEmail(email).isEmpty()) {
                request.getSession().invalidate(); // Знищуємо зомбі-сесію
                SecurityContextHolder.clearContext(); // Очищаємо контекст безпеки
                response.sendRedirect("/login?deleted=true"); // Редирект на вхід
                return; // Жорстко перериваємо ланцюжок, контролери не виконуються
            }
        }

        // Якщо все ок - пропускаємо запит далі
        filterChain.doFilter(request, response);
    }
}