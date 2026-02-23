package com.codewiki.repository;

import com.codewiki.model.GenerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GenerationStatusRepository extends JpaRepository<GenerationStatus, String> {
    Optional<GenerationStatus> findTopByWikiIdOrderByUpdatedAtDesc(String wikiId);
}
