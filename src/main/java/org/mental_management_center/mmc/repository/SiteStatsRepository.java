package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.SiteStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SiteStatsRepository
        extends JpaRepository<SiteStats, UUID> {}
