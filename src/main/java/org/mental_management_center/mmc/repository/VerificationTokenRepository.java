package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    VerificationToken findByToken(String token);
    Optional<VerificationToken> findByUser(User user);
    void deleteByUser(User user);
}