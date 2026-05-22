# OCPP 1.6J Simulator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a self-contained Java Spring Boot application that simulates 5 OCPP 1.6J charge points connected to a local CSMS, exposing a REST API + WebSocket for the NEXCharge monitoring app.

**Architecture:** Single JVM process. Spring Boot on port 8080 (REST + STOMP WebSocket), embedded Java-OCA-OCPP `JSONServer` on port 9000. Five `ChargePointSimulator` instances connect via real WebSocket to the local CSMS, drive a state machine (BOOTING → AVAILABLE → PREPARING → CHARGING → FAULTED), and emit realistic MeterValues using a pure CC/CV `ChargingProfile`. H2 file DB persists state. All cross-component events flow through a `LiveEventService` that publishes to `/topic/events`.

**Tech Stack:** Java 21, Spring Boot 3.3.0, Spring Data JPA, H2 file DB, Java-OCA-OCPP (`v1_6:1.1.0` + `OCPP-J:1.0.2`), Lombok, JUnit 5, Mockito, AssertJ, Maven (with wrapper).

**Spec source:** `docs/superpowers/specs/2026-05-22-ocpp-simulator-design.md`

---

## File structure (locked in)

```
simulateur/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/wrapper/
├── README.md
├── clean.sh
├── data/                                 # gitignored
└── src/
    ├── main/
    │   ├── java/com/accenture/nexcharge/simulator/
    │   │   ├── CsmsApplication.java
    │   │   ├── config/
    │   │   │   ├── SimulatorProperties.java       # @ConfigurationProperties("simulator")
    │   │   │   ├── OcppProperties.java            # @ConfigurationProperties("ocpp")
    │   │   │   ├── WebSocketConfig.java
    │   │   │   ├── CorsConfig.java
    │   │   │   └── JacksonConfig.java
    │   │   ├── model/
    │   │   │   ├── entity/
    │   │   │   │   ├── ChargePointEntity.java
    │   │   │   │   ├── ConnectorEntity.java
    │   │   │   │   ├── ChargingSessionEntity.java
    │   │   │   │   ├── MeterReadingEntity.java
    │   │   │   │   └── OcppLogEntity.java
    │   │   │   ├── enums/
    │   │   │   │   ├── ChargePointStatus.java
    │   │   │   │   ├── ConnectorStatus.java
    │   │   │   │   ├── SessionStatus.java
    │   │   │   │   ├── LogDirection.java
    │   │   │   │   └── LiveEventType.java
    │   │   │   └── dto/
    │   │   │       ├── ChargePointDto.java
    │   │   │       ├── ConnectorDto.java
    │   │   │       ├── SessionDto.java
    │   │   │       ├── MeterValueDto.java
    │   │   │       ├── StatsDto.java
    │   │   │       ├── OcppLogDto.java
    │   │   │       ├── LiveEventDto.java
    │   │   │       ├── RemoteStartRequest.java
    │   │   │       ├── RemoteStopRequest.java
    │   │   │       ├── ResetRequest.java
    │   │   │       ├── UnlockRequest.java
    │   │   │       ├── ScenarioRequest.java
    │   │   │       └── CommandResponse.java
    │   │   ├── repository/
    │   │   │   ├── ChargePointRepository.java
    │   │   │   ├── ConnectorRepository.java
    │   │   │   ├── ChargingSessionRepository.java
    │   │   │   ├── MeterReadingRepository.java
    │   │   │   └── OcppLogRepository.java
    │   │   ├── service/
    │   │   │   ├── ChargePointService.java
    │   │   │   ├── SessionService.java
    │   │   │   ├── MeterService.java
    │   │   │   ├── StatsService.java
    │   │   │   ├── LogService.java
    │   │   │   └── LiveEventService.java
    │   │   ├── ocpp/
    │   │   │   ├── CsmsServer.java
    │   │   │   ├── CsmsEventHandler.java
    │   │   │   └── OcppSessionRegistry.java
    │   │   ├── simulator/
    │   │   │   ├── ChargingProfile.java
    │   │   │   ├── PowerSnapshot.java               # record
    │   │   │   ├── SimulatorState.java              # enum
    │   │   │   ├── ChargePointSimulator.java
    │   │   │   ├── OcppClient.java                  # interface
    │   │   │   ├── JsonOcppClient.java              # real WS adapter
    │   │   │   ├── SimulatorClientHandler.java      # Client Core + RemoteTrigger handler
    │   │   │   ├── SimulatorManager.java
    │   │   │   └── SimulatorScenarioService.java
    │   │   └── controller/
    │   │       ├── ChargePointController.java
    │   │       ├── SessionController.java
    │   │       ├── MeterController.java
    │   │       ├── StatsController.java
    │   │       ├── LogController.java
    │   │       ├── RemoteCommandController.java
    │   │       ├── SimulatorController.java
    │   │       └── GlobalExceptionHandler.java
    │   └── resources/
    │       ├── application.yml
    │       └── logback-spring.xml
    └── test/
        ├── java/com/accenture/nexcharge/simulator/
        │   ├── simulator/
        │   │   ├── ChargingProfileTest.java
        │   │   ├── ChargePointSimulatorStateMachineTest.java
        │   │   ├── SimulatorClientHandlerTest.java
        │   │   └── SimulatorScenarioServiceTest.java
        │   ├── ocpp/
        │   │   ├── CsmsEventHandlerTest.java
        │   │   └── OcppSessionRegistryTest.java
        │   ├── service/
        │   │   ├── ChargePointServiceTest.java
        │   │   ├── SessionServiceTest.java
        │   │   ├── MeterServiceTest.java
        │   │   ├── StatsServiceTest.java
        │   │   └── LiveEventServiceTest.java
        │   ├── repository/
        │   │   ├── ChargePointRepositoryTest.java
        │   │   ├── ConnectorRepositoryTest.java
        │   │   ├── ChargingSessionRepositoryTest.java
        │   │   ├── MeterReadingRepositoryTest.java
        │   │   └── OcppLogRepositoryTest.java
        │   ├── controller/
        │   │   ├── ChargePointControllerTest.java
        │   │   ├── SessionControllerTest.java
        │   │   ├── MeterControllerTest.java
        │   │   ├── StatsControllerTest.java
        │   │   ├── LogControllerTest.java
        │   │   ├── RemoteCommandControllerTest.java
        │   │   └── SimulatorControllerTest.java
        │   ├── websocket/
        │   │   └── LiveEventWebSocketTest.java
        │   └── integration/
        │       └── EndToEndSimulationIT.java
        └── resources/
            ├── application-test.yml
            └── application-e2e.yml
```

**File responsibilities:**
- `config/*Properties` — `@ConfigurationProperties` for typed YAML access
- `model/entity/*` — pure JPA entities, no business logic
- `model/dto/*` — API request/response payloads, immutable records where possible
- `repository/*` — Spring Data JPA interfaces, custom queries via `@Query`
- `service/*` — business logic, transactional, thin façades over repositories
- `ocpp/*` — Java-OCA-OCPP integration (server-side)
- `simulator/*` — state machine + OCPP client per virtual borne
- `controller/*` — REST endpoints, delegate to services, no logic
- All tests follow TDD: red → green → refactor, one test class per main class

---

## Build/test commands quick reference

```bash
# Compile
./mvnw clean compile

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ChargingProfileTest

# Run a single test method
./mvnw test -Dtest=ChargingProfileTest#initialPowerIsZero

# Run app
./mvnw spring-boot:run

# Full build with tests
./mvnw clean install
```

---

## Phase 1: Bootstrap Maven project

Goal: `./mvnw spring-boot:run` starts an empty Spring Boot app on port 8080. No business code yet.

### Task 1.1: Create `pom.xml`

**Files:**
- Create: `pom.xml`

- [ ] **Step 1: Write the pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.0</version>
        <relativePath/>
    </parent>

    <groupId>com.accenture.nexcharge</groupId>
    <artifactId>simulator</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>NEXCharge OCPP Simulator</name>
    <description>OCPP 1.6J Charge Point Simulator and CSMS Server</description>

    <properties>
        <java.version>21</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>eu.chargetime.ocpp</groupId>
            <artifactId>v1_6</artifactId>
            <version>1.1.0</version>
        </dependency>
        <dependency>
            <groupId>eu.chargetime.ocpp</groupId>
            <artifactId>OCPP-J</artifactId>
            <version>1.0.2</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Generate Maven wrapper**

Run: `mvn -N wrapper:wrapper -Dmaven=3.9.6` (or use any installed Maven once)
Expected: creates `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `.mvn/wrapper/maven-wrapper.jar`

If no Maven installed, manually download: `curl -L https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar -o .mvn/wrapper/maven-wrapper.jar` and create the wrapper scripts manually.

- [ ] **Step 3: Verify pom.xml resolves dependencies**

Run: `./mvnw dependency:resolve -q`
Expected: no errors, dependencies download to `~/.m2/repository`

### Task 1.2: Create main application class

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/CsmsApplication.java`

- [ ] **Step 1: Create `CsmsApplication.java`**

```java
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
```

- [ ] **Step 2: Create minimal `application.yml`**

**Files:**
- Create: `src/main/resources/application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: nexcharge-simulator
  datasource:
    url: jdbc:h2:file:./data/csms;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate.format_sql: false
  h2:
    console:
      enabled: true
      path: /h2-console

ocpp:
  server:
    port: 9000
    host: 0.0.0.0

simulator:
  enabled: true
  acceleration-factor: 15
  heartbeat-interval-seconds: 30
  meter-interval-seconds: 10
  auto-session-probability: 0.05
  random-event-probability: 0.02
  charge-points:
    - id: BORNE_A
      vendor: Legrand
      model: "Green'Up Premium"
      serial: LGR-2024-001
      max-power-kw: 7.4
      connectors: 1
      firmware: "1.4.2"
    - id: BORNE_B
      vendor: Legrand
      model: "Green'Up Premium"
      serial: LGR-2024-002
      max-power-kw: 7.4
      connectors: 1
      firmware: "1.4.2"
    - id: BORNE_C
      vendor: Legrand
      model: "Green'Up Control"
      serial: LGR-2024-003
      max-power-kw: 22.0
      connectors: 2
      firmware: "2.1.0"
    - id: BORNE_D
      vendor: Legrand
      model: "Green'Up Premium"
      serial: LGR-2024-004
      max-power-kw: 7.4
      connectors: 1
      firmware: "1.4.2"
    - id: BORNE_E
      vendor: Legrand
      model: "Green'Up Control"
      serial: LGR-2024-005
      max-power-kw: 22.0
      connectors: 2
      firmware: "2.1.0"
  rfid-tags:
    - RFID-0001
    - RFID-0002
    - RFID-0003
    - RFID-0004
    - RFID-0005
    - RFID-0006
    - RFID-0007
    - RFID-0008
    - RFID-0009
    - RFID-0010
    - RFID-0011
    - RFID-0012
    - RFID-0013
    - RFID-0014
    - RFID-0015
    - RFID-0016
    - RFID-0017
    - RFID-0018
    - RFID-0019
    - RFID-0020

logging:
  level:
    root: INFO
    com.accenture.nexcharge: INFO
    eu.chargetime.ocpp: WARN
    org.hibernate.SQL: WARN
```

- [ ] **Step 3: Verify it boots**

Run: `./mvnw spring-boot:run`
Expected: console shows `Started CsmsApplication in X.X seconds`. Visit `http://localhost:8080/h2-console` → H2 login page appears. Ctrl+C to stop.

- [ ] **Step 4: Commit**

```bash
git add pom.xml mvnw mvnw.cmd .mvn/ src/main/java/com/accenture/nexcharge/simulator/CsmsApplication.java src/main/resources/application.yml
git commit -m "build: bootstrap Spring Boot 3.3 project on Java 21"
```

### Task 1.3: Configuration properties classes

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/config/SimulatorProperties.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/config/OcppProperties.java`
- Modify: `src/main/java/com/accenture/nexcharge/simulator/CsmsApplication.java` (add `@ConfigurationPropertiesScan`)

- [ ] **Step 1: Create `SimulatorProperties.java`**

```java
package com.accenture.nexcharge.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("simulator")
public record SimulatorProperties(
        boolean enabled,
        int accelerationFactor,
        int heartbeatIntervalSeconds,
        int meterIntervalSeconds,
        double autoSessionProbability,
        double randomEventProbability,
        List<ChargePointConfig> chargePoints,
        List<String> rfidTags
) {
    public record ChargePointConfig(
            String id,
            String vendor,
            String model,
            String serial,
            double maxPowerKw,
            int connectors,
            String firmware
    ) {}
}
```

- [ ] **Step 2: Create `OcppProperties.java`**

```java
package com.accenture.nexcharge.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ocpp")
public record OcppProperties(Server server) {
    public record Server(int port, String host) {}
}
```

- [ ] **Step 3: Enable scanning in `CsmsApplication.java`**

```java
package com.accenture.nexcharge.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CsmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CsmsApplication.class, args);
    }
}
```

- [ ] **Step 4: Verify properties bind correctly**

Run: `./mvnw spring-boot:run` → no `BindException`. Ctrl+C.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/config/ src/main/java/com/accenture/nexcharge/simulator/CsmsApplication.java
git commit -m "config: add typed configuration properties for simulator and OCPP"
```

### Task 1.4: CORS, Jackson, WebSocket configs

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/config/CorsConfig.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/config/JacksonConfig.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/config/WebSocketConfig.java`

- [ ] **Step 1: Create `CorsConfig.java`**

```java
package com.accenture.nexcharge.simulator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
```

- [ ] **Step 2: Create `JacksonConfig.java`**

```java
package com.accenture.nexcharge.simulator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder
                .modulesToInstall(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
```

- [ ] **Step 3: Create `WebSocketConfig.java`**

```java
package com.accenture.nexcharge.simulator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/live")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws/live")
                .setAllowedOriginPatterns("*");
    }
}
```

- [ ] **Step 4: Boot test**

Run: `./mvnw spring-boot:run` → starts cleanly. Ctrl+C.

- [ ] **Step 5: Create `.gitignore` already exists; create `clean.sh` and `README.md`**

**Files:**
- Create: `clean.sh`
- Create: `README.md`

`clean.sh`:
```bash
#!/usr/bin/env bash
set -e
rm -rf data/ target/
echo "Cleaned data/ and target/"
```

`README.md`:
```markdown
# NEXCharge OCPP 1.6J Simulator

Local CSMS server + 5 simulated Legrand charge points. Built for the NEXCharge hackathon.

## Quickstart

```bash
./mvnw spring-boot:run
```

- REST API: http://localhost:8080/api
- H2 Console: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:file:./data/csms`)
- OCPP WebSocket: ws://localhost:9000/ocpp/{chargePointId}
- Live events: ws://localhost:8080/ws/live (STOMP topic `/topic/events`)

## Useful commands

```bash
# All charge points
curl http://localhost:8080/api/chargepoints | jq

# Force all charge points to charge
curl -X POST http://localhost:8080/api/simulator/scenario \
  -H "Content-Type: application/json" \
  -d '{"scenario":"START_ALL"}'

# Reset persistent state
./clean.sh
```

See `docs/superpowers/specs/2026-05-22-ocpp-simulator-design.md` for the full spec.
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/config/ clean.sh README.md
chmod +x clean.sh
git commit -m "config: add CORS, Jackson, WebSocket STOMP and bootstrap scripts"
```

---

## Phase 2: Domain model (enums, entities, DTOs)

Goal: All persistent types defined. JPA `ddl-auto=update` creates tables on next boot. No business logic yet.

### Task 2.1: Enums

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/model/enums/ChargePointStatus.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/model/enums/ConnectorStatus.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/model/enums/SessionStatus.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/model/enums/LogDirection.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/model/enums/LiveEventType.java`

- [ ] **Step 1: Create the 5 enums**

`ChargePointStatus.java`:
```java
package com.accenture.nexcharge.simulator.model.enums;

public enum ChargePointStatus {
    Available, Preparing, Charging, SuspendedEV, SuspendedEVSE,
    Finishing, Reserved, Unavailable, Faulted
}
```

`ConnectorStatus.java`:
```java
package com.accenture.nexcharge.simulator.model.enums;

public enum ConnectorStatus {
    Available, Preparing, Charging, SuspendedEV, SuspendedEVSE,
    Finishing, Reserved, Unavailable, Faulted
}
```

`SessionStatus.java`:
```java
package com.accenture.nexcharge.simulator.model.enums;

public enum SessionStatus {
    Active, Completed, Error
}
```

`LogDirection.java`:
```java
package com.accenture.nexcharge.simulator.model.enums;

public enum LogDirection {
    IN, OUT
}
```

`LiveEventType.java`:
```java
package com.accenture.nexcharge.simulator.model.enums;

public enum LiveEventType {
    CHARGE_POINT_CONNECTED,
    CHARGE_POINT_DISCONNECTED,
    STATUS_CHANGE,
    SESSION_STARTED,
    SESSION_STOPPED,
    METER_UPDATE,
    FAULT,
    HEARTBEAT
}
```

- [ ] **Step 2: Compile**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/model/enums/
git commit -m "model: add status and event-type enums"
```

### Task 2.2: JPA Entities

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/model/entity/ChargePointEntity.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/model/entity/ConnectorEntity.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/model/entity/ChargingSessionEntity.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/model/entity/MeterReadingEntity.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/model/entity/OcppLogEntity.java`

- [ ] **Step 1: Create `ChargePointEntity.java`**

```java
package com.accenture.nexcharge.simulator.model.entity;

import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "charge_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargePointEntity {

    @Id
    @Column(name = "charge_point_id", length = 50)
    private String chargePointId;

    private String vendor;
    private String model;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ChargePointStatus status;

    private boolean online;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(name = "error_code", length = 50)
    private String errorCode;
}
```

- [ ] **Step 2: Create `ConnectorEntity.java`**

```java
package com.accenture.nexcharge.simulator.model.entity;

import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "connectors",
    uniqueConstraints = @UniqueConstraint(columnNames = {"charge_point_id", "connector_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charge_point_id", length = 50, nullable = false)
    private String chargePointId;

    @Column(name = "connector_id", nullable = false)
    private Integer connectorId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ConnectorStatus status;

    @Column(name = "current_power_kw")
    private Double currentPowerKw;

    @Column(name = "current_amps")
    private Double currentAmps;

    private Double voltage;

    @Column(name = "temperature_celsius")
    private Double temperatureCelsius;

    @Column(name = "total_energy_kwh")
    private Double totalEnergyKwh;

    @Column(name = "error_code", length = 50)
    private String errorCode;
}
```

- [ ] **Step 3: Create `ChargingSessionEntity.java`**

```java
package com.accenture.nexcharge.simulator.model.entity;

import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "charging_sessions",
    indexes = {
        @Index(name = "idx_session_status", columnList = "status"),
        @Index(name = "idx_session_cp", columnList = "charge_point_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargingSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", unique = true, nullable = false)
    private Integer transactionId;

    @Column(name = "charge_point_id", length = 50, nullable = false)
    private String chargePointId;

    @Column(name = "connector_id", nullable = false)
    private Integer connectorId;

    @Column(name = "id_tag", length = 50)
    private String idTag;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "stop_time")
    private Instant stopTime;

    @Column(name = "meter_start_wh")
    private Double meterStartWh;

    @Column(name = "meter_stop_wh")
    private Double meterStopWh;

    @Column(name = "energy_delivered_kwh")
    private Double energyDeliveredKwh;

    @Column(name = "stop_reason", length = 50)
    private String stopReason;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SessionStatus status;
}
```

- [ ] **Step 4: Create `MeterReadingEntity.java`**

```java
package com.accenture.nexcharge.simulator.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "meter_readings",
    indexes = {
        @Index(name = "idx_meter_cp_ts", columnList = "charge_point_id, timestamp"),
        @Index(name = "idx_meter_tx", columnList = "transaction_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charge_point_id", length = 50, nullable = false)
    private String chargePointId;

    @Column(name = "connector_id")
    private Integer connectorId;

    @Column(name = "transaction_id")
    private Integer transactionId;

    @Column(length = 100)
    private String measurand;

    @Column(name = "value", nullable = false)
    private Double value;

    @Column(length = 10)
    private String unit;

    @Column(nullable = false)
    private Instant timestamp;
}
```

- [ ] **Step 5: Create `OcppLogEntity.java`**

```java
package com.accenture.nexcharge.simulator.model.entity;

import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "ocpp_logs",
    indexes = {
        @Index(name = "idx_log_cp_ts", columnList = "charge_point_id, timestamp"),
        @Index(name = "idx_log_action", columnList = "action")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcppLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charge_point_id", length = 50)
    private String chargePointId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private LogDirection direction;

    @Column(length = 50)
    private String action;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant timestamp;
}
```

- [ ] **Step 6: Verify schema generation**

Run: `./mvnw spring-boot:run` → in another terminal: `./mvnw spring-boot:run` should start cleanly. Open `http://localhost:8080/h2-console`, login (JDBC URL `jdbc:h2:file:./data/csms`, user `sa`, no password) → see 5 tables. Ctrl+C.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/model/entity/
git commit -m "model: add JPA entities for charge points, sessions, meters and logs"
```

### Task 2.3: DTOs

**Files:**
- Create: 13 DTO files in `src/main/java/com/accenture/nexcharge/simulator/model/dto/`

- [ ] **Step 1: Create `ConnectorDto.java`**

```java
package com.accenture.nexcharge.simulator.model.dto;

import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;

public record ConnectorDto(
        Integer connectorId,
        ConnectorStatus status,
        Double currentPowerKw,
        Double currentAmps,
        Double voltage,
        Double temperatureCelsius,
        Double totalEnergyKwh,
        String errorCode
) {}
```

- [ ] **Step 2: Create `ChargePointDto.java`**

```java
package com.accenture.nexcharge.simulator.model.dto;

import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;

import java.time.Instant;
import java.util.List;

public record ChargePointDto(
        String chargePointId,
        String vendor,
        String model,
        String serialNumber,
        String firmwareVersion,
        ChargePointStatus status,
        boolean online,
        Instant lastHeartbeat,
        Instant registeredAt,
        String errorCode,
        List<ConnectorDto> connectors
) {}
```

- [ ] **Step 3: Create `SessionDto.java`**

```java
package com.accenture.nexcharge.simulator.model.dto;

import com.accenture.nexcharge.simulator.model.enums.SessionStatus;

import java.time.Instant;

public record SessionDto(
        Long id,
        Integer transactionId,
        String chargePointId,
        Integer connectorId,
        String idTag,
        Instant startTime,
        Instant stopTime,
        Double meterStartWh,
        Double meterStopWh,
        Double energyDeliveredKwh,
        String stopReason,
        SessionStatus status,
        Long durationMinutes
) {}
```

- [ ] **Step 4: Create `MeterValueDto.java`**

```java
package com.accenture.nexcharge.simulator.model.dto;

import java.time.Instant;

public record MeterValueDto(
        Instant timestamp,
        Integer connectorId,
        Integer transactionId,
        String measurand,
        Double value,
        String unit
) {}
```

- [ ] **Step 5: Create `StatsDto.java`**

```java
package com.accenture.nexcharge.simulator.model.dto;

public record StatsDto(
        long totalChargePoints,
        long onlineChargePoints,
        long chargingNow,
        long availableNow,
        long faultedNow,
        long activeSessionsCount,
        double totalPowerKw,
        double todayEnergyKwh,
        long todaySessionsCount,
        long todaySessionsCompleted,
        Long averageSessionDurationMinutes,
        Double averageEnergyPerSessionKwh
) {}
```

- [ ] **Step 6: Create `OcppLogDto.java`**

```java
package com.accenture.nexcharge.simulator.model.dto;

import com.accenture.nexcharge.simulator.model.enums.LogDirection;

import java.time.Instant;

public record OcppLogDto(
        Long id,
        String chargePointId,
        LogDirection direction,
        String action,
        String payload,
        Instant timestamp
) {}
```

- [ ] **Step 7: Create `LiveEventDto.java`**

```java
package com.accenture.nexcharge.simulator.model.dto;

import com.accenture.nexcharge.simulator.model.enums.LiveEventType;

import java.time.Instant;
import java.util.Map;

public record LiveEventDto(
        LiveEventType type,
        String chargePointId,
        Integer connectorId,
        Map<String, Object> data,
        Instant timestamp
) {
    public static LiveEventDto of(LiveEventType type, String chargePointId, Map<String, Object> data) {
        return new LiveEventDto(type, chargePointId, null, data, Instant.now());
    }

    public static LiveEventDto of(LiveEventType type, String chargePointId, Integer connectorId, Map<String, Object> data) {
        return new LiveEventDto(type, chargePointId, connectorId, data, Instant.now());
    }
}
```

- [ ] **Step 8: Create the request DTOs**

`RemoteStartRequest.java`:
```java
package com.accenture.nexcharge.simulator.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RemoteStartRequest(
        @NotBlank String idTag,
        @NotNull Integer connectorId
) {}
```

`RemoteStopRequest.java`:
```java
package com.accenture.nexcharge.simulator.model.dto;

import jakarta.validation.constraints.NotNull;

public record RemoteStopRequest(@NotNull Integer transactionId) {}
```

`ResetRequest.java`:
```java
package com.accenture.nexcharge.simulator.model.dto;

import jakarta.validation.constraints.Pattern;

public record ResetRequest(
        @Pattern(regexp = "Soft|Hard", message = "type must be Soft or Hard")
        String type
) {}
```

`UnlockRequest.java`:
```java
package com.accenture.nexcharge.simulator.model.dto;

import jakarta.validation.constraints.NotNull;

public record UnlockRequest(@NotNull Integer connectorId) {}
```

`ScenarioRequest.java`:
```java
package com.accenture.nexcharge.simulator.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ScenarioRequest(
        @NotBlank String scenario,
        String chargePointId
) {}
```

`CommandResponse.java`:
```java
package com.accenture.nexcharge.simulator.model.dto;

public record CommandResponse(String status, String message) {
    public static CommandResponse accepted(String message) {
        return new CommandResponse("Accepted", message);
    }

    public static CommandResponse rejected(String message) {
        return new CommandResponse("Rejected", message);
    }
}
```

- [ ] **Step 9: Compile**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/model/dto/
git commit -m "model: add DTOs for API responses and request bodies"
```

### Task 2.4: Repositories with TDD

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/repository/ChargePointRepository.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/repository/ConnectorRepository.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/repository/ChargingSessionRepository.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/repository/MeterReadingRepository.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/repository/OcppLogRepository.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/repository/*RepositoryTest.java`
- Create: `src/test/resources/application-test.yml`

- [ ] **Step 1: Create test config**

`src/test/resources/application-test.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:test;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false

simulator:
  enabled: false
  acceleration-factor: 15
  heartbeat-interval-seconds: 30
  meter-interval-seconds: 10
  auto-session-probability: 0.0
  random-event-probability: 0.0
  charge-points: []
  rfid-tags: [RFID-TEST]

ocpp:
  server:
    port: 0
    host: 127.0.0.1
```

- [ ] **Step 2: Write `ChargePointRepositoryTest` (RED)**

```java
package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.ChargePointEntity;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class ChargePointRepositoryTest {

    @Autowired
    ChargePointRepository repository;

    @Test
    void savesAndLoadsByPrimaryKey() {
        ChargePointEntity cp = ChargePointEntity.builder()
                .chargePointId("BORNE_TEST")
                .vendor("Legrand")
                .model("Green'Up Premium")
                .serialNumber("LGR-TEST")
                .firmwareVersion("1.0.0")
                .status(ChargePointStatus.Available)
                .online(true)
                .lastHeartbeat(Instant.now())
                .registeredAt(Instant.now())
                .errorCode("NoError")
                .build();
        repository.save(cp);

        Optional<ChargePointEntity> found = repository.findById("BORNE_TEST");
        assertThat(found).isPresent();
        assertThat(found.get().getVendor()).isEqualTo("Legrand");
    }

    @Test
    void findsByStatus() {
        repository.save(ChargePointEntity.builder()
                .chargePointId("BORNE_X").status(ChargePointStatus.Charging).build());
        repository.save(ChargePointEntity.builder()
                .chargePointId("BORNE_Y").status(ChargePointStatus.Available).build());

        List<ChargePointEntity> charging = repository.findByStatus(ChargePointStatus.Charging);
        assertThat(charging).extracting(ChargePointEntity::getChargePointId).containsExactly("BORNE_X");
    }

    @Test
    void countsOnline() {
        repository.save(ChargePointEntity.builder()
                .chargePointId("ON_1").online(true).build());
        repository.save(ChargePointEntity.builder()
                .chargePointId("ON_2").online(true).build());
        repository.save(ChargePointEntity.builder()
                .chargePointId("OFF_1").online(false).build());

        assertThat(repository.countByOnline(true)).isEqualTo(2);
    }
}
```

- [ ] **Step 3: Run test (verify it fails to compile)**

Run: `./mvnw test -Dtest=ChargePointRepositoryTest -q`
Expected: compilation error — `findByStatus`, `countByOnline` don't exist.

- [ ] **Step 4: Create `ChargePointRepository.java` (GREEN)**

```java
package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.model.entity.ChargePointEntity;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargePointRepository extends JpaRepository<ChargePointEntity, String> {
    List<ChargePointEntity> findByStatus(ChargePointStatus status);
    long countByOnline(boolean online);
    long countByStatus(ChargePointStatus status);
}
```

- [ ] **Step 5: Run test again (PASS)**

Run: `./mvnw test -Dtest=ChargePointRepositoryTest -q`
Expected: PASS.

- [ ] **Step 6: Write `ConnectorRepositoryTest`**

```java
package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class ConnectorRepositoryTest {

    @Autowired
    ConnectorRepository repository;

    @Test
    void findsByChargePointId() {
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP1").connectorId(1).status(ConnectorStatus.Available).build());
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP1").connectorId(2).status(ConnectorStatus.Charging).build());
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP2").connectorId(1).status(ConnectorStatus.Available).build());

        List<ConnectorEntity> connectors = repository.findByChargePointIdOrderByConnectorIdAsc("CP1");
        assertThat(connectors).hasSize(2);
        assertThat(connectors.get(0).getConnectorId()).isEqualTo(1);
    }

    @Test
    void findsByChargePointAndConnector() {
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP1").connectorId(1).status(ConnectorStatus.Charging).build());

        Optional<ConnectorEntity> found = repository.findByChargePointIdAndConnectorId("CP1", 1);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(ConnectorStatus.Charging);
    }

    @Test
    void countsByStatus() {
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP1").connectorId(1).status(ConnectorStatus.Charging).build());
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP2").connectorId(1).status(ConnectorStatus.Charging).build());
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP3").connectorId(1).status(ConnectorStatus.Available).build());

        assertThat(repository.countByStatus(ConnectorStatus.Charging)).isEqualTo(2);
    }
}
```

- [ ] **Step 7: Verify it fails to compile**

Run: `./mvnw test -Dtest=ConnectorRepositoryTest -q`
Expected: compilation errors.

- [ ] **Step 8: Create `ConnectorRepository.java`**

```java
package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectorRepository extends JpaRepository<ConnectorEntity, Long> {
    List<ConnectorEntity> findByChargePointIdOrderByConnectorIdAsc(String chargePointId);
    Optional<ConnectorEntity> findByChargePointIdAndConnectorId(String chargePointId, Integer connectorId);
    long countByStatus(ConnectorStatus status);
}
```

- [ ] **Step 9: Run test (PASS)**

Run: `./mvnw test -Dtest=ConnectorRepositoryTest -q`

- [ ] **Step 10: Write `ChargingSessionRepositoryTest`**

```java
package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class ChargingSessionRepositoryTest {

    @Autowired
    ChargingSessionRepository repository;

    @Test
    void findsByTransactionId() {
        repository.save(ChargingSessionEntity.builder()
                .transactionId(1001).chargePointId("CP1").connectorId(1)
                .startTime(Instant.now()).status(SessionStatus.Active).build());

        Optional<ChargingSessionEntity> found = repository.findByTransactionId(1001);
        assertThat(found).isPresent();
        assertThat(found.get().getChargePointId()).isEqualTo("CP1");
    }

    @Test
    void findsActiveSessions() {
        repository.save(ChargingSessionEntity.builder()
                .transactionId(2001).chargePointId("CP1").connectorId(1)
                .startTime(Instant.now()).status(SessionStatus.Active).build());
        repository.save(ChargingSessionEntity.builder()
                .transactionId(2002).chargePointId("CP2").connectorId(1)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .stopTime(Instant.now())
                .status(SessionStatus.Completed).build());

        List<ChargingSessionEntity> active = repository.findByStatus(SessionStatus.Active);
        assertThat(active).extracting(ChargingSessionEntity::getTransactionId).containsExactly(2001);
    }

    @Test
    void findsBetweenDates() {
        Instant from = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant to = Instant.now();
        repository.save(ChargingSessionEntity.builder()
                .transactionId(3001).chargePointId("CP1").connectorId(1)
                .startTime(from.plus(30, ChronoUnit.MINUTES))
                .status(SessionStatus.Active).build());
        repository.save(ChargingSessionEntity.builder()
                .transactionId(3002).chargePointId("CP1").connectorId(1)
                .startTime(from.minus(1, ChronoUnit.HOURS))
                .status(SessionStatus.Completed).build());

        List<ChargingSessionEntity> result = repository.findByStartTimeBetween(from, to);
        assertThat(result).extracting(ChargingSessionEntity::getTransactionId).containsExactly(3001);
    }
}
```

- [ ] **Step 11: Create `ChargingSessionRepository.java`**

```java
package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChargingSessionRepository extends JpaRepository<ChargingSessionEntity, Long> {
    Optional<ChargingSessionEntity> findByTransactionId(Integer transactionId);
    List<ChargingSessionEntity> findByStatus(SessionStatus status);
    List<ChargingSessionEntity> findByChargePointId(String chargePointId);
    List<ChargingSessionEntity> findByStartTimeBetween(Instant from, Instant to);
    long countByStatus(SessionStatus status);

    @Query("SELECT s FROM ChargingSessionEntity s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:chargePointId IS NULL OR s.chargePointId = :chargePointId) AND " +
           "(:from IS NULL OR s.startTime >= :from) AND " +
           "(:to IS NULL OR s.startTime <= :to) " +
           "ORDER BY s.startTime DESC")
    List<ChargingSessionEntity> search(
            @Param("status") SessionStatus status,
            @Param("chargePointId") String chargePointId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("SELECT COUNT(s) FROM ChargingSessionEntity s WHERE s.startTime >= :from")
    long countSince(@Param("from") Instant from);

    @Query("SELECT COALESCE(SUM(s.energyDeliveredKwh), 0) FROM ChargingSessionEntity s WHERE s.startTime >= :from")
    double sumEnergyDeliveredSince(@Param("from") Instant from);

    @Query("SELECT COUNT(s) FROM ChargingSessionEntity s WHERE s.startTime >= :from AND s.status = :status")
    long countSinceWithStatus(@Param("from") Instant from, @Param("status") SessionStatus status);
}
```

- [ ] **Step 12: Run test (PASS)**

Run: `./mvnw test -Dtest=ChargingSessionRepositoryTest -q`

- [ ] **Step 13: Write `MeterReadingRepositoryTest`**

```java
package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.MeterReadingEntity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class MeterReadingRepositoryTest {

    @Autowired
    MeterReadingRepository repository;

    @Test
    void findsByChargePointSinceTimestamp() {
        Instant now = Instant.now();
        repository.save(MeterReadingEntity.builder()
                .chargePointId("CP1").connectorId(1).measurand("Power.Active.Import")
                .value(7000.0).unit("W").timestamp(now.minus(5, ChronoUnit.MINUTES)).build());
        repository.save(MeterReadingEntity.builder()
                .chargePointId("CP1").connectorId(1).measurand("Power.Active.Import")
                .value(7100.0).unit("W").timestamp(now.minus(30, ChronoUnit.MINUTES)).build());

        List<MeterReadingEntity> recent = repository.findByChargePointIdAndTimestampAfterOrderByTimestampDesc(
                "CP1", now.minus(10, ChronoUnit.MINUTES));
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getValue()).isEqualTo(7000.0);
    }

    @Test
    void filtersByConnectorId() {
        Instant now = Instant.now();
        repository.save(MeterReadingEntity.builder()
                .chargePointId("CP1").connectorId(1).measurand("Voltage")
                .value(230.0).unit("V").timestamp(now).build());
        repository.save(MeterReadingEntity.builder()
                .chargePointId("CP1").connectorId(2).measurand("Voltage")
                .value(231.0).unit("V").timestamp(now).build());

        List<MeterReadingEntity> connector1 = repository
                .findByChargePointIdAndConnectorIdAndTimestampAfterOrderByTimestampDesc(
                        "CP1", 1, now.minus(1, ChronoUnit.HOURS));
        assertThat(connector1).hasSize(1);
        assertThat(connector1.get(0).getValue()).isEqualTo(230.0);
    }
}
```

- [ ] **Step 14: Create `MeterReadingRepository.java`**

```java
package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.model.entity.MeterReadingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MeterReadingRepository extends JpaRepository<MeterReadingEntity, Long> {
    List<MeterReadingEntity> findByChargePointIdAndTimestampAfterOrderByTimestampDesc(
            String chargePointId, Instant after);

    List<MeterReadingEntity> findByChargePointIdAndConnectorIdAndTimestampAfterOrderByTimestampDesc(
            String chargePointId, Integer connectorId, Instant after);
}
```

- [ ] **Step 15: Run test (PASS)**

Run: `./mvnw test -Dtest=MeterReadingRepositoryTest -q`

- [ ] **Step 16: Write `OcppLogRepositoryTest`**

```java
package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.OcppLogEntity;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class OcppLogRepositoryTest {

    @Autowired
    OcppLogRepository repository;

    @Test
    void searchByMultipleFilters() {
        Instant now = Instant.now();
        repository.save(OcppLogEntity.builder()
                .chargePointId("CP1").direction(LogDirection.IN).action("BootNotification")
                .payload("{}").timestamp(now).build());
        repository.save(OcppLogEntity.builder()
                .chargePointId("CP1").direction(LogDirection.IN).action("Heartbeat")
                .payload("{}").timestamp(now.minus(2, ChronoUnit.MINUTES)).build());
        repository.save(OcppLogEntity.builder()
                .chargePointId("CP2").direction(LogDirection.OUT).action("RemoteStart")
                .payload("{}").timestamp(now).build());

        List<OcppLogEntity> result = repository.search(
                "CP1", "Heartbeat", LogDirection.IN, now.minus(10, ChronoUnit.MINUTES),
                PageRequest.of(0, 10));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("Heartbeat");
    }

    @Test
    void searchWithNoFilters() {
        Instant now = Instant.now();
        repository.save(OcppLogEntity.builder()
                .chargePointId("CP1").direction(LogDirection.IN).action("Boot")
                .payload("{}").timestamp(now).build());

        List<OcppLogEntity> result = repository.search(null, null, null, null, PageRequest.of(0, 100));
        assertThat(result).hasSize(1);
    }
}
```

- [ ] **Step 17: Create `OcppLogRepository.java`**

```java
package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.model.entity.OcppLogEntity;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OcppLogRepository extends JpaRepository<OcppLogEntity, Long> {

    @Query("SELECT l FROM OcppLogEntity l WHERE " +
           "(:chargePointId IS NULL OR l.chargePointId = :chargePointId) AND " +
           "(:action IS NULL OR l.action = :action) AND " +
           "(:direction IS NULL OR l.direction = :direction) AND " +
           "(:after IS NULL OR l.timestamp >= :after) " +
           "ORDER BY l.timestamp DESC")
    List<OcppLogEntity> search(
            @Param("chargePointId") String chargePointId,
            @Param("action") String action,
            @Param("direction") LogDirection direction,
            @Param("after") Instant after,
            Pageable pageable
    );
}
```

- [ ] **Step 18: Run test (PASS)**

Run: `./mvnw test -Dtest=OcppLogRepositoryTest -q`

- [ ] **Step 19: Run all repo tests together**

Run: `./mvnw test -Dtest='*RepositoryTest' -q`
Expected: all 5 test classes PASS.

- [ ] **Step 20: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/repository/ src/test/java/com/accenture/nexcharge/simulator/repository/ src/test/resources/application-test.yml
git commit -m "repo: add JPA repositories with query methods (TDD)"
```

---

## Phase 3: ChargingProfile (pure unit, TDD)

Goal: A pure Java class that produces realistic CC/CV power snapshots over time. No Spring, no DB, fully deterministic with seeded `Random`.

### Task 3.1: PowerSnapshot record + SimulatorState enum

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/simulator/PowerSnapshot.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/simulator/SimulatorState.java`

- [ ] **Step 1: Create `PowerSnapshot.java`**

```java
package com.accenture.nexcharge.simulator.simulator;

public record PowerSnapshot(
        double powerKw,
        double voltage,
        double currentAmps,
        double temperatureCelsius,
        double totalEnergyKwh,
        double socPercent
) {}
```

- [ ] **Step 2: Create `SimulatorState.java`**

```java
package com.accenture.nexcharge.simulator.simulator;

public enum SimulatorState {
    BOOTING, AVAILABLE, PREPARING, CHARGING, FAULTED
}
```

- [ ] **Step 3: Compile**

Run: `./mvnw compile -q`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/simulator/PowerSnapshot.java src/main/java/com/accenture/nexcharge/simulator/simulator/SimulatorState.java
git commit -m "simulator: add PowerSnapshot record and SimulatorState enum"
```

### Task 3.2: ChargingProfile — initial state and ramp-up phase

**Files:**
- Test: `src/test/java/com/accenture/nexcharge/simulator/simulator/ChargingProfileTest.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/simulator/ChargingProfile.java`

- [ ] **Step 1: Write first test (initial state)**

```java
package com.accenture.nexcharge.simulator.simulator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ChargingProfileTest {

    private static final long DETERMINISTIC_SEED = 42L;

    @Test
    void initialPowerIsZero() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        assertThat(profile.getSocPercent()).isZero();
        assertThat(profile.getTotalEnergyDeliveredKwh()).isZero();
    }
}
```

- [ ] **Step 2: Run test (RED — class doesn't exist)**

Run: `./mvnw test -Dtest=ChargingProfileTest -q`
Expected: compilation failure.

- [ ] **Step 3: Create minimal `ChargingProfile.java`**

```java
package com.accenture.nexcharge.simulator.simulator;

import java.time.Duration;
import java.util.Random;

public class ChargingProfile {

    private final double maxPowerKw;
    private final double accelerationFactor;
    private final Random random;

    private double socPercent = 0.0;
    private double totalEnergyDeliveredKwh = 0.0;

    public ChargingProfile(double maxPowerKw, double accelerationFactor, Random random) {
        this.maxPowerKw = maxPowerKw;
        this.accelerationFactor = accelerationFactor;
        this.random = random;
    }

    public double getSocPercent() {
        return socPercent;
    }

    public double getTotalEnergyDeliveredKwh() {
        return totalEnergyDeliveredKwh;
    }
}
```

- [ ] **Step 4: Run test (PASS)**

Run: `./mvnw test -Dtest=ChargingProfileTest -q`
Expected: PASS.

- [ ] **Step 5: Add ramp-up tests**

Append to `ChargingProfileTest.java`:
```java
    @Test
    void rampUpPhaseStartsBelowMaxPower() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        // 10 real seconds = 150 simulated seconds = 2.5 simulated minutes
        // Within ramp-up phase (0% -> 5% SoC = ~2 simulated minutes)
        PowerSnapshot snap = profile.tick(Duration.ofSeconds(10));
        assertThat(snap.powerKw()).isPositive();
        assertThat(snap.powerKw()).isLessThan(7.4 * 1.10); // never exceed max+noise
    }

    @Test
    void energyAccumulatesMonotonically() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        PowerSnapshot s1 = profile.tick(Duration.ofSeconds(10));
        PowerSnapshot s2 = profile.tick(Duration.ofSeconds(10));
        assertThat(s2.totalEnergyKwh()).isGreaterThan(s1.totalEnergyKwh());
    }

    @Test
    void socAdvancesWithTime() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        profile.tick(Duration.ofSeconds(60));
        double socAfter1Min = profile.getSocPercent();
        profile.tick(Duration.ofSeconds(60));
        assertThat(profile.getSocPercent()).isGreaterThan(socAfter1Min);
    }
```

- [ ] **Step 6: Run tests (RED — `tick` not implemented)**

Run: `./mvnw test -Dtest=ChargingProfileTest -q`
Expected: compilation error or NPE.

- [ ] **Step 7: Implement `tick()` with all 4 phases**

Replace `ChargingProfile.java` with:

```java
package com.accenture.nexcharge.simulator.simulator;

import java.time.Duration;
import java.util.Random;

public class ChargingProfile {

    private static final double NOMINAL_VOLTAGE = 230.0;
    private static final double VOLTAGE_NOISE_RANGE = 5.0;
    private static final double POWER_NOISE_RATIO = 0.03;
    private static final double TEMPERATURE_BASE_CELSIUS = 20.0;
    private static final double TEMPERATURE_DELTA_CELSIUS = 25.0;

    private static final double SIMULATED_FULL_CHARGE_MINUTES = 120.0;

    private final double maxPowerKw;
    private final double accelerationFactor;
    private final Random random;

    private double socPercent = 0.0;
    private double totalEnergyDeliveredKwh = 0.0;

    public ChargingProfile(double maxPowerKw, double accelerationFactor, Random random) {
        this.maxPowerKw = maxPowerKw;
        this.accelerationFactor = accelerationFactor;
        this.random = random;
    }

    public PowerSnapshot tick(Duration realElapsed) {
        double simulatedSeconds = realElapsed.toMillis() / 1000.0 * accelerationFactor;
        double simulatedMinutes = simulatedSeconds / 60.0;

        double socIncrement = (simulatedMinutes / SIMULATED_FULL_CHARGE_MINUTES) * 100.0;
        socPercent = Math.min(100.0, socPercent + socIncrement);

        double basePowerKw = computePowerForSoc(socPercent);
        double noisyPowerKw = applyGaussianNoise(basePowerKw);
        noisyPowerKw = Math.max(0.0, noisyPowerKw);

        double voltage = NOMINAL_VOLTAGE + random.nextDouble() * VOLTAGE_NOISE_RANGE;
        double currentAmps = (noisyPowerKw * 1000.0) / voltage;
        double temperatureCelsius = TEMPERATURE_BASE_CELSIUS
                + (noisyPowerKw / maxPowerKw) * TEMPERATURE_DELTA_CELSIUS;

        double realHours = realElapsed.toMillis() / 1000.0 / 3600.0;
        totalEnergyDeliveredKwh += noisyPowerKw * realHours * accelerationFactor;

        return new PowerSnapshot(
                noisyPowerKw, voltage, currentAmps, temperatureCelsius,
                totalEnergyDeliveredKwh, socPercent
        );
    }

    private double computePowerForSoc(double soc) {
        if (soc <= 5.0) {
            return maxPowerKw * (soc / 5.0);
        }
        if (soc <= 80.0) {
            return maxPowerKw;
        }
        if (soc <= 95.0) {
            double slope = (maxPowerKw * 0.3 - maxPowerKw) / (95.0 - 80.0);
            return maxPowerKw + slope * (soc - 80.0);
        }
        double slope = (maxPowerKw * 0.1 - maxPowerKw * 0.3) / (100.0 - 95.0);
        return maxPowerKw * 0.3 + slope * (soc - 95.0);
    }

    private double applyGaussianNoise(double value) {
        double noise = random.nextGaussian() * POWER_NOISE_RATIO;
        return value * (1.0 + noise);
    }

    public double getSocPercent() {
        return socPercent;
    }

    public double getTotalEnergyDeliveredKwh() {
        return totalEnergyDeliveredKwh;
    }

    public boolean isComplete() {
        return socPercent >= 100.0;
    }
}
```

- [ ] **Step 8: Run tests (PASS)**

Run: `./mvnw test -Dtest=ChargingProfileTest -q`
Expected: 4 tests PASS.

- [ ] **Step 9: Add CC plateau test**

Append:
```java
    @Test
    void ccPlateauAtMaxPower() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        // Advance to 50% SoC (well into CC plateau)
        for (int i = 0; i < 60; i++) {
            profile.tick(Duration.ofSeconds(10));
        }
        assertThat(profile.getSocPercent()).isBetween(5.0, 80.0);

        PowerSnapshot snap = profile.tick(Duration.ofSeconds(1));
        // CC plateau: power is around max (within ±5% noise)
        assertThat(snap.powerKw()).isBetween(7.4 * 0.92, 7.4 * 1.08);
    }

    @Test
    void cvPhaseDecreasesPower() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        // Force into CV phase by direct loop
        while (profile.getSocPercent() < 90.0) {
            profile.tick(Duration.ofSeconds(10));
        }
        PowerSnapshot snap = profile.tick(Duration.ofSeconds(1));
        // CV phase (80-95% SoC): power drops
        assertThat(snap.powerKw()).isLessThan(7.4);
    }

    @Test
    void completesAt100Percent() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        while (!profile.isComplete()) {
            profile.tick(Duration.ofSeconds(10));
        }
        assertThat(profile.getSocPercent()).isEqualTo(100.0);
        assertThat(profile.isComplete()).isTrue();
    }
```

- [ ] **Step 10: Run tests (PASS)**

Run: `./mvnw test -Dtest=ChargingProfileTest -q`
Expected: 7 tests PASS.

- [ ] **Step 11: Add voltage/current/temperature tests**

Append:
```java
    @Test
    void voltageIsAroundNominal() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        PowerSnapshot snap = profile.tick(Duration.ofSeconds(10));
        assertThat(snap.voltage()).isBetween(230.0, 235.0);
    }

    @Test
    void currentIsPowerOverVoltage() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        PowerSnapshot snap = profile.tick(Duration.ofSeconds(60));
        double expected = (snap.powerKw() * 1000.0) / snap.voltage();
        assertThat(snap.currentAmps()).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void temperatureScalesWithPower() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        // Advance to CC plateau (highest power -> highest temp)
        while (profile.getSocPercent() < 50.0) {
            profile.tick(Duration.ofSeconds(10));
        }
        PowerSnapshot snap = profile.tick(Duration.ofSeconds(1));
        assertThat(snap.temperatureCelsius()).isBetween(20.0, 50.0);
    }
```

- [ ] **Step 12: Run tests (PASS)**

Run: `./mvnw test -Dtest=ChargingProfileTest -q`
Expected: 10 tests PASS.

- [ ] **Step 13: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/simulator/ChargingProfile.java src/test/java/com/accenture/nexcharge/simulator/simulator/ChargingProfileTest.java
git commit -m "simulator: add ChargingProfile with CC/CV phases and gaussian noise"
```

---

## Phase 4: Service layer

Goal: All entity↔DTO mapping and business queries available as injectable Spring services. Each service unit-tested with `@Mock` repos.

### Task 4.1: LiveEventService

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/service/LiveEventService.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/service/LiveEventServiceTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.LiveEventDto;
import com.accenture.nexcharge.simulator.model.enums.LiveEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveEventServiceTest {

    @Mock
    SimpMessagingTemplate template;

    @InjectMocks
    LiveEventService service;

    @Test
    void publishesToTopicEvents() {
        LiveEventDto event = LiveEventDto.of(
                LiveEventType.STATUS_CHANGE, "BORNE_A", Map.of("status", "Charging"));

        service.publish(event);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(destinationCaptor.capture(), payloadCaptor.capture());

        assertThat(destinationCaptor.getValue()).isEqualTo("/topic/events");
        assertThat(payloadCaptor.getValue()).isSameAs(event);
    }
}
```

- [ ] **Step 2: Run test (RED)**

Run: `./mvnw test -Dtest=LiveEventServiceTest -q`
Expected: compilation failure.

- [ ] **Step 3: Create `LiveEventService.java`**

```java
package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.LiveEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveEventService {

    public static final String TOPIC = "/topic/events";

    private final SimpMessagingTemplate template;

    public void publish(LiveEventDto event) {
        try {
            template.convertAndSend(TOPIC, event);
        } catch (Exception e) {
            log.warn("Failed to publish live event {}: {}", event.type(), e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run test (PASS)**

Run: `./mvnw test -Dtest=LiveEventServiceTest -q`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/service/LiveEventService.java src/test/java/com/accenture/nexcharge/simulator/service/LiveEventServiceTest.java
git commit -m "service: add LiveEventService for STOMP broadcast"
```

### Task 4.2: ChargePointService

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/service/ChargePointService.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/service/ChargePointServiceTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.ChargePointDto;
import com.accenture.nexcharge.simulator.model.entity.ChargePointEntity;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.ConnectorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargePointServiceTest {

    @Mock ChargePointRepository chargePointRepository;
    @Mock ConnectorRepository connectorRepository;

    @InjectMocks ChargePointService service;

    @Test
    void getAllReturnsDtoWithConnectors() {
        ChargePointEntity cp = ChargePointEntity.builder()
                .chargePointId("BORNE_A").vendor("Legrand")
                .model("Green'Up Premium").serialNumber("LGR-001")
                .firmwareVersion("1.4.2")
                .status(ChargePointStatus.Charging).online(true)
                .lastHeartbeat(Instant.now()).registeredAt(Instant.now())
                .errorCode("NoError").build();
        ConnectorEntity conn = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1)
                .status(ConnectorStatus.Charging)
                .currentPowerKw(7.2).voltage(230.0).currentAmps(31.0)
                .temperatureCelsius(38.0).totalEnergyKwh(14.5)
                .build();

        when(chargePointRepository.findAll()).thenReturn(List.of(cp));
        when(connectorRepository.findByChargePointIdOrderByConnectorIdAsc("BORNE_A"))
                .thenReturn(List.of(conn));

        List<ChargePointDto> result = service.getAll();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).chargePointId()).isEqualTo("BORNE_A");
        assertThat(result.get(0).connectors()).hasSize(1);
        assertThat(result.get(0).connectors().get(0).currentPowerKw()).isEqualTo(7.2);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(chargePointRepository.findById("UNKNOWN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById("UNKNOWN"))
                .isInstanceOf(ChargePointNotFoundException.class);
    }

    @Test
    void getByIdReturnsDto() {
        ChargePointEntity cp = ChargePointEntity.builder()
                .chargePointId("BORNE_A").status(ChargePointStatus.Available).build();
        when(chargePointRepository.findById("BORNE_A")).thenReturn(Optional.of(cp));
        when(connectorRepository.findByChargePointIdOrderByConnectorIdAsc("BORNE_A"))
                .thenReturn(List.of());

        ChargePointDto dto = service.getById("BORNE_A");
        assertThat(dto.chargePointId()).isEqualTo("BORNE_A");
        assertThat(dto.connectors()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test (RED)**

Run: `./mvnw test -Dtest=ChargePointServiceTest -q`
Expected: compilation errors.

- [ ] **Step 3: Create exception class**

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/service/ChargePointNotFoundException.java`

```java
package com.accenture.nexcharge.simulator.service;

public class ChargePointNotFoundException extends RuntimeException {
    public ChargePointNotFoundException(String chargePointId) {
        super("Charge point not found: " + chargePointId);
    }
}
```

- [ ] **Step 4: Create `ChargePointService.java`**

```java
package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.ChargePointDto;
import com.accenture.nexcharge.simulator.model.dto.ConnectorDto;
import com.accenture.nexcharge.simulator.model.entity.ChargePointEntity;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChargePointService {

    private final ChargePointRepository chargePointRepository;
    private final ConnectorRepository connectorRepository;

    public List<ChargePointDto> getAll() {
        return chargePointRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public ChargePointDto getById(String chargePointId) {
        ChargePointEntity entity = chargePointRepository.findById(chargePointId)
                .orElseThrow(() -> new ChargePointNotFoundException(chargePointId));
        return toDto(entity);
    }

    public List<ConnectorDto> getConnectors(String chargePointId) {
        if (!chargePointRepository.existsById(chargePointId)) {
            throw new ChargePointNotFoundException(chargePointId);
        }
        return connectorRepository.findByChargePointIdOrderByConnectorIdAsc(chargePointId).stream()
                .map(this::toConnectorDto)
                .toList();
    }

    private ChargePointDto toDto(ChargePointEntity cp) {
        List<ConnectorDto> connectors = connectorRepository
                .findByChargePointIdOrderByConnectorIdAsc(cp.getChargePointId()).stream()
                .map(this::toConnectorDto)
                .toList();

        return new ChargePointDto(
                cp.getChargePointId(),
                cp.getVendor(),
                cp.getModel(),
                cp.getSerialNumber(),
                cp.getFirmwareVersion(),
                cp.getStatus(),
                cp.isOnline(),
                cp.getLastHeartbeat(),
                cp.getRegisteredAt(),
                cp.getErrorCode(),
                connectors
        );
    }

    private ConnectorDto toConnectorDto(ConnectorEntity c) {
        return new ConnectorDto(
                c.getConnectorId(),
                c.getStatus(),
                c.getCurrentPowerKw(),
                c.getCurrentAmps(),
                c.getVoltage(),
                c.getTemperatureCelsius(),
                c.getTotalEnergyKwh(),
                c.getErrorCode()
        );
    }
}
```

- [ ] **Step 5: Run test (PASS)**

Run: `./mvnw test -Dtest=ChargePointServiceTest -q`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/service/ChargePointService.java src/main/java/com/accenture/nexcharge/simulator/service/ChargePointNotFoundException.java src/test/java/com/accenture/nexcharge/simulator/service/ChargePointServiceTest.java
git commit -m "service: add ChargePointService with DTO mapping"
```

### Task 4.3: SessionService

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/service/SessionService.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/service/SessionNotFoundException.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/service/SessionServiceTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.SessionDto;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.repository.ChargingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock ChargingSessionRepository repository;
    @InjectMocks SessionService service;

    @Test
    void searchByStatusOnly() {
        ChargingSessionEntity entity = ChargingSessionEntity.builder()
                .id(1L).transactionId(1001).chargePointId("CP1").connectorId(1)
                .idTag("RFID-001")
                .startTime(Instant.parse("2026-05-22T10:00:00Z"))
                .meterStartWh(0.0)
                .status(SessionStatus.Active).build();
        when(repository.search(SessionStatus.Active, null, null, null))
                .thenReturn(List.of(entity));

        List<SessionDto> result = service.search(SessionStatus.Active, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).transactionId()).isEqualTo(1001);
        assertThat(result.get(0).status()).isEqualTo(SessionStatus.Active);
    }

    @Test
    void durationMinutesComputedFromStartAndStop() {
        Instant start = Instant.parse("2026-05-22T10:00:00Z");
        Instant stop = start.plus(45, ChronoUnit.MINUTES);
        ChargingSessionEntity entity = ChargingSessionEntity.builder()
                .id(1L).transactionId(1001).chargePointId("CP1").connectorId(1)
                .startTime(start).stopTime(stop)
                .meterStartWh(0.0).meterStopWh(5000.0).energyDeliveredKwh(5.0)
                .stopReason("Local").status(SessionStatus.Completed).build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        SessionDto dto = service.getById(1L);
        assertThat(dto.durationMinutes()).isEqualTo(45L);
    }

    @Test
    void durationMinutesIsBasedOnNowWhenSessionActive() {
        Instant start = Instant.now().minus(10, ChronoUnit.MINUTES);
        ChargingSessionEntity entity = ChargingSessionEntity.builder()
                .id(2L).transactionId(2001).chargePointId("CP1").connectorId(1)
                .startTime(start).stopTime(null)
                .status(SessionStatus.Active).build();
        when(repository.findById(2L)).thenReturn(Optional.of(entity));

        SessionDto dto = service.getById(2L);
        assertThat(dto.durationMinutes()).isBetween(9L, 11L);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void findActiveDelegatesToRepository() {
        ChargingSessionEntity entity = ChargingSessionEntity.builder()
                .id(1L).transactionId(1).chargePointId("CP1").connectorId(1)
                .startTime(Instant.now()).status(SessionStatus.Active).build();
        when(repository.findByStatus(SessionStatus.Active)).thenReturn(List.of(entity));

        List<SessionDto> result = service.findActive();
        assertThat(result).hasSize(1);
    }
}
```

- [ ] **Step 2: Run test (RED)**

Run: `./mvnw test -Dtest=SessionServiceTest -q`
Expected: compilation errors.

- [ ] **Step 3: Create `SessionNotFoundException.java`**

```java
package com.accenture.nexcharge.simulator.service;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(Long id) {
        super("Session not found: " + id);
    }
}
```

- [ ] **Step 4: Create `SessionService.java`**

```java
package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.SessionDto;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.repository.ChargingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionService {

    private final ChargingSessionRepository repository;

    public List<SessionDto> search(SessionStatus status, String chargePointId, Instant from, Instant to) {
        return repository.search(status, chargePointId, from, to).stream()
                .map(this::toDto)
                .toList();
    }

    public List<SessionDto> findActive() {
        return repository.findByStatus(SessionStatus.Active).stream()
                .map(this::toDto)
                .toList();
    }

    public SessionDto getById(Long id) {
        ChargingSessionEntity entity = repository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
        return toDto(entity);
    }

    private SessionDto toDto(ChargingSessionEntity s) {
        Long durationMinutes = computeDurationMinutes(s);
        return new SessionDto(
                s.getId(),
                s.getTransactionId(),
                s.getChargePointId(),
                s.getConnectorId(),
                s.getIdTag(),
                s.getStartTime(),
                s.getStopTime(),
                s.getMeterStartWh(),
                s.getMeterStopWh(),
                s.getEnergyDeliveredKwh(),
                s.getStopReason(),
                s.getStatus(),
                durationMinutes
        );
    }

    private Long computeDurationMinutes(ChargingSessionEntity s) {
        if (s.getStartTime() == null) {
            return null;
        }
        Instant end = s.getStopTime() != null ? s.getStopTime() : Instant.now();
        return Duration.between(s.getStartTime(), end).toMinutes();
    }
}
```

- [ ] **Step 5: Run test (PASS)**

Run: `./mvnw test -Dtest=SessionServiceTest -q`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/service/SessionService.java src/main/java/com/accenture/nexcharge/simulator/service/SessionNotFoundException.java src/test/java/com/accenture/nexcharge/simulator/service/SessionServiceTest.java
git commit -m "service: add SessionService with duration computation"
```

### Task 4.4: MeterService

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/service/MeterService.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/service/MeterServiceTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.MeterValueDto;
import com.accenture.nexcharge.simulator.model.entity.MeterReadingEntity;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.MeterReadingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeterServiceTest {

    @Mock MeterReadingRepository meterRepository;
    @Mock ChargePointRepository chargePointRepository;
    @InjectMocks MeterService service;

    @Test
    void findRecentDelegatesToRepository() {
        when(chargePointRepository.existsById("CP1")).thenReturn(true);
        MeterReadingEntity entity = MeterReadingEntity.builder()
                .chargePointId("CP1").connectorId(1).transactionId(1001)
                .measurand("Power.Active.Import").value(7000.0).unit("W")
                .timestamp(Instant.now()).build();
        when(meterRepository.findByChargePointIdAndTimestampAfterOrderByTimestampDesc(eq("CP1"), any()))
                .thenReturn(List.of(entity));

        List<MeterValueDto> result = service.findRecent("CP1", null, 60);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).measurand()).isEqualTo("Power.Active.Import");
    }

    @Test
    void findRecentFiltersByConnector() {
        when(chargePointRepository.existsById("CP1")).thenReturn(true);
        when(meterRepository.findByChargePointIdAndConnectorIdAndTimestampAfterOrderByTimestampDesc(
                eq("CP1"), eq(1), any())).thenReturn(List.of());

        service.findRecent("CP1", 1, 60);

        ArgumentCaptor<Instant> after = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(meterRepository)
                .findByChargePointIdAndConnectorIdAndTimestampAfterOrderByTimestampDesc(
                        eq("CP1"), eq(1), after.capture());
        assertThat(after.getValue()).isBefore(Instant.now());
    }

    @Test
    void findRecentThrowsForUnknownChargePoint() {
        when(chargePointRepository.existsById("UNKNOWN")).thenReturn(false);
        assertThatThrownBy(() -> service.findRecent("UNKNOWN", null, 60))
                .isInstanceOf(ChargePointNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run test (RED)**

Run: `./mvnw test -Dtest=MeterServiceTest -q`

- [ ] **Step 3: Create `MeterService.java`**

```java
package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.MeterValueDto;
import com.accenture.nexcharge.simulator.model.entity.MeterReadingEntity;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.MeterReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MeterService {

    private static final int DEFAULT_LOOKBACK_MINUTES = 60;

    private final MeterReadingRepository meterRepository;
    private final ChargePointRepository chargePointRepository;

    public List<MeterValueDto> findRecent(String chargePointId, Integer connectorId, Integer lastMinutes) {
        if (!chargePointRepository.existsById(chargePointId)) {
            throw new ChargePointNotFoundException(chargePointId);
        }

        int minutes = lastMinutes != null ? lastMinutes : DEFAULT_LOOKBACK_MINUTES;
        Instant after = Instant.now().minus(minutes, ChronoUnit.MINUTES);

        List<MeterReadingEntity> entities = (connectorId == null)
                ? meterRepository.findByChargePointIdAndTimestampAfterOrderByTimestampDesc(chargePointId, after)
                : meterRepository.findByChargePointIdAndConnectorIdAndTimestampAfterOrderByTimestampDesc(
                        chargePointId, connectorId, after);

        return entities.stream().map(this::toDto).toList();
    }

    public void save(MeterReadingEntity entity) {
        meterRepository.save(entity);
    }

    public void saveAll(List<MeterReadingEntity> entities) {
        meterRepository.saveAll(entities);
    }

    private MeterValueDto toDto(MeterReadingEntity m) {
        return new MeterValueDto(
                m.getTimestamp(),
                m.getConnectorId(),
                m.getTransactionId(),
                m.getMeasurand(),
                m.getValue(),
                m.getUnit()
        );
    }
}
```

- [ ] **Step 4: Run test (PASS)**

Run: `./mvnw test -Dtest=MeterServiceTest -q`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/service/MeterService.java src/test/java/com/accenture/nexcharge/simulator/service/MeterServiceTest.java
git commit -m "service: add MeterService for time-window queries"
```

### Task 4.5: StatsService

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/service/StatsService.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/service/StatsServiceTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.StatsDto;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.ChargingSessionRepository;
import com.accenture.nexcharge.simulator.repository.ConnectorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock ChargePointRepository chargePointRepository;
    @Mock ConnectorRepository connectorRepository;
    @Mock ChargingSessionRepository sessionRepository;

    @InjectMocks StatsService service;

    @Test
    void aggregatesAllMetrics() {
        when(chargePointRepository.count()).thenReturn(5L);
        when(chargePointRepository.countByOnline(true)).thenReturn(4L);
        when(chargePointRepository.countByStatus(ChargePointStatus.Charging)).thenReturn(2L);
        when(chargePointRepository.countByStatus(ChargePointStatus.Available)).thenReturn(2L);
        when(chargePointRepository.countByStatus(ChargePointStatus.Faulted)).thenReturn(1L);
        when(sessionRepository.countByStatus(SessionStatus.Active)).thenReturn(2L);

        ConnectorEntity charging1 = ConnectorEntity.builder()
                .currentPowerKw(7.2).status(ConnectorStatus.Charging).build();
        ConnectorEntity charging2 = ConnectorEntity.builder()
                .currentPowerKw(22.0).status(ConnectorStatus.Charging).build();
        when(connectorRepository.findAll()).thenReturn(List.of(charging1, charging2));

        when(sessionRepository.countSince(any())).thenReturn(8L);
        when(sessionRepository.countSinceWithStatus(any(), org.mockito.ArgumentMatchers.eq(SessionStatus.Completed)))
                .thenReturn(6L);
        when(sessionRepository.sumEnergyDeliveredSince(any())).thenReturn(63.6);

        ChargingSessionEntity completed = ChargingSessionEntity.builder()
                .startTime(Instant.parse("2026-05-22T08:00:00Z"))
                .stopTime(Instant.parse("2026-05-22T09:35:00Z"))
                .energyDeliveredKwh(18.5)
                .status(SessionStatus.Completed).build();
        when(sessionRepository.findByStatus(SessionStatus.Completed))
                .thenReturn(List.of(completed));

        StatsDto stats = service.compute();
        assertThat(stats.totalChargePoints()).isEqualTo(5);
        assertThat(stats.onlineChargePoints()).isEqualTo(4);
        assertThat(stats.chargingNow()).isEqualTo(2);
        assertThat(stats.availableNow()).isEqualTo(2);
        assertThat(stats.faultedNow()).isEqualTo(1);
        assertThat(stats.activeSessionsCount()).isEqualTo(2);
        assertThat(stats.totalPowerKw()).isEqualTo(29.2);
        assertThat(stats.todayEnergyKwh()).isEqualTo(63.6);
        assertThat(stats.todaySessionsCount()).isEqualTo(8);
        assertThat(stats.todaySessionsCompleted()).isEqualTo(6);
        assertThat(stats.averageSessionDurationMinutes()).isEqualTo(95L);
        assertThat(stats.averageEnergyPerSessionKwh()).isEqualTo(18.5);
    }

    @Test
    void averageNullsWhenNoCompletedSessions() {
        when(chargePointRepository.count()).thenReturn(0L);
        when(chargePointRepository.countByOnline(true)).thenReturn(0L);
        when(chargePointRepository.countByStatus(any())).thenReturn(0L);
        when(sessionRepository.countByStatus(any())).thenReturn(0L);
        when(connectorRepository.findAll()).thenReturn(List.of());
        when(sessionRepository.countSince(any())).thenReturn(0L);
        when(sessionRepository.countSinceWithStatus(any(), any())).thenReturn(0L);
        when(sessionRepository.sumEnergyDeliveredSince(any())).thenReturn(0.0);
        when(sessionRepository.findByStatus(SessionStatus.Completed)).thenReturn(List.of());

        StatsDto stats = service.compute();
        assertThat(stats.averageSessionDurationMinutes()).isNull();
        assertThat(stats.averageEnergyPerSessionKwh()).isNull();
    }
}
```

- [ ] **Step 2: Run test (RED)**

Run: `./mvnw test -Dtest=StatsServiceTest -q`

- [ ] **Step 3: Create `StatsService.java`**

```java
package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.StatsDto;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.ChargingSessionRepository;
import com.accenture.nexcharge.simulator.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final ChargePointRepository chargePointRepository;
    private final ConnectorRepository connectorRepository;
    private final ChargingSessionRepository sessionRepository;

    public StatsDto compute() {
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);

        double totalPowerKw = connectorRepository.findAll().stream()
                .filter(c -> c.getStatus() == ConnectorStatus.Charging && c.getCurrentPowerKw() != null)
                .mapToDouble(c -> c.getCurrentPowerKw())
                .sum();

        List<ChargingSessionEntity> completedSessions = sessionRepository.findByStatus(SessionStatus.Completed);

        Long avgDurationMinutes = computeAverageDuration(completedSessions);
        Double avgEnergy = computeAverageEnergy(completedSessions);

        return new StatsDto(
                chargePointRepository.count(),
                chargePointRepository.countByOnline(true),
                chargePointRepository.countByStatus(ChargePointStatus.Charging),
                chargePointRepository.countByStatus(ChargePointStatus.Available),
                chargePointRepository.countByStatus(ChargePointStatus.Faulted),
                sessionRepository.countByStatus(SessionStatus.Active),
                round1(totalPowerKw),
                round1(sessionRepository.sumEnergyDeliveredSince(startOfToday)),
                sessionRepository.countSince(startOfToday),
                sessionRepository.countSinceWithStatus(startOfToday, SessionStatus.Completed),
                avgDurationMinutes,
                avgEnergy
        );
    }

    private Long computeAverageDuration(List<ChargingSessionEntity> sessions) {
        if (sessions.isEmpty()) {
            return null;
        }
        long totalMinutes = sessions.stream()
                .filter(s -> s.getStartTime() != null && s.getStopTime() != null)
                .mapToLong(s -> Duration.between(s.getStartTime(), s.getStopTime()).toMinutes())
                .sum();
        long count = sessions.stream()
                .filter(s -> s.getStartTime() != null && s.getStopTime() != null)
                .count();
        return count == 0 ? null : totalMinutes / count;
    }

    private Double computeAverageEnergy(List<ChargingSessionEntity> sessions) {
        if (sessions.isEmpty()) {
            return null;
        }
        double total = sessions.stream()
                .filter(s -> s.getEnergyDeliveredKwh() != null)
                .mapToDouble(ChargingSessionEntity::getEnergyDeliveredKwh)
                .sum();
        long count = sessions.stream()
                .filter(s -> s.getEnergyDeliveredKwh() != null)
                .count();
        return count == 0 ? null : round1(total / count);
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
```

- [ ] **Step 4: Run test (PASS)**

Run: `./mvnw test -Dtest=StatsServiceTest -q`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/service/StatsService.java src/test/java/com/accenture/nexcharge/simulator/service/StatsServiceTest.java
git commit -m "service: add StatsService aggregation"
```

### Task 4.6: LogService

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/service/LogService.java`

- [ ] **Step 1: Create `LogService.java` (no separate test — it's a thin wrapper used by handlers/controllers, covered indirectly)**

```java
package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.OcppLogDto;
import com.accenture.nexcharge.simulator.model.entity.OcppLogEntity;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import com.accenture.nexcharge.simulator.repository.OcppLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LogService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final OcppLogRepository repository;

    public void log(String chargePointId, LogDirection direction, String action, String payload) {
        repository.save(OcppLogEntity.builder()
                .chargePointId(chargePointId)
                .direction(direction)
                .action(action)
                .payload(payload)
                .timestamp(Instant.now())
                .build());
    }

    public List<OcppLogDto> search(String chargePointId, String action, LogDirection direction,
                                   Integer lastMinutes, Integer limit) {
        Instant after = lastMinutes != null
                ? Instant.now().minus(lastMinutes, ChronoUnit.MINUTES)
                : null;
        int safeLimit = limit == null ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return repository.search(chargePointId, action, direction, after, PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private OcppLogDto toDto(OcppLogEntity l) {
        return new OcppLogDto(
                l.getId(), l.getChargePointId(), l.getDirection(),
                l.getAction(), l.getPayload(), l.getTimestamp());
    }
}
```

- [ ] **Step 2: Compile**

Run: `./mvnw compile -q`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/service/LogService.java
git commit -m "service: add LogService for OCPP log persistence and search"
```

---

## Phase 5: CSMS Server + OCPP handlers

Goal: Spring-managed `JSONServer` from Java-OCA-OCPP listens on port 9000. `CsmsEventHandler` persists every OCPP message and publishes live events. Tests use mocked repos; real WebSocket integration in Phase 10.

### Task 5.1: OcppSessionRegistry

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/ocpp/OcppSessionRegistry.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/ocpp/OcppSessionRegistryTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.ocpp;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OcppSessionRegistryTest {

    @Test
    void registerAndLookup() {
        OcppSessionRegistry registry = new OcppSessionRegistry();
        UUID sessionId = UUID.randomUUID();
        registry.register("BORNE_A", sessionId);

        assertThat(registry.findSessionId("BORNE_A")).hasValue(sessionId);
        assertThat(registry.findChargePointId(sessionId)).hasValue("BORNE_A");
    }

    @Test
    void unregisterRemovesBothMappings() {
        OcppSessionRegistry registry = new OcppSessionRegistry();
        UUID sessionId = UUID.randomUUID();
        registry.register("BORNE_A", sessionId);
        registry.unregisterBySessionId(sessionId);

        assertThat(registry.findSessionId("BORNE_A")).isEmpty();
        assertThat(registry.findChargePointId(sessionId)).isEmpty();
    }

    @Test
    void replaceOldSessionWhenSameChargePointReconnects() {
        OcppSessionRegistry registry = new OcppSessionRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registry.register("BORNE_A", first);
        registry.register("BORNE_A", second);

        assertThat(registry.findSessionId("BORNE_A")).hasValue(second);
        assertThat(registry.findChargePointId(first)).isEmpty();
        assertThat(registry.findChargePointId(second)).hasValue("BORNE_A");
    }

    @Test
    void missingLookupsReturnEmpty() {
        OcppSessionRegistry registry = new OcppSessionRegistry();
        assertThat(registry.findSessionId("NOPE")).isEmpty();
        assertThat(registry.findChargePointId(UUID.randomUUID())).isEmpty();
    }
}
```

- [ ] **Step 2: Run test (RED)**

Run: `./mvnw test -Dtest=OcppSessionRegistryTest -q`
Expected: compilation error.

- [ ] **Step 3: Create `OcppSessionRegistry.java`**

```java
package com.accenture.nexcharge.simulator.ocpp;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class OcppSessionRegistry {

    private final ConcurrentMap<String, UUID> chargePointToSession = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> sessionToChargePoint = new ConcurrentHashMap<>();

    public synchronized void register(String chargePointId, UUID sessionId) {
        UUID existing = chargePointToSession.get(chargePointId);
        if (existing != null) {
            sessionToChargePoint.remove(existing);
        }
        chargePointToSession.put(chargePointId, sessionId);
        sessionToChargePoint.put(sessionId, chargePointId);
    }

    public synchronized void unregisterBySessionId(UUID sessionId) {
        String chargePointId = sessionToChargePoint.remove(sessionId);
        if (chargePointId != null) {
            chargePointToSession.remove(chargePointId, sessionId);
        }
    }

    public Optional<UUID> findSessionId(String chargePointId) {
        return Optional.ofNullable(chargePointToSession.get(chargePointId));
    }

    public Optional<String> findChargePointId(UUID sessionId) {
        return Optional.ofNullable(sessionToChargePoint.get(sessionId));
    }
}
```

- [ ] **Step 4: Run test (PASS)**

Run: `./mvnw test -Dtest=OcppSessionRegistryTest -q`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/ocpp/OcppSessionRegistry.java src/test/java/com/accenture/nexcharge/simulator/ocpp/OcppSessionRegistryTest.java
git commit -m "ocpp: add thread-safe session registry"
```

### Task 5.2: CsmsEventHandler

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/ocpp/CsmsEventHandler.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/ocpp/CsmsEventHandlerTest.java`

The Java-OCA-OCPP `ServerCoreEventHandler` interface has these methods:
- `handleAuthorizeRequest(UUID sessionIndex, AuthorizeRequest)` → `AuthorizeConfirmation`
- `handleBootNotificationRequest(UUID, BootNotificationRequest)` → `BootNotificationConfirmation`
- `handleDataTransferRequest(UUID, DataTransferRequest)` → `DataTransferConfirmation`
- `handleHeartbeatRequest(UUID, HeartbeatRequest)` → `HeartbeatConfirmation`
- `handleMeterValuesRequest(UUID, MeterValuesRequest)` → `MeterValuesConfirmation`
- `handleStartTransactionRequest(UUID, StartTransactionRequest)` → `StartTransactionConfirmation`
- `handleStatusNotificationRequest(UUID, StatusNotificationRequest)` → `StatusNotificationConfirmation`
- `handleStopTransactionRequest(UUID, StopTransactionRequest)` → `StopTransactionConfirmation`

- [ ] **Step 1: Write the test (covering happy paths for all 8 handlers)**

```java
package com.accenture.nexcharge.simulator.ocpp;

import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.ChargePointEntity;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.ChargingSessionRepository;
import com.accenture.nexcharge.simulator.repository.ConnectorRepository;
import com.accenture.nexcharge.simulator.repository.MeterReadingRepository;
import com.accenture.nexcharge.simulator.service.LiveEventService;
import com.accenture.nexcharge.simulator.service.LogService;
import eu.chargetime.ocpp.model.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsmsEventHandlerTest {

    @Mock ChargePointRepository chargePointRepository;
    @Mock ConnectorRepository connectorRepository;
    @Mock ChargingSessionRepository sessionRepository;
    @Mock MeterReadingRepository meterRepository;
    @Mock LogService logService;
    @Mock LiveEventService liveEventService;
    @Mock OcppSessionRegistry registry;

    SimulatorProperties properties;
    CsmsEventHandler handler;

    @BeforeEach
    void setUp() {
        properties = new SimulatorProperties(true, 15, 30, 10, 0.05, 0.02, List.of(), List.of("RFID-001"));
        handler = new CsmsEventHandler(
                chargePointRepository, connectorRepository, sessionRepository,
                meterRepository, logService, liveEventService, registry, properties);
    }

    @Test
    void bootNotificationAcceptsAndSavesEntity() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        when(chargePointRepository.findById("BORNE_A")).thenReturn(Optional.empty());

        BootNotificationRequest req = new BootNotificationRequest("Legrand", "Green'Up Premium");
        req.setChargePointSerialNumber("LGR-001");
        req.setFirmwareVersion("1.4.2");

        BootNotificationConfirmation conf = handler.handleBootNotificationRequest(sessionId, req);

        assertThat(conf.getStatus()).isEqualTo(RegistrationStatus.Accepted);
        assertThat(conf.getInterval()).isEqualTo(30);
        verify(chargePointRepository).save(any(ChargePointEntity.class));
    }

    @Test
    void heartbeatUpdatesLastHeartbeatTimestamp() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        ChargePointEntity cp = ChargePointEntity.builder().chargePointId("BORNE_A").build();
        when(chargePointRepository.findById("BORNE_A")).thenReturn(Optional.of(cp));

        HeartbeatConfirmation conf = handler.handleHeartbeatRequest(sessionId, new HeartbeatRequest());

        assertThat(conf.getCurrentTime()).isNotNull();
        verify(chargePointRepository).save(cp);
        assertThat(cp.getLastHeartbeat()).isNotNull();
    }

    @Test
    void authorizeAlwaysAccepts() {
        UUID sessionId = UUID.randomUUID();
        AuthorizeRequest req = new AuthorizeRequest("RFID-001");

        AuthorizeConfirmation conf = handler.handleAuthorizeRequest(sessionId, req);

        IdTagInfo info = conf.getIdTagInfo();
        assertThat(info.getStatus()).isEqualTo(AuthorizationStatus.Accepted);
    }

    @Test
    void startTransactionAssignsIncrementingTransactionIds() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_A", 1))
                .thenReturn(Optional.of(ConnectorEntity.builder().chargePointId("BORNE_A").connectorId(1).build()));

        StartTransactionRequest req1 = new StartTransactionRequest(1, "RFID-001", 0, ZonedDateTime.now());
        StartTransactionRequest req2 = new StartTransactionRequest(1, "RFID-001", 0, ZonedDateTime.now());

        StartTransactionConfirmation c1 = handler.handleStartTransactionRequest(sessionId, req1);
        StartTransactionConfirmation c2 = handler.handleStartTransactionRequest(sessionId, req2);

        assertThat(c1.getTransactionId()).isGreaterThanOrEqualTo(1000);
        assertThat(c2.getTransactionId()).isEqualTo(c1.getTransactionId() + 1);
        assertThat(c1.getIdTagInfo().getStatus()).isEqualTo(AuthorizationStatus.Accepted);
        verify(sessionRepository, org.mockito.Mockito.atLeast(2)).save(any(ChargingSessionEntity.class));
    }

    @Test
    void stopTransactionMarksSessionCompleted() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        ChargingSessionEntity entity = ChargingSessionEntity.builder()
                .id(1L).transactionId(1001).chargePointId("BORNE_A").connectorId(1)
                .meterStartWh(0.0).startTime(java.time.Instant.now())
                .status(SessionStatus.Active).build();
        when(sessionRepository.findByTransactionId(1001)).thenReturn(Optional.of(entity));

        StopTransactionRequest req = new StopTransactionRequest(5000, ZonedDateTime.now(), 1001);
        req.setReason(Reason.Local);

        StopTransactionConfirmation conf = handler.handleStopTransactionRequest(sessionId, req);

        assertThat(conf).isNotNull();
        ArgumentCaptor<ChargingSessionEntity> captor = ArgumentCaptor.forClass(ChargingSessionEntity.class);
        verify(sessionRepository).save(captor.capture());
        ChargingSessionEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.Completed);
        assertThat(saved.getEnergyDeliveredKwh()).isEqualTo(5.0);
        assertThat(saved.getStopReason()).isEqualTo("Local");
    }

    @Test
    void statusNotificationUpdatesConnector() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        ConnectorEntity connector = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1).build();
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_A", 1))
                .thenReturn(Optional.of(connector));

        StatusNotificationRequest req = new StatusNotificationRequest(
                1, ChargePointErrorCode.NoError, ChargePointStatus.Charging);

        StatusNotificationConfirmation conf = handler.handleStatusNotificationRequest(sessionId, req);

        assertThat(conf).isNotNull();
        verify(connectorRepository).save(connector);
        assertThat(connector.getStatus().toString()).isEqualTo("Charging");
    }

    @Test
    void meterValuesPersistsSampledValues() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));

        SampledValue sv = new SampledValue();
        sv.setValue("7200");
        sv.setMeasurand("Power.Active.Import");
        sv.setUnit(ValueFormat.Raw == null ? null : null);
        MeterValue mv = new MeterValue();
        mv.setTimestamp(ZonedDateTime.now());
        mv.setSampledValue(new SampledValue[]{sv});

        MeterValuesRequest req = new MeterValuesRequest(1);
        req.setTransactionId(1001);
        req.setMeterValue(new MeterValue[]{mv});

        MeterValuesConfirmation conf = handler.handleMeterValuesRequest(sessionId, req);

        assertThat(conf).isNotNull();
        verify(meterRepository).saveAll(any());
    }

    @Test
    void dataTransferAlwaysAccepts() {
        UUID sessionId = UUID.randomUUID();
        DataTransferRequest req = new DataTransferRequest("Legrand");

        DataTransferConfirmation conf = handler.handleDataTransferRequest(sessionId, req);

        assertThat(conf.getStatus()).isEqualTo(DataTransferStatus.Accepted);
    }
}
```

- [ ] **Step 2: Run test (RED)**

Run: `./mvnw test -Dtest=CsmsEventHandlerTest -q`
Expected: compilation error.

- [ ] **Step 3: Create `CsmsEventHandler.java`**

```java
package com.accenture.nexcharge.simulator.ocpp;

import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.dto.LiveEventDto;
import com.accenture.nexcharge.simulator.model.entity.*;
import com.accenture.nexcharge.simulator.model.enums.*;
import com.accenture.nexcharge.simulator.repository.*;
import com.accenture.nexcharge.simulator.service.LiveEventService;
import com.accenture.nexcharge.simulator.service.LogService;
import eu.chargetime.ocpp.model.core.*;
import eu.chargetime.ocpp.model.core.ChargePointStatus;
import eu.chargetime.ocpp.feature.profile.ServerCoreEventHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsmsEventHandler implements ServerCoreEventHandler {

    private static final int TRANSACTION_ID_START = 1000;

    private final ChargePointRepository chargePointRepository;
    private final ConnectorRepository connectorRepository;
    private final ChargingSessionRepository sessionRepository;
    private final MeterReadingRepository meterRepository;
    private final LogService logService;
    private final LiveEventService liveEventService;
    private final OcppSessionRegistry registry;
    private final SimulatorProperties properties;

    private final AtomicInteger transactionCounter = new AtomicInteger(TRANSACTION_ID_START);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    @Transactional
    public BootNotificationConfirmation handleBootNotificationRequest(UUID sessionIndex, BootNotificationRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        log.info("[CSMS] BootNotification from {}: {} {}",
                chargePointId, request.getChargePointVendor(), request.getChargePointModel());
        logIncoming(chargePointId, "BootNotification", request);

        ChargePointEntity entity = chargePointRepository.findById(chargePointId)
                .orElse(ChargePointEntity.builder()
                        .chargePointId(chargePointId)
                        .registeredAt(Instant.now())
                        .build());

        entity.setVendor(request.getChargePointVendor());
        entity.setModel(request.getChargePointModel());
        entity.setSerialNumber(request.getChargePointSerialNumber());
        entity.setFirmwareVersion(request.getFirmwareVersion());
        entity.setStatus(com.accenture.nexcharge.simulator.model.enums.ChargePointStatus.Available);
        entity.setOnline(true);
        entity.setLastHeartbeat(Instant.now());
        entity.setErrorCode("NoError");
        chargePointRepository.save(entity);

        liveEventService.publish(LiveEventDto.of(LiveEventType.CHARGE_POINT_CONNECTED, chargePointId,
                Map.of("vendor", request.getChargePointVendor(), "model", request.getChargePointModel())));

        BootNotificationConfirmation conf = new BootNotificationConfirmation(
                ZonedDateTime.now(), properties.heartbeatIntervalSeconds(), RegistrationStatus.Accepted);
        return conf;
    }

    @Override
    @Transactional
    public HeartbeatConfirmation handleHeartbeatRequest(UUID sessionIndex, HeartbeatRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        logIncoming(chargePointId, "Heartbeat", request);

        chargePointRepository.findById(chargePointId).ifPresent(cp -> {
            cp.setLastHeartbeat(Instant.now());
            cp.setOnline(true);
            chargePointRepository.save(cp);
        });

        liveEventService.publish(LiveEventDto.of(LiveEventType.HEARTBEAT, chargePointId, Map.of()));
        return new HeartbeatConfirmation(ZonedDateTime.now());
    }

    @Override
    public AuthorizeConfirmation handleAuthorizeRequest(UUID sessionIndex, AuthorizeRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        logIncoming(chargePointId, "Authorize", request);

        IdTagInfo info = new IdTagInfo(AuthorizationStatus.Accepted);
        info.setExpiryDate(ZonedDateTime.now().plusYears(1));
        return new AuthorizeConfirmation(info);
    }

    @Override
    @Transactional
    public StartTransactionConfirmation handleStartTransactionRequest(UUID sessionIndex, StartTransactionRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        log.info("[CSMS] StartTransaction from {} connector {} idTag {}",
                chargePointId, request.getConnectorId(), request.getIdTag());
        logIncoming(chargePointId, "StartTransaction", request);

        int transactionId = transactionCounter.getAndIncrement();
        Instant startTime = request.getTimestamp() != null
                ? request.getTimestamp().toInstant()
                : Instant.now();

        ChargingSessionEntity session = ChargingSessionEntity.builder()
                .transactionId(transactionId)
                .chargePointId(chargePointId)
                .connectorId(request.getConnectorId())
                .idTag(request.getIdTag())
                .startTime(startTime)
                .meterStartWh(request.getMeterStart() == null ? 0.0 : request.getMeterStart().doubleValue())
                .status(SessionStatus.Active)
                .build();
        sessionRepository.save(session);

        connectorRepository.findByChargePointIdAndConnectorId(chargePointId, request.getConnectorId())
                .ifPresent(c -> {
                    c.setStatus(ConnectorStatus.Charging);
                    connectorRepository.save(c);
                });

        liveEventService.publish(LiveEventDto.of(LiveEventType.SESSION_STARTED, chargePointId, request.getConnectorId(),
                Map.of("transactionId", transactionId, "idTag", request.getIdTag())));

        IdTagInfo info = new IdTagInfo(AuthorizationStatus.Accepted);
        return new StartTransactionConfirmation(info, transactionId);
    }

    @Override
    @Transactional
    public StopTransactionConfirmation handleStopTransactionRequest(UUID sessionIndex, StopTransactionRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        log.info("[CSMS] StopTransaction from {} txn {} reason {}",
                chargePointId, request.getTransactionId(), request.getReason());
        logIncoming(chargePointId, "StopTransaction", request);

        sessionRepository.findByTransactionId(request.getTransactionId()).ifPresent(s -> {
            double meterStop = request.getMeterStop() == null ? 0.0 : request.getMeterStop().doubleValue();
            double meterStart = s.getMeterStartWh() == null ? 0.0 : s.getMeterStartWh();
            s.setMeterStopWh(meterStop);
            s.setEnergyDeliveredKwh((meterStop - meterStart) / 1000.0);
            s.setStopTime(request.getTimestamp() != null ? request.getTimestamp().toInstant() : Instant.now());
            s.setStopReason(request.getReason() != null ? request.getReason().name() : "Local");
            s.setStatus(SessionStatus.Completed);
            sessionRepository.save(s);

            connectorRepository.findByChargePointIdAndConnectorId(chargePointId, s.getConnectorId())
                    .ifPresent(c -> {
                        c.setStatus(ConnectorStatus.Available);
                        c.setCurrentPowerKw(0.0);
                        c.setCurrentAmps(0.0);
                        connectorRepository.save(c);
                    });

            liveEventService.publish(LiveEventDto.of(LiveEventType.SESSION_STOPPED, chargePointId, s.getConnectorId(),
                    Map.of("transactionId", s.getTransactionId(),
                           "energyKwh", s.getEnergyDeliveredKwh(),
                           "reason", s.getStopReason())));
        });

        IdTagInfo info = new IdTagInfo(AuthorizationStatus.Accepted);
        StopTransactionConfirmation conf = new StopTransactionConfirmation();
        conf.setIdTagInfo(info);
        return conf;
    }

    @Override
    @Transactional
    public StatusNotificationConfirmation handleStatusNotificationRequest(UUID sessionIndex, StatusNotificationRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        log.info("[CSMS] StatusNotification {} connector {} status {} error {}",
                chargePointId, request.getConnectorId(), request.getStatus(), request.getErrorCode());
        logIncoming(chargePointId, "StatusNotification", request);

        ConnectorStatus newStatus = ConnectorStatus.valueOf(request.getStatus().name());
        String errorCode = request.getErrorCode() != null ? request.getErrorCode().name() : "NoError";

        if (request.getConnectorId() == 0) {
            chargePointRepository.findById(chargePointId).ifPresent(cp -> {
                cp.setStatus(com.accenture.nexcharge.simulator.model.enums.ChargePointStatus.valueOf(newStatus.name()));
                cp.setErrorCode(errorCode);
                chargePointRepository.save(cp);
            });
        } else {
            ConnectorEntity connector = connectorRepository
                    .findByChargePointIdAndConnectorId(chargePointId, request.getConnectorId())
                    .orElseGet(() -> ConnectorEntity.builder()
                            .chargePointId(chargePointId)
                            .connectorId(request.getConnectorId())
                            .build());
            connector.setStatus(newStatus);
            connector.setErrorCode(errorCode);
            connectorRepository.save(connector);

            if (newStatus == ConnectorStatus.Faulted) {
                liveEventService.publish(LiveEventDto.of(LiveEventType.FAULT, chargePointId,
                        request.getConnectorId(), Map.of("errorCode", errorCode)));
            }
        }

        liveEventService.publish(LiveEventDto.of(LiveEventType.STATUS_CHANGE, chargePointId,
                request.getConnectorId(),
                Map.of("status", newStatus.name(), "errorCode", errorCode)));

        return new StatusNotificationConfirmation();
    }

    @Override
    @Transactional
    public MeterValuesConfirmation handleMeterValuesRequest(UUID sessionIndex, MeterValuesRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        logIncoming(chargePointId, "MeterValues", request);

        Integer connectorId = request.getConnectorId();
        Integer txId = request.getTransactionId();
        List<MeterReadingEntity> readings = new ArrayList<>();
        Map<String, Double> latest = new HashMap<>();

        if (request.getMeterValue() != null) {
            for (MeterValue mv : request.getMeterValue()) {
                Instant ts = mv.getTimestamp() != null ? mv.getTimestamp().toInstant() : Instant.now();
                if (mv.getSampledValue() == null) continue;
                for (SampledValue sv : mv.getSampledValue()) {
                    double parsed = parseDouble(sv.getValue());
                    String measurand = sv.getMeasurand() != null ? sv.getMeasurand() : "Energy.Active.Import.Register";
                    String unit = sv.getUnit() != null ? sv.getUnit() : "Wh";
                    readings.add(MeterReadingEntity.builder()
                            .chargePointId(chargePointId)
                            .connectorId(connectorId)
                            .transactionId(txId)
                            .measurand(measurand)
                            .value(parsed)
                            .unit(unit)
                            .timestamp(ts)
                            .build());
                    latest.put(measurand, parsed);
                }
            }
        }

        if (!readings.isEmpty()) {
            meterRepository.saveAll(readings);

            connectorRepository.findByChargePointIdAndConnectorId(chargePointId, connectorId)
                    .ifPresent(c -> {
                        if (latest.containsKey("Power.Active.Import"))
                            c.setCurrentPowerKw(latest.get("Power.Active.Import") / 1000.0);
                        if (latest.containsKey("Current.Import"))
                            c.setCurrentAmps(latest.get("Current.Import"));
                        if (latest.containsKey("Voltage"))
                            c.setVoltage(latest.get("Voltage"));
                        if (latest.containsKey("Temperature"))
                            c.setTemperatureCelsius(latest.get("Temperature"));
                        if (latest.containsKey("Energy.Active.Import.Register"))
                            c.setTotalEnergyKwh(latest.get("Energy.Active.Import.Register") / 1000.0);
                        connectorRepository.save(c);
                    });

            liveEventService.publish(LiveEventDto.of(LiveEventType.METER_UPDATE, chargePointId, connectorId,
                    Map.of("readings", latest, "transactionId", txId)));
        }

        return new MeterValuesConfirmation();
    }

    @Override
    public DataTransferConfirmation handleDataTransferRequest(UUID sessionIndex, DataTransferRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        logIncoming(chargePointId, "DataTransfer", request);
        return new DataTransferConfirmation(DataTransferStatus.Accepted);
    }

    private void logIncoming(String chargePointId, String action, Object request) {
        try {
            String payload = objectMapper.writeValueAsString(request);
            logService.log(chargePointId, LogDirection.IN, action, payload);
        } catch (Exception e) {
            logService.log(chargePointId, LogDirection.IN, action, "<unserializable: " + e.getMessage() + ">");
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
```

- [ ] **Step 4: Run test (PASS)**

Run: `./mvnw test -Dtest=CsmsEventHandlerTest -q`

Note: if a test fails because the SDK API doesn't match exactly (constructor signatures, etc.), open the failing test, look at the actual SDK class with `./mvnw dependency:sources`, and adjust the test/handler. The SDK shape can drift between minor versions.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/ocpp/CsmsEventHandler.java src/test/java/com/accenture/nexcharge/simulator/ocpp/CsmsEventHandlerTest.java
git commit -m "ocpp: add CSMS event handler for all 8 OCPP 1.6 messages"
```

### Task 5.3: CsmsServer (boot the JSONServer)

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/ocpp/CsmsServer.java`

- [ ] **Step 1: Create `CsmsServer.java`**

```java
package com.accenture.nexcharge.simulator.ocpp;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.model.dto.LiveEventDto;
import com.accenture.nexcharge.simulator.model.enums.LiveEventType;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.service.LiveEventService;
import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.ServerEvents;
import eu.chargetime.ocpp.feature.profile.ServerCoreProfile;
import eu.chargetime.ocpp.model.SessionInformation;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsmsServer {

    private final OcppProperties ocppProperties;
    private final CsmsEventHandler eventHandler;
    private final OcppSessionRegistry sessionRegistry;
    private final LiveEventService liveEventService;
    private final ChargePointRepository chargePointRepository;

    private JSONServer server;

    @PostConstruct
    public void start() {
        ServerCoreProfile core = new ServerCoreProfile(eventHandler);
        server = new JSONServer(core);

        server.open(ocppProperties.server().host(), ocppProperties.server().port(), new ServerEvents() {
            @Override
            public boolean authenticateSession(SessionInformation info, String username, byte[] password) {
                return true;
            }

            @Override
            public void newSession(UUID sessionIndex, SessionInformation info) {
                String chargePointId = info.getIdentifier();
                if (chargePointId != null && chargePointId.startsWith("/")) {
                    chargePointId = chargePointId.substring(1);
                }
                if (chargePointId != null && chargePointId.startsWith("ocpp/")) {
                    chargePointId = chargePointId.substring("ocpp/".length());
                }

                log.info("[CSMS] New session: {} connected (sessionId={})", chargePointId, sessionIndex);
                sessionRegistry.register(chargePointId, sessionIndex);
            }

            @Override
            public void lostSession(UUID sessionIndex) {
                sessionRegistry.findChargePointId(sessionIndex).ifPresent(cpId -> {
                    log.info("[CSMS] Session lost: {}", cpId);
                    chargePointRepository.findById(cpId).ifPresent(cp -> {
                        cp.setOnline(false);
                        chargePointRepository.save(cp);
                    });
                    liveEventService.publish(LiveEventDto.of(
                            LiveEventType.CHARGE_POINT_DISCONNECTED, cpId, Map.of()));
                });
                sessionRegistry.unregisterBySessionId(sessionIndex);
            }
        });

        log.info("[CSMS] Server started on {}:{} (OCPP 1.6J)",
                ocppProperties.server().host(), ocppProperties.server().port());
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            try {
                server.close();
                log.info("[CSMS] Server stopped");
            } catch (Exception e) {
                log.warn("Error stopping CSMS server: {}", e.getMessage());
            }
        }
    }

    public boolean send(String chargePointId, eu.chargetime.ocpp.model.Request request) {
        return sessionRegistry.findSessionId(chargePointId)
                .map(sessionId -> {
                    try {
                        server.send(sessionId, request);
                        return true;
                    } catch (Exception e) {
                        log.warn("Failed to send {} to {}: {}",
                                request.getClass().getSimpleName(), chargePointId, e.getMessage());
                        return false;
                    }
                })
                .orElse(false);
    }
}
```

- [ ] **Step 2: Verify Spring boots and CSMS opens port 9000**

Run: `./mvnw spring-boot:run` → console shows `[CSMS] Server started on 0.0.0.0:9000`. Ctrl+C.

If port 9000 is already in use, check with `netstat -an | grep 9000` and either kill the holder or change `ocpp.server.port` in `application.yml`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/ocpp/CsmsServer.java
git commit -m "ocpp: add CSMS JSONServer lifecycle and session events"
```

---

## Phase 6: ChargePointSimulator state machine

Goal: One `ChargePointSimulator` instance per configured borne. It runs a state machine (BOOTING → AVAILABLE → PREPARING → CHARGING → FAULTED), connects via real WebSocket using `JSONClient`, sends OCPP messages, and accepts inbound commands (RemoteStart, RemoteStop, Reset, UnlockConnector, TriggerMessage).

### Task 6.1: SimulatorClientHandler

This is the inbound-message handler for each simulated charge point. Each `ChargePointSimulator` owns one. We isolate it from the simulator class so the simulator stays focused on outbound state.

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/simulator/SimulatorClientHandler.java`

- [ ] **Step 1: Create `SimulatorClientHandler.java`**

```java
package com.accenture.nexcharge.simulator.simulator;

import eu.chargetime.ocpp.feature.profile.ClientCoreEventHandler;
import eu.chargetime.ocpp.feature.profile.ClientRemoteTriggerEventHandler;
import eu.chargetime.ocpp.model.core.*;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageConfirmation;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequest;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageStatus;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SimulatorClientHandler implements ClientCoreEventHandler, ClientRemoteTriggerEventHandler {

    public interface InboundCommands {
        void onRemoteStart(int connectorId, String idTag);
        void onRemoteStop(int transactionId);
        void onReset(boolean hard);
        void onUnlock(int connectorId);
        void onTriggerStatusNotification(int connectorId);
        void onTriggerHeartbeat();
        void onTriggerMeterValues(int connectorId);
        void onTriggerBootNotification();
    }

    private final String chargePointId;
    private final InboundCommands commands;

    public SimulatorClientHandler(String chargePointId, InboundCommands commands) {
        this.chargePointId = chargePointId;
        this.commands = commands;
    }

    @Override
    public ChangeAvailabilityConfirmation handleChangeAvailabilityRequest(ChangeAvailabilityRequest request) {
        return new ChangeAvailabilityConfirmation(AvailabilityStatus.Accepted);
    }

    @Override
    public GetConfigurationConfirmation handleGetConfigurationRequest(GetConfigurationRequest request) {
        return new GetConfigurationConfirmation();
    }

    @Override
    public ChangeConfigurationConfirmation handleChangeConfigurationRequest(ChangeConfigurationRequest request) {
        return new ChangeConfigurationConfirmation(ConfigurationStatus.Accepted);
    }

    @Override
    public ClearCacheConfirmation handleClearCacheRequest(ClearCacheRequest request) {
        return new ClearCacheConfirmation(ClearCacheStatus.Accepted);
    }

    @Override
    public DataTransferConfirmation handleDataTransferRequest(DataTransferRequest request) {
        return new DataTransferConfirmation(DataTransferStatus.Accepted);
    }

    @Override
    public RemoteStartTransactionConfirmation handleRemoteStartTransactionRequest(RemoteStartTransactionRequest request) {
        log.info("[{}] RemoteStart received connector={} idTag={}",
                chargePointId, request.getConnectorId(), request.getIdTag());
        int connectorId = request.getConnectorId() == null ? 1 : request.getConnectorId();
        commands.onRemoteStart(connectorId, request.getIdTag());
        return new RemoteStartTransactionConfirmation(RemoteStartStopStatus.Accepted);
    }

    @Override
    public RemoteStopTransactionConfirmation handleRemoteStopTransactionRequest(RemoteStopTransactionRequest request) {
        log.info("[{}] RemoteStop received txn={}", chargePointId, request.getTransactionId());
        commands.onRemoteStop(request.getTransactionId());
        return new RemoteStopTransactionConfirmation(RemoteStartStopStatus.Accepted);
    }

    @Override
    public ResetConfirmation handleResetRequest(ResetRequest request) {
        log.info("[{}] Reset received type={}", chargePointId, request.getType());
        commands.onReset(request.getType() == ResetType.Hard);
        return new ResetConfirmation(ResetStatus.Accepted);
    }

    @Override
    public UnlockConnectorConfirmation handleUnlockConnectorRequest(UnlockConnectorRequest request) {
        log.info("[{}] UnlockConnector received connector={}", chargePointId, request.getConnectorId());
        commands.onUnlock(request.getConnectorId());
        return new UnlockConnectorConfirmation(UnlockStatus.Unlocked);
    }

    @Override
    public TriggerMessageConfirmation handleTriggerMessageRequest(TriggerMessageRequest request) {
        TriggerMessageRequestType type = request.getRequestedMessage();
        Integer connectorId = request.getConnectorId();
        log.info("[{}] TriggerMessage received type={} connector={}", chargePointId, type, connectorId);
        switch (type) {
            case StatusNotification -> commands.onTriggerStatusNotification(connectorId == null ? 0 : connectorId);
            case Heartbeat -> commands.onTriggerHeartbeat();
            case MeterValues -> commands.onTriggerMeterValues(connectorId == null ? 1 : connectorId);
            case BootNotification -> commands.onTriggerBootNotification();
            default -> {
                return new TriggerMessageConfirmation(TriggerMessageStatus.NotImplemented);
            }
        }
        return new TriggerMessageConfirmation(TriggerMessageStatus.Accepted);
    }
}
```

- [ ] **Step 2: Write a small unit test for TriggerMessage routing**

**File:** `src/test/java/com/accenture/nexcharge/simulator/simulator/SimulatorClientHandlerTest.java`

```java
package com.accenture.nexcharge.simulator.simulator;

import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequest;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SimulatorClientHandlerTest {

    private final SimulatorClientHandler.InboundCommands commands =
            mock(SimulatorClientHandler.InboundCommands.class);
    private final SimulatorClientHandler handler = new SimulatorClientHandler("BORNE_A", commands);

    @Test
    void triggerHeartbeatRoutesToInbound() {
        TriggerMessageRequest req = new TriggerMessageRequest(TriggerMessageRequestType.Heartbeat);
        var conf = handler.handleTriggerMessageRequest(req);
        assertThat(conf.getStatus()).isEqualTo(TriggerMessageStatus.Accepted);
        verify(commands).onTriggerHeartbeat();
    }

    @Test
    void triggerStatusNotificationRoutesToInbound() {
        TriggerMessageRequest req = new TriggerMessageRequest(TriggerMessageRequestType.StatusNotification);
        req.setConnectorId(1);
        var conf = handler.handleTriggerMessageRequest(req);
        assertThat(conf.getStatus()).isEqualTo(TriggerMessageStatus.Accepted);
        verify(commands).onTriggerStatusNotification(1);
    }

    @Test
    void remoteStartRoutesToInbound() {
        var req = new eu.chargetime.ocpp.model.core.RemoteStartTransactionRequest("RFID-0001");
        req.setConnectorId(1);
        var conf = handler.handleRemoteStartTransactionRequest(req);
        assertThat(conf.getStatus()).isEqualTo(eu.chargetime.ocpp.model.core.RemoteStartStopStatus.Accepted);
        verify(commands).onRemoteStart(1, "RFID-0001");
    }
}
```

- [ ] **Step 3: Run the test (PASS)**

Run: `./mvnw test -Dtest=SimulatorClientHandlerTest -q`
Expected: 3 tests passed.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/simulator/SimulatorClientHandler.java src/test/java/com/accenture/nexcharge/simulator/simulator/SimulatorClientHandlerTest.java
git commit -m "simulator: add inbound OCPP command handler with TriggerMessage support"
```

### Task 6.2: ChargePointSimulator state machine — pure state transitions (TDD)

Before wiring the real `JSONClient`, we test the state machine in isolation with an injected `OcppClient` interface so we can assert transitions deterministically.

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/simulator/OcppClient.java`
- Create: `src/main/java/com/accenture/nexcharge/simulator/simulator/ChargePointSimulator.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/simulator/ChargePointSimulatorStateMachineTest.java`

- [ ] **Step 1: Define `OcppClient` interface**

```java
package com.accenture.nexcharge.simulator.simulator;

import eu.chargetime.ocpp.model.Request;

import java.util.concurrent.CompletableFuture;

public interface OcppClient {
    boolean connect();
    void disconnect();
    boolean isConnected();
    CompletableFuture<?> send(Request request);
}
```

- [ ] **Step 2: Write the state-machine test**

```java
package com.accenture.nexcharge.simulator.simulator;

import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties.ChargePointConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChargePointSimulatorStateMachineTest {

    private OcppClient client;
    private ChargePointSimulator simulator;
    private SimulatorProperties props;
    private ChargePointConfig config;

    @BeforeEach
    void setUp() {
        client = mock(OcppClient.class);
        when(client.connect()).thenReturn(true);
        when(client.isConnected()).thenReturn(true);
        when(client.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        config = new ChargePointConfig("BORNE_TEST", "Legrand", "Green'Up Premium",
                "LGR-TEST", 7.4, 1, "1.4.2");
        props = new SimulatorProperties(true, 15, 30, 10, 0.0, 0.0,
                List.of(config), List.of("RFID-TEST"));

        simulator = new ChargePointSimulator(config, props, client, new Random(42L));
    }

    @Test
    void initialStateIsBooting() {
        assertThat(simulator.getState()).isEqualTo(SimulatorState.BOOTING);
    }

    @Test
    void bootTransitionsToAvailable() {
        simulator.boot();
        assertThat(simulator.getState()).isEqualTo(SimulatorState.AVAILABLE);
    }

    @Test
    void startSessionFromAvailableTransitionsToPreparing() {
        simulator.boot();
        simulator.startSession(1, "RFID-TEST");
        assertThat(simulator.getState()).isEqualTo(SimulatorState.PREPARING);
    }

    @Test
    void confirmCableTransitionsToCharging() {
        simulator.boot();
        simulator.startSession(1, "RFID-TEST");
        simulator.confirmCablePluggedAndStartCharging(5001);
        assertThat(simulator.getState()).isEqualTo(SimulatorState.CHARGING);
        assertThat(simulator.getCurrentTransactionId()).isEqualTo(5001);
    }

    @Test
    void stopSessionFromChargingReturnsToAvailable() {
        simulator.boot();
        simulator.startSession(1, "RFID-TEST");
        simulator.confirmCablePluggedAndStartCharging(5001);
        simulator.stopSession("Local");
        assertThat(simulator.getState()).isEqualTo(SimulatorState.AVAILABLE);
        assertThat(simulator.getCurrentTransactionId()).isNull();
    }

    @Test
    void faultFromAnyStateTransitionsToFaulted() {
        simulator.boot();
        simulator.fault("GroundFailure");
        assertThat(simulator.getState()).isEqualTo(SimulatorState.FAULTED);
    }

    @Test
    void recoverFromFaultedReturnsToAvailable() {
        simulator.boot();
        simulator.fault("GroundFailure");
        simulator.recoverFromFault();
        assertThat(simulator.getState()).isEqualTo(SimulatorState.AVAILABLE);
    }

    @Test
    void resetReturnsToBooting() {
        simulator.boot();
        simulator.reset();
        assertThat(simulator.getState()).isEqualTo(SimulatorState.BOOTING);
    }

    @Test
    void cannotStartSessionWhenNotAvailable() {
        // BOOTING state — startSession is a no-op
        simulator.startSession(1, "RFID-TEST");
        assertThat(simulator.getState()).isEqualTo(SimulatorState.BOOTING);
    }

    @Test
    void cannotStopSessionWhenNotCharging() {
        simulator.boot();
        // AVAILABLE state — stopSession is a no-op
        simulator.stopSession("Local");
        assertThat(simulator.getState()).isEqualTo(SimulatorState.AVAILABLE);
    }

    @Test
    void bootSendsBootNotificationAndStatusNotifications() {
        simulator.boot();
        // BootNotification + 1 StatusNotification(connector=0) + 1 per connector
        verify(client, org.mockito.Mockito.atLeast(2)).send(any());
    }
}
```

- [ ] **Step 3: Run test (RED — class doesn't exist)**

Run: `./mvnw test -Dtest=ChargePointSimulatorStateMachineTest -q`
Expected: compilation failure.

- [ ] **Step 4: Create `ChargePointSimulator.java`**

```java
package com.accenture.nexcharge.simulator.simulator;

import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties.ChargePointConfig;
import eu.chargetime.ocpp.model.core.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ChargePointSimulator {

    private final ChargePointConfig config;
    private final SimulatorProperties properties;
    private final OcppClient client;
    private final Random random;
    private final ChargingProfile chargingProfile;

    private final AtomicReference<SimulatorState> state = new AtomicReference<>(SimulatorState.BOOTING);
    @Getter
    private volatile Integer currentTransactionId;
    @Getter
    private volatile Integer currentConnectorId;
    @Getter
    private volatile String currentIdTag;
    @Getter
    private volatile double meterStartWh;
    @Getter
    private volatile double currentMeterWh;

    public ChargePointSimulator(ChargePointConfig config, SimulatorProperties properties,
                                OcppClient client, Random random) {
        this.config = config;
        this.properties = properties;
        this.client = client;
        this.random = random;
        this.chargingProfile = new ChargingProfile(
                config.maxPowerKw(), properties.accelerationFactor(), random);
    }

    public ChargePointConfig getConfig() {
        return config;
    }

    public SimulatorState getState() {
        return state.get();
    }

    public synchronized void boot() {
        if (state.get() != SimulatorState.BOOTING) {
            return;
        }
        sendBootNotification();
        sendStatusNotification(0, ChargePointStatus.Available);
        for (int connectorId = 1; connectorId <= config.connectors(); connectorId++) {
            sendStatusNotification(connectorId, ChargePointStatus.Available);
        }
        state.set(SimulatorState.AVAILABLE);
        log.info("[{}] Booted — Status: Available", config.id());
    }

    public synchronized void startSession(int connectorId, String idTag) {
        if (state.get() != SimulatorState.AVAILABLE) {
            log.debug("[{}] startSession ignored, current state {}", config.id(), state.get());
            return;
        }
        sendAuthorize(idTag);
        sendStatusNotification(connectorId, ChargePointStatus.Preparing);
        currentConnectorId = connectorId;
        currentIdTag = idTag;
        state.set(SimulatorState.PREPARING);
        log.info("[{}] Preparing — connector {} idTag {}", config.id(), connectorId, idTag);
    }

    public synchronized void confirmCablePluggedAndStartCharging(int assignedTransactionId) {
        if (state.get() != SimulatorState.PREPARING) {
            return;
        }
        currentTransactionId = assignedTransactionId;
        meterStartWh = currentMeterWh;
        sendStatusNotification(currentConnectorId, ChargePointStatus.Charging);
        state.set(SimulatorState.CHARGING);
        log.info("[{}] Charging — txn {}", config.id(), assignedTransactionId);
    }

    public synchronized void stopSession(String reason) {
        if (state.get() != SimulatorState.CHARGING) {
            return;
        }
        sendStopTransaction(reason);
        sendStatusNotification(currentConnectorId, ChargePointStatus.Available);
        currentTransactionId = null;
        currentIdTag = null;
        currentConnectorId = null;
        state.set(SimulatorState.AVAILABLE);
        log.info("[{}] Stopped — reason {}", config.id(), reason);
    }

    public synchronized void fault(String errorCode) {
        if (state.get() == SimulatorState.CHARGING) {
            stopSession("Other");
        }
        int connectorId = currentConnectorId != null ? currentConnectorId : 1;
        sendStatusNotificationWithError(connectorId, ChargePointStatus.Faulted, errorCode);
        state.set(SimulatorState.FAULTED);
        log.warn("[{}] FAULTED — {}", config.id(), errorCode);
    }

    public synchronized void recoverFromFault() {
        if (state.get() != SimulatorState.FAULTED) {
            return;
        }
        int connectorId = currentConnectorId != null ? currentConnectorId : 1;
        sendStatusNotificationWithError(connectorId, ChargePointStatus.Available, "NoError");
        state.set(SimulatorState.AVAILABLE);
        log.info("[{}] Recovered from fault", config.id());
    }

    public synchronized void reset() {
        if (state.get() == SimulatorState.CHARGING) {
            stopSession("Reset");
        }
        currentTransactionId = null;
        currentIdTag = null;
        currentConnectorId = null;
        state.set(SimulatorState.BOOTING);
        log.info("[{}] Reset — state BOOTING", config.id());
    }

    /** Called every meter-interval-seconds while in CHARGING. */
    public synchronized PowerSnapshot tickMeter(Duration realElapsed) {
        if (state.get() != SimulatorState.CHARGING) {
            return null;
        }
        PowerSnapshot snap = chargingProfile.tick(realElapsed);
        currentMeterWh = meterStartWh + snap.totalEnergyKwh() * 1000.0;
        sendMeterValues(snap);
        if (chargingProfile.isComplete()) {
            stopSession("Local");
        }
        return snap;
    }

    public synchronized void sendHeartbeat() {
        if (!client.isConnected()) {
            return;
        }
        client.send(new HeartbeatRequest());
    }

    public synchronized void triggerStatusNotification(int connectorId) {
        ChargePointStatus status = mapStateToStatus();
        sendStatusNotification(connectorId, status);
    }

    public synchronized void triggerBootNotification() {
        sendBootNotification();
    }

    private ChargePointStatus mapStateToStatus() {
        return switch (state.get()) {
            case BOOTING, AVAILABLE -> ChargePointStatus.Available;
            case PREPARING -> ChargePointStatus.Preparing;
            case CHARGING -> ChargePointStatus.Charging;
            case FAULTED -> ChargePointStatus.Faulted;
        };
    }

    private void sendBootNotification() {
        BootNotificationRequest req = new BootNotificationRequest(config.vendor(), config.model());
        req.setChargePointSerialNumber(config.serial());
        req.setFirmwareVersion(config.firmware());
        client.send(req);
    }

    private void sendAuthorize(String idTag) {
        client.send(new AuthorizeRequest(idTag));
    }

    private void sendStatusNotification(int connectorId, ChargePointStatus status) {
        StatusNotificationRequest req = new StatusNotificationRequest(
                connectorId, ChargePointErrorCode.NoError, status);
        req.setTimestamp(ZonedDateTime.now());
        client.send(req);
    }

    private void sendStatusNotificationWithError(int connectorId, ChargePointStatus status, String errorCode) {
        ChargePointErrorCode code;
        try {
            code = ChargePointErrorCode.valueOf(errorCode);
        } catch (IllegalArgumentException e) {
            code = ChargePointErrorCode.NoError;
        }
        StatusNotificationRequest req = new StatusNotificationRequest(connectorId, code, status);
        req.setTimestamp(ZonedDateTime.now());
        client.send(req);
    }

    public void sendStartTransaction(int connectorId, String idTag) {
        StartTransactionRequest req = new StartTransactionRequest(
                connectorId, idTag, (int) currentMeterWh, ZonedDateTime.now());
        client.send(req);
    }

    private void sendStopTransaction(String reason) {
        if (currentTransactionId == null) {
            return;
        }
        StopTransactionRequest req = new StopTransactionRequest(
                (int) currentMeterWh, ZonedDateTime.now(), currentTransactionId);
        try {
            req.setReason(Reason.valueOf(reason));
        } catch (IllegalArgumentException ignored) {
            req.setReason(Reason.Local);
        }
        client.send(req);
    }

    private void sendMeterValues(PowerSnapshot snap) {
        if (currentConnectorId == null) {
            return;
        }
        MeterValuesRequest req = new MeterValuesRequest(currentConnectorId);
        if (currentTransactionId != null) {
            req.setTransactionId(currentTransactionId);
        }
        ZonedDateTime ts = ZonedDateTime.now();
        req.setMeterValue(new MeterValue[]{ buildMeterValue(ts, snap) });
        client.send(req);
    }

    private MeterValue buildMeterValue(ZonedDateTime ts, PowerSnapshot snap) {
        MeterValue mv = new MeterValue();
        mv.setTimestamp(ts);
        mv.setSampledValue(new SampledValue[]{
                sampledValue(String.valueOf(currentMeterWh), "Energy.Active.Import.Register", "Wh"),
                sampledValue(String.format(java.util.Locale.ROOT, "%.1f", snap.powerKw() * 1000.0), "Power.Active.Import", "W"),
                sampledValue(String.format(java.util.Locale.ROOT, "%.2f", snap.currentAmps()), "Current.Import", "A"),
                sampledValue(String.format(java.util.Locale.ROOT, "%.1f", snap.voltage()), "Voltage", "V"),
                sampledValue(String.format(java.util.Locale.ROOT, "%.1f", snap.temperatureCelsius()), "Temperature", "Celsius")
        });
        return mv;
    }

    private SampledValue sampledValue(String value, String measurand, String unit) {
        SampledValue sv = new SampledValue();
        sv.setValue(value);
        sv.setMeasurand(measurand);
        sv.setUnit(unit);
        return sv;
    }
}
```

- [ ] **Step 5: Run test (PASS)**

Run: `./mvnw test -Dtest=ChargePointSimulatorStateMachineTest -q`
Expected: 11 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/simulator/OcppClient.java src/main/java/com/accenture/nexcharge/simulator/simulator/ChargePointSimulator.java src/test/java/com/accenture/nexcharge/simulator/simulator/ChargePointSimulatorStateMachineTest.java
git commit -m "simulator: add ChargePointSimulator state machine with TDD"
```

### Task 6.3: JsonOcppClient adapter (real WebSocket transport)

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/simulator/JsonOcppClient.java`

- [ ] **Step 1: Create `JsonOcppClient.java`**

```java
package com.accenture.nexcharge.simulator.simulator;

import eu.chargetime.ocpp.JSONClient;
import eu.chargetime.ocpp.feature.profile.ClientCoreProfile;
import eu.chargetime.ocpp.feature.profile.ClientRemoteTriggerProfile;
import eu.chargetime.ocpp.model.Request;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class JsonOcppClient implements OcppClient {

    private static final long INITIAL_BACKOFF_MS = 2000L;
    private static final long MAX_BACKOFF_MS = 30000L;

    private final String chargePointId;
    private final String csmsUrl;
    private final ClientCoreProfile core;
    private final ClientRemoteTriggerProfile remoteTrigger;

    private JSONClient jsonClient;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public JsonOcppClient(String chargePointId, String csmsUrl,
                          ClientCoreProfile core, ClientRemoteTriggerProfile remoteTrigger) {
        this.chargePointId = chargePointId;
        this.csmsUrl = csmsUrl;
        this.core = core;
        this.remoteTrigger = remoteTrigger;
    }

    @Override
    public boolean connect() {
        long backoff = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                jsonClient = new JSONClient(core, chargePointId);
                jsonClient.addFeatureProfile(remoteTrigger);
                jsonClient.connect(csmsUrl, null);
                connected.set(true);
                log.info("[{}] Connected to CSMS at {}", chargePointId, csmsUrl);
                return true;
            } catch (Exception e) {
                log.warn("[{}] Connection attempt {} failed: {}", chargePointId, attempt, e.getMessage());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        }
        return false;
    }

    @Override
    public void disconnect() {
        if (jsonClient != null) {
            try {
                jsonClient.disconnect();
            } catch (Exception e) {
                log.warn("[{}] Error during disconnect: {}", chargePointId, e.getMessage());
            }
        }
        connected.set(false);
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public CompletableFuture<?> send(Request request) {
        if (jsonClient == null || !connected.get()) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Not connected"));
            return failed;
        }
        try {
            return jsonClient.send(request);
        } catch (Exception e) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./mvnw compile -q`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/simulator/JsonOcppClient.java
git commit -m "simulator: add JSONClient adapter with retry/backoff and RemoteTrigger profile"
```

---

## Phase 7: SimulatorManager + scenarios

Goal: Spring orchestrator that creates a `ChargePointSimulator` per configured borne, sequences boot at 2s intervals, and runs scheduled ticks (heartbeat 30s, meter 10s, auto-session/fault probabilities). Exposes a service that triggers scenarios on demand.

### Task 7.1: SimulatorManager

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/simulator/SimulatorManager.java`

- [ ] **Step 1: Create `SimulatorManager.java`**

```java
package com.accenture.nexcharge.simulator.simulator;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties.ChargePointConfig;
import com.accenture.nexcharge.simulator.ocpp.CsmsServer;
import com.accenture.nexcharge.simulator.ocpp.OcppSessionRegistry;
import com.accenture.nexcharge.simulator.simulator.SimulatorClientHandler.InboundCommands;
import eu.chargetime.ocpp.feature.profile.ClientCoreProfile;
import eu.chargetime.ocpp.feature.profile.ClientRemoteTriggerProfile;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class SimulatorManager {

    private static final long BOOT_DELAY_MS = 2000L;

    private final SimulatorProperties properties;
    private final OcppProperties ocppProperties;
    private final CsmsServer csmsServer;
    private final OcppSessionRegistry registry;

    private final Map<String, ChargePointSimulator> simulators = new ConcurrentHashMap<>();
    private final Map<String, JsonOcppClient> clients = new ConcurrentHashMap<>();
    private final Random globalRandom = new Random();
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        if (!properties.enabled()) {
            log.info("[SIMULATOR] disabled — not starting any simulated charge point");
            return;
        }
        scheduler = Executors.newScheduledThreadPool(2,
                Thread.ofVirtual().name("simulator-", 1).factory());

        log.info("[SIMULATOR] Starting {} simulated charge points...", properties.chargePoints().size());

        long delay = 0L;
        for (ChargePointConfig cfg : properties.chargePoints()) {
            scheduler.schedule(() -> bootOne(cfg), delay, TimeUnit.MILLISECONDS);
            delay += BOOT_DELAY_MS;
        }
    }

    private void bootOne(ChargePointConfig cfg) {
        String url = String.format("ws://%s:%d", "localhost", ocppProperties.server().port());
        SimulatorClientHandler handler = new SimulatorClientHandler(cfg.id(), inboundCommandsFor(cfg.id()));
        ClientCoreProfile core = new ClientCoreProfile(handler);
        ClientRemoteTriggerProfile remoteTrigger = new ClientRemoteTriggerProfile(handler);
        JsonOcppClient client = new JsonOcppClient(cfg.id(), url, core, remoteTrigger);
        clients.put(cfg.id(), client);

        ChargePointSimulator sim = new ChargePointSimulator(
                cfg, properties, client, new Random(globalRandom.nextLong()));
        simulators.put(cfg.id(), sim);

        if (client.connect()) {
            sim.boot();
        } else {
            log.error("[{}] Failed to connect after retries", cfg.id());
        }
    }

    private InboundCommands inboundCommandsFor(String chargePointId) {
        return new InboundCommands() {
            @Override public void onRemoteStart(int connectorId, String idTag) {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null) s.startSession(connectorId, idTag);
            }
            @Override public void onRemoteStop(int transactionId) {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null && transactionId == nullSafe(s.getCurrentTransactionId()))
                    s.stopSession("Remote");
            }
            @Override public void onReset(boolean hard) {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null) s.reset();
                JsonOcppClient c = clients.get(chargePointId);
                if (c != null) {
                    c.disconnect();
                    if (c.connect() && s != null) s.boot();
                }
            }
            @Override public void onUnlock(int connectorId) {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null && s.getState() == SimulatorState.CHARGING)
                    s.stopSession("UnlockCommand");
            }
            @Override public void onTriggerStatusNotification(int connectorId) {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null) s.triggerStatusNotification(connectorId);
            }
            @Override public void onTriggerHeartbeat() {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null) s.sendHeartbeat();
            }
            @Override public void onTriggerMeterValues(int connectorId) {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null && s.getState() == SimulatorState.CHARGING) {
                    s.tickMeter(Duration.ofSeconds(properties.meterIntervalSeconds()));
                }
            }
            @Override public void onTriggerBootNotification() {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null) s.triggerBootNotification();
            }
        };
    }

    private int nullSafe(Integer v) { return v == null ? -1 : v; }

    @Scheduled(fixedDelayString = "${simulator.heartbeat-interval-seconds:30}000")
    public void heartbeatTick() {
        if (!properties.enabled()) return;
        for (ChargePointSimulator s : simulators.values()) {
            try { s.sendHeartbeat(); } catch (Exception e) { log.debug("heartbeat error: {}", e.getMessage()); }
        }
    }

    @Scheduled(fixedDelayString = "${simulator.meter-interval-seconds:10}000")
    public void meterTick() {
        if (!properties.enabled()) return;
        Duration elapsed = Duration.ofSeconds(properties.meterIntervalSeconds());
        for (ChargePointSimulator s : simulators.values()) {
            try {
                if (s.getState() == SimulatorState.CHARGING) {
                    s.tickMeter(elapsed);
                }
            } catch (Exception e) {
                log.debug("meter tick error for {}: {}", s.getConfig().id(), e.getMessage());
            }
        }
    }

    /** Auto-trigger sessions and faults probabilistically. */
    @Scheduled(fixedDelayString = "${simulator.heartbeat-interval-seconds:30}000")
    public void worldTick() {
        if (!properties.enabled()) return;
        for (ChargePointSimulator s : simulators.values()) {
            try {
                if (s.getState() == SimulatorState.AVAILABLE
                        && globalRandom.nextDouble() < properties.autoSessionProbability()) {
                    String idTag = pickRandomRfid();
                    s.startSession(1, idTag);
                    finishPreparing(s);
                } else if (s.getState() == SimulatorState.CHARGING
                        && globalRandom.nextDouble() < properties.randomEventProbability()) {
                    s.fault("GroundFailure");
                    scheduleRecovery(s);
                }
            } catch (Exception e) {
                log.debug("world tick error: {}", e.getMessage());
            }
        }
    }

    private void finishPreparing(ChargePointSimulator s) {
        scheduler.schedule(() -> {
            int txnId = -1;
            try {
                txnId = ((Number) ((java.util.concurrent.CompletableFuture<?>) s.sendStartTransactionAndAwait()).get()).intValue();
            } catch (Throwable ignored) {}
            if (txnId > 0) s.confirmCablePluggedAndStartCharging(txnId);
            else s.confirmCablePluggedAndStartCharging(globalRandom.nextInt(900_000) + 100_000);
        }, 3, TimeUnit.SECONDS);
    }

    private void scheduleRecovery(ChargePointSimulator s) {
        long delay = 30 + globalRandom.nextInt(90);
        scheduler.schedule(s::recoverFromFault, delay, TimeUnit.SECONDS);
    }

    private String pickRandomRfid() {
        var tags = properties.rfidTags();
        return tags.isEmpty() ? "RFID-DEFAULT" : tags.get(globalRandom.nextInt(tags.size()));
    }

    public Collection<ChargePointSimulator> getAll() { return simulators.values(); }
    public ChargePointSimulator get(String id) { return simulators.get(id); }

    @PreDestroy
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        for (JsonOcppClient c : clients.values()) {
            try { c.disconnect(); } catch (Exception ignored) {}
        }
    }
}
```

- [ ] **Step 2: Add `sendStartTransactionAndAwait` helper to `ChargePointSimulator.java`**

Replace the existing `sendStartTransaction` method in `ChargePointSimulator.java` with:

```java
    public java.util.concurrent.CompletableFuture<?> sendStartTransactionAndAwait() {
        if (currentConnectorId == null || currentIdTag == null) {
            java.util.concurrent.CompletableFuture<Integer> failed = new java.util.concurrent.CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("No pending session"));
            return failed;
        }
        StartTransactionRequest req = new StartTransactionRequest(
                currentConnectorId, currentIdTag, (int) currentMeterWh, ZonedDateTime.now());
        return client.send(req);
    }
```

- [ ] **Step 3: Verify boot end-to-end**

Run: `./mvnw spring-boot:run`
Expected: console shows `[SIMULATOR] Starting 5 simulated charge points...` followed by 5 `[BORNE_X] Booted — Status: Available`. Open `http://localhost:8080/h2-console`, login (JDBC URL `jdbc:h2:file:./data/csms`), `SELECT * FROM charge_points` → 5 rows. Ctrl+C.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/simulator/SimulatorManager.java src/main/java/com/accenture/nexcharge/simulator/simulator/ChargePointSimulator.java
git commit -m "simulator: add SimulatorManager with scheduled ticks and probabilistic events"
```

### Task 7.2: SimulatorScenarioService

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/simulator/SimulatorScenarioService.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/simulator/SimulatorScenarioServiceTest.java`

Supported scenarios (from spec): `START_ALL`, `STOP_ALL`, `FAULT_ONE`, `DISCONNECT_ONE`, `PEAK_LOAD`, `RESET_ALL`.

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.simulator;

import com.accenture.nexcharge.simulator.model.dto.ScenarioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulatorScenarioServiceTest {

    @Mock SimulatorManager manager;
    @Mock ChargePointSimulator s1;
    @Mock ChargePointSimulator s2;
    @InjectMocks SimulatorScenarioService service;

    @BeforeEach
    void setUp() {
        when(manager.getAll()).thenReturn(List.of(s1, s2));
        when(s1.getState()).thenReturn(SimulatorState.AVAILABLE);
        when(s2.getState()).thenReturn(SimulatorState.AVAILABLE);
    }

    @Test
    void startAllStartsAvailableOnes() {
        service.run(new ScenarioRequest("START_ALL", null));
        verify(s1).startSession(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.anyString());
        verify(s2).startSession(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void stopAllStopsChargingOnes() {
        when(s1.getState()).thenReturn(SimulatorState.CHARGING);
        when(s2.getState()).thenReturn(SimulatorState.AVAILABLE);
        service.run(new ScenarioRequest("STOP_ALL", null));
        verify(s1).stopSession("Remote");
        verify(s2, never()).stopSession(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void faultOneTargetsSpecificChargePoint() {
        when(manager.get("BORNE_A")).thenReturn(s1);
        service.run(new ScenarioRequest("FAULT_ONE", "BORNE_A"));
        verify(s1).fault(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void resetAllResetsEverySimulator() {
        service.run(new ScenarioRequest("RESET_ALL", null));
        verify(s1).reset();
        verify(s2).reset();
    }

    @Test
    void unknownScenarioThrows() {
        assertThatThrownBy(() -> service.run(new ScenarioRequest("BOGUS", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test (RED)**

Run: `./mvnw test -Dtest=SimulatorScenarioServiceTest -q`

- [ ] **Step 3: Create `SimulatorScenarioService.java`**

```java
package com.accenture.nexcharge.simulator.simulator;

import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.dto.ScenarioRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimulatorScenarioService {

    private final SimulatorManager manager;
    private final SimulatorProperties properties;
    private final Random random = new Random();

    public void run(ScenarioRequest request) {
        String scenario = request.scenario();
        log.info("[SCENARIO] {} target={}", scenario, request.chargePointId());
        switch (scenario) {
            case "START_ALL" -> startAll();
            case "STOP_ALL" -> stopAll();
            case "FAULT_ONE" -> faultOne(request.chargePointId());
            case "DISCONNECT_ONE" -> disconnectOne(request.chargePointId());
            case "PEAK_LOAD" -> peakLoad();
            case "RESET_ALL" -> resetAll();
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
    }

    private void startAll() {
        for (ChargePointSimulator s : manager.getAll()) {
            if (s.getState() == SimulatorState.AVAILABLE) {
                s.startSession(1, pickRfid());
            }
        }
    }

    private void stopAll() {
        for (ChargePointSimulator s : manager.getAll()) {
            if (s.getState() == SimulatorState.CHARGING) {
                s.stopSession("Remote");
            }
        }
    }

    private void faultOne(String chargePointId) {
        ChargePointSimulator s = (chargePointId != null)
                ? manager.get(chargePointId)
                : manager.getAll().stream()
                    .filter(x -> x.getState() != SimulatorState.FAULTED)
                    .findFirst().orElse(null);
        if (s != null) s.fault("GroundFailure");
    }

    private void disconnectOne(String chargePointId) {
        ChargePointSimulator s = (chargePointId != null) ? manager.get(chargePointId) : null;
        if (s != null) s.reset();
    }

    private void peakLoad() {
        for (ChargePointSimulator s : manager.getAll()) {
            switch (s.getState()) {
                case AVAILABLE -> s.startSession(1, pickRfid());
                case FAULTED -> s.recoverFromFault();
                default -> { /* leave CHARGING and PREPARING as is */ }
            }
        }
    }

    private void resetAll() {
        for (ChargePointSimulator s : manager.getAll()) {
            s.reset();
        }
    }

    private String pickRfid() {
        var tags = properties.rfidTags();
        return tags.isEmpty() ? "RFID-DEFAULT" : tags.get(random.nextInt(tags.size()));
    }
}
```

- [ ] **Step 4: Run test (PASS)**

Run: `./mvnw test -Dtest=SimulatorScenarioServiceTest -q`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/simulator/SimulatorScenarioService.java src/test/java/com/accenture/nexcharge/simulator/simulator/SimulatorScenarioServiceTest.java
git commit -m "simulator: add SimulatorScenarioService for manual scenarios"
```

---

## Phase 8: REST controllers

Goal: All 15 endpoints from the spec wired to the services. Each controller is a thin pass-through; logic lives in services.

### Task 8.1: ChargePointController

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/controller/ChargePointController.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/controller/ChargePointControllerTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.ChargePointDto;
import com.accenture.nexcharge.simulator.model.dto.ConnectorDto;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import com.accenture.nexcharge.simulator.service.ChargePointNotFoundException;
import com.accenture.nexcharge.simulator.service.ChargePointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChargePointController.class)
@Import(GlobalExceptionHandler.class)
class ChargePointControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ChargePointService service;

    @Test
    void getAllReturnsList() throws Exception {
        ChargePointDto cp = new ChargePointDto("BORNE_A", "Legrand", "Green'Up Premium",
                "LGR-001", "1.4.2", ChargePointStatus.Available, true,
                Instant.now(), Instant.now(), "NoError",
                List.of(new ConnectorDto(1, ConnectorStatus.Available, 0.0, 0.0, 230.0, 22.0, 0.0, "NoError")));
        when(service.getAll()).thenReturn(List.of(cp));

        mvc.perform(get("/api/chargepoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chargePointId").value("BORNE_A"))
                .andExpect(jsonPath("$[0].connectors[0].connectorId").value(1));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        when(service.getById("UNKNOWN")).thenThrow(new ChargePointNotFoundException("UNKNOWN"));
        mvc.perform(get("/api/chargepoints/UNKNOWN")).andExpect(status().isNotFound());
    }

    @Test
    void getConnectorsReturnsList() throws Exception {
        when(service.getConnectors("BORNE_A")).thenReturn(List.of(
                new ConnectorDto(1, ConnectorStatus.Charging, 7.2, 31.0, 230.0, 38.5, 14.5, "NoError")));
        mvc.perform(get("/api/chargepoints/BORNE_A/connectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentPowerKw").value(7.2));
    }
}
```

- [ ] **Step 2: Run test (RED — class doesn't exist)**

Run: `./mvnw test -Dtest=ChargePointControllerTest -q`

- [ ] **Step 3: Create `ChargePointController.java`**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.ChargePointDto;
import com.accenture.nexcharge.simulator.model.dto.ConnectorDto;
import com.accenture.nexcharge.simulator.service.ChargePointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chargepoints")
@RequiredArgsConstructor
public class ChargePointController {

    private final ChargePointService service;

    @GetMapping
    public List<ChargePointDto> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ChargePointDto getById(@PathVariable String id) {
        return service.getById(id);
    }

    @GetMapping("/{id}/connectors")
    public List<ConnectorDto> getConnectors(@PathVariable String id) {
        return service.getConnectors(id);
    }
}
```

- [ ] **Step 4: Run test (compile error: GlobalExceptionHandler missing)**

We'll create it in Task 8.8. For now, comment out `@Import(GlobalExceptionHandler.class)` in the test temporarily, run, and accept that 404 test will fail until 8.8.

Or, create a minimal `GlobalExceptionHandler` skeleton now to keep tests passing:

**Files:** Create stub `src/main/java/com/accenture/nexcharge/simulator/controller/GlobalExceptionHandler.java`:

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.service.ChargePointNotFoundException;
import com.accenture.nexcharge.simulator.service.SessionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ChargePointNotFoundException.class, SessionNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "fields", ex.getBindingResult().getFieldErrors().stream()
                        .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                        .toList()
        ));
    }
}
```

- [ ] **Step 5: Run test (PASS)**

Run: `./mvnw test -Dtest=ChargePointControllerTest -q`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/controller/ChargePointController.java src/main/java/com/accenture/nexcharge/simulator/controller/GlobalExceptionHandler.java src/test/java/com/accenture/nexcharge/simulator/controller/ChargePointControllerTest.java
git commit -m "api: add charge points controller with 404/400 handling"
```

### Task 8.2: SessionController

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/controller/SessionController.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/controller/SessionControllerTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.SessionDto;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionController.class)
@Import(GlobalExceptionHandler.class)
class SessionControllerTest {

    @Autowired MockMvc mvc;
    @MockBean SessionService service;

    @Test
    void searchActiveSessions() throws Exception {
        when(service.search(eq(SessionStatus.Active), any(), any(), any())).thenReturn(List.of(
                new SessionDto(1L, 1001, "BORNE_A", 1, "RFID-001",
                        Instant.parse("2026-05-22T12:30:00Z"), null,
                        500000.0, null, 14.8, null, SessionStatus.Active, 120L)));

        mvc.perform(get("/api/sessions").param("status", "Active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value(1001))
                .andExpect(jsonPath("$[0].durationMinutes").value(120));
    }

    @Test
    void getActiveShortcut() throws Exception {
        when(service.findActive()).thenReturn(List.of());
        mvc.perform(get("/api/sessions/active")).andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run test (RED)**

Run: `./mvnw test -Dtest=SessionControllerTest -q`

- [ ] **Step 3: Create `SessionController.java`**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.SessionDto;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService service;

    @GetMapping
    public List<SessionDto> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String chargePointId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        SessionStatus parsed = parseStatus(status);
        return service.search(parsed, chargePointId, from, to);
    }

    @GetMapping("/active")
    public List<SessionDto> getActive() {
        return service.findActive();
    }

    @GetMapping("/{id}")
    public SessionDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    private SessionStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) return null;
        try {
            return SessionStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }
}
```

- [ ] **Step 4: Run test (PASS)**

Run: `./mvnw test -Dtest=SessionControllerTest -q`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/accenture/nexcharge/simulator/controller/SessionController.java src/test/java/com/accenture/nexcharge/simulator/controller/SessionControllerTest.java
git commit -m "api: add sessions controller with filter parameters"
```

### Task 8.3: MeterController

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/controller/MeterController.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/controller/MeterControllerTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.MeterValueDto;
import com.accenture.nexcharge.simulator.service.MeterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeterController.class)
@Import(GlobalExceptionHandler.class)
class MeterControllerTest {

    @Autowired MockMvc mvc;
    @MockBean MeterService service;

    @Test
    void returnsMeterValues() throws Exception {
        when(service.findRecent(eq("BORNE_A"), eq(1), eq(60))).thenReturn(List.of(
                new MeterValueDto(Instant.parse("2026-05-22T14:30:10Z"), 1, 1001,
                        "Power.Active.Import", 7200.0, "W")));

        mvc.perform(get("/api/meter-values/BORNE_A").param("connectorId", "1").param("last", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].measurand").value("Power.Active.Import"))
                .andExpect(jsonPath("$[0].value").value(7200.0));
    }
}
```

- [ ] **Step 2: Run test (RED)**

Run: `./mvnw test -Dtest=MeterControllerTest -q`

- [ ] **Step 3: Create `MeterController.java`**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.MeterValueDto;
import com.accenture.nexcharge.simulator.service.MeterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meter-values")
@RequiredArgsConstructor
public class MeterController {

    private final MeterService service;

    @GetMapping("/{chargePointId}")
    public List<MeterValueDto> get(
            @PathVariable String chargePointId,
            @RequestParam(required = false) Integer connectorId,
            @RequestParam(required = false) Integer last) {
        return service.findRecent(chargePointId, connectorId, last);
    }
}
```

- [ ] **Step 4: Run test (PASS) and commit**

```bash
./mvnw test -Dtest=MeterControllerTest -q
git add src/main/java/com/accenture/nexcharge/simulator/controller/MeterController.java src/test/java/com/accenture/nexcharge/simulator/controller/MeterControllerTest.java
git commit -m "api: add meter-values controller with filter and lookback window"
```

### Task 8.4: StatsController

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/controller/StatsController.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/controller/StatsControllerTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.StatsDto;
import com.accenture.nexcharge.simulator.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatsController.class)
@Import(GlobalExceptionHandler.class)
class StatsControllerTest {

    @Autowired MockMvc mvc;
    @MockBean StatsService service;

    @Test
    void returnsStats() throws Exception {
        when(service.compute()).thenReturn(new StatsDto(
                5, 4, 2, 2, 1, 2, 29.2, 63.6, 8, 6, 95L, 18.5));
        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChargePoints").value(5))
                .andExpect(jsonPath("$.totalPowerKw").value(29.2));
    }
}
```

- [ ] **Step 2: Create `StatsController.java`**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.StatsDto;
import com.accenture.nexcharge.simulator.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService service;

    @GetMapping
    public StatsDto get() {
        return service.compute();
    }
}
```

- [ ] **Step 3: Run test and commit**

```bash
./mvnw test -Dtest=StatsControllerTest -q
git add src/main/java/com/accenture/nexcharge/simulator/controller/StatsController.java src/test/java/com/accenture/nexcharge/simulator/controller/StatsControllerTest.java
git commit -m "api: add stats controller"
```

### Task 8.5: LogController

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/controller/LogController.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/controller/LogControllerTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.OcppLogDto;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import com.accenture.nexcharge.simulator.service.LogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LogController.class)
@Import(GlobalExceptionHandler.class)
class LogControllerTest {

    @Autowired MockMvc mvc;
    @MockBean LogService service;

    @Test
    void searchByFilters() throws Exception {
        when(service.search(eq("BORNE_A"), eq("Heartbeat"), eq(LogDirection.IN), eq(60), eq(20)))
                .thenReturn(List.of(new OcppLogDto(42L, "BORNE_A", LogDirection.IN, "Heartbeat",
                        "{}", Instant.parse("2026-05-22T14:30:10Z"))));
        mvc.perform(get("/api/logs")
                        .param("chargePointId", "BORNE_A")
                        .param("action", "Heartbeat")
                        .param("direction", "IN")
                        .param("last", "60")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("Heartbeat"));
    }
}
```

- [ ] **Step 2: Create `LogController.java`**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.OcppLogDto;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import com.accenture.nexcharge.simulator.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService service;

    @GetMapping
    public List<OcppLogDto> search(
            @RequestParam(required = false) String chargePointId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) LogDirection direction,
            @RequestParam(required = false) Integer last,
            @RequestParam(required = false) Integer limit) {
        return service.search(chargePointId, action, direction, last, limit);
    }
}
```

- [ ] **Step 3: Run test and commit**

```bash
./mvnw test -Dtest=LogControllerTest -q
git add src/main/java/com/accenture/nexcharge/simulator/controller/LogController.java src/test/java/com/accenture/nexcharge/simulator/controller/LogControllerTest.java
git commit -m "api: add OCPP logs controller with multi-filter search"
```

### Task 8.6: RemoteCommandController

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/controller/RemoteCommandController.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/controller/RemoteCommandControllerTest.java`

This controller routes the four `POST /api/chargepoints/{id}/...` commands. It does NOT go through the CSMS over WebSocket — it directly calls the `ChargePointSimulator` via the manager. (Spec section: an out-of-band shortcut is acceptable for the simulator's needs; in a real system the CSMS would issue OCPP RemoteStart/Stop/Reset/Unlock messages.)

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.simulator.ChargePointSimulator;
import com.accenture.nexcharge.simulator.simulator.SimulatorManager;
import com.accenture.nexcharge.simulator.simulator.SimulatorState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RemoteCommandController.class)
@Import(GlobalExceptionHandler.class)
class RemoteCommandControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean SimulatorManager manager;
    @MockBean ChargePointSimulator simulator;

    @Test
    void remoteStartReturnsAccepted() throws Exception {
        when(manager.get("BORNE_A")).thenReturn(simulator);
        when(simulator.getState()).thenReturn(SimulatorState.AVAILABLE);

        mvc.perform(post("/api/chargepoints/BORNE_A/remote-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("idTag", "RFID-0042", "connectorId", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Accepted"));

        verify(simulator).startSession(1, "RFID-0042");
    }

    @Test
    void remoteStartReturns404WhenChargePointMissing() throws Exception {
        when(manager.get("UNKNOWN")).thenReturn(null);
        mvc.perform(post("/api/chargepoints/UNKNOWN/remote-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("idTag", "RFID-0042", "connectorId", 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void remoteStartReturns400OnValidationError() throws Exception {
        mvc.perform(post("/api/chargepoints/BORNE_A/remote-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("idTag", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetTriggersSimulatorReset() throws Exception {
        when(manager.get("BORNE_A")).thenReturn(simulator);
        mvc.perform(post("/api/chargepoints/BORNE_A/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("type", "Soft"))))
                .andExpect(status().isOk());
        verify(simulator).reset();
    }
}
```

- [ ] **Step 2: Run test (RED)**

Run: `./mvnw test -Dtest=RemoteCommandControllerTest -q`

- [ ] **Step 3: Create `RemoteCommandController.java`**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.*;
import com.accenture.nexcharge.simulator.service.ChargePointNotFoundException;
import com.accenture.nexcharge.simulator.simulator.ChargePointSimulator;
import com.accenture.nexcharge.simulator.simulator.SimulatorManager;
import com.accenture.nexcharge.simulator.simulator.SimulatorState;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chargepoints/{id}")
@RequiredArgsConstructor
public class RemoteCommandController {

    private final SimulatorManager manager;

    @PostMapping("/remote-start")
    public CommandResponse remoteStart(@PathVariable String id, @Valid @RequestBody RemoteStartRequest req) {
        ChargePointSimulator s = require(id);
        s.startSession(req.connectorId(), req.idTag());
        return CommandResponse.accepted("RemoteStart sent to " + id);
    }

    @PostMapping("/remote-stop")
    public CommandResponse remoteStop(@PathVariable String id, @Valid @RequestBody RemoteStopRequest req) {
        ChargePointSimulator s = require(id);
        if (s.getState() != SimulatorState.CHARGING) {
            return CommandResponse.rejected("Charge point not charging");
        }
        s.stopSession("Remote");
        return CommandResponse.accepted("RemoteStop sent to " + id);
    }

    @PostMapping("/reset")
    public CommandResponse reset(@PathVariable String id, @Valid @RequestBody ResetRequest req) {
        ChargePointSimulator s = require(id);
        s.reset();
        return CommandResponse.accepted("Reset (" + req.type() + ") sent to " + id);
    }

    @PostMapping("/unlock")
    public CommandResponse unlock(@PathVariable String id, @Valid @RequestBody UnlockRequest req) {
        ChargePointSimulator s = require(id);
        if (s.getState() == SimulatorState.CHARGING) {
            s.stopSession("UnlockCommand");
        }
        return CommandResponse.accepted("Unlock connector " + req.connectorId() + " on " + id);
    }

    private ChargePointSimulator require(String id) {
        ChargePointSimulator s = manager.get(id);
        if (s == null) throw new ChargePointNotFoundException(id);
        return s;
    }
}
```

- [ ] **Step 4: Run test (PASS) and commit**

```bash
./mvnw test -Dtest=RemoteCommandControllerTest -q
git add src/main/java/com/accenture/nexcharge/simulator/controller/RemoteCommandController.java src/test/java/com/accenture/nexcharge/simulator/controller/RemoteCommandControllerTest.java
git commit -m "api: add remote command controller (start/stop/reset/unlock)"
```

### Task 8.7: SimulatorController (scenario endpoint)

**Files:**
- Create: `src/main/java/com/accenture/nexcharge/simulator/controller/SimulatorController.java`
- Test: `src/test/java/com/accenture/nexcharge/simulator/controller/SimulatorControllerTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.simulator.SimulatorScenarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SimulatorController.class)
@Import(GlobalExceptionHandler.class)
class SimulatorControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean SimulatorScenarioService service;

    @Test
    void runsValidScenario() throws Exception {
        mvc.perform(post("/api/simulator/scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("scenario", "PEAK_LOAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Accepted"));

        ArgumentCaptor<com.accenture.nexcharge.simulator.model.dto.ScenarioRequest> captor =
                ArgumentCaptor.forClass(com.accenture.nexcharge.simulator.model.dto.ScenarioRequest.class);
        verify(service).run(captor.capture());
        assertThat(captor.getValue().scenario()).isEqualTo("PEAK_LOAD");
    }

    @Test
    void unknownScenarioReturns400() throws Exception {
        doThrow(new IllegalArgumentException("Unknown scenario: BOGUS")).when(service).run(org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/api/simulator/scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("scenario", "BOGUS"))))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Create `SimulatorController.java`**

```java
package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.CommandResponse;
import com.accenture.nexcharge.simulator.model.dto.ScenarioRequest;
import com.accenture.nexcharge.simulator.simulator.SimulatorScenarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulatorScenarioService service;

    @PostMapping("/scenario")
    public CommandResponse runScenario(@Valid @RequestBody ScenarioRequest request) {
        service.run(request);
        return CommandResponse.accepted("Scenario " + request.scenario() + " executed");
    }
}
```

- [ ] **Step 3: Run test and commit**

```bash
./mvnw test -Dtest=SimulatorControllerTest -q
git add src/main/java/com/accenture/nexcharge/simulator/controller/SimulatorController.java src/test/java/com/accenture/nexcharge/simulator/controller/SimulatorControllerTest.java
git commit -m "api: add simulator scenario controller"
```

### Task 8.8: Run full controller test suite

- [ ] **Step 1: Run all tests so far**

Run: `./mvnw test -q`
Expected: all unit tests pass.

- [ ] **Step 2: Verify the full app boots and routes are wired**

Run: `./mvnw spring-boot:run` in one terminal. In another:

```bash
curl -s http://localhost:8080/api/chargepoints | head -20
curl -s http://localhost:8080/api/stats
curl -s http://localhost:8080/api/sessions/active
```

Expected: 200 responses, populated charge-points list with 5 entries.

Ctrl+C to stop.

- [ ] **Step 3: Commit (no changes; verification only)**

If anything was tweaked during smoke test (logging, missing field), commit with message `api: minor fixes after end-to-end smoke test`.

---

## Phase 9: WebSocket integration test

Goal: Prove the STOMP `/ws/live` endpoint actually broadcasts events when something happens server-side.

### Task 9.1: Live event WebSocket integration test

**Files:**
- Test: `src/test/java/com/accenture/nexcharge/simulator/websocket/LiveEventWebSocketTest.java`

- [ ] **Step 1: Write the test**

```java
package com.accenture.nexcharge.simulator.websocket;

import com.accenture.nexcharge.simulator.model.dto.LiveEventDto;
import com.accenture.nexcharge.simulator.model.enums.LiveEventType;
import com.accenture.nexcharge.simulator.service.LiveEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LiveEventWebSocketTest {

    @LocalServerPort int port;
    @Autowired LiveEventService liveEventService;
    @Autowired ObjectMapper json;

    @Test
    void clientReceivesPublishedEvent() throws Exception {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new MappingJackson2MessageConverter());

        BlockingQueue<String> received = new ArrayBlockingQueue<>(4);

        StompSession session = stomp.connectAsync("ws://localhost:" + port + "/ws/live",
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/events", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.offer((String) payload);
            }
        });

        // tiny pause to ensure subscription is processed
        Thread.sleep(200);
        liveEventService.publish(LiveEventDto.of(
                LiveEventType.STATUS_CHANGE, "BORNE_TEST",
                Map.of("status", "Charging")));

        String payload = received.poll(5, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload).contains("STATUS_CHANGE");
        assertThat(payload).contains("BORNE_TEST");

        session.disconnect();
    }
}
```

- [ ] **Step 2: Run test**

Run: `./mvnw test -Dtest=LiveEventWebSocketTest -q`
Expected: PASS. The simulator is disabled by `application-test.yml`, so no chatter from real bornes interferes.

If it fails with timeout, increase the `Thread.sleep` to 500ms. If it fails with `MissingClassError` for `StandardWebSocketClient`, ensure `spring-boot-starter-websocket` is on the test classpath (it is — included via main).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/accenture/nexcharge/simulator/websocket/LiveEventWebSocketTest.java
git commit -m "test: add WebSocket STOMP integration test for live events"
```

---

## Phase 10: End-to-end integration test

Goal: Boot the entire app (Spring Boot + CSMS + simulator), wait for the simulators to register, force a scenario via REST, and verify state changes through the API.

### Task 10.1: End-to-end simulator integration test

**Files:**
- Test: `src/test/java/com/accenture/nexcharge/simulator/integration/EndToEndSimulationIT.java`
- Modify: `src/test/resources/application-test.yml` — provide one minimal charge point so this test has something real to drive

- [ ] **Step 1: Add a dedicated profile for E2E**

**Files:** Create `src/test/resources/application-e2e.yml`

```yaml
server:
  port: 0
spring:
  datasource:
    url: jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop

ocpp:
  server:
    port: 9100  # avoid conflict with main 9000
    host: 127.0.0.1

simulator:
  enabled: true
  acceleration-factor: 60   # fast for tests
  heartbeat-interval-seconds: 2
  meter-interval-seconds: 1
  auto-session-probability: 0.0
  random-event-probability: 0.0
  charge-points:
    - id: BORNE_E2E
      vendor: Legrand
      model: Test
      serial: E2E-001
      max-power-kw: 7.4
      connectors: 1
      firmware: "1.0.0"
  rfid-tags: [RFID-E2E]
```

- [ ] **Step 2: Write the test**

```java
package com.accenture.nexcharge.simulator.integration;

import com.accenture.nexcharge.simulator.model.dto.ChargePointDto;
import com.accenture.nexcharge.simulator.model.dto.CommandResponse;
import com.accenture.nexcharge.simulator.model.dto.ScenarioRequest;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
class EndToEndSimulationIT {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @Test
    void simulatorBootsAndRespondsToScenarios() {
        // 1. Wait for simulator boot to register the charge point
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ResponseEntity<List<ChargePointDto>> resp = rest.exchange(
                    "http://localhost:" + port + "/api/chargepoints",
                    HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(resp.getBody()).extracting(ChargePointDto::chargePointId).contains("BORNE_E2E");
            assertThat(resp.getBody().stream().filter(cp -> cp.chargePointId().equals("BORNE_E2E")).findFirst().get().online())
                    .isTrue();
        });

        // 2. Trigger START_ALL scenario
        ResponseEntity<CommandResponse> scenario = rest.postForEntity(
                "http://localhost:" + port + "/api/simulator/scenario",
                new HttpEntity<>(new ScenarioRequest("START_ALL", null)),
                CommandResponse.class);
        assertThat(scenario.getStatusCode().is2xxSuccessful()).isTrue();

        // 3. Wait for borne to enter Charging state (PREPARING -> CHARGING after ~3s in manager)
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ResponseEntity<List<ChargePointDto>> resp = rest.exchange(
                    "http://localhost:" + port + "/api/chargepoints",
                    HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            ChargePointDto cp = resp.getBody().stream()
                    .filter(c -> c.chargePointId().equals("BORNE_E2E")).findFirst().orElseThrow();
            assertThat(cp.status()).isIn(ChargePointStatus.Charging, ChargePointStatus.Preparing);
        });
    }
}
```

- [ ] **Step 3: Run E2E test**

Run: `./mvnw test -Dtest=EndToEndSimulationIT -q`
Expected: PASS. Watch logs for `[BORNE_E2E] Booted — Status: Available` then state transitions.

If it fails with timeout, increase awaitility timeouts. If port 9100 is in use, change in `application-e2e.yml`.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/accenture/nexcharge/simulator/integration/EndToEndSimulationIT.java src/test/resources/application-e2e.yml
git commit -m "test: add end-to-end integration test booting full simulator + CSMS"
```

---

## Phase 11: Manual verification + final polish

Goal: Run the application as a developer would, verify all spec acceptance criteria, fix any rough edges, and tag the final commit.

### Task 11.1: Manual smoke test

- [ ] **Step 1: Clean state and start fresh**

```bash
./clean.sh
./mvnw spring-boot:run
```

Expected console output (in order):
```
[CSMS] Server started on 0.0.0.0:9000 (OCPP 1.6J)
[SIMULATOR] Starting 5 simulated charge points...
[BORNE_A] Connected to CSMS at ws://localhost:9000
[CSMS] New session: BORNE_A connected
[CSMS] BootNotification from BORNE_A: Legrand Green'Up Premium
[BORNE_A] Booted — Status: Available
... (4 more bornes) ...
Started CsmsApplication in X.X seconds
```

- [ ] **Step 2: Run the spec's curl commands**

In a second terminal:

```bash
curl -s http://localhost:8080/api/chargepoints | jq '.[].chargePointId'
# expect: 5 lines with BORNE_A..BORNE_E

curl -s http://localhost:8080/api/stats | jq
# expect: totalChargePoints=5, onlineChargePoints=5

curl -s "http://localhost:8080/api/logs?limit=5" | jq '.[].action'
# expect: BootNotification, StatusNotification, Heartbeat...

curl -s -X POST http://localhost:8080/api/chargepoints/BORNE_B/remote-start \
  -H "Content-Type: application/json" \
  -d '{"idTag": "RFID-0042", "connectorId": 1}'
# expect: {"status":"Accepted","message":"RemoteStart sent to BORNE_B"}

# Wait ~5s
curl -s http://localhost:8080/api/sessions/active | jq
# expect: at least one Active session for BORNE_B

curl -s -X POST http://localhost:8080/api/simulator/scenario \
  -H "Content-Type: application/json" \
  -d '{"scenario":"PEAK_LOAD"}'

# Wait ~10s
curl -s http://localhost:8080/api/stats | jq '.chargingNow'
# expect: > 0
```

- [ ] **Step 3: H2 console sanity check**

Browse: `http://localhost:8080/h2-console`. Connect (JDBC URL `jdbc:h2:file:./data/csms`, user `sa`, no password). Run:
```sql
SELECT charge_point_id, status, online FROM charge_points;
SELECT count(*) FROM ocpp_logs;
SELECT measurand, count(*) FROM meter_readings GROUP BY measurand;
```
Expect populated rows and multiple measurands.

- [ ] **Step 4: WebSocket sanity check (optional but recommended)**

If you have `wscat` (`npm i -g wscat`), test the SockJS-less raw STOMP endpoint:
```bash
wscat -c ws://localhost:8080/ws/live
> CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n 
> SUBSCRIBE\nid:0\ndestination:/topic/events\n\n 
```
You should see live MESSAGE frames every ~10s (METER_UPDATE).

- [ ] **Step 5: Stop and re-start (persistence check)**

Ctrl+C the server. Restart:
```bash
./mvnw spring-boot:run
```
Expect: previous data still in H2 (file-backed). `curl http://localhost:8080/api/sessions` returns history including pre-restart sessions.

- [ ] **Step 6: Commit any small fixes from manual testing**

If any logging or behaviour was off:
```bash
git add -p
git commit -m "fix: minor adjustments from manual smoke test"
```

### Task 11.2: Final full-test run

- [ ] **Step 1: Run the complete suite**

```bash
./mvnw clean test
```
Expected: all tests pass.

- [ ] **Step 2: Run the build**

```bash
./mvnw clean install -DskipITs=false
```
Expected: BUILD SUCCESS. Executable jar at `target/simulator-0.1.0-SNAPSHOT.jar`.

- [ ] **Step 3: Final commit (if any version bumps or fixes)**

```bash
git status
# only commit if there are changes
```

### Task 11.3: Tag the milestone

- [ ] **Step 1: Tag**

```bash
git tag -a v0.1.0 -m "OCPP 1.6J simulator v0.1.0 — initial working build"
```

(Don't push the tag unless the user asks.)

---

## Acceptance criteria (from spec)

These must all hold after Phase 11:

1. ✅ `./mvnw spring-boot:run` boots without manual config
2. ✅ All 5 bornes appear in `GET /api/chargepoints` with status=Available within 20s of boot
3. ✅ `GET /api/stats` returns valid aggregate counts
4. ✅ `POST /api/simulator/scenario {"scenario":"START_ALL"}` triggers sessions on every available borne; within 30s `GET /api/sessions/active` returns ≥1
5. ✅ `MeterValues` are recorded every 10s during charging — visible in `/api/meter-values/{id}` and via `/topic/events` METER_UPDATE
6. ✅ `STOMP /ws/live` delivers events to subscribers
7. ✅ App restart preserves session history (H2 file mode)
8. ✅ `./mvnw test` is green

