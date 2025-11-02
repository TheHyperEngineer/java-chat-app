package engineer.hyper.chat_app.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * This class contains all Data Transfer Objects (DTOs) used by the agentic services.
 * It serves as a single source of truth for the data contracts between agents.
 */
public class AgentDto {

    // --- DTOs for the OrchestratorService ---

    public enum TaskType {
        CODE_GENERATION,
        GENERAL_CHAT,
        NEWS_REPORT
    }

    public record TaskClassification(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The classification of the user's request. Must be either 'CODE_GENERATION' or 'GENERAL_CHAT'.")
            TaskType taskType,

            @JsonProperty(required = true)
            @JsonPropertyDescription("A brief justification for the classification.")
            String reason
    ) {
    }


    // --- DTOs for the CodeAssistantService Workflow ---

    public record CodeRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The user's detailed request for the program to be written.")
            String task,

            @JsonProperty(required = false)
            @JsonPropertyDescription("Feedback from the reviewer on why the previous code was rejected. Use this to improve the code.")
            String reviewFeedback
    ) {
    }

    public record TestRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The full Java code to be tested.")
            String code,

            @JsonProperty(required = true)
            @JsonPropertyDescription("The original user task, to provide context for testing.")
            String task
    ) {
    }

    public record ReviewRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The generated Java code.")
            String code,

            @JsonProperty(required = true)
            @JsonPropertyDescription("The generated JUnit tests for the code.")
            String tests
    ) {
    }

    public enum ReviewStatus {
        APPROVED,
        REJECTED
    }

    public record ReviewResponse(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The verdict of the code review. Must be either 'APPROVED' or 'REJECTED'.")
            ReviewStatus status,

            @JsonProperty(required = true)
            @JsonPropertyDescription("Detailed feedback explaining the reason for the verdict. If approved, this can be a simple confirmation. If rejected, this MUST contain specific instructions for improvement.")
            String feedback
    ) {
    }
}