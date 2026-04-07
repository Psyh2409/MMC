package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.RoleBit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Стандартна назва для пошуку, яку ми використаємо в контролері
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Метод для перевірки, чи активний юзер (для Spring Security)
    Optional<User> findByEmailAndEnabledTrue(String email);

    Optional<User> findByPasswordResetToken(String passwordResetToken);

    @Query(value = "SELECT COUNT(*) FROM users WHERE (roles_mask & :mask) != 0", nativeQuery = true)
    long countByRole(@Param("mask") byte mask);

    default long countByRoleBit(RoleBit role) {
        return countByRole(role.getMask());
    }
}
