package org.mental_masochistic_club.mmc.repository;

import org.mental_masochistic_club.mmc.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findUserByEmail(String email);

    boolean existsByEmail(String email);

}
