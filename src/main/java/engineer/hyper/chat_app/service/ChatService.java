package engineer.hyper.chat_app.service;

import engineer.hyper.chat_app.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ChatService {
    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_PROMPT = """
            You are a playful and helpful conversation assistant.
            You must follow all of the following rules strictly:
            1.  First, think about a step-by-step plan to answer the user's question.
            2.  Your final response must be structured in three distinct parts, in the exact following order, using the specified markdown headers.
            3.  The parts are: '### Plan', '### Answer', and '### Suggestions'.
            4.  Under '### Plan', provide your step-by-step plan as a numbered list.
            5.  Under '### Answer', provide the detailed answer to the user's question.
            6.  Under '### Suggestions', provide a numbered list of three relevant follow-up questions.
            7.  Do not add any other headers or sections.
            """;

    public ChatService(ChatModel chatModel, ChatMemory chatMemory) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
    }

    public Flux<ChatResponse> streamAnswer(String conversationId, String question) {
        List<Message> history = chatMemory.get(conversationId);
        List<Message> messages = new ArrayList<>(history);
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        UserMessage userMessage = new UserMessage(question);
        messages.add(userMessage);

        Prompt prompt = new Prompt(messages);

        Flux<String> sharedFlux = chatModel.stream(prompt)
                .map(response -> response.getResult().getOutput().getText())
                .share();

        Flux<ChatResponse> intermediateAnswerChunks = sharedFlux
                .skipUntil(text -> text.contains("### Answer"))
                .takeUntil(text -> text.contains("### Suggestions"))
                .map(text -> text.replace("### Answer", "").replaceAll("### Suggestions.*", ""))
                .filter(text -> !text.isBlank())
                .delayElements(Duration.ofMillis(50))
                .map(answerText -> ChatResponse.builder().answer(answerText).build());

        Mono<ChatResponse> finalStructuredChunk = sharedFlux
                .collect(Collectors.joining())
                .doOnSuccess(fullContent -> {
                    if (!fullContent.isBlank()) {
                        AssistantMessage assistantMessage = new AssistantMessage(fullContent);
                        chatMemory.add(conversationId, List.of(userMessage, assistantMessage));
                        log.info("Saved conversation history for ID: {}", conversationId);
                    }
                })
                .map(fullContent -> {
                    List<String> plan = parseListSection(fullContent, "### Plan", "### Answer");
                    List<String> suggestions = parseListSection(fullContent, "### Suggestions", null);
                    return ChatResponse.builder()
                            .plan(plan)
                            .suggestions(suggestions)
                            .finalChunk(true)
                            .build();
                });

        AtomicInteger orderIdCounter = new AtomicInteger(0);

        return Flux.concat(intermediateAnswerChunks, finalStructuredChunk.flux())
                .map(response -> {
                    response.setOrderId(orderIdCounter.incrementAndGet());
                    response.setQuestion(question);
                    response.setConversationId(conversationId);
                    return response;
                });
    }

    private List<String> parseListSection(String content, String startHeader, String endHeader) {
        try {
            int startIndex = content.indexOf(startHeader);
            if (startIndex == -1) return Collections.emptyList();
            int endIndex = (endHeader != null) ? content.indexOf(endHeader, startIndex) : content.length();
            if (endIndex == -1) endIndex = content.length();
            String sectionBlock = content.substring(startIndex + startHeader.length(), endIndex).trim();
            return sectionBlock.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.replaceAll("^\\d+\\.\\s*", ""))
                    .toList();
        } catch (Exception e) {
            log.error("Error parsing section '{}'", startHeader, e);
            return Collections.emptyList();
        }
    }
}