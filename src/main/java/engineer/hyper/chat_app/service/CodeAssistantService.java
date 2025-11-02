package engineer.hyper.chat_app.service;

import engineer.hyper.chat_app.agent.AgentDto;
import engineer.hyper.chat_app.agent.CoderAgent;
import engineer.hyper.chat_app.agent.ReviewerAgent;
import engineer.hyper.chat_app.agent.TesterAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CodeAssistantService {

    private static final Logger log = LoggerFactory.getLogger(CodeAssistantService.class);
    private static final int MAX_ATTEMPTS = 3;

    private record WorkflowState(int attempt, String feedback, String code, String tests, boolean isApproved) {}

    private final CoderAgent coderAgent;
    private final TesterAgent testerAgent;
    private final ReviewerAgent reviewerAgent;

    public CodeAssistantService(CoderAgent coderAgent, TesterAgent testerAgent, ReviewerAgent reviewerAgent) {
        this.coderAgent = coderAgent;
        this.testerAgent = testerAgent;
        this.reviewerAgent = reviewerAgent;
    }

    public Mono<String> executeWorkflowReactively(String userRequest) {
        WorkflowState initialState = new WorkflowState(1, null, null, null, false);

        return Mono.just(initialState)
                .expand(currentState -> {
                    if (currentState.isApproved() || currentState.attempt() > MAX_ATTEMPTS) {
                        return Mono.empty();
                    }

                    log.info("[Attempt {}] Starting reactive code generation workflow.", currentState.attempt());

                    return coderAgent.generateCodeReactively(new AgentDto.CodeRequest(userRequest, currentState.feedback()))
                            .flatMap(code -> testerAgent.generateTestsReactively(new AgentDto.TestRequest(code, userRequest))
                                    .map(tests -> new WorkflowState(currentState.attempt(), null, code, tests, false)))
                            .flatMap(stateWithCode -> reviewerAgent.reviewCodeAndTestsReactively(new AgentDto.ReviewRequest(stateWithCode.code(), stateWithCode.tests()))
                                    // NEW LOGIC: Parse the simple string response from the reviewer.
                                    .map(reviewText -> {
                                        if (reviewText.trim().startsWith("APPROVED")) {
                                            log.info("...Reviewer APPROVED.");
                                            return new WorkflowState(stateWithCode.attempt(), reviewText, stateWithCode.code(), stateWithCode.tests(), true);
                                        } else {
                                            log.warn("...Reviewer REJECTED. Feedback: {}", reviewText);
                                            // The entire text is the feedback for the next iteration.
                                            return new WorkflowState(stateWithCode.attempt() + 1, reviewText, null, null, false);
                                        }
                                    }));
                })
                .last()
                .map(finalState -> {
                    if (finalState.isApproved()) {
                        return formatSuccessfulResponse(finalState.code(), finalState.tests());
                    } else {
                        log.error("...Maximum attempts reached. Workflow failed.");
                        // The feedback is now the full final rejection message.
                        return formatFailedResponse(finalState.feedback());
                    }
                });
    }

    private String formatSuccessfulResponse(String code, String tests) {
        return """
               ### Plan
               1. Delegate to Code Generation Agent Team
               2. Coder Agent: Generate Code
               3. Tester Agent: Generate Tests
               4. Reviewer Agent: Review and Approve
               5. Final Output

               ### Answer
               The code and tests have been generated and approved by the agent team.

               #### Generated Code:
               ```java
               %s
               ```

               #### Generated Tests:
               ```java
               %s
               ```
               """.formatted(code, tests);
    }

    private String formatFailedResponse(String finalFeedback) {
        return """
               ### Plan
               1. Delegate to Code Generation Agent Team
               2. Execute Coder/Tester/Reviewer Loop
               3. Process Failed After Max Attempts

               ### Answer
               The code generation process failed after the maximum number of attempts.

               **Final Reviewer Feedback:**
               %s
               """.formatted(finalFeedback);
    }
}