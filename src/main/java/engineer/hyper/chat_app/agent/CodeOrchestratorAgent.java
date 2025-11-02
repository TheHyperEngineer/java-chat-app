package engineer.hyper.chat_app.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

//@Service
public class CodeOrchestratorAgent {

    private static final Logger log = LoggerFactory.getLogger(CodeOrchestratorAgent.class);
    private static final int MAX_ATTEMPTS = 3;

    private final CoderAgent coderAgent;
    private final TesterAgent testerAgent;
    private final ReviewerAgent reviewerAgent;

    public CodeOrchestratorAgent(CoderAgent coderAgent, TesterAgent testerAgent, ReviewerAgent reviewerAgent) {
        this.coderAgent = coderAgent;
        this.testerAgent = testerAgent;
        this.reviewerAgent = reviewerAgent;
    }

    public String execute(String userRequest) {
        String feedback = null;
        String finalCode = null;
        String finalTests = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            log.info("[Attempt {}] Starting code generation process.", attempt);

            // 1. Coder Agent generates code
            log.info("...Calling Coder Agent.");
            String code = "";//coderAgent.generateCode(new AgentDto.CodeRequest(userRequest, feedback));

            // 2. Tester Agent generates tests
            log.info("...Calling Tester Agent.");
            String tests = "";//testerAgent.generateTests(new AgentDto.TestRequest(code, userRequest));

            // 3. Reviewer Agent reviews
            log.info("...Calling Reviewer Agent.");
            AgentDto.ReviewResponse review = null;//reviewerAgent.reviewCodeAndTests(new AgentDto.ReviewRequest(code, tests));

            // 4. Check review and decide next step
            if (review.status() == AgentDto.ReviewStatus.APPROVED) {
                log.info("...Reviewer APPROVED. Workflow successful.");
                finalCode = code;
                finalTests = tests;
                break; // Exit the loop on success
            } else {
                log.warn("...Reviewer REJECTED. Feedback: {}", review.feedback());
                feedback = review.feedback(); // Store feedback for the next loop iteration
                if (attempt == MAX_ATTEMPTS) {
                    log.error("...Maximum attempts reached. Workflow failed.");
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
                1. Generate Code
                2. Generate Tests
                3. Review and Approve
                4. Final Output
                
                ### Answer
                The code and tests have been generated and approved.
                
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
                1. Generate Code
                2. Generate Tests
                3. Review and Reject
                4. Process Failed
                
                ### Answer
                The code generation process failed after the maximum number of attempts.
                
                **Final Rejection Feedback:**
                %s
                """.formatted(finalFeedback);
    }
}