package com.example.backend.tool;

import com.example.backend.agent.AgentExecutionContext;

import java.util.Map;

public interface AgentTool {

    String getName();

    String getDescription();

    Map<String, Object> getParametersSchema();

    String execute(Map<String, Object> arguments, AgentExecutionContext context);
}
