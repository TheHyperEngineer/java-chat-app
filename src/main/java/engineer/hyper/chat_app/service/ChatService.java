package engineer.hyper.chat_app.service;

import engineer.hyper.chat_app.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@Service
public class ChatService {
    public Flux<ChatResponse> streamAnswer(String conversationId, String question) {
        // Simulate streaming chunks
        return Flux.just(
                ChatResponse.builder()
                        .question(question)
                        .conversationId(conversationId)
                        .plan("Step 1: Understand the question")
                        .answer("Partial answer...")
                        .suggestions(List.of("Suggestion 1"))
                        .build(),
                ChatResponse.builder()
                        .question(question)
                        .conversationId(conversationId)
                        .plan("Step 2: Generate response")
                        .answer("Final answer.")
                        .suggestions(List.of("Suggestion 1", "Suggestion 2"))
                        .build())
                .delayElements(Duration.ofMillis(500));
    }
}
