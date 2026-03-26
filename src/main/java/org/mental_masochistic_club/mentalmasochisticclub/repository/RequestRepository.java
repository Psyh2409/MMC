package org.mental_masochistic_club.mentalmasochisticclub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.mental_masochistic_club.mentalmasochisticclub.model.Request;

public interface RequestRepository extends JpaRepository<Request, Long>{
}
