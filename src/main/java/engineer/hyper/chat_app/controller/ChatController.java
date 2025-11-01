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
import reactor.core.publisher.Mono;

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

        // CHANGED: A single, clean call to the orchestrator
        return orchestratorService.delegateRequest(conversationId, request.getQuestion())
                .onErrorResume(e -> {
                    log.error("Error streaming chat for conversationId {}:", conversationId, e);
                    return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error during streaming"));
                });
    }
}