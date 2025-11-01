package engineer.hyper.chat_app.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ChatResponse {
    private String question;
    private String conversationId;
    private String plan;
    private String answer;
    private List<String> suggestions;
}
