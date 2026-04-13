package org.mental_management_center.mmc.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "category_translations")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryTranslation {

    @Id
    @Column(name = "category_slug", nullable = false, unique = true)
    private String categorySlug; // англійська версія (slug), наприклад: "inner-calm"

    @Column(name = "display_name", nullable = false)
    private String displayName; // українська версія, наприклад: "Тривога та панічні стани"
}