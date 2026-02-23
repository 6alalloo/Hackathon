package com.codewiki.controller;

import com.codewiki.dto.ChatRequest;
import com.codewiki.dto.ChatMessageDTO;
import com.codewiki.dto.ChatResponse;
import com.codewiki.model.ChatMessage;
import com.codewiki.service.ChatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

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
            @Valid @RequestBody ChatRequest request) {
        
        logger.info("Received chat question for wiki {}: {}", wikiId, request.getQuestion());
        try {
            ChatResponse response = chatService.askQuestion(wikiId, request.getQuestion());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid chat request for wiki {}: {}", wikiId, e.getMessage());
            return ResponseEntity.notFound().build();
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
    public ResponseEntity<List<ChatMessageDTO>> getHistory(@PathVariable String wikiId) {
        logger.debug("Retrieving chat history for wiki {}", wikiId);

        try {
            List<ChatMessageDTO> history = chatService.getConversationHistory(wikiId).stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(history);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid history request for wiki {}: {}", wikiId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private ChatMessageDTO toDto(ChatMessage message) {
        return new ChatMessageDTO(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
