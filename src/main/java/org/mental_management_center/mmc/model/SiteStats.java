package org.mental_management_center.mmc.model;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class SiteStats {
    // ID вже фіксований, тому @UuidGenerator не потрібен
    @Id
    private UUID id = UUID.fromString(
        "00000000-0000-0000-0000-000000000001");
    private long guestVisits = 0;

    public SiteStats() {
    }
}
