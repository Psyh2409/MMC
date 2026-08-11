package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shared_wall_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SharedWallComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wall_entry_id", nullable = false)
    private SharedWallEntry wallEntry;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Lob
    @Column(name = "encrypted_content", nullable = false)
    private byte[] encryptedContent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "parent_id")
    private UUID parentId;
}