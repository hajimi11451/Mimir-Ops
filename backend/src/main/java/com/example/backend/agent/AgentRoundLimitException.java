package com.example.backend.agent;

public class AgentRoundLimitException extends RuntimeException {

    public AgentRoundLimitException(String message) {
        super(message);
    }
}
