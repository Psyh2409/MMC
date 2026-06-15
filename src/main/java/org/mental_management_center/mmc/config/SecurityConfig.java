package org.mental_management_center.mmc.config;

import org.mental_management_center.mmc.service.CustomOAuth2UserService;
import org.mental_management_center.mmc.service.CustomOidcUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final Environment environment;
    private final SessionRegistry sessionRegistry;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          CustomOidcUserService customOidcUserService,
                          Environment environment, SessionRegistry sessionRegistry) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.customOidcUserService = customOidcUserService;
        this.environment = environment;
        this.sessionRegistry = sessionRegistry;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers("/requests/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/articles/**").authenticated()
                        .requestMatchers(
                                "/",
                                "/about",
                                "/issues/**", // Дозволяємо перегляд опису статті всім
                                "/contact",
                                "/first-steps",
                                "/forgot-password",
                                "/register",
                                "/registrationConfirm",
                                "/login",
                                "/oauth2/**",
                                "/reset-password",
                                "/login/oauth2/**",
                                "/css/**",
                                "/js/**",
                                "/format",
                                "/error",
                                "/test/**",
                                "/test-email",
                                "/images/**")
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/ws-chat/**")
                )
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())
                )
                .formLogin((form) -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout((logout) -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .sessionManagement(session -> session
                        .maximumSessions(10)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/login?expired=true")
                );

        if (isGoogleOAuthEnabled()) {
            http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login") // Використовувати ВАШУ сторінку логіну
                    .defaultSuccessUrl("/", true)
                    .userInfoEndpoint(userInfo -> userInfo
                            .userService(customOAuth2UserService)
                            .oidcUserService(customOidcUserService)
                    )
            );
        }
        return http.build();
    }

    private boolean isGoogleOAuthEnabled() {
        String clientId = environment.getProperty("app.oauth2.google.client-id");
        String clientSecret = environment.getProperty("app.oauth2.google.client-secret");
        return (hasText(clientId) && hasText(clientSecret));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}