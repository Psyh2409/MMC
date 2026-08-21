package org.mental_management_center.mmc.dto;

import org.mental_management_center.mmc.model.Reaction;
import java.util.UUID;

public record ReactionRequestDto(
        Reaction.TargetType targetType,
        UUID targetId,
        Reaction.ReactionType reactionType
) {}