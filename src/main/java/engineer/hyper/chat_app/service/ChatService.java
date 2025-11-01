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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ChatService {
    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    // REINFORCED PROMPT: This is our first line of defense against malformed JSON.
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

        // This is the core of the resilient streaming logic.
        return parseJsonStream(chatModel.stream(prompt)
                        .map(response -> response.getResult().getOutput().getText())
                        .doOnNext(fullResponseAggregator::append), // Aggregate the full text for saving history
                question,
                conversationId)
                .doOnTerminate(() -> { // This runs on completion or error
                    String fullContent = fullResponseAggregator.toString();
                    if (!fullContent.isBlank()) {
                        AssistantMessage assistantMessage = new AssistantMessage(fullContent);
                        chatMemory.add(conversationId, List.of(userMessage, assistantMessage));
                        log.info("Saved conversation history for ID: {}", conversationId);
                    }
                });
    }

    /**
     * This method implements the stateful, real-time JSON value parser.
     * It transforms a Flux of JSON string chunks into a Flux of structured ChatResponse objects.
     */
    private Flux<ChatResponse> parseJsonStream(Flux<String> jsonChunks, String question, String conversationId) {
        // State machine variables
        StringBuilder buffer = new StringBuilder();
        AtomicInteger braceDepth = new AtomicInteger(0);
        AtomicInteger orderId = new AtomicInteger(0);

        return jsonChunks.concatMap(chunk -> {
            buffer.append(chunk);
            List<ChatResponse> emittedResponses = new ArrayList<>();

            // Process the buffer to find and emit complete JSON values.
            // This loop allows us to emit multiple complete values if they arrive in a single chunk.
            while (true) {
                String currentBuffer = buffer.toString();
                int keyIndex = findNextKey(currentBuffer);
                if (keyIndex == -1) break; // No key found yet, need more data.

                String key = parseKey(currentBuffer, keyIndex);
                int valueStartIndex = findValueStart(currentBuffer, keyIndex);
                if (valueStartIndex == -1) break; // Key found, but value hasn't started.

                int valueEndIndex = findValueEnd(currentBuffer, valueStartIndex);
                if (valueEndIndex == -1) break; // Value started, but not yet complete.

                // We have a complete key-value pair.
                String value = currentBuffer.substring(valueStartIndex, valueEndIndex + 1);
                emittedResponses.add(createResponseChunk(key, value, question, conversationId, orderId.incrementAndGet()));

                // Reset buffer to the remaining unprocessed part.
                buffer.delete(0, valueEndIndex + 1);
            }
            return Flux.fromIterable(emittedResponses);
        }).concatWith(
                // This Mono acts as the final chunk emitter when the source completes.
                Mono.fromCallable(() -> {
                            // Check if there's any remaining buffer content that might be the start of the suggestions.
                            // This handles the case where the stream ends exactly on the last ']' of suggestions.
                            String finalBuffer = buffer.toString();
                            if (finalBuffer.contains("\"suggestions\"")) {
                                int keyIndex = findNextKey(finalBuffer);
                                String key = parseKey(finalBuffer, keyIndex);
                                int valueStartIndex = findValueStart(finalBuffer, keyIndex);
                                int valueEndIndex = findValueEnd(finalBuffer, valueStartIndex);
                                if (valueEndIndex != -1) {
                                    String value = finalBuffer.substring(valueStartIndex, valueEndIndex + 1);
                                    return createResponseChunk(key, value, question, conversationId, orderId.incrementAndGet());
                                }
                            }
                            // If no complete value is found in the buffer, we still need a final chunk.
                            return ChatResponse.builder()
                                    .orderId(orderId.incrementAndGet())
                                    .question(question)
                                    .conversationId(conversationId)
                                    .plan(Collections.emptyList())
                                    .answer(null)
                                    .suggestions(Collections.emptyList())
                                    .finalChunk(true)
                                    .build();
                        })
                        .map(response -> {
                            // Ensure the very last response is marked as the final chunk.
                            response.setFinalChunk(true);
                            return response;
                        })
        );
    }

    // Helper methods for the stateful parser
    private int findNextKey(String buffer) {
        return buffer.indexOf("\"plan\"") != -1 ? buffer.indexOf("\"plan\"") :
                buffer.indexOf("\"answer\"") != -1 ? buffer.indexOf("\"answer\"") :
                        buffer.indexOf("\"suggestions\"") != -1 ? buffer.indexOf("\"suggestions\"") : -1;
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
        if (startChar == '"') { // It's a string value
            for (int i = valueStartIndex + 1; i < buffer.length(); i++) {
                if (buffer.charAt(i) == '"' && buffer.charAt(i - 1) != '\\') {
                    return i;
                }
            }
        } else if (startChar == '[') { // It's an array value
            int depth = 1;
            for (int i = valueStartIndex + 1; i < buffer.length(); i++) {
                char c = buffer.charAt(i);
                if (c == '[') depth++;
                if (c == ']') depth--;
                if (depth == 0) return i;
            }
        }
        return -1; // Not found
    }

    private ChatResponse createResponseChunk(String key, String value, String question, String conversationId, int orderId) {
        ChatResponse.ChatResponseBuilder builder = ChatResponse.builder()
                .orderId(orderId)
                .question(question)
                .conversationId(conversationId)
                .finalChunk(false); // Default to false, will be updated by the final mono.

        try {
            switch (key) {
                case "plan":
                    builder.plan(objectMapper.readValue(value, new TypeReference<List<String>>() {}));
                    break;
                case "answer":
                    builder.answer(objectMapper.readValue(value, String.class));
                    break;
                case "suggestions":
                    builder.suggestions(objectMapper.readValue(value, new TypeReference<List<String>>() {}));
                    break;
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON value for key '{}'. Value: '{}'", key, value, e);
            // Gracefully handle parsing error for a chunk, maybe send an error message.
            builder.plan(List.of("Error parsing chunk for: " + key));
        }
        return builder.build();
    }
}