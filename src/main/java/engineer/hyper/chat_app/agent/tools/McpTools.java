package engineer.hyper.chat_app.agent.tools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

@Configuration
public class McpTools {

    private final WebClient mcpWebClient = WebClient.create("http://localhost:8081");

    @Bean
    @Description("Gets the latest breaking news headlines for a specific topic. Use this to research current events.")
    public Function<String, Flux<String>> getLatestNews() {
        return topic -> mcpWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/tools/get-latest-news").queryParam("topic", topic).build())
                .retrieve()
                .bodyToFlux(String.class);
    }

    @Bean
    @Description("Gets a list of currently trending topics or hashtags on social media. Use this to make content relevant.")
    public Function<String, Mono<List<String>>> getTrendingTopics() {
        return (dummy) -> mcpWebClient.get()
                .uri("/tools/get-trending-topics")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<String>>() {});
    }
}