package engineer.hyper.chat_app.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ChatResponse {
    // ADDED: To guarantee correct ordering of chunks on the client.
    private int orderId;
    private String question;
    private String conversationId;
    private List<String> plan;
    private String answer;
    private List<String> suggestions;
    private boolean finalChunk;
}