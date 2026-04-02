package org.mental_masochistic_club.mmc.config;

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
            "(!'${app.oauth2.google.client-id:}'.isBlank() and !'${app.oauth2.google.client-secret:}'.isBlank()) " +
            "or (!'${app.oauth2.facebook.client-id:}'.isBlank() and !'${app.oauth2.facebook.client-secret:}'.isBlank())")
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${app.oauth2.google.client-id:}") String googleClientId,
            @Value("${app.oauth2.google.client-secret:}") String googleClientSecret,
            @Value("${app.oauth2.facebook.client-id:}") String facebookClientId,
            @Value("${app.oauth2.facebook.client-secret:}") String facebookClientSecret) {
        List<ClientRegistration> registrations = new ArrayList<>();

        if (hasText(googleClientId) && hasText(googleClientSecret)) {
            registrations.add(CommonOAuth2Provider.GOOGLE
                    .getBuilder("google")
                    .clientId(googleClientId)
                    .clientSecret(googleClientSecret)
                    .scope("openid", "profile", "email")
                    .build());
        }

        if (hasText(facebookClientId) && hasText(facebookClientSecret)) {
            registrations.add(CommonOAuth2Provider.FACEBOOK
                    .getBuilder("facebook")
                    .clientId(facebookClientId)
                    .clientSecret(facebookClientSecret)
                    .scope("public_profile", "email")
                    .build());
        }

        return new InMemoryClientRegistrationRepository(registrations);
    }

    @Bean
    @ConditionalOnExpression(
            "(!'${app.oauth2.google.client-id:}'.isBlank() and !'${app.oauth2.google.client-secret:}'.isBlank()) " +
            "or (!'${app.oauth2.facebook.client-id:}'.isBlank() and !'${app.oauth2.facebook.client-secret:}'.isBlank())")
    public OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
