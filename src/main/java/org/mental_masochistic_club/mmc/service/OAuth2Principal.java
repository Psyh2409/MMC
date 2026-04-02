package org.mental_masochistic_club.mmc.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

public class OAuth2Principal implements OAuth2User {

    private final Collection<? extends GrantedAuthority> authorities;
    private final Map<String, Object> attributes;
    private final String email;
    private final String firstName;

    public OAuth2Principal(
            Collection<? extends GrantedAuthority> authorities,
            Map<String, Object> attributes,
            String email,
            String firstName) {
        this.authorities = authorities;
        this.attributes = attributes;
        this.email = email;
        this.firstName = firstName;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }
}
