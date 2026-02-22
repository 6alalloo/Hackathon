package com.codewiki.repository;

import com.codewiki.model.WikiSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WikiSectionRepository extends JpaRepository<WikiSection, String> {
}
