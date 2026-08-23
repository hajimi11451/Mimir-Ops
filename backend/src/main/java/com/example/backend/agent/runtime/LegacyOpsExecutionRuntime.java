package com.example.backend.agent.runtime;

import com.example.backend.service.OpsAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyOpsExecutionRuntime implements OpsExecutionRuntime {

    private final OpsAgentService opsAgentService;

    @Override
    public OpsRuntimeMode mode() {
        return OpsRuntimeMode.LEGACY;
    }

    @Override
    public OpsAgentService.AgentRunResult run(OpsExecutionRequest request) {
        return opsAgentService.runAgentLoopWithAdvice(
                request.userQuery(),
                request.serverIp(),
                request.username(),
                request.password(),
                request.maxRounds(),
                request.approvedRiskCommand(),
                request.session(),
                request.existingTaskState()
        );
    }

    @Override
    public void stop(String clientSessionId) {
        opsAgentService.stopAgent(clientSessionId);
    }
}
