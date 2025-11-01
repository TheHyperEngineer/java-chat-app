package engineer.hyper.chat_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import engineer.hyper.chat_app.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ChatService {
    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_PROMPT = """
            You are a helpful conversation assistant.
            You MUST follow all of the following rules strictly:
            1. Your entire response MUST be a single, valid JSON object. Do not include any text or markdown outside of this JSON object.
            2. The JSON object must have exactly three keys in this exact order: "plan", "answer", and "suggestions".
            3. The "plan" value must be a JSON array of strings.
            4. The "answer" value must be a single JSON string.
            5. The "suggestions" value must be a JSON array of strings.
            6. Ensure all brackets, braces, and quotes are correctly opened and closed. Do not stop generating until the final closing brace '}' of the JSON object is complete.
            """;

    public ChatService(ChatModel chatModel, ChatMemory chatMemory, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.objectMapper = objectMapper;
    }

    public Flux<ChatResponse> streamAnswer(String conversationId, String question) {
        List<Message> history = chatMemory.get(conversationId);
        List<Message> messages = new ArrayList<>(history);
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        UserMessage userMessage = new UserMessage(question);
        messages.add(userMessage);

        Prompt prompt = new Prompt(messages);
        StringBuilder fullResponseAggregator = new StringBuilder();
        AtomicBoolean suggestionsEmitted = new AtomicBoolean(false);

        Flux<ChatResponse> mainStream = parseJsonStream(chatModel.stream(prompt)
                        .map(response -> response.getResult().getOutput().getText())
                        .doOnNext(fullResponseAggregator::append),
                question,
                conversationId,
                suggestionsEmitted);

        // CORRECTED: Use concatWith(Mono.defer(...)) for conditional completion.
        return mainStream.concatWith(Mono.defer(() -> {
                    // This logic is now executed only after the mainStream completes.
                    if (!suggestionsEmitted.get()) {
                        // If suggestions were never sent, emit a final, empty chunk to terminate the stream gracefully.
                        return Mono.just(ChatResponse.builder()
                                .orderId(999)
                                .question(question)
                                .conversationId(conversationId)
                                .plan(Collections.emptyList())
                                .answer(null)
                                .suggestions(Collections.emptyList())
                                .finalChunk(true)
                                .build());
                    }
                    // If suggestions were already emitted and marked as final, complete the stream without adding anything.
                    return Mono.empty();
                }))
                .doOnTerminate(() -> {
                    String fullContent = fullResponseAggregator.toString();
                    if (!fullContent.isBlank()) {
                        AssistantMessage assistantMessage = new AssistantMessage(fullContent);
                        chatMemory.add(conversationId, List.of(userMessage, assistantMessage));
                        log.info("Saved conversation history for ID: {}", conversationId);
                    }
                });
    }

    private Flux<ChatResponse> parseJsonStream(Flux<String> jsonChunks, String question, String conversationId, AtomicBoolean suggestionsEmitted) {
        StringBuilder buffer = new StringBuilder();
        AtomicInteger orderId = new AtomicInteger(0);

        return jsonChunks.concatMap(chunk -> {
            buffer.append(chunk);
            List<ChatResponse> emittedResponses = new ArrayList<>();
            while (true) {
                String currentBuffer = buffer.toString();
                int keyIndex = findNextKey(currentBuffer);
                if (keyIndex == -1) break;

                String key = parseKey(currentBuffer, keyIndex);
                int valueStartIndex = findValueStart(currentBuffer, keyIndex);
                if (valueStartIndex == -1) break;

                int valueEndIndex = findValueEnd(currentBuffer, valueStartIndex);
                if (valueEndIndex == -1) break;

                String value = currentBuffer.substring(valueStartIndex, valueEndIndex + 1);
                emittedResponses.add(createResponseChunk(key, value, question, conversationId, orderId.incrementAndGet(), suggestionsEmitted));

                buffer.delete(0, valueEndIndex + 1);
            }
            return Flux.fromIterable(emittedResponses);
        });
    }

    private int findNextKey(String buffer) {
        int planIndex = buffer.indexOf("\"plan\"");
        int answerIndex = buffer.indexOf("\"answer\"");
        int suggestionsIndex = buffer.indexOf("\"suggestions\"");

        int minIndex = Integer.MAX_VALUE;
        if (planIndex != -1) minIndex = Math.min(minIndex, planIndex);
        if (answerIndex != -1) minIndex = Math.min(minIndex, answerIndex);
        if (suggestionsIndex != -1) minIndex = Math.min(minIndex, suggestionsIndex);

        return minIndex == Integer.MAX_VALUE ? -1 : minIndex;
    }

    private String parseKey(String buffer, int keyIndex) {
        int endQuoteIndex = buffer.indexOf('"', keyIndex + 1);
        return buffer.substring(keyIndex + 1, endQuoteIndex);
    }

    private int findValueStart(String buffer, int keyIndex) {
        int colonIndex = buffer.indexOf(':', keyIndex);
        if (colonIndex == -1) return -1;
        for (int i = colonIndex + 1; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (!Character.isWhitespace(c)) return i;
        }
        return -1;
    }

    private int findValueEnd(String buffer, int valueStartIndex) {
        char startChar = buffer.charAt(valueStartIndex);
        if (startChar == '"') {
            for (int i = valueStartIndex + 1; i < buffer.length(); i++) {
                if (buffer.charAt(i) == '"' && buffer.charAt(i - 1) != '\\') {
                    return i;
                }
            }
        } else if (startChar == '[') {
            int depth = 1;
            for (int i = valueStartIndex + 1; i < buffer.length(); i++) {
                char c = buffer.charAt(i);
                if (c == '[') depth++;
                if (c == ']') depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private ChatResponse createResponseChunk(String key, String value, String question, String conversationId, int orderId, AtomicBoolean suggestionsEmitted) {
        ChatResponse.ChatResponseBuilder builder = ChatResponse.builder()
                .orderId(orderId)
                .question(question)
                .conversationId(conversationId);

        try {
            switch (key) {
                case "plan":
                    builder.plan(objectMapper.readValue(value, new TypeReference<List<String>>() {})).finalChunk(false);
                    break;
                case "answer":
                    builder.answer(objectMapper.readValue(value, String.class)).finalChunk(false);
                    break;
                case "suggestions":
                    builder.suggestions(objectMapper.readValue(value, new TypeReference<List<String>>() {})).finalChunk(true);
                    suggestionsEmitted.set(true);
                    break;
                default:
                    builder.finalChunk(false);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON value for key '{}'. Value: '{}'", key, value, e);
            builder.plan(List.of("Error parsing chunk for: " + key)).finalChunk(false);
        }
        return builder.build();
    }
}