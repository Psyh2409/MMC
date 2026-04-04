package org.mental_management_center.mmc.service;

import org.mental_management_center.mmc.model.User;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class CustomOidcUserService extends OidcUserService {

    private final UserService userService;

    public CustomOidcUserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("OIDC provider did not return an email address");
        }

        String name = oidcUser.getFullName();
        if (name == null || name.isBlank()) {
            name = oidcUser.getGivenName();
        }

        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        String providerId = oidcUser.getSubject();

        User user = userService.upsertOAuth2User(email, name, provider, providerId);

        return new DefaultOidcUser(
                user.getAuthorities(),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "email"
        );
    }
}
