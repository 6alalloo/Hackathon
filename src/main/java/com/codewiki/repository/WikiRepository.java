package com.codewiki.repository;

import com.codewiki.model.Wiki;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WikiRepository extends JpaRepository<Wiki, String> {
    
    Optional<Wiki> findByRepositoryUrl(String repositoryUrl);
}
