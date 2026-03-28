package org.mental_masochistic_club.mmc.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class SiteStats {
    @Id
    private Long id = 1L;
    private long guestVisits = 0;

    public SiteStats() {
    }
}
