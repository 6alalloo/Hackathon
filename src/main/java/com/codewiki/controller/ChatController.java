package com.codewiki.controller;

import com.codewiki.dto.ChatRequest;
import com.codewiki.dto.ChatResponse;
import com.codewiki.model.ChatMessage;
import com.codewiki.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for chatbot functionality.
 * Provides endpoints for asking questions and retrieving conversation history.
 */
@RestController
@RequestMapping("/api/wikis/{wikiId}/chat")
public class ChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    private final ChatService chatService;
    
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }
    
    /**
     * Submit a question to the chatbot and receive an answer.
     * 
     * POST /api/wikis/{wikiId}/chat
     * 
     * @param wikiId The ID of the wiki to query
     * @param request The chat request containing the question
     * @return ChatResponse with answer and references
     */
    @PostMapping
    public ResponseEntity<ChatResponse> askQuestion(
            @PathVariable String wikiId,
            @RequestBody ChatRequest request) {
        
        logger.info("Received chat question for wiki {}: {}", wikiId, request.getQuestion());
        
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            ChatResponse response = chatService.askQuestion(wikiId, request.getQuestion());
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid wiki ID: {}", wikiId, e);
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            logger.error("Error processing chat question for wiki {}", wikiId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Retrieve conversation history for a wiki.
     * 
     * GET /api/wikis/{wikiId}/chat/history
     * 
     * @param wikiId The ID of the wiki
     * @return List of chat messages ordered by timestamp
     */
    @GetMapping("/history")
    public ResponseEntity<List<ChatMessage>> getHistory(@PathVariable String wikiId) {
        logger.debug("Retrieving chat history for wiki {}", wikiId);
        
        try {
            List<ChatMessage> history = chatService.getConversationHistory(wikiId);
            return ResponseEntity.ok(history);
            
        } catch (Exception e) {
            logger.error("Error retrieving chat history for wiki {}", wikiId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
