package engineer.hyper.chat_app.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class NewsOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(NewsOrchestratorService.class);
    private static final int MAX_ATTEMPTS = 2;

    // ENHANCED: State now includes the last generated report.
    private record WorkflowState(int attempt, String topic, NewsAgentDto.CombinedReport lastReport, boolean isApproved,
                                 String feedback) {
    }

    private final NewsReportAgent newsReportAgent;
    private final TweetAgent tweetAgent;
    private final SocialMediaAgent socialMediaAgent;
    private final CriticAgent criticAgent;

    public NewsOrchestratorService(
            NewsReportAgent newsReportAgent,
            TweetAgent tweetAgent,
            SocialMediaAgent socialMediaAgent,
            CriticAgent criticAgent) {
        this.newsReportAgent = newsReportAgent;
        this.tweetAgent = tweetAgent;
        this.socialMediaAgent = socialMediaAgent;
        this.criticAgent = criticAgent;
    }

    public Mono<String> executeWorkflowReactively(String userRequest) {
        WorkflowState initialState = new WorkflowState(1, userRequest, null, false, null);

        return Mono.just(initialState)
                .expand(currentState -> {
                    if (currentState.isApproved() || currentState.attempt() > MAX_ATTEMPTS) {
                        return Mono.empty();
                    }

                    log.info("[Attempt {}] Starting News Desk workflow for topic: {}", currentState.attempt(), currentState.topic());

                    Mono<NewsAgentDto.CombinedReport> combinedReportMono = Mono.zip(
                            newsReportAgent.writeArticle(currentState.topic()),
                            tweetAgent.generateTweets(currentState.topic()),
                            socialMediaAgent.generateHashtags(currentState.topic())
                    ).map(tuple -> new NewsAgentDto.CombinedReport(tuple.getT1(), tuple.getT2(), tuple.getT3()));

                    return combinedReportMono
                            .flatMap(report -> criticAgent.review(report)
                                    .map(criticismText -> {
                                        if (criticismText.trim().startsWith("APPROVED")) {
                                            log.info("...Critic APPROVED the report.");
                                            return new WorkflowState(currentState.attempt(), currentState.topic(), report, true, criticismText);
                                        } else {
                                            log.warn("...Critic REJECTED the report. Feedback: {}", criticismText);
                                            String refinedTopic = currentState.topic() + ". Refinement based on feedback: " + criticismText;
                                            // ENHANCED: Pass the rejected report to the next state.
                                            return new WorkflowState(currentState.attempt() + 1, refinedTopic, report, false, criticismText);
                                        }
                                    }));
                })
                .last()
                .map(finalState -> {
                    if (finalState.isApproved()) {
                        return formatSuccessfulResponse(finalState.lastReport());
                    } else {
                        log.error("...Maximum attempts reached. News Desk workflow failed.");
                        // ENHANCED: Pass both feedback and the last report to the formatter.
                        return formatFailedResponse(finalState.feedback(), finalState.lastReport());
                    }
                });
    }

    private String formatSuccessfulResponse(NewsAgentDto.CombinedReport report) {
        // Ensure we don't fail if the list is smaller than expected
        String tweet1 = report.tweets() != null && !report.tweets().isEmpty() ? report.tweets().get(0) : "N/A";
        String tweet2 = report.tweets() != null && report.tweets().size() > 1 ? report.tweets().get(1) : "N/A";

        return """
                ### Plan
                1. Delegate to News Desk Agent Team
                2. Agents for Reporting, Tweets, and Hashtags work in parallel
                3. Critic Agent reviews and approves the work
                4. Final Output
                
                ### Answer
                Here is the news report compiled by our agent team:
                
                #### News Article:
                %s
                
                #### Suggested Tweets:
                - %s
                - %s
                
                #### Social Media Hashtags:
                %s
                """.formatted(
                report.newsArticle(),
                tweet1,
                tweet2,
                String.join(" ", report.hashtags())
        );
    }

    // ENHANCED: This method now formats the final attempt for the user.
    private String formatFailedResponse(String finalFeedback, NewsAgentDto.CombinedReport lastReport) {
        String lastAttemptContent = "No content was generated in the final attempt.";
        if (lastReport != null) {
            lastAttemptContent = """
                    #### Last Generated Article:
                    %s
                    
                    #### Last Generated Tweets:
                    - %s
                    
                    #### Last Generated Hashtags:
                    %s
                    """.formatted(
                    lastReport.newsArticle(),
                    lastReport.tweets() != null ? String.join("\n- ", lastReport.tweets()) : "N/A",
                    lastReport.hashtags() != null ? String.join(" ", lastReport.hashtags()) : "N/A"
            );
        }

        return """
                ### Plan
                1. Delegate to News Desk Agent Team
                2. Execute Specialist/Critic Loop
                3. Process Failed After Max Attempts
                
                ### Answer
                The News Desk failed to produce a satisfactory report after multiple attempts.
                
                **Final Critic Feedback:**
                %s
                
                ---
                
                **Last Attempted Output:**
                %s
                """.formatted(finalFeedback != null ? finalFeedback : "No feedback was provided.", lastAttemptContent);
    }
}