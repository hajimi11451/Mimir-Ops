package com.example.backend.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {

    @Builder.Default
    private ResponseType type = ResponseType.TEXT;

    @Builder.Default
    private String content = "";

    private Message assistantMessage;

    @Builder.Default
    private List<ToolCall> toolCalls = new ArrayList<>();

    public enum ResponseType {
        TEXT,
        TOOL_CALL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {

        private String id;

        private String name;

        @Builder.Default
        private String argumentsRaw = "{}";

        @Builder.Default
        private Map<String, Object> arguments = new LinkedHashMap<>();
    }
}
