package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.dto.ReactionRequestDto;
import org.mental_management_center.mmc.dto.ReactionSummaryDto;
import org.mental_management_center.mmc.event.ReactionCreatedEvent;
import org.mental_management_center.mmc.model.Reaction;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.ReactionRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void toggleReaction(String userEmail, ReactionRequestDto dto) {
        User user = userRepository.findByEmail(userEmail).orElseThrow();

        reactionRepository.findByUserIdAndTargetTypeAndTargetId(user.getId(), dto.targetType(), dto.targetId())
                .ifPresentOrElse(
                        existingReaction -> {
                            if (existingReaction.getReactionType() == dto.reactionType()) {
                                // Користувач зняв реакцію — видаляємо, подію не публікуємо
                                reactionRepository.delete(existingReaction);
                            } else {
                                // Зміна емоції
                                existingReaction.setReactionType(dto.reactionType());
                                reactionRepository.save(existingReaction);
                                publishEvent(user, dto);
                            }
                        },
                        () -> {
                            // Нова реакція
                            Reaction newReaction = Reaction.builder()
                                    .user(user)
                                    .targetType(dto.targetType())
                                    .targetId(dto.targetId())
                                    .reactionType(dto.reactionType())
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            reactionRepository.save(newReaction);
                            publishEvent(user, dto);
                        }
                );
    }

    private void publishEvent(User user, ReactionRequestDto dto) {
        ReactionCreatedEvent event = new ReactionCreatedEvent(
                user.getId(),
                user.getName(), // Або user.getFirstName(), залежно від вашої моделі
                dto.targetType(),
                dto.targetId(),
                dto.reactionType()
        );
        eventPublisher.publishEvent(event);
    }

    @Transactional(readOnly = true)
    public Map<UUID, ReactionSummaryDto> getSummaries(List<UUID> targetIds, String userEmail) {
        if (targetIds == null || targetIds.isEmpty()) return new HashMap<>();

        UUID currentUserId = null;
        if (userEmail != null) {
            currentUserId = userRepository.findByEmail(userEmail).map(User::getId).orElse(null);
        }

        // Робимо всього 1 запит до бази даних для всіх елементів на сторінці
        List<Reaction> reactions = reactionRepository.findByTargetIdIn(targetIds);
        Map<UUID, ReactionSummaryDto> result = new HashMap<>();
        final UUID uid = currentUserId;

        for (UUID targetId : targetIds) {
            List<Reaction> targetReactions = reactions.stream()
                    .filter(r -> r.getTargetId().equals(targetId))
                    .toList();

            // Підраховуємо лічильники для кожної емоції
            Map<String, Long> counts = targetReactions.stream()
                    .collect(Collectors.groupingBy(r -> r.getReactionType().name(), Collectors.counting()));

            // Визначаємо, чи ставив поточний користувач реакцію
            String userReaction = targetReactions.stream()
                    .filter(r -> uid != null && r.getUser().getId().equals(uid))
                    .map(r -> r.getReactionType().name())
                    .findFirst()
                    .orElse(null);

            result.put(targetId, new ReactionSummaryDto(counts, userReaction));
        }
        return result;
    }
}