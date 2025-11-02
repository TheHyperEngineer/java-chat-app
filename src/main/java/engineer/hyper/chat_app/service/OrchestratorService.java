package engineer.hyper.chat_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import engineer.hyper.chat_app.agent.AgentDto;
import engineer.hyper.chat_app.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final ChatClient chatClient;
    private final ChatService chatService;
    private final CodeAssistantService codeAssistantService;

    public OrchestratorService(ChatClient.Builder builder, ChatService chatService, CodeAssistantService codeAssistantService) {
        this.chatClient = builder.build();
        this.chatService = chatService;
        this.codeAssistantService = codeAssistantService;
    }

    public Flux<ChatResponse> delegateRequest(String conversationId, String question) {
        return classifyTaskReactively(question)
                .onErrorResume(e -> {
                    // CATCH ALL parsing errors, including mismatched fields.
                    if (e instanceof JsonProcessingException || e.getCause() instanceof MismatchedInputException) {
                        log.warn("LLM failed to produce valid/matching JSON for classification. Falling back to GENERAL_CHAT. Error: {}", e.getMessage());
                        return Mono.just(new AgentDto.TaskClassification(AgentDto.TaskType.GENERAL_CHAT, "Fallback due to parsing error"));
                    }
                    // For other unexpected errors, propagate them.
                    return Mono.error(e);
                })
                .flatMapMany(classification -> {
                    log.info("Request classified as: {}", classification.taskType());
                    switch (classification.taskType()) {
                        case CODE_GENERATION:
                            return codeAssistantService.executeWorkflowReactively(question)
                                    .map(finalResult -> ChatResponse.builder()
                                            .orderId(1)
                                            .question(question)
                                            .conversationId(conversationId)
                                            .answer(finalResult)
                                            .finalChunk(true)
                                            .build());
                        case GENERAL_CHAT:
                        default:
                            return chatService.streamAnswer(conversationId, question);
                    }
                });
    }

    private Mono<AgentDto.TaskClassification> classifyTaskReactively(String question) {
        var outputConverter = new BeanOutputConverter<>(AgentDto.TaskClassification.class);
        // REINFORCED PROMPT: Explicitly name the required fields.
        String systemPrompt = """
                You are a task classification expert. Your job is to analyze the user's request and classify it.
                - If the user is asking to write code, a program, a script, a function, or a class, classify it as 'CODE_GENERATION'.
                - For any other type of request (greetings, questions, general conversation), classify it as 'GENERAL_CHAT'.

                Your response MUST be a single, valid JSON object.
                The JSON object MUST have two keys:
                1. "taskType": The classification value.
                2. "reason": A brief justification.
                """;

        return this.chatClient.prompt()
                .system(s -> s.text(systemPrompt).param("format", outputConverter.getFormat()))
                .user(question)
                .stream()
                .content()
                .collect(Collectors.joining())
                .map(outputConverter::convert);
    }
}