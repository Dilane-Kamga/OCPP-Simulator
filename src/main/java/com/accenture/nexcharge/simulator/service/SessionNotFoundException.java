package com.accenture.nexcharge.simulator.service;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(Long id) {
        super("Session not found: " + id);
    }
}
