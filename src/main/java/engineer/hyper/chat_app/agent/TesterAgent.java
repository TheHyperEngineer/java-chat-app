package engineer.hyper.chat_app.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service("testerAgent")
public class TesterAgent {

    private final ChatClient chatClient;

    public TesterAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public Mono<String> generateTestsReactively(AgentDto.TestRequest request) {
        // REINFORCED PROMPT: More aggressive instructions.
        String systemPrompt = """
                You are an expert in software testing and writing JUnit 5 tests.
                Your SOLE task is to write JUnit 5 tests for the given Java code.
                YOU MUST NOT include any markdown formatting, explanations, or any text other than the raw Java test code itself.
                DO NOT write ```java.
                DO NOT write any conversational text or greetings.
                Your response must be ONLY the test code.
                """;

        return this.chatClient.prompt()
                .system(systemPrompt)
                .user(userSpec -> {
                    userSpec.text("Original Task: {task}\n\nJava Code to Test:\n```java\n{code}\n```\n\nNow, write the JUnit 5 tests.");
                    userSpec.param("task", request.task());
                    userSpec.param("code", request.code());
                })
                .stream()
                .content()
                .collect(Collectors.joining())
                // ADDED: Programmatic safeguard for the tester's output as well.
                .map(this::extractCode);
    }

    private String extractCode(String response) {
        Pattern pattern = Pattern.compile("```(?:java)?\\s*([\\s\\S]*?)\\s*```");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return response.trim();
    }
}