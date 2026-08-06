package org.mental_management_center.mmc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TestUserProtectionInterceptor testUserInterceptor;

    public WebConfig(TestUserProtectionInterceptor testUserInterceptor) {
        this.testUserInterceptor = testUserInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Підключаємо фільтр для всіх маршрутів
        registry.addInterceptor(testUserInterceptor)
                .addPathPatterns("/**") // Працює для всього сайту
                .excludePathPatterns("/login", "/register", "/css/**", "/js/**"); // Крім базових речей
    }
}