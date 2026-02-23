package com.codewiki.service;

public interface WikiGenerationOrchestratorService {
    void generateWikiAsync(String wikiId, String repositoryUrl);
}
