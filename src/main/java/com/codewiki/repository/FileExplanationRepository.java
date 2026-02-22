package com.codewiki.repository;

import com.codewiki.model.FileExplanation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileExplanationRepository extends JpaRepository<FileExplanation, String> {
}
