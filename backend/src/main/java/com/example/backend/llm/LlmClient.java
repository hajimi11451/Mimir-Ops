package com.example.backend.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {
    private static final int REQUEST_MAX_RETRIES = 2;

    private final ObjectMapper objectMapper;

    @Value("${qianfan.v2.base-url}")
    private String baseUrl;

    @Value("${qianfan.v2.token}")
    private String token;

    @Value("${qianfan.v2.chat-model-name}")
    private String chatModelName;

    @Value("${qianfan.v2.agent-read-timeout-seconds}")
    private int agentReadTimeoutSeconds;

    private RestClient restClient;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(Math.max(agentReadTimeoutSeconds, 5) * 1_000);
        restClient = RestClient.builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .requestFactory(factory)
                .build();
        log.info("LLM client initialized, baseUrl={}, chatModel={}, agentReadTimeoutSeconds={}, tokenConfigured={}",
                normalizeBaseUrl(baseUrl), chatModelName, Math.max(agentReadTimeoutSeconds, 5), StringUtils.hasText(token));
    }

    public LlmResponse call(List<Message> messages, List<Map<String, Object>> toolDefinitions) {
        long start = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", chatModelName);
        requestBody.put("messages", toApiMessages(messages));
        requestBody.put("tools", toolDefinitions == null ? Collections.emptyList() : toolDefinitions);

        JsonNode root = post(requestId, "tool-call", requestBody);
        JsonNode messageNode = firstMessageNode(root);
        String content = extractMessageContent(messageNode);

        List<Map<String, Object>> rawToolCalls = new ArrayList<>();
        List<LlmResponse.ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallsNode = messageNode.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode toolCallNode : toolCallsNode) {
                rawToolCalls.add(objectMapper.convertValue(toolCallNode, Map.class));
                String argumentsRaw = toolCallNode.path("function").path("arguments").asText("{}");
                Map<String, Object> arguments = parseArguments(argumentsRaw);
                toolCalls.add(LlmResponse.ToolCall.builder()
                        .id(toolCallNode.path("id").asText(""))
                        .name(toolCallNode.path("function").path("name").asText(""))
                        .argumentsRaw(argumentsRaw)
                        .arguments(arguments)
                        .build());
            }
        }

        Message assistantMessage = Message.assistantWithToolCalls(content, rawToolCalls);
        LlmResponse response = LlmResponse.builder()
                .type(toolCalls.isEmpty() ? LlmResponse.ResponseType.TEXT : LlmResponse.ResponseType.TOOL_CALL)
                .content(content)
                .assistantMessage(assistantMessage)
                .toolCalls(toolCalls)
                .build();

        log.info("LLM tool call success, requestId={}, toolCalls={}, elapsedMs={}, contentLength={}, contentPreview={}",
                requestId, toolCalls.size(), System.currentTimeMillis() - start,
                content == null ? 0 : content.length(), summarizeText(content, 300));
        return response;
    }

    public JsonNode callForJson(String systemPrompt, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Message.system(systemPrompt));
        }
        messages.add(Message.user(userPrompt == null ? "" : userPrompt));
        return callForJson(messages);
    }

    public JsonNode callForJson(List<Message> messages) {
        long start = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", chatModelName);
        requestBody.put("messages", toApiMessages(messages));
        requestBody.put("response_format", Map.of("type", "json_object"));

        JsonNode root = post(requestId, "json", requestBody);
        JsonNode messageNode = firstMessageNode(root);
        String content = extractMessageContent(messageNode);

        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("LLM 未返回 JSON 内容。");
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(extractJsonText(content));
            log.info("LLM JSON call success, requestId={}, elapsedMs={}, contentLength={}, contentPreview={}",
                    requestId, System.currentTimeMillis() - start, content.length(), summarizeText(content, 300));
            return jsonNode;
        } catch (Exception e) {
            log.error("LLM JSON parse failed, requestId={}, elapsedMs={}, rawContent={}",
                    requestId, System.currentTimeMillis() - start, summarizeText(content, 500), e);
            throw new IllegalStateException("LLM JSON 解析失败: " + e.getMessage(), e);
        }
    }

    private JsonNode post(String requestId, String requestType, Map<String, Object> requestBody) {
        long start = System.currentTimeMillis();
        String messagePreview = summarizeMessages(requestBody.get("messages"));
        String toolPreview = summarizeTools(requestBody.get("tools"));

        for (int attempt = 0; attempt <= REQUEST_MAX_RETRIES; attempt++) {
            try {
                log.info("LLM request start, requestId={}, type={}, attempt={}/{}, model={}, url={}, messageCount={}, toolCount={}, responseFormat={}, messagePreview={}, toolPreview={}",
                        requestId,
                        requestType,
                        attempt + 1,
                        REQUEST_MAX_RETRIES + 1,
                        requestBody.get("model"),
                        normalizeBaseUrl(baseUrl) + "/chat/completions",
                        countItems(requestBody.get("messages")),
                        countItems(requestBody.get("tools")),
                        summarizeText(String.valueOf(requestBody.get("response_format")), 120),
                        messagePreview,
                        toolPreview);
                String responseBody = restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

                log.info("LLM response received, requestId={}, type={}, attempt={}/{}, elapsedMs={}, bodyLength={}, bodyPreview={}",
                        requestId,
                        requestType,
                        attempt + 1,
                        REQUEST_MAX_RETRIES + 1,
                        System.currentTimeMillis() - start,
                        responseBody == null ? 0 : responseBody.length(),
                        summarizeText(responseBody, 500));

                if (!StringUtils.hasText(responseBody)) {
                    log.error("LLM response empty, requestId={}, type={}, attempt={}/{}, elapsedMs={}",
                            requestId, requestType, attempt + 1, REQUEST_MAX_RETRIES + 1, System.currentTimeMillis() - start);
                    throw new IllegalStateException("LLM 返回为空。");
                }

                JsonNode root = objectMapper.readTree(responseBody);
                if (root.has("error")) {
                    log.error("LLM response contained error, requestId={}, type={}, attempt={}/{}, elapsedMs={}, error={}",
                            requestId, requestType, attempt + 1, REQUEST_MAX_RETRIES + 1, System.currentTimeMillis() - start, root.path("error"));
                    throw new IllegalStateException("LLM API error: " + root.path("error"));
                }
                return root;
            } catch (RestClientResponseException e) {
                log.error("LLM request response exception, requestId={}, type={}, attempt={}/{}, status={}, elapsedMs={}, responseBody={}, messagePreview={}, toolPreview={}",
                        requestId,
                        requestType,
                        attempt + 1,
                        REQUEST_MAX_RETRIES + 1,
                        e.getStatusCode(),
                        System.currentTimeMillis() - start,
                        summarizeText(e.getResponseBodyAsString(), 500),
                        messagePreview,
                        toolPreview,
                        e);
                throw new IllegalStateException("调用 LLM 失败: " + e.getMessage(), e);
            } catch (ResourceAccessException e) {
                boolean retryable = isRetryableException(e);
                if (retryable && attempt < REQUEST_MAX_RETRIES) {
                    log.warn("LLM request transient access exception, requestId={}, type={}, attempt={}/{}, elapsedMs={}, message={}, willRetry=true",
                            requestId, requestType, attempt + 1, REQUEST_MAX_RETRIES + 1, System.currentTimeMillis() - start, e.getMessage());
                    sleepBeforeRetry(attempt);
                    continue;
                }
                log.error("LLM request access exception, requestId={}, type={}, attempt={}/{}, elapsedMs={}, errorType={}, message={}, messagePreview={}, toolPreview={}",
                        requestId,
                        requestType,
                        attempt + 1,
                        REQUEST_MAX_RETRIES + 1,
                        System.currentTimeMillis() - start,
                        e.getClass().getSimpleName(),
                        e.getMessage(),
                        messagePreview,
                        toolPreview,
                        e);
                throw new IllegalStateException("调用 LLM 失败: " + e.getMessage(), e);
            } catch (RestClientException e) {
                boolean retryable = isRetryableException(e);
                if (retryable && attempt < REQUEST_MAX_RETRIES) {
                    log.warn("LLM request transient client exception, requestId={}, type={}, attempt={}/{}, elapsedMs={}, message={}, willRetry=true",
                            requestId, requestType, attempt + 1, REQUEST_MAX_RETRIES + 1, System.currentTimeMillis() - start, e.getMessage());
                    sleepBeforeRetry(attempt);
                    continue;
                }
                log.error("LLM request failed, requestId={}, type={}, attempt={}/{}, elapsedMs={}, errorType={}, message={}, messagePreview={}, toolPreview={}",
                        requestId,
                        requestType,
                        attempt + 1,
                        REQUEST_MAX_RETRIES + 1,
                        System.currentTimeMillis() - start,
                        e.getClass().getSimpleName(),
                        e.getMessage(),
                        messagePreview,
                        toolPreview,
                        e);
                throw new IllegalStateException("调用 LLM 失败: " + e.getMessage(), e);
            } catch (Exception e) {
                boolean retryable = isRetryableException(e);
                if (retryable && attempt < REQUEST_MAX_RETRIES) {
                    log.warn("LLM request transient exception, requestId={}, type={}, attempt={}/{}, elapsedMs={}, errorType={}, message={}, willRetry=true",
                            requestId, requestType, attempt + 1, REQUEST_MAX_RETRIES + 1, System.currentTimeMillis() - start, e.getClass().getSimpleName(), e.getMessage());
                    sleepBeforeRetry(attempt);
                    continue;
                }
                log.error("LLM request failed, requestId={}, type={}, attempt={}/{}, elapsedMs={}, errorType={}, message={}, messagePreview={}, toolPreview={}",
                        requestId,
                        requestType,
                        attempt + 1,
                        REQUEST_MAX_RETRIES + 1,
                        System.currentTimeMillis() - start,
                        e.getClass().getSimpleName(),
                        e.getMessage(),
                        messagePreview,
                        toolPreview,
                        e);
                throw new IllegalStateException("调用 LLM 失败: " + e.getMessage(), e);
            }
        }

        throw new IllegalStateException("调用 LLM 失败: 未获取有效响应。");
    }

    private boolean isRetryableException(Exception e) {
        String message = e == null ? "" : String.valueOf(e.getMessage()).toLowerCase();
        return message.contains("read timed out")
                || message.contains("connect timed out")
                || message.contains("connection reset")
                || message.contains("unexpected end of file")
                || message.contains("broken pipe")
                || message.contains("timed out");
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(1000L * (attempt + 1));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode firstMessageNode(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("LLM 未返回 choices。");
        }
        return choices.get(0).path("message");
    }

    private List<Map<String, Object>> toApiMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> apiMessages = new ArrayList<>();
        for (Message message : messages) {
            apiMessages.add(message.toMap());
        }
        return apiMessages;
    }

    private Map<String, Object> parseArguments(String argumentsRaw) {
        try {
            JsonNode argsNode = objectMapper.readTree(argumentsRaw);
            return objectMapper.convertValue(argsNode, Map.class);
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private int countItems(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private String summarizeMessages(Object messagesObj) {
        if (!(messagesObj instanceof List<?> messages) || messages.isEmpty()) {
            return "[]";
        }
        List<String> parts = new ArrayList<>();
        for (Object item : messages) {
            if (!(item instanceof Map<?, ?> messageMap)) {
                parts.add(summarizeText(String.valueOf(item), 120));
                continue;
            }
            Object role = messageMap.get("role");
            Object content = messageMap.get("content");
            parts.add(String.valueOf(role != null ? role : "unknown") + ":"
                    + summarizeText(String.valueOf(content != null ? content : ""), 160));
        }
        return parts.toString();
    }

    private String summarizeTools(Object toolsObj) {
        if (!(toolsObj instanceof List<?> tools) || tools.isEmpty()) {
            return "[]";
        }
        List<String> parts = new ArrayList<>();
        for (Object item : tools) {
            if (!(item instanceof Map<?, ?> toolMap)) {
                parts.add(summarizeText(String.valueOf(item), 120));
                continue;
            }
            Object functionObj = toolMap.get("function");
            if (functionObj instanceof Map<?, ?> functionMap) {
                parts.add(String.valueOf(functionMap.get("name")));
            } else {
                parts.add(summarizeText(String.valueOf(toolMap), 120));
            }
        }
        return parts.toString();
    }

    private String summarizeText(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return "";
        }
        return rawBaseUrl.endsWith("/") ? rawBaseUrl.substring(0, rawBaseUrl.length() - 1) : rawBaseUrl;
    }

    private String extractMessageContent(JsonNode messageNode) {
        if (messageNode == null || messageNode.isMissingNode() || messageNode.isNull()) {
            return "";
        }
        String content = flattenContentNode(messageNode.path("content"));
        if (StringUtils.hasText(content)) {
            return content;
        }
        return flattenContentNode(messageNode.path("reasoning_content"));
    }

    private String flattenContentNode(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText("").trim();
        }
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : contentNode) {
                String text = "";
                if (item.isTextual()) {
                    text = item.asText("");
                } else if (item.isObject()) {
                    text = item.path("text").asText("");
                    if (!StringUtils.hasText(text)) {
                        text = item.path("content").asText("");
                    }
                }
                if (StringUtils.hasText(text)) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(text.trim());
                }
            }
            return sb.toString().trim();
        }
        if (contentNode.isObject()) {
            String text = contentNode.path("text").asText("");
            if (!StringUtils.hasText(text)) {
                text = contentNode.path("content").asText("");
            }
            return text.trim();
        }
        return contentNode.asText("").trim();
    }

    private String extractJsonText(String rawContent) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.startsWith("```")) {
            content = content.replaceFirst("^```(?:json)?", "").trim();
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3).trim();
            }
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }
}
