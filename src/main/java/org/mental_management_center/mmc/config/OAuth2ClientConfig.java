package org.mental_management_center.mmc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OAuth2ClientConfig {

    @Bean
    @ConditionalOnExpression(
            "(!'${app.oauth2.google.client-id:}'.isBlank() and !'${app.oauth2.google.client-secret:}'.isBlank())")
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${app.oauth2.google.client-id:}") String googleClientId,
            @Value("${app.oauth2.google.client-secret:}") String googleClientSecret,
            @Value("${app.public-base-url:}") String publicBaseUrl) {
        List<ClientRegistration> registrations = new ArrayList<>();
        String redirectUri = buildRedirectUri(publicBaseUrl);

        if (hasText(googleClientId) && hasText(googleClientSecret)) {
            registrations.add(CommonOAuth2Provider.GOOGLE
                    .getBuilder("google")
                    .clientId(googleClientId)
                    .clientSecret(googleClientSecret)
                    .redirectUri(redirectUri)
                    .scope("openid", "profile", "email")
                    .build());
        }

        return new InMemoryClientRegistrationRepository(registrations);
    }

    @Bean
    @ConditionalOnExpression(
            "(!'${app.oauth2.google.client-id:}'.isBlank() and !'${app.oauth2.google.client-secret:}'.isBlank())")
    public OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String buildRedirectUri(String publicBaseUrl) {
        if (!hasText(publicBaseUrl)) {
            return "{baseUrl}/login/oauth2/code/{registrationId}";
        }
        return publicBaseUrl.replaceAll("/+$", "") + "/login/oauth2/code/{registrationId}";
    }
}
