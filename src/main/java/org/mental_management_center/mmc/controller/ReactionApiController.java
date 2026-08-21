package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.dto.ReactionRequestDto;
import org.mental_management_center.mmc.dto.ReactionSummaryDto;
import org.mental_management_center.mmc.service.ReactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reactions")
@RequiredArgsConstructor
public class ReactionApiController {

    private final ReactionService reactionService;

    @PostMapping("/toggle")
    public ResponseEntity<Void> toggleReaction(@RequestBody ReactionRequestDto dto, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        reactionService.toggleReaction(principal.getName(), dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/summaries")
    public ResponseEntity<Map<UUID, ReactionSummaryDto>> getSummaries(@RequestBody List<UUID> targetIds, Principal principal) {
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(reactionService.getSummaries(targetIds, email));
    }
}