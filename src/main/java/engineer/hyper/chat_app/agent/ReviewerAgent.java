package engineer.hyper.chat_app.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Service("reviewerAgent")
public class ReviewerAgent {

    private final ChatClient chatClient;

    public ReviewerAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // CHANGED: Returns a simple Mono<String>, no more BeanOutputConverter.
    public Mono<String> reviewCodeAndTestsReactively(AgentDto.ReviewRequest request) {
        // REINFORCED PROMPT: Establish a simple, text-based contract.
        String systemPrompt = """
                You are a senior software engineer and a meticulous code reviewer. Your task is to review the provided code and its tests.
                - You MUST determine if the code and tests are correct, complete, and meet high-quality standards.
                - Your response MUST begin with the single word 'APPROVED' or 'REJECTED'.
                - If approved, follow with a brief confirmation. Example: 'APPROVED: The code is clean and the tests are comprehensive.'
                - If rejected, follow with clear, actionable feedback for the Coder Agent. Example: 'REJECTED: The algorithm is inefficient. Use a HashMap for better performance.'
                """;

        return this.chatClient.prompt()
                .system(systemPrompt) // The new, simpler prompt.
                .user(userSpec -> {
                    userSpec.text("""
                            Review the following code and tests. Your response MUST follow the format described in the system prompt.

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
                .stream()
                .content()
                .collect(Collectors.joining());
    }
}