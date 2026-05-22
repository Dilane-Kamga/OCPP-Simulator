package com.accenture.nexcharge.simulator.service;

public class ConnectorNotFoundException extends RuntimeException {
    public ConnectorNotFoundException(String chargePointId, int connectorId) {
        super("Connector not found: " + chargePointId + "/" + connectorId);
    }
}
