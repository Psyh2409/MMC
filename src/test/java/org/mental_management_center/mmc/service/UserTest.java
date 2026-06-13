package org.mental_management_center.mmc.service;

import org.junit.jupiter.api.Test;
import org.mental_management_center.mmc.model.RoleBit;
import org.mental_management_center.mmc.model.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void testRoleBitOperations() {
        User user = new User();
        user.setRolesMask(0); // Починаємо з порожньої маски

        // Додаємо роль READER (2)
        user.addRole(RoleBit.READER);
        assertTrue(user.hasRole(RoleBit.READER));
        assertEquals(2, user.getRolesMask());

        // Додаємо роль ADMIN (16)
        user.addRole(RoleBit.ADMIN);
        assertTrue(user.hasRole(RoleBit.ADMIN));
        assertTrue(user.hasRole(RoleBit.READER));
        assertEquals(18, user.getRolesMask());

        // Видаляємо READER
        user.removeRole(RoleBit.READER);
        assertFalse(user.hasRole(RoleBit.READER));
        assertTrue(user.hasRole(RoleBit.ADMIN));
        assertEquals(16, user.getRolesMask());
    }

    @Test
    void testGetAuthorities() {
        User user = new User();
        user.setRolesMask(RoleBit.CLIENT.getMask() | RoleBit.ADMIN.getMask());

        Collection<SimpleGrantedAuthority> authorities = user.getAuthorities();

        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_CLIENT")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertFalse(authorities.contains(new SimpleGrantedAuthority("ROLE_READER")));
        assertEquals(2, authorities.size());
    }
}
