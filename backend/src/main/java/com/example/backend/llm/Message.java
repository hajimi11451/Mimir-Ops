package com.example.backend.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private String role;

    private String content;

    private String name;

    private String toolCallId;

    @Builder.Default
    private List<Map<String, Object>> toolCalls = new ArrayList<>();

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", role);
        if (content != null) {
            map.put("content", content);
        }
        if (StringUtils.hasText(name)) {
            map.put("name", name);
        }
        if (StringUtils.hasText(toolCallId)) {
            map.put("tool_call_id", toolCallId);
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            map.put("tool_calls", toolCalls);
        }
        return map;
    }

    public static Message system(String content) {
        return Message.builder().role("system").content(content).build();
    }

    public static Message user(String content) {
        return Message.builder().role("user").content(content).build();
    }

    public static Message assistant(String content) {
        return Message.builder().role("assistant").content(content).build();
    }

    public static Message assistantWithToolCalls(String content, List<Map<String, Object>> toolCalls) {
        return Message.builder()
                .role("assistant")
                .content(content)
                .toolCalls(toolCalls == null ? new ArrayList<>() : new ArrayList<>(toolCalls))
                .build();
    }

    public static Message tool(String toolCallId, String name, String content) {
        return Message.builder()
                .role("tool")
                .toolCallId(toolCallId)
                .name(name)
                .content(content == null ? "" : content)
                .build();
    }
}
