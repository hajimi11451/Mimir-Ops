package com.example.backend.agent;

public class StopRequestedException extends RuntimeException {

    public StopRequestedException(String message) {
        super(message);
    }
}
