package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.SiteStats;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteStatsRepository
        extends JpaRepository<SiteStats, Long> {}
