package engineer.hyper.chat_app.controller;

import engineer.hyper.chat_app.model.ChatRequest;
import engineer.hyper.chat_app.model.ChatResponse;
import engineer.hyper.chat_app.model.ErrorResponse;
import engineer.hyper.chat_app.service.ChatService;
import engineer.hyper.chat_app.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final SessionService sessionService;
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @PostMapping(value = "/{conversationId}/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Object> streamChat(@PathVariable String conversationId, @RequestBody ChatRequest request) {
        if (sessionService.getSession(conversationId) == null) {
            return Flux.just(
                    ErrorResponse.builder().error("Invalid conversationId").code("INVALID_CONVERSATION_ID").build());
        }
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return Flux.just(ErrorResponse.builder().error("Missing question").code("MISSING_QUESTION").build());
        }

        return chatService.streamAnswer(conversationId, request.getQuestion())
                .cast(Object.class)
                .onErrorResume(e -> {
                    log.error("Error streaming chat", e);
                    return Flux.just(ErrorResponse.builder().error("Internal error").code("INTERNAL_ERROR").build());
                });
    }
}