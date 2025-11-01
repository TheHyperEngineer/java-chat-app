package engineer.hyper.chat_app.service;

import engineer.hyper.chat_app.agent.AgentDto;
import engineer.hyper.chat_app.agent.CoderAgent;
import engineer.hyper.chat_app.agent.ReviewerAgent;
import engineer.hyper.chat_app.agent.TesterAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CodeAssistantService {

    private static final Logger log = LoggerFactory.getLogger(CodeAssistantService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final CoderAgent coderAgent;
    private final TesterAgent testerAgent;
    private final ReviewerAgent reviewerAgent;

    public CodeAssistantService(CoderAgent coderAgent, TesterAgent testerAgent, ReviewerAgent reviewerAgent) {
        this.coderAgent = coderAgent;
        this.testerAgent = testerAgent;
        this.reviewerAgent = reviewerAgent;
    }

    public String executeWorkflow(String userRequest) {
        String feedback = null;
        String finalCode = null;
        String finalTests = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            log.info("[Attempt {}] Starting code generation workflow.", attempt);

            String code = coderAgent.generateCode(new AgentDto.CodeRequest(userRequest, feedback));
            String tests = testerAgent.generateTests(new AgentDto.TestRequest(code, userRequest));
            AgentDto.ReviewResponse review = reviewerAgent.reviewCodeAndTests(new AgentDto.ReviewRequest(code, tests));

            if (review.status() == AgentDto.ReviewStatus.APPROVED) {
                log.info("Reviewer APPROVED. Workflow successful.");
                finalCode = code;
                finalTests = tests;
                break;
            } else {
                log.warn("Reviewer REJECTED. Feedback: {}", review.feedback());
                feedback = review.feedback();
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Maximum attempts reached. Workflow failed.");
                    return formatFailedResponse(feedback);
                }
            }
        }

        if (finalCode != null && finalTests != null) {
            return formatSuccessfulResponse(finalCode, finalTests);
        } else {
            return "The code generation process failed to complete after multiple attempts.";
        }
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
                
                **Final Rejection Feedback:**
                %s
                """.formatted(finalFeedback);
    }
}