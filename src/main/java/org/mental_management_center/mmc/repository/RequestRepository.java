package org.mental_management_center.mmc.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.mental_management_center.mmc.model.Request;

import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long>{

    @EntityGraph(attributePaths = "user")
    List<Request> findAllByOrderByCreatedAtDesc();
}
