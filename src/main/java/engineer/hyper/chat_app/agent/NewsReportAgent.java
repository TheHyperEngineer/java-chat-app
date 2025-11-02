package engineer.hyper.chat_app.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service("newsReportAgent")
class NewsReportAgent {
    private final ChatClient chatClient;

    public NewsReportAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public Mono<String> writeArticle(String topic) {
        String systemPrompt = "You are a news reporter. Write a short, engaging news article on the given topic based on your general knowledge. Respond with only the article text.";
        return chatClient.prompt()
                .system(systemPrompt)
                .user(topic)
                .stream().content().collect(Collectors.joining());
    }
}

@Service("tweetAgent")
class TweetAgent {
    private final ChatClient chatClient;

    public TweetAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public Mono<List<String>> generateTweets(String topic) {
        // REINFORCED PROMPT
        String systemPrompt = "You are a social media manager. Generate exactly 2 engaging tweets about the given topic. Separate each tweet with a newline. DO NOT include empty lines, numbering, or any other text.";
        return chatClient.prompt()
                .system(systemPrompt)
                .user(topic)
                .stream().content().collect(Collectors.joining())
                // ADDED: Programmatic safeguard to filter out empty strings.
                .map(response -> Arrays.stream(response.split("\n"))
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toList()));
    }
}

@Service("socialMediaAgent")
class SocialMediaAgent {
    private final ChatClient chatClient;

    public SocialMediaAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public Mono<List<String>> generateHashtags(String topic) {
        // REINFORCED PROMPT
        String systemPrompt = "You are a social media expert. Generate a list of 5 relevant hashtags for the given topic. Separate each hashtag with a space. DO NOT include empty lines, numbering, or any other text. Example: #Topic1 #Topic2 #Topic3";
        return chatClient.prompt()
                .system(systemPrompt)
                .user(topic)
                .stream().content().collect(Collectors.joining())
                // ADDED: Programmatic safeguard to filter out empty strings.
                .map(response -> Arrays.stream(response.split("\\s+"))
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toList()));
    }
}

@Service("criticAgent")
class CriticAgent {
    private final ChatClient chatClient;

    public CriticAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // CHANGED: Returns a simple Mono<String> with a text-based contract.
    public Mono<String> review(NewsAgentDto.CombinedReport report) {
        String systemPrompt = """
                You are a critical editor. Review the combined news report.
                - Your response MUST begin with the single word 'APPROVED' or 'REJECTED'.
                - If approved, follow with a brief confirmation.
                - If rejected, follow with clear, actionable feedback for the EditorAgent.
                """;
        return chatClient.prompt()
                .system(systemPrompt)
                .user(u -> u.text("Review this report: {report}").param("report", report.toString()))
                .stream().content().collect(Collectors.joining());
    }
}