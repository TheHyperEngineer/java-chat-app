package engineer.hyper.chat_app.controller;

import engineer.hyper.chat_app.model.Session;
import engineer.hyper.chat_app.model.ErrorResponse;
import engineer.hyper.chat_app.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {
    private final SessionService sessionService;

    @PostMapping
    public Mono<ResponseEntity<?>> startSession(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        if (userId == null || userId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ErrorResponse.builder().error("Missing userId").code("MISSING_USER_ID").build()));
        }
        Session session = sessionService.startSession(userId);
        return Mono.just(ResponseEntity.ok(Map.of("conversationId", session.getConversationId())));
    }

    @PostMapping("/{conversationId}/end")
    public Mono<ResponseEntity<?>> endSession(@PathVariable String conversationId) {
        sessionService.endSession(conversationId);
        return Mono.just(ResponseEntity.ok().build());
    }
}
