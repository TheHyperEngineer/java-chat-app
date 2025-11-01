package engineer.hyper.chat_app.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

@Service("reviewerAgent")
public class ReviewerAgent {

    private final ChatClient chatClient;

    public ReviewerAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public AgentDto.ReviewResponse reviewCodeAndTests(AgentDto.ReviewRequest request) {
        String systemPrompt = """
                You are a senior software engineer and a meticulous code reviewer. Your task is to review the provided code and its tests.
                - You MUST determine if the code and tests are correct, complete, and meet high-quality standards.
                - If they are excellent, you will approve them.
                - If there are any issues (bugs, missing tests, bad practices), you MUST reject them and provide clear, actionable feedback.
                """;

        // This converter forces the AI to respond with a valid ReviewResponse object.
        var outputConverter = new BeanOutputConverter<>(AgentDto.ReviewResponse.class);

        return this.chatClient.prompt()
                .system(s -> s.text(systemPrompt).param("format", outputConverter.getFormat()))
                .user(userSpec -> {
                    userSpec.text("""
                            Review the following code and tests. Your response MUST be in the format described in the system prompt.
                            
                            Generated Code:
                            ```java
                            {code}
                            ```
                            
                            Generated Tests:
                            ```java
                            {tests}
                            ```
                            """);
                    userSpec.param("code", request.code());
                    userSpec.param("tests", request.tests());
                })
                .call()
                .entity(outputConverter);
    }
}