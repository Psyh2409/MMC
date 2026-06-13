package org.mental_management_center.mmc.service;

import org.mental_management_center.mmc.model.User;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserService userService;

    public CustomOAuth2UserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);

        String email = (String) oauth2User.getAttributes().get("email");
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("OAuth2 provider did not return an email address");
        }

        String name = (String) oauth2User.getAttributes().get("name");
        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        String providerId = oauth2User.getName();

        User user = userService.upsertOAuth2User(email, name, provider, providerId);
        if (!user.isEnabled()) {
            throw new OAuth2AuthenticationException("Будь ласка, активуйте ваш акаунт через посилання у листі.");
        }

        return new OAuth2Principal(
                user.getAuthorities(),
                oauth2User.getAttributes(),
                user.getEmail(),
                user.getName());
    }
}
