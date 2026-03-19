package com.example.backend.tool;

import com.example.backend.agent.AgentExecutionContext;
import com.example.backend.utils.SshUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LogTool implements AgentTool {

    private static final int DEFAULT_LINES = 200;
    private static final int MAX_LINES = 500;
    private static final long DEFAULT_TIMEOUT_SECONDS = 30L;

    private final SshCommandRunner sshCommandRunner;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "read_log";
    }

    @Override
    public String getDescription() {
        return "读取目标服务器日志文件，可指定行数和关键字过滤。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "file_path", Map.of(
                        "type", "string",
                        "description", "日志文件绝对路径"
                ),
                "lines", Map.of(
                        "type", "integer",
                        "description", "读取最近多少行，默认 200，最大 500"
                ),
                "keyword", Map.of(
                        "type", "string",
                        "description", "可选关键字过滤"
                ),
                "timeout_seconds", Map.of(
                        "type", "integer",
                        "description", "命令超时时间，单位秒，默认 30"
                )
        ));
        schema.put("required", java.util.List.of("file_path"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments, AgentExecutionContext context) {
        String filePath = String.valueOf(arguments.getOrDefault("file_path", "")).trim();
        if (!StringUtils.hasText(filePath)) {
            return "ERROR: file_path 为空，无法读取日志。";
        }

        int lines = (int) Math.min(MAX_LINES, Math.max(1, parseLong(arguments.get("lines"), DEFAULT_LINES)));
        String keyword = String.valueOf(arguments.getOrDefault("keyword", "")).trim();
        long timeoutSeconds = parseLong(arguments.get("timeout_seconds"), DEFAULT_TIMEOUT_SECONDS);

        String command = "tail -n " + lines + " -- " + shellQuote(filePath);
        if (StringUtils.hasText(keyword)) {
            command += " | grep -n -- " + shellQuote(keyword);
        }

        SshUtils.SshResult result = sshCommandRunner.execute(
                context.getServerIp(),
                context.getUsername(),
                context.getPassword(),
                command,
                timeoutSeconds
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("file_path", filePath);
        payload.put("lines", lines);
        payload.put("keyword", keyword);
        payload.put("exit_code", result.exitCode());
        payload.put("output", sshCommandRunner.trimOutputLines(result.output(), MAX_LINES));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "ERROR: 日志结果序列化失败: " + e.getMessage();
        }
    }

    private long parseLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String shellQuote(String value) {
        return "'" + String.valueOf(value).replace("'", "'\"'\"'") + "'";
    }
}
