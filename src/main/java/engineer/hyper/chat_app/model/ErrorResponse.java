package engineer.hyper.chat_app.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorResponse {
    private String error;
    private String code;
}
