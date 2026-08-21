package org.mental_management_center.mmc.dto;

import java.util.Map;

// Зберігає загальну кількість емоцій та емоцію, яку поставив поточний користувач
public record ReactionSummaryDto(
        Map<String, Long> counts,
        String userReaction
) {}