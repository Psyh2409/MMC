package org.mental_management_center.mmc.event;

import org.mental_management_center.mmc.model.Reaction;
import java.util.UUID;

public record ReactionCreatedEvent(
        UUID actorId,            // Хто поставив реакцію
        String actorName,        // Ім'я для тексту сповіщення
        Reaction.TargetType targetType, // Де поставили (стаття, коментар)
        UUID targetId,           // ID статті або коментаря
        Reaction.ReactionType reactionType // Тип емоції (SUPPORT, INSIGHT тощо)
) {}