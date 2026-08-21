package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

    // Пошук існуючої реакції користувача на конкретний об'єкт
    Optional<Reaction> findByUserIdAndTargetTypeAndTargetId(
            UUID userId, Reaction.TargetType targetType, UUID targetId);

    // Отримання всіх реакцій для конкретного об'єкта (наприклад, статті або повідомлення чату)
    List<Reaction> findByTargetTypeAndTargetId(Reaction.TargetType targetType, UUID targetId);

    // Підрахунок кількості конкретного типу реакції для об'єкта
    long countByTargetTypeAndTargetIdAndReactionType(
            Reaction.TargetType targetType, UUID targetId, Reaction.ReactionType reactionType);

    // Додати до ReactionRepository.java
    List<Reaction> findByTargetIdIn(List<UUID> targetIds);
}