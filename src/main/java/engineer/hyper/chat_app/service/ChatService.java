package engineer.hyper.chat_app.service;

import engineer.hyper.chat_app.model.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class ChatService {
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public ChatService(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.chatClient = builder.build();
    }

    public Flux<ChatResponse> streamAnswer(String conversationId, String question) {

        Flux<ChatResponse> chatResponseFlux = chatClient
                .prompt()
                .system("You are a playful conversation assistant.")
                .user(question)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .stream()
                .chatResponse()
                .map(org.springframework.ai.chat.model.ChatResponse::getResults)
                .flatMap(Flux::fromIterable)
                .map(generation -> ChatResponse
                        .builder()
                        .question(question)
                        .conversationId(conversationId)
                        .plan("Step 2: Generate response")
                        .answer(generation.getOutput().getText())
                        .suggestions(List.of()) // Empty suggestions for intermediate chunks
                        .finalChunk(false) // Mark as not final chunk
                        .build()
                );

        // Get the last element and modify it
        return chatResponseFlux
                .takeLast(1)
                .map(chatResponse ->
                        ChatResponse
                                .builder()
                                .question(question)
                                .conversationId(conversationId)
                                .plan("Step 2: Generate response")
                                .answer(chatResponse.getAnswer())
                                .suggestions(List.of("Suggestion 1", "Suggestion 2"))
                                .finalChunk(true)
                                .build()
                );
    }
}