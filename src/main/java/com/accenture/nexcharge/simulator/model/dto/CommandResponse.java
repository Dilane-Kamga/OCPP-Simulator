package com.accenture.nexcharge.simulator.model.dto;

public record CommandResponse(String status, String message) {
    public static CommandResponse accepted(String message) {
        return new CommandResponse("Accepted", message);
    }

    public static CommandResponse rejected(String message) {
        return new CommandResponse("Rejected", message);
    }
}
