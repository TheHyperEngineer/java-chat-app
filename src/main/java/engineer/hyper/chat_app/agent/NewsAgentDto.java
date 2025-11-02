package engineer.hyper.chat_app.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public class NewsAgentDto {

    // The final combined report from the specialist agents
    public record CombinedReport(String newsArticle, List<String> tweets, List<String> hashtags) {}

    // The structured output from the CriticAgent
    public enum CriticStatus {
        APPROVED,
        REJECTED
    }

    public record Criticism(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The verdict of the review. Must be either 'APPROVED' or 'REJECTED'.")
        CriticStatus status,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Detailed feedback. If approved, a confirmation. If rejected, specific instructions for the EditorAgent on how to improve the plan.")
        String feedback
    ) {}
}