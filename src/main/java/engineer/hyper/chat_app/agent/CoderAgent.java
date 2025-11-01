package engineer.hyper.chat_app.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service("coderAgent")
public class CoderAgent {

    private final ChatClient chatClient;

    public CoderAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String generateCode(AgentDto.CodeRequest request) {
        String systemPrompt = """
                You are an expert Java programmer. Your sole purpose is to write clean, efficient, and correct Java code based on the user's request.
                - Respond ONLY with the raw Java code.
                - Do not include any markdown formatting, explanations, or any text other than the code itself.
                - Ensure the code is a single, complete, and runnable block.
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
                .call()
                .content();
    }
}