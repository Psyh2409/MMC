package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByPasswordResetToken(String token);

    // Спеціальний запит для підрахунку ролей через бітову маску
    @Query("SELECT COUNT(u) FROM User u WHERE BITAND(u.rolesMask, :mask) != 0")
    long countByRoleMask(@Param("mask") int mask);
}