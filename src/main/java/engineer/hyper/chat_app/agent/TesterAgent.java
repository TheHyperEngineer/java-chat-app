package engineer.hyper.chat_app.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service("testerAgent")
public class TesterAgent {

    private final ChatClient chatClient;

    public TesterAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String generateTests(AgentDto.TestRequest request) {
        String systemPrompt = """
                You are an expert in software testing. Your sole purpose is to write comprehensive JUnit 5 tests for the given Java code.
                - Respond ONLY with the raw Java code for the tests.
                - Do not include any markdown formatting, explanations, or any text other than the test code itself.
                - Ensure the tests are runnable and cover the logic of the provided code.
                """;

        return this.chatClient.prompt()
                .system(systemPrompt)
                .user(userSpec -> {
                    userSpec.text("Original Task: {task}\n\nJava Code to Test:\n```java\n{code}\n```\n\nNow, write the JUnit 5 tests.");
                    userSpec.param("task", request.task());
                    userSpec.param("code", request.code());
                })
                .call()
                .content();
    }
}