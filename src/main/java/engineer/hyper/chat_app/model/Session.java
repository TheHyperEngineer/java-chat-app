package engineer.hyper.chat_app.model;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class Session {
    private String conversationId;
    private String userId;
    private Instant createdAt;

    // Optionally: private List<ChatMessage> history;
    public Session(String userId) {
        this.conversationId = UUID.randomUUID().toString();
        this.userId = userId;
        this.createdAt = Instant.now();
    }
}
