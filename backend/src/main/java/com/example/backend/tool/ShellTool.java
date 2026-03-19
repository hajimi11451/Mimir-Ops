package com.example.backend.tool;

import com.example.backend.agent.AgentExecutionContext;
import com.example.backend.agent.PendingRiskConfirmationException;
import com.example.backend.service.CommandSafetyService;
import com.example.backend.utils.SshUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ShellTool implements AgentTool {

    private static final long DEFAULT_TIMEOUT_SECONDS = 60L;
    private static final int MAX_OUTPUT_LINES = 500;

    private final SshCommandRunner sshCommandRunner;
    private final CommandSafetyService commandSafetyService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "execute_command";
    }

    @Override
    public String getDescription() {
        return "在目标服务器执行 Linux 命令，并返回 exit_code 和 stdout/stderr。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "command", Map.of(
                        "type", "string",
                        "description", "需要在目标服务器执行的 Linux 命令"
                ),
                "timeout_seconds", Map.of(
                        "type", "integer",
                        "description", "命令超时时间，单位秒，默认 60"
                )
        ));
        schema.put("required", java.util.List.of("command"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments, AgentExecutionContext context) {
        String rawCommand = String.valueOf(arguments.getOrDefault("command", "")).trim();
        if (!StringUtils.hasText(rawCommand)) {
            return "ERROR: command 为空，无法执行。";
        }

        if (commandSafetyService.isHighRiskCommand(rawCommand)
                && !rawCommand.equals(String.valueOf(context.getApprovedRiskCommand()))) {
            throw new PendingRiskConfirmationException(rawCommand, "检测到高风险命令，需要用户确认后再执行。");
        }

        long timeoutSeconds = parseLong(arguments.get("timeout_seconds"), DEFAULT_TIMEOUT_SECONDS);
        String safeCommand = commandSafetyService.injectSudoPassword(rawCommand, context.getPassword());
        SshUtils.SshResult result = sshCommandRunner.execute(
                context.getServerIp(),
                context.getUsername(),
                context.getPassword(),
                safeCommand,
                timeoutSeconds
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("command", rawCommand);
        payload.put("exit_code", result.exitCode());
        payload.put("success", result.exitCode() == 0);
        payload.put("output", sshCommandRunner.trimOutputLines(result.output(), MAX_OUTPUT_LINES));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "ERROR: 命令结果序列化失败: " + e.getMessage();
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
}
