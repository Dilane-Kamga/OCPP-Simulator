package com.accenture.nexcharge.simulator.service;

public class TagNotFoundException extends RuntimeException {
    public TagNotFoundException(String idTag) {
        super("Authorized tag not found: " + idTag);
    }
}
