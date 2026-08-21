package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false)
    private ReactionType reactionType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public enum TargetType {
        ARTICLE,
        ARTICLE_COMMENT,
        PUBLIC_WALL_POST,
        PUBLIC_WALL_COMMENT,
        ROOM_WALL_POST,
        ROOM_WALL_COMMENT,
        CHAT_MESSAGE
    }

    public enum ReactionType {
        SUPPORT,   // 🫂
        EMPATHY,   // 💛
        INSIGHT,   // 💡
        GRATITUDE, // 🙏
        AGREE      // 👍
    }
}