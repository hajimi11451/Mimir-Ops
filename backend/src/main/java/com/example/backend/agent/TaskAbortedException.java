package com.example.backend.agent;

public class TaskAbortedException extends RuntimeException {

    public TaskAbortedException(String message) {
        super(message);
    }
}
