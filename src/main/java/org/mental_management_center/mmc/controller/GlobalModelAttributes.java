package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.MyUserDetails;
import org.mental_management_center.mmc.service.OAuth2Principal;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    private final UserService userService;

    public GlobalModelAttributes(UserService userService) {
        this.userService = userService;
    }

    @ModelAttribute("currentUserDisplayName")
    public String currentUserDisplayName(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof String principalName && "anonymousUser".equals(principalName)) {
            return null;
        }
        if (principal instanceof MyUserDetails userDetails) {
            return userDetails.getFirstName();
        }
        if (principal instanceof OAuth2Principal oauth2Principal) {
            return oauth2Principal.getFirstName();
        }
        if (principal instanceof OidcUser oidcUser) {
            String name = oidcUser.getFullName();
            if (oidcUser.getEmail() != null && !oidcUser.getEmail().isBlank()) {
                String persistedName = userService.findByEmail(oidcUser.getEmail())
                        .map(User::getName)
                        .orElse(null);
                if (persistedName != null && !persistedName.isBlank()) {
                    return persistedName;
                }
            }
            if (name != null && !name.isBlank()) {
                return name;
            }
            name = oidcUser.getGivenName();
            if (name != null && !name.isBlank()) {
                return name;
            }
            if (oidcUser.getEmail() != null && !oidcUser.getEmail().isBlank()) {
                return userService.findByEmail(oidcUser.getEmail())
                        .map(User::getName)
                        .orElse(oidcUser.getEmail());
            }
        }

        return userService.findByEmail(authentication.getName())
                .map(User::getName)
                .orElse(authentication.getName());
    }
}
