package engineer.hyper.chat_app.controller;

import engineer.hyper.chat_app.model.ChatRequest;
import engineer.hyper.chat_app.model.ChatResponse;
import engineer.hyper.chat_app.service.ChatService;
import engineer.hyper.chat_app.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final SessionService sessionService;
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @PostMapping(value = "/{conversationId}/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    // REFACTORED: Return type is now a type-safe Flux of ChatResponse.
    public Flux<ChatResponse> streamChat(@PathVariable String conversationId, @RequestBody ChatRequest request) {
        // REFACTORED: Use reactive error signaling for validation.
        if (sessionService.getSession(conversationId) == null) {
            // This will be translated by WebFlux to a 404 Not Found.
            return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid conversationId"));
        }
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            // This will be translated by WebFlux to a 400 Bad Request.
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing question"));
        }

        return chatService.streamAnswer(conversationId, request.getQuestion())
                .onErrorResume(e -> {
                    // REFACTORED: Log the error and propagate it.
                    // WebFlux will automatically convert this to a 500 Internal Server Error response.
                    log.error("Error streaming chat for conversationId {}:", conversationId, e);
                    return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error during streaming"));
                });
    }
}