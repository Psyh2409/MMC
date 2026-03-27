package org.mental_masochistic_club.mmc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.mental_masochistic_club.mmc.model.Request;

public interface RequestRepository extends JpaRepository<Request, Long>{
}
