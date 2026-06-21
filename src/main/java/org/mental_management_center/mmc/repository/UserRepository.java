package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    // ПЕРЕВИЗНАЧЕННЯ: findAll() заборонено для безпеки (змішування тестових/реальних користувачів)
    @Query("SELECT u FROM User u WHERE 1=0") // Завжди повертає порожній список
    @Override
    List<User> findAll();

    // ПЕРЕВИЗНАЧЕННЯ: findAllById() заборонено для безпеки
    @Query("SELECT u FROM User u WHERE 1=0")
    @Override
    List<User> findAllById(Iterable<UUID> ids);

    // ПЕРЕВИЗНАЧЕННЯ: findAll(Sort) заборонено для безпеки
    @Query("SELECT u FROM User u WHERE 1=0")
    @Override
    List<User> findAll(org.springframework.data.domain.Sort sort);

    // ПЕРЕВИЗНАЧЕННЯ: findAll(Pageable) заборонено для безпеки
    @Query("SELECT u FROM User u WHERE 1=0")
    @Override
    org.springframework.data.domain.Page<User> findAll(org.springframework.data.domain.Pageable pageable);

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByPasswordResetToken(String token);

    // Спеціальний запит для підрахунку ролей через бітову маску
    @Query(value = "SELECT COUNT(*) FROM users WHERE (roles_mask & :mask) != 0", nativeQuery = true)
    long countByRoleMask(@Param("mask") int mask);

    // Отримати тільки реальних користувачів (де біт 128 НЕ встановлений)
    @Query(value = "SELECT * FROM users WHERE (roles_mask & 128) = 0", nativeQuery = true)
    List<User> findRealUsers();

    // Отримати тільки тестових користувачів (де біт 128 ВСТАНОВЛЕНИЙ)
    @Query(value = "SELECT * FROM users WHERE (roles_mask & 128) != 0", nativeQuery = true)
    List<User> findTestUsers();
}