package org.mental_masochistic_club.mmc.service;

import org.mental_masochistic_club.mmc.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class MyUserDetails implements UserDetails {

    private final String email;
    private final String password;
    private final String firstName; // Ось воно! Наше ім'я
    private final Collection<? extends GrantedAuthority> authorities;

    public MyUserDetails(
            User user,
            Collection<? extends GrantedAuthority> authorities) {
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.firstName = user.getName();
        this.authorities = authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
