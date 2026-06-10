package org.mental_management_center.mmc.service;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.mental_management_center.mmc.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Objects; // <-- Додано цей імпорт

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MyUserDetails implements UserDetails {

    @Getter
    private final User user; // Твоя сутність з бази

    public MyUserDetails(User user) {
        this.user = user;
    }

    // Ми просто просимо твій клас User віддати список прав (авторитетів)
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getAuthorities();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @EqualsAndHashCode.Include
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    public String getFirstName() {
        return user.getName();
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
        return user.isEnabled(); // <-- Спрінг дивиться сюди, щоб знати, чи юзер не забанений
    }
}