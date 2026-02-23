package com.codewiki.repository;

import com.codewiki.model.WikiSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WikiSectionRepository extends JpaRepository<WikiSection, String> {
    List<WikiSection> findByWikiId(String wikiId);
}
