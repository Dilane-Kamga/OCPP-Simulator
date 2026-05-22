package com.accenture.nexcharge.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CsmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsmsApplication.class, args);
    }
}
