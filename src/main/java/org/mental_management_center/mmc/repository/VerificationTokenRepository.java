package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    VerificationToken findByToken(String token);
    Optional<VerificationToken> findByUser(User user);
}
