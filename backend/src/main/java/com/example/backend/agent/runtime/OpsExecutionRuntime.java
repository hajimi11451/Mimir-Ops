package com.example.backend.agent.runtime;

import com.example.backend.service.OpsAgentService;

public interface OpsExecutionRuntime {

    OpsRuntimeMode mode();

    OpsAgentService.AgentRunResult run(OpsExecutionRequest request);

    void stop(String clientSessionId);
}
