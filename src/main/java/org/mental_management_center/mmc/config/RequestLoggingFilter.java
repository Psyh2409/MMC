package org.mental_management_center.mmc.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class RequestLoggingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String url = httpRequest.getRequestURI();

        // Виводимо в консоль кожний запит, який приходить на сервер
        if (!url.equals("/api/ping") && !url.equals("/")) {
            System.out.println(">>> REQUEST URL: " + url);
        }

        chain.doFilter(request, response);
    }
}