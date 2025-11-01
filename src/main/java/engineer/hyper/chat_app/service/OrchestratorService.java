package engineer.hyper.chat_app.service;

import engineer.hyper.chat_app.agent.AgentDto;
import engineer.hyper.chat_app.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        // 1. Classify the user's request
        AgentDto.TaskClassification classification = classifyTask(question);
        log.info("Request classified as: {}", classification.taskType());

        // 2. Delegate to the appropriate service based on classification
        switch (classification.taskType()) {
            case CODE_GENERATION:
                // The code assistant is a blocking, non-streaming service.
                // We wrap its execution in a Mono to integrate it into the reactive chain.
                return Mono.fromCallable(() -> codeAssistantService.executeWorkflow(question))
                        .map(finalResult -> ChatResponse.builder()
                                .orderId(1)
                                .question(question)
                                .conversationId(conversationId)
                                .answer(finalResult)
                                .finalChunk(true)
                                .build())
                        .flux(); // Convert the Mono to a Flux

            case GENERAL_CHAT:
            default:
                // The chat service is already a streaming, reactive service.
                return chatService.streamAnswer(conversationId, question);
        }
    }

    private AgentDto.TaskClassification classifyTask(String question) {
        var outputConverter = new BeanOutputConverter<>(AgentDto.TaskClassification.class);

        String systemPrompt = """
                You are a task classification expert. Your job is to analyze the user's request and classify it.
                - If the user is asking to write code, a program, a script, a function, or a class, classify it as 'CODE_GENERATION'.
                - For any other type of request (greetings, questions, general conversation), classify it as 'GENERAL_CHAT'.
                Your response MUST be in the format described below.
                """;

        return this.chatClient.prompt()
                .system(s -> s.text(systemPrompt).param("format", outputConverter.getFormat()))
                .user(question)
                .call()
                .entity(outputConverter);
    }
}