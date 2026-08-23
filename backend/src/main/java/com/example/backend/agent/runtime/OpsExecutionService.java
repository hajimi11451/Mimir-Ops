package com.example.backend.agent.runtime;

import com.example.backend.model.TaskState;
import com.example.backend.service.CommandSafetyService;
import com.example.backend.service.OpsAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

/**
 * Stable facade used by the WebSocket handler. It keeps the legacy runtime as
 * the default and makes the OpenCode runtime an explicit, reversible switch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsExecutionService {

    private final OpsAgentProperties properties;
    private final LegacyOpsExecutionRuntime legacyRuntime;
    private final OpenCodeOpsExecutionRuntime openCodeRuntime;
    private final CommandSafetyService commandSafetyService;

    public OpsAgentService.AgentRunResult runAgentLoopWithAdvice(String userQuery,
                                                                  String serverIp,
                                                                  String username,
                                                                  String password,
                                                                  int maxRounds,
                                                                  String approvedRiskCommand,
                                                                  WebSocketSession session,
                                                                  TaskState existingTaskState) {
        OpsExecutionRequest request = new OpsExecutionRequest(
                userQuery,
                serverIp,
                username,
                password,
                maxRounds,
                approvedRiskCommand,
                session,
                existingTaskState
        );
        return activeRuntime().run(request);
    }

    public void stopAgent(String clientSessionId) {
        // A mode can change while a task is running. Notify both runtimes so a
        // stop request never leaves a stale OpenCode session behind.
        legacyRuntime.stop(clientSessionId);
        openCodeRuntime.stop(clientSessionId);
    }

    public boolean isHighRiskCommand(String command) {
        return commandSafetyService.isHighRiskCommand(command);
    }

    public OpsRuntimeMode activeMode() {
        return activeRuntime().mode();
    }

    private OpsExecutionRuntime activeRuntime() {
        OpsRuntimeMode configured = properties.getRuntime() == null
                ? OpsRuntimeMode.LEGACY
                : properties.getRuntime();
        if (configured == OpsRuntimeMode.OPENCODE) {
            return openCodeRuntime;
        }
        if (configured == OpsRuntimeMode.SHADOW) {
            // P1 keeps execution authoritative in Legacy. P2 will attach an
            // asynchronous, read-only OpenCode evaluator here.
            log.debug("Ops Agent shadow mode selected; executing legacy runtime in P1.");
        }
        return legacyRuntime;
    }
}
