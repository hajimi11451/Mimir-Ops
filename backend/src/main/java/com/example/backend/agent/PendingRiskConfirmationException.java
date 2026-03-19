package com.example.backend.agent;

import lombok.Getter;

@Getter
public class PendingRiskConfirmationException extends RuntimeException {

    private final String command;

    private final String reason;

    public PendingRiskConfirmationException(String command, String reason) {
        super(reason);
        this.command = command;
        this.reason = reason;
    }
}
