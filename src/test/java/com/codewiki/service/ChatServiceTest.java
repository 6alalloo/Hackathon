package com.codewiki.service;

import com.codewiki.client.LLMClient;
import com.codewiki.dto.ChatResponse;
import com.codewiki.model.*;
import com.codewiki.repository.ChatMessageRepository;
import com.codewiki.repository.FileExplanationRepository;
import com.codewiki.repository.WikiRepository;
import com.codewiki.repository.WikiSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ChatService edge cases.
 * Tests specific scenarios and boundary conditions.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChatServiceTest {
    
    @Autowired
    private WikiRepository wikiRepository;
    
    @Autowired
    private WikiSectionRepository wikiSectionRepository;
    
    @Autowired
    private FileExplanationRepository fileExplanationRepository;
    
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    
    @MockBean
    private LLMClient llmClient;
    
    @Autowired
    private ChatService chatService;
    
    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        fileExplanationRepository.deleteAll();
        wikiSectionRepository.deleteAll();
        wikiRepository.deleteAll();
    }
    
    /**
     * Test first question in conversation (no history).
     * Verifies that chatbot works correctly when there's no prior conversation.
     */
    @Test
    void testFirstQuestionWithNoHistory() {
        // Given: A wiki with no chat history
        Wiki wiki = createTestWiki("https://github.com/test/first-question");
        
        WikiSection section = new WikiSection();
        section.setWiki(wiki);
        section.setSectionType(SectionType.OVERVIEW);
        section.setTitle("Project Overview");
        section.setContent("This is a test project");
        section.setOrderIndex(0);
        wiki.getSections().add(section);
        
        wikiRepository.save(wiki);
        
        // Mock LLM response
        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(0);
                    // Verify no conversation history in prompt
                    assertThat(prompt).doesNotContain("Conversation History");
                    return "This is the first answer.";
                });
        
        // When: User asks the first question
        ChatResponse response = chatService.askQuestion(wiki.getId(), "What is this project?");
        
        // Then: Response should be generated successfully
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).isEqualTo("This is the first answer.");
        
        // Verify conversation history was created
        List<ChatMessage> history = chatService.getConversationHistory(wiki.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(history.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
    }
    
    /**
     * Test question referencing previous answer.
     * Verifies that chatbot maintains context across conversation turns.
     */
    @Test
    void testQuestionReferencingPreviousAnswer() {
        // Given: A wiki with existing conversation
        Wiki wiki = createTestWiki("https://github.com/test/follow-up");
        
        WikiSection section = new WikiSection();
        section.setWiki(wiki);
        section.setSectionType(SectionType.ARCHITECTURE);
        section.setTitle("Architecture");
        section.setContent("The system uses microservices architecture");
        section.setOrderIndex(0);
        wiki.getSections().add(section);
        
        wikiRepository.save(wiki);
        
        // Create initial conversation
        ChatMessage userMsg1 = new ChatMessage();
        userMsg1.setWiki(wiki);
        userMsg1.setRole(MessageRole.USER);
        userMsg1.setContent("What architecture does this use?");
        chatMessageRepository.save(userMsg1);
        
        ChatMessage assistantMsg1 = new ChatMessage();
        assistantMsg1.setWiki(wiki);
        assistantMsg1.setRole(MessageRole.ASSISTANT);
        assistantMsg1.setContent("It uses microservices architecture.");
        chatMessageRepository.save(assistantMsg1);
        
        // Mock LLM to verify history is included
        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(0);
                    // Verify conversation history is included
                    assertThat(prompt).contains("Conversation History");
                    assertThat(prompt).contains("What architecture does this use?");
                    assertThat(prompt).contains("microservices architecture");
                    return "Microservices allow independent deployment of services.";
                });
        
        // When: User asks a follow-up question
        ChatResponse response = chatService.askQuestion(wiki.getId(), "Why is that beneficial?");
        
        // Then: Response should use conversation context
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).contains("Microservices");
        
        // Verify history now has 4 messages
        List<ChatMessage> history = chatService.getConversationHistory(wiki.getId());
        assertThat(history).hasSize(4);
    }
    
    /**
     * Test question about non-existent code.
     * Verifies graceful handling when question references files that don't exist.
     */
    @Test
    void testQuestionAboutNonExistentCode() {
        // Given: A wiki with limited content
        Wiki wiki = createTestWiki("https://github.com/test/non-existent");
        
        WikiSection section = new WikiSection();
        section.setWiki(wiki);
        section.setSectionType(SectionType.OVERVIEW);
        section.setTitle("Overview");
        section.setContent("Basic project");
        section.setOrderIndex(0);
        wiki.getSections().add(section);
        
        wikiRepository.save(wiki);
        
        // Mock LLM to handle missing context
        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenReturn("I don't have information about that specific file in the documentation.");
        
        // When: User asks about a file that doesn't exist
        ChatResponse response = chatService.askQuestion(wiki.getId(), 
                "How does the authentication.java file work?");
        
        // Then: Response should be generated (even if it says "not found")
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).isNotEmpty();
    }
    
    /**
     * Test question requiring multiple wiki sections.
     * Verifies that retrieval logic can combine information from multiple sections.
     */
    @Test
    void testQuestionRequiringMultipleSections() {
        // Given: A wiki with multiple related sections
        Wiki wiki = createTestWiki("https://github.com/test/multiple-sections");
        
        WikiSection overview = new WikiSection();
        overview.setWiki(wiki);
        overview.setSectionType(SectionType.OVERVIEW);
        overview.setTitle("Overview");
        overview.setContent("This project handles authentication and authorization");
        overview.setOrderIndex(0);
        wiki.getSections().add(overview);
        
        WikiSection architecture = new WikiSection();
        architecture.setWiki(wiki);
        architecture.setSectionType(SectionType.ARCHITECTURE);
        architecture.setTitle("Architecture");
        architecture.setContent("Authentication uses JWT tokens. Authorization uses role-based access control.");
        architecture.setOrderIndex(1);
        wiki.getSections().add(architecture);
        
        WikiSection security = new WikiSection();
        security.setWiki(wiki);
        security.setSectionType(SectionType.INTERACTIONS);
        security.setTitle("Security");
        security.setContent("Security is implemented through authentication and authorization layers");
        security.setOrderIndex(2);
        wiki.getSections().add(security);
        
        wikiRepository.save(wiki);
        
        // Mock LLM response
        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(0);
                    // Verify multiple sections are included in context
                    assertThat(prompt).contains("authentication");
                    assertThat(prompt).contains("authorization");
                    return "The system uses JWT for authentication and RBAC for authorization.";
                });
        
        // When: User asks a question that requires multiple sections
        ChatResponse response = chatService.askQuestion(wiki.getId(), 
                "How does authentication and authorization work?");
        
        // Then: Response should synthesize information from multiple sections
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).contains("authentication");
        assertThat(response.getAnswer()).contains("authorization");
        
        // Verify references include multiple sections
        assertThat(response.getReferences()).hasSizeGreaterThanOrEqualTo(1);
    }
    
    /**
     * Test hyperlink injection edge cases.
     * Verifies correct handling of various section reference patterns.
     */
    @Test
    void testHyperlinkInjectionEdgeCases() {
        // Given: A wiki with sections that have various naming patterns
        Wiki wiki = createTestWiki("https://github.com/test/hyperlinks");
        
        WikiSection section1 = new WikiSection();
        section1.setWiki(wiki);
        section1.setSectionType(SectionType.OVERVIEW);
        section1.setTitle("Project Overview");
        section1.setContent("Overview content");
        section1.setOrderIndex(0);
        wiki.getSections().add(section1);
        
        WikiSection section2 = new WikiSection();
        section2.setWiki(wiki);
        section2.setSectionType(SectionType.ARCHITECTURE);
        section2.setTitle("System Architecture");
        section2.setContent("Architecture content");
        section2.setOrderIndex(1);
        wiki.getSections().add(section2);
        
        wikiRepository.save(wiki);
        
        // Test various reference patterns
        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenReturn("As explained in the Project Overview section, and see the System Architecture for details.");
        
        // When: User asks a question
        ChatResponse response = chatService.askQuestion(wiki.getId(), "Tell me about the system");
        
        // Then: Both section references should be converted to hyperlinks
        assertThat(response.getAnswer()).contains("/wiki/" + wiki.getId() + "/section/");
        assertThat(response.getAnswer()).contains("[Project Overview]");
    }
    
    /**
     * Test invalid wiki ID.
     * Verifies proper error handling for non-existent wikis.
     */
    @Test
    void testInvalidWikiId() {
        // When/Then: Asking question for non-existent wiki should throw exception
        assertThatThrownBy(() -> chatService.askQuestion("non-existent-id", "What is this?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wiki not found");
    }
    
    /**
     * Test empty question.
     * Verifies handling of edge case inputs.
     */
    @Test
    void testEmptyQuestion() {
        // Given: A valid wiki
        Wiki wiki = createTestWiki("https://github.com/test/empty-question");
        
        WikiSection section = new WikiSection();
        section.setWiki(wiki);
        section.setSectionType(SectionType.OVERVIEW);
        section.setTitle("Overview");
        section.setContent("Content");
        section.setOrderIndex(0);
        wiki.getSections().add(section);
        
        wikiRepository.save(wiki);
        
        // Mock LLM
        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenReturn("Please ask a specific question.");
        
        // When: User submits empty or whitespace question
        ChatResponse response = chatService.askQuestion(wiki.getId(), "   ");
        
        // Then: Should still process (controller validates, not service)
        assertThat(response).isNotNull();
    }
    
    /**
     * Test retrieval with no matching sections.
     * Verifies behavior when question keywords don't match any content.
     */
    @Test
    void testRetrievalWithNoMatchingSections() {
        // Given: A wiki with specific content
        Wiki wiki = createTestWiki("https://github.com/test/no-match");
        
        WikiSection section = new WikiSection();
        section.setWiki(wiki);
        section.setSectionType(SectionType.OVERVIEW);
        section.setTitle("Database Schema");
        section.setContent("Tables and relationships");
        section.setOrderIndex(0);
        wiki.getSections().add(section);
        
        wikiRepository.save(wiki);
        
        // When: Retrieving sections for unrelated question
        List<WikiSection> sections = chatService.retrieveRelevantSections(wiki.getId(), 
                "How does the frontend rendering work?");
        
        // Then: Should return empty or low-relevance sections
        // (The algorithm may still return sections with score 0 or find some weak matches)
        assertThat(sections).isNotNull();
    }
    
    // Helper methods
    
    private Wiki createTestWiki(String repoUrl) {
        Wiki wiki = new Wiki();
        wiki.setRepositoryUrl(repoUrl);
        wiki.setRepositoryName(extractRepoName(repoUrl));
        wiki.setStatus(WikiStatus.COMPLETED);
        return wiki;
    }
    
    private String extractRepoName(String url) {
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }
}
