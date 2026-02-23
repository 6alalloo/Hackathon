package com.codewiki.service;

import com.codewiki.dto.ChatRequest;
import com.codewiki.dto.ChatResponse;
import com.codewiki.model.ChatMessage;
import com.codewiki.model.WikiSection;

import java.util.List;

/**
 * Service interface for chatbot functionality with RAG (Retrieval-Augmented Generation).
 * Handles question answering, context retrieval, and conversation history management.
 */
public interface ChatService {
    
    /**
     * Process a user question and generate a response using RAG.
     * 
     * @param wikiId The ID of the wiki to query
     * @param question The user's question
     * @return ChatResponse containing the answer with hyperlinks
     */
    ChatResponse askQuestion(String wikiId, String question);
    
    /**
     * Retrieve relevant wiki sections based on the question.
     * Uses keyword extraction and semantic search.
     * 
     * @param wikiId The ID of the wiki to search
     * @param question The user's question
     * @return List of top 3 most relevant wiki sections
     */
    List<WikiSection> retrieveRelevantSections(String wikiId, String question);
    
    /**
     * Generate a response using LLM with retrieved context and conversation history.
     * 
     * @param wikiId The ID of the wiki
     * @param question The user's question
     * @param context The retrieved context (wiki sections and code files)
     * @param history The conversation history (last 3 turns)
     * @return Generated response text
     */
    String generateResponse(String wikiId, String question, String context, List<ChatMessage> history);
    
    /**
     * Retrieve conversation history for a wiki.
     * 
     * @param wikiId The ID of the wiki
     * @return List of chat messages ordered by timestamp
     */
    List<ChatMessage> getConversationHistory(String wikiId);
}
