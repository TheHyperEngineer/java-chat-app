package engineer.hyper.chat_app.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service("coderAgent")
public class CoderAgent {

    private final ChatClient chatClient;

    public CoderAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public Mono<String> generateCodeReactively(AgentDto.CodeRequest request) {
        // REINFORCED PROMPT: More aggressive instructions to prevent conversational text.
        String systemPrompt = """
                You are a world-class Java programmer.
                Your SOLE task is to write Java code based on the user's request.
                YOU MUST NOT include any markdown formatting, explanations, or any text other than the raw Java code itself.
                DO NOT write ```java.
                DO NOT write any conversational text or greetings.
                Your response must be ONLY the code.
                """;

        return this.chatClient.prompt()
                .system(systemPrompt)
                .user(userSpec -> {
                    userSpec.text("Write a Java program for the following task: {task}");
                    userSpec.param("task", request.task());
                    if (request.reviewFeedback() != null && !request.reviewFeedback().isBlank()) {
                        userSpec.text("\n\nThe previous attempt was rejected. You MUST address the following feedback: {feedback}");
                        userSpec.param("feedback", request.reviewFeedback());
                    }
                })
                .stream()
                .content()
                .collect(Collectors.joining())
                // ADDED: Programmatic safeguard to extract code if the LLM fails to follow instructions.
                .map(this::extractCode);
    }

    /**
     * A resilient method to extract code from a markdown block, in case the LLM
     * ignores the prompt and wraps the code in markdown.
     */
    private String extractCode(String response) {
        // This pattern looks for ```java ... ``` or ``` ... ```
        Pattern pattern = Pattern.compile("```(?:java)?\\s*([\\s\\S]*?)\\s*```");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim(); // Return only the content inside the markdown block
        }
        return response.trim(); // If no markdown block is found, assume the whole response is the code
    }
}