package com.accenture.nexcharge.simulator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI nexchargeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("NEXCharge OCPP 1.6J Simulator")
                .description("Real-time CSMS + autonomous charge-point fleet for the NEXLevel Reinvented hackathon")
                .version("0.1.0"));
    }
}
