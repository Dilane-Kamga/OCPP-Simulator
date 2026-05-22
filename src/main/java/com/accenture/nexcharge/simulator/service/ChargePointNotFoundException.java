package com.accenture.nexcharge.simulator.service;

public class ChargePointNotFoundException extends RuntimeException {
    public ChargePointNotFoundException(String chargePointId) {
        super("Charge point not found: " + chargePointId);
    }
}
