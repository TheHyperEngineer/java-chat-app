package engineer.hyper.chat_app.controller;

import engineer.hyper.chat_app.model.ChatRequest;
import engineer.hyper.chat_app.model.ChatResponse;
import engineer.hyper.chat_app.service.OrchestratorService;
import engineer.hyper.chat_app.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final OrchestratorService orchestratorService;
    private final SessionService sessionService;
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @PostMapping(value = "/{conversationId}/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ChatResponse> streamChat(@PathVariable String conversationId, @RequestBody ChatRequest request) {
        if (sessionService.getSession(conversationId) == null) {
            return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid conversationId"));
        }
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing question"));
        }

        return orchestratorService.delegateRequest(conversationId, request.getQuestion())
                // REFACTORED: This block now sends a final, structured error message to the UI.
                .onErrorResume(e -> {
                    log.error("An unrecoverable error occurred in the stream for conversationId {}:", conversationId, e);
                    // Create a user-friendly error response.
                    ChatResponse errorResponse = ChatResponse.builder()
                            .orderId(999)
                            .question(request.getQuestion())
                            .conversationId(conversationId)
                            .plan(List.of("An internal error occurred."))
                            .answer("Sorry, I was unable to process your request. Please try again later.")
                            .finalChunk(true) // IMPORTANT: Signal to the UI that the stream is over.
                            .build();
                    // Return it as a single-element Flux.
                    return Flux.just(errorResponse);
                });
    }
}