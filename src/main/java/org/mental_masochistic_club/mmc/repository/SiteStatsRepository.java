package org.mental_masochistic_club.mmc.repository;

import org.mental_masochistic_club.mmc.model.SiteStats;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteStatsRepository
        extends JpaRepository<SiteStats, Long> {}
