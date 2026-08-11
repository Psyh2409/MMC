package org.mental_management_center.mmc.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class SharedWallCommentDto {
    private UUID id;
    private UUID wallEntryId;
    private UUID parentId;
    private UUID authorId;
    private String authorName;
    private String content;
    private LocalDateTime createdAt;
    private boolean isMine;

    // 🟢 Додаємо список для дочірніх коментарів (дерево)
    private List<SharedWallCommentDto> replies = new ArrayList<>();
}