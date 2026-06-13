package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.CategoryTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CategoryTranslationRepository extends JpaRepository<CategoryTranslation, String> {
    Optional<CategoryTranslation> findByDisplayName(String displayName);
}