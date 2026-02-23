package com.codewiki.service;

import com.codewiki.client.LLMClient;
import com.codewiki.dto.ChatResponse;
import com.codewiki.model.ChatMessage;
import com.codewiki.model.FileExplanation;
import com.codewiki.model.MessageRole;
import com.codewiki.model.SectionType;
import com.codewiki.model.Wiki;
import com.codewiki.model.WikiSection;
import com.codewiki.model.WikiStatus;
import com.codewiki.repository.ChatMessageRepository;
import com.codewiki.repository.FileExplanationRepository;
import com.codewiki.repository.WikiRepository;
import com.codewiki.repository.WikiSectionRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.NotEmpty;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServicePropertyTest {

    private WikiRepository wikiRepository;
    private WikiSectionRepository wikiSectionRepository;
    private FileExplanationRepository fileExplanationRepository;
    private ChatMessageRepository chatMessageRepository;
    private LLMClient llmClient;
    private ChatService chatService;

    private Map<String, Wiki> wikiStore;
    private List<ChatMessage> messageStore;

    @BeforeTry
    void setUp() {
        wikiRepository = mock(WikiRepository.class);
        wikiSectionRepository = mock(WikiSectionRepository.class);
        fileExplanationRepository = mock(FileExplanationRepository.class);
        chatMessageRepository = mock(ChatMessageRepository.class);
        llmClient = mock(LLMClient.class);

        wikiStore = new HashMap<>();
        messageStore = new ArrayList<>();

        when(wikiRepository.findById(anyString())).thenAnswer(inv -> {
            String wikiId = inv.getArgument(0);
            return Optional.ofNullable(wikiStore.get(wikiId));
        });
        when(wikiRepository.save(any(Wiki.class))).thenAnswer(inv -> {
            Wiki wiki = inv.getArgument(0);
            wikiStore.put(wiki.getId(), wiki);
            return wiki;
        });
        when(wikiSectionRepository.findByWikiId(anyString())).thenAnswer(inv -> {
            Wiki wiki = wikiStore.get(inv.getArgument(0));
            return wiki == null ? List.of() : wiki.getSections();
        });
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            messageStore.add(msg);
            return msg;
        });
        when(chatMessageRepository.findByWikiIdOrderByCreatedAt(anyString())).thenAnswer(inv -> {
            String wikiId = inv.getArgument(0);
            return messageStore.stream()
                    .filter(m -> m.getWiki() != null && wikiId.equals(m.getWiki().getId()))
                    .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                    .toList();
        });

        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenReturn("As explained in the Overview section, this is the answer.");

        chatService = new ChatServiceImpl(
                wikiRepository,
                wikiSectionRepository,
                fileExplanationRepository,
                chatMessageRepository,
                llmClient
        );
    }

    @Property(tries = 100)
    void chatbotUsesRAGContext(
            @ForAll @NotEmpty String question,
            @ForAll @Size(min = 1, max = 5) List<@NotEmpty String> sectionTitles) {
        Wiki wiki = createWikiWithSections("https://github.com/test/repo", sectionTitles);
        wikiStore.put(wiki.getId(), wiki);

        ChatResponse response = chatService.askQuestion(wiki.getId(), question);
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).isNotBlank();
        assertThat(chatService.getConversationHistory(wiki.getId())).hasSize(2);
    }

    @Property(tries = 100)
    void chatbotMaintainsConversationContext(@ForAll @Size(min = 2, max = 5) List<@NotEmpty String> questions) {
        Wiki wiki = createWikiWithSections("https://github.com/test/context", List.of("Overview", "Architecture"));
        wikiStore.put(wiki.getId(), wiki);

        for (String question : questions) {
            chatService.askQuestion(wiki.getId(), question);
        }

        List<ChatMessage> history = chatService.getConversationHistory(wiki.getId());
        assertThat(history).hasSize(questions.size() * 2);
        for (int i = 0; i < history.size(); i++) {
            MessageRole expectedRole = (i % 2 == 0) ? MessageRole.USER : MessageRole.ASSISTANT;
            assertThat(history.get(i).getRole()).isEqualTo(expectedRole);
        }
    }

    @Property(tries = 100)
    void chatbotHyperlinksAreValid(
            @ForAll("sectionTitles") String sectionTitle,
            @ForAll @NotEmpty String question) {
        Wiki wiki = createWikiWithSections("https://github.com/test/hyperlinks", List.of(sectionTitle));
        wikiStore.put(wiki.getId(), wiki);

        when(llmClient.generateWithRetry(anyString(), anyInt()))
                .thenReturn("As explained in the " + sectionTitle + " section, details are available.");

        ChatResponse response = chatService.askQuestion(wiki.getId(), question);
        assertThat(response.getAnswer()).contains("/wiki/" + wiki.getId() + "/section/");
    }

    @Provide
    Arbitrary<String> sectionTitles() {
        return Arbitraries.of("Overview", "Architecture", "Project Overview", "System Architecture");
    }

    private Wiki createWikiWithSections(String repoUrl, List<String> titles) {
        Wiki wiki = new Wiki();
        wiki.setRepositoryUrl(repoUrl);
        wiki.setRepositoryName("test/repo");
        wiki.setStatus(WikiStatus.COMPLETED);

        for (int i = 0; i < titles.size(); i++) {
            WikiSection section = new WikiSection();
            section.setWiki(wiki);
            section.setSectionType(SectionType.OVERVIEW);
            section.setTitle(titles.get(i));
            section.setContent("Content for " + titles.get(i));
            section.setOrderIndex(i);
            wiki.getSections().add(section);
        }

        FileExplanation file = new FileExplanation();
        file.setWiki(wiki);
        file.setFilePath("src/main/Main.java");
        file.setLanguage("Java");
        file.setExplanation("Main entry point");
        wiki.getFileExplanations().add(file);

        return wiki;
    }
}
