package com.accenture.nexcharge.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ocpp")
public record OcppProperties(Server server) {
    public record Server(int port, String host) {}
}
