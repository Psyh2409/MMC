package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.Role;
import org.mental_management_center.mmc.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findUserByEmail(String email);

    long countByRolesContaining(Role role);

    boolean existsByEmail(String email);

}
