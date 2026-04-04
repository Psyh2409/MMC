package org.mental_management_center.mmc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.mental_management_center.mmc.model.Request;

public interface RequestRepository extends JpaRepository<Request, Long>{
}
