package com.example.backend.agent.runtime;

import com.example.backend.model.TaskState;
import org.springframework.web.socket.WebSocketSession;

/**
 * Input held by the Spring control plane. Implementations must not send the
 * username or password to an LLM or an OpenCode process.
 */
public record OpsExecutionRequest(
        String userQuery,
        String serverIp,
        String username,
        String password,
        int maxRounds,
        String approvedRiskCommand,
        WebSocketSession session,
        TaskState existingTaskState
) {
}
