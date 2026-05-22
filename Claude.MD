# OCPP 1.6J Charging Station Simulator & CSMS Server

## Objectif

Construire un serveur complet qui simule un réseau de bornes de recharge EV via le protocole OCPP 1.6J. Ce serveur expose une API REST que notre app de monitoring consommera. Le tout tourne en local, aucune borne physique requise.

## Architecture

```
+---------------------------------------------------------+
¦                    JVM (même process)                    ¦
¦                                                         ¦
¦  +---------------+         +----------------------+     ¦
¦  ¦  CSMS Server   ¦?--ws--?¦  Simulated Charge    ¦     ¦
¦  ¦  (WebSocket    ¦        ¦  Points (3-5 bornes) ¦     ¦
¦  ¦   port 9000)   ¦        ¦  - state machine     ¦     ¦
¦  +---------------+         ¦  - meter progression ¦     ¦
¦          ¦                 ¦  - random events     ¦     ¦
¦          ¦                 +----------------------+     ¦
¦          ¦                                              ¦
¦  +-------?---------------------------------------+     ¦
¦  ¦              REST API (port 8080)              ¦     ¦
¦  ¦  GET  /api/chargepoints                        ¦     ¦
¦  ¦  GET  /api/chargepoints/{id}                   ¦     ¦
¦  ¦  GET  /api/chargepoints/{id}/connectors        ¦     ¦
¦  ¦  GET  /api/sessions                            ¦     ¦
¦  ¦  GET  /api/sessions/active                     ¦     ¦
¦  ¦  GET  /api/sessions/{id}                       ¦     ¦
¦  ¦  GET  /api/meter-values/{chargePointId}        ¦     ¦
¦  ¦  GET  /api/stats                               ¦     ¦
¦  ¦  GET  /api/logs                                ¦     ¦
¦  ¦  POST /api/chargepoints/{id}/remote-start      ¦     ¦
¦  ¦  POST /api/chargepoints/{id}/remote-stop       ¦     ¦
¦  ¦  POST /api/chargepoints/{id}/reset             ¦     ¦
¦  ¦  POST /api/chargepoints/{id}/unlock            ¦     ¦
¦  ¦  POST /api/simulator/scenario                  ¦     ¦
¦  ¦  WS   /ws/live                                 ¦     ¦
¦  +-----------------------------------------------+     ¦
¦                                                         ¦
¦  +-----------------------------------------------+     ¦
¦  ¦          H2 In-Memory Database                 ¦     ¦
¦  ¦  Tables: charge_points, connectors,            ¦     ¦
¦  ¦  charging_sessions, meter_readings, ocpp_logs  ¦     ¦
¦  +-----------------------------------------------+     ¦
+---------------------------------------------------------+
```

## Stack Technique

- **Java 17+**
- **Spring Boot 3.3+** (web, websocket, data-jpa)
- **H2** base in-memory (zéro config, auto-reset au restart)
- **Java-OCA-OCPP** (`eu.chargetime.ocpp` v1.1.0) pour le CSMS
- **Lombok** pour réduire le boilerplate
- **Jackson** (inclus dans Spring Boot) pour le JSON

### pom.xml — dépendances exactes

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.0</version>
</parent>

<properties>
    <java.version>17</java.version>
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
        <version>1.1.0</version>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### application.yml

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:mem:csms;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
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
  # Combien de secondes entre chaque heartbeat simulé
  heartbeat-interval-seconds: 30
  # Combien de secondes entre chaque MeterValues simulé
  meter-interval-seconds: 10
  # Probabilité qu'une borne démarre une session spontanément (par tick de 30s)
  auto-session-probability: 0.15
  # Probabilité d'événement aléatoire (erreur, déconnexion)
  random-event-probability: 0.02
```

## Structure du projet

```
src/main/java/com/monapp/csms/
+-- CsmsApplication.java                  # @SpringBootApplication, main
¦
+-- config/
¦   +-- SimulatorProperties.java           # @ConfigurationProperties("simulator")
¦   +-- WebSocketConfig.java               # WebSocket STOMP config pour /ws/live
¦   +-- CorsConfig.java                    # CORS permissif (dev)
¦
+-- model/
¦   +-- entity/
¦   ¦   +-- ChargePointEntity.java         # JPA entity
¦   ¦   +-- ConnectorEntity.java           # JPA entity (1 borne = N connecteurs)
¦   ¦   +-- ChargingSessionEntity.java     # JPA entity
¦   ¦   +-- MeterReadingEntity.java        # JPA entity
¦   ¦   +-- OcppLogEntity.java             # JPA entity (tous messages OCPP loggés)
¦   +-- enums/
¦   ¦   +-- ChargePointStatus.java         # Available, Charging, Faulted, Unavailable, etc
¦   ¦   +-- ConnectorStatus.java           # idem au niveau connecteur
¦   ¦   +-- SessionStatus.java             # Active, Completed, Error
¦   +-- dto/
¦       +-- ChargePointDto.java            # réponse API (inclut connecteurs inline)
¦       +-- SessionDto.java
¦       +-- MeterValueDto.java
¦       +-- StatsDto.java
¦       +-- OcppLogDto.java
¦       +-- LiveEventDto.java              # événement WebSocket temps réel
¦       +-- RemoteStartRequest.java        # body POST
¦       +-- RemoteStopRequest.java
¦       +-- ScenarioRequest.java           # déclencher un scénario de simulation
¦
+-- repository/
¦   +-- ChargePointRepository.java
¦   +-- ConnectorRepository.java
¦   +-- ChargingSessionRepository.java
¦   +-- MeterReadingRepository.java
¦   +-- OcppLogRepository.java
¦
+-- ocpp/
¦   +-- CsmsServer.java                   # Démarre le JSONServer OCPP sur port 9000
¦   +-- CsmsEventHandler.java             # Implémente ServerCoreEventHandler
¦   +-- OcppSessionRegistry.java          # Map<chargePointId, UUID sessionId>
¦
+-- simulator/
¦   +-- ChargePointSimulator.java          # Simule UNE borne (state machine + client OCPP)
¦   +-- SimulatorManager.java             # Gère toutes les bornes simulées, scheduling
¦   +-- ChargingProfile.java              # Profil de charge réaliste (courbe puissance)
¦   +-- SimulatorScenarioService.java     # Déclencher scénarios manuellement via API
¦
+-- service/
¦   +-- ChargePointService.java
¦   +-- SessionService.java
¦   +-- MeterService.java
¦   +-- StatsService.java
¦   +-- LiveEventService.java             # Envoie les events sur /ws/live via STOMP
¦
+-- controller/
    +-- ChargePointController.java
    +-- SessionController.java
    +-- MeterController.java
    +-- StatsController.java
    +-- LogController.java
    +-- RemoteCommandController.java
    +-- SimulatorController.java
```

## Modèle de données

### charge_points

| Colonne | Type | Description |
|---------|------|-------------|
| charge_point_id | VARCHAR(50) PK | Ex: BORNE_A |
| vendor | VARCHAR(100) | Legrand |
| model | VARCHAR(100) | Green'Up Premium |
| serial_number | VARCHAR(100) | |
| firmware_version | VARCHAR(50) | |
| status | VARCHAR(20) | Available, Charging, Faulted, Unavailable |
| online | BOOLEAN | true/false |
| last_heartbeat | TIMESTAMP | |
| registered_at | TIMESTAMP | |
| error_code | VARCHAR(50) | NoError, GroundFailure, etc |

### connectors

| Colonne | Type | Description |
|---------|------|-------------|
| id | BIGINT PK AUTO | |
| charge_point_id | VARCHAR(50) FK | |
| connector_id | INTEGER | 1, 2 (0 = borne elle-même) |
| status | VARCHAR(20) | Available, Occupied, Charging, Faulted |
| current_power_kw | DOUBLE | Puissance instantanée |
| current_amps | DOUBLE | Courant instantané |
| voltage | DOUBLE | Tension |
| temperature_celsius | DOUBLE | |
| total_energy_kwh | DOUBLE | Cumul énergie délivrée |

### charging_sessions

| Colonne | Type | Description |
|---------|------|-------------|
| id | BIGINT PK AUTO | |
| transaction_id | INTEGER UNIQUE | ID transaction OCPP |
| charge_point_id | VARCHAR(50) FK | |
| connector_id | INTEGER | |
| id_tag | VARCHAR(50) | Badge RFID simulé |
| start_time | TIMESTAMP | |
| stop_time | TIMESTAMP | null si active |
| meter_start_wh | DOUBLE | Compteur au début |
| meter_stop_wh | DOUBLE | Compteur à la fin |
| energy_delivered_kwh | DOUBLE | Calculé |
| stop_reason | VARCHAR(50) | Local, Remote, EVDisconnected, etc |
| status | VARCHAR(20) | Active, Completed, Error |

### meter_readings

| Colonne | Type | Description |
|---------|------|-------------|
| id | BIGINT PK AUTO | |
| charge_point_id | VARCHAR(50) | |
| connector_id | INTEGER | |
| transaction_id | INTEGER | |
| measurand | VARCHAR(100) | Energy.Active.Import.Register, Power.Active.Import, Current.Import, Voltage, Temperature |
| value | DOUBLE | |
| unit | VARCHAR(10) | Wh, W, A, V, Celsius |
| timestamp | TIMESTAMP | |

### ocpp_logs

| Colonne | Type | Description |
|---------|------|-------------|
| id | BIGINT PK AUTO | |
| charge_point_id | VARCHAR(50) | |
| direction | VARCHAR(10) | IN (borne?CSMS) ou OUT (CSMS?borne) |
| action | VARCHAR(50) | BootNotification, Heartbeat, etc |
| payload | TEXT | JSON complet du message |
| timestamp | TIMESTAMP | |

## Simulateur — State Machine par Borne

Chaque `ChargePointSimulator` est un thread/scheduled task qui cycle à travers ces états :

```
                    +----------+
          +--------?¦ BOOTING  ¦
          ¦         +----------+
          ¦              ¦ BootNotification ? Accepted
          ¦              ?
          ¦    +------------------+
   Reset  ¦    ¦    AVAILABLE     ¦?---------------------+
          ¦    +------------------+                      ¦
          ¦             ¦ Badge RFID présenté             ¦
          ¦             ¦ (Authorize ? Accepted)          ¦
          ¦             ?                                 ¦
          ¦    +------------------+                      ¦
          ¦    ¦    PREPARING     ¦                      ¦
          ¦    +------------------+                      ¦
          ¦             ¦ Câble branché                   ¦
          ¦             ¦ (StartTransaction)              ¦ StopTransaction
          ¦             ?                                 ¦ (EVDisconnected,
          ¦    +------------------+                      ¦  Local, Remote)
          ¦    ¦    CHARGING      ¦----------------------+
          ¦    ¦ (MeterValues     ¦
          ¦    ¦  envoyés toutes  ¦
          ¦    ¦  les N secondes) ¦
          ¦    +------------------+
          ¦             ¦ random: 2% chance
          ¦             ?
          ¦    +------------------+
          +----¦    FAULTED       ¦ (auto-recover après 30-120s)
               +------------------+
```

### Logique de simulation détaillée

#### Phase BOOTING (au démarrage du simulateur)
1. Se connecter en WebSocket au CSMS `ws://localhost:9000/ocpp/{chargePointId}`
2. Envoyer `BootNotification` avec vendor, model, serial, firmware
3. Attendre `BootNotificationConfirmation` avec status=Accepted
4. Passer en AVAILABLE
5. Envoyer `StatusNotification(connectorId=0, status=Available)`
6. Envoyer `StatusNotification(connectorId=1, status=Available)` (pour chaque connecteur)

#### Phase AVAILABLE (en attente)
1. Envoyer `Heartbeat` toutes les `heartbeat-interval-seconds`
2. À chaque tick (30s) : probabilité `auto-session-probability` (15%) de démarrer une session
3. Si déclenchement ? passer en PREPARING

#### Phase PREPARING (badge détecté)
1. Générer un idTag aléatoire parmi une liste prédéfinie : `["RFID-0001", "RFID-0002", ..., "RFID-0020"]`
2. Envoyer `Authorize(idTag)` ? attendre `AuthorizeConfirmation(Accepted)`
3. Envoyer `StatusNotification(connectorId, status=Preparing)`
4. Attendre 2-5 secondes (simulation branchement câble)
5. Passer en CHARGING

#### Phase CHARGING (charge active)
1. Envoyer `StartTransaction(connectorId, idTag, meterStart, timestamp)`
2. Attendre `StartTransactionConfirmation` ? récupérer le `transactionId`
3. Envoyer `StatusNotification(connectorId, status=Charging)`
4. **Boucle MeterValues** toutes les `meter-interval-seconds` (10s) :
   - Calculer la puissance selon le `ChargingProfile` (voir ci-dessous)
   - Incrémenter le compteur énergie
   - Envoyer `MeterValues` avec les sampledValues :
     - `Energy.Active.Import.Register` (Wh) — cumul
     - `Power.Active.Import` (W) — instantané
     - `Current.Import` (A) — instantané
     - `Voltage` (V) — ~230V ± bruit
     - `Temperature` (Celsius) — 20-45°C selon puissance
5. **Durée de session** : random entre 30 min et 4 heures (simulé accéléré : 3-8 minutes réelles)
6. À la fin ? `StopTransaction(transactionId, meterStop, timestamp, reason=Local)`
7. Envoyer `StatusNotification(connectorId, status=Available)`
8. Retour en AVAILABLE

#### Phase FAULTED (erreur)
1. Envoyer `StatusNotification(connectorId, status=Faulted, errorCode=GroundFailure|OverCurrentFailure|PowerSwitchFailure)`
2. Attendre 30-120 secondes
3. Envoyer `StatusNotification(connectorId, status=Available, errorCode=NoError)`
4. Retour en AVAILABLE

### ChargingProfile — Courbe de puissance réaliste

```
Puissance (kW)
  max ¦     +--------------------------+
      ¦    /                            \
      ¦   /                              \
      ¦  / montée                  descente \
  0   ¦-/     (CC phase)          (CV phase) \--
      +-------------------------------------------- Temps
       0%   5%              80%    95%   100% SoC
```

Implémenter un profil simplifié :
- **0% ? 5%** : montée linéaire de 0 à `maxPowerKw` sur 2 minutes
- **5% ? 80%** : puissance constante à `maxPowerKw` (mode CC)
- **80% ? 95%** : décroissance linéaire de `maxPowerKw` à `maxPowerKw * 0.3`
- **95% ? 100%** : décroissance à `maxPowerKw * 0.1`
- Ajouter du bruit gaussien ±3% sur la puissance
- Tension : `230V ± random(0, 5)V`
- Courant : `puissance / tension`
- Température : `20 + (puissance / maxPowerKw) * 25` °C

## REST API — Spécifications complètes

### GET /api/chargepoints

Retourne toutes les bornes avec leurs connecteurs inline.

```json
[
  {
    "chargePointId": "BORNE_A",
    "vendor": "Legrand",
    "model": "Green'Up Premium",
    "serialNumber": "LGR-2024-001",
    "firmwareVersion": "1.4.2",
    "status": "Charging",
    "online": true,
    "lastHeartbeat": "2026-05-22T14:30:00Z",
    "registeredAt": "2026-05-22T08:00:00Z",
    "errorCode": "NoError",
    "connectors": [
      {
        "connectorId": 1,
        "status": "Charging",
        "currentPowerKw": 7.2,
        "currentAmps": 31.3,
        "voltage": 230.2,
        "temperatureCelsius": 38.5,
        "totalEnergyKwh": 14.8
      }
    ]
  }
]
```

### GET /api/chargepoints/{id}

Même structure, une seule borne. 404 si pas trouvée.

### GET /api/chargepoints/{id}/connectors

Retourne uniquement les connecteurs d'une borne.

### GET /api/sessions?status={Active|Completed|all}&from={iso}&to={iso}&chargePointId={id}

Paramètres optionnels de filtrage. Retourne :

```json
[
  {
    "id": 1,
    "transactionId": 1001,
    "chargePointId": "BORNE_A",
    "connectorId": 1,
    "idTag": "RFID-0042",
    "startTime": "2026-05-22T12:30:00Z",
    "stopTime": null,
    "meterStartWh": 500000,
    "meterStopWh": null,
    "energyDeliveredKwh": 14.8,
    "stopReason": null,
    "status": "Active",
    "durationMinutes": 120
  }
]
```

### GET /api/sessions/active

Raccourci : sessions avec `status=Active`.

### GET /api/meter-values/{chargePointId}?connectorId={n}&last={minutes}

Historique des mesures. `last=60` = dernière heure. Retourne :

```json
[
  {
    "timestamp": "2026-05-22T14:30:10Z",
    "connectorId": 1,
    "transactionId": 1001,
    "measurand": "Power.Active.Import",
    "value": 7200.0,
    "unit": "W"
  }
]
```

### GET /api/stats

```json
{
  "totalChargePoints": 5,
  "onlineChargePoints": 4,
  "chargingNow": 2,
  "availableNow": 2,
  "faultedNow": 1,
  "activeSessionsCount": 2,
  "totalPowerKw": 29.2,
  "todayEnergyKwh": 63.6,
  "todaySessionsCount": 8,
  "todaySessionsCompleted": 6,
  "averageSessionDurationMinutes": 95,
  "averageEnergyPerSessionKwh": 18.5
}
```

### GET /api/logs?chargePointId={id}&action={action}&direction={IN|OUT}&last={minutes}&limit={n}

```json
[
  {
    "id": 42,
    "chargePointId": "BORNE_A",
    "direction": "IN",
    "action": "MeterValues",
    "payload": "{...json complet...}",
    "timestamp": "2026-05-22T14:30:10Z"
  }
]
```

### POST /api/chargepoints/{id}/remote-start

Body : `{ "idTag": "RFID-0042", "connectorId": 1 }`

Envoie un `RemoteStartTransaction` OCPP à la borne simulée. Retourne :

```json
{ "status": "Accepted", "message": "RemoteStartTransaction envoyé à BORNE_A" }
```

### POST /api/chargepoints/{id}/remote-stop

Body : `{ "transactionId": 1001 }`

### POST /api/chargepoints/{id}/reset

Body : `{ "type": "Soft" }` ou `{ "type": "Hard" }`

### POST /api/chargepoints/{id}/unlock

Body : `{ "connectorId": 1 }`

### POST /api/simulator/scenario

Déclencher manuellement un scénario de simulation :

```json
{
  "scenario": "START_ALL",       // démarre une charge sur toutes les bornes dispo
  "scenario": "FAULT_ONE",       // met une borne random en Faulted
  "scenario": "STOP_ALL",        // arrête toutes les charges
  "scenario": "DISCONNECT_ONE",  // simule déconnexion d'une borne
  "scenario": "PEAK_LOAD",       // toutes bornes en charge simultanée
  "scenario": "RESET_ALL",       // reboot toutes les bornes
  "chargePointId": "BORNE_A"     // optionnel, cibler une borne spécifique
}
```

### WebSocket /ws/live (STOMP)

L'app front-end se connecte en STOMP sur `/ws/live` et s'abonne à `/topic/events`.

Chaque événement :

```json
{
  "type": "STATUS_CHANGE",
  "chargePointId": "BORNE_A",
  "connectorId": 1,
  "data": { "status": "Charging", "previousStatus": "Preparing" },
  "timestamp": "2026-05-22T14:30:00Z"
}
```

Types d'événements :
- `CHARGE_POINT_CONNECTED` — borne se connecte au CSMS
- `CHARGE_POINT_DISCONNECTED` — borne perdue
- `STATUS_CHANGE` — changement état connecteur
- `SESSION_STARTED` — nouvelle session de charge
- `SESSION_STOPPED` — fin de session
- `METER_UPDATE` — nouvelles mesures (puissance, énergie, courant)
- `FAULT` — erreur détectée
- `HEARTBEAT` — preuve de vie

## CORS

Autoriser toutes les origines en dev :

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("*").allowedMethods("*");
    }
}
```

## Comportement au démarrage

1. Spring Boot démarre sur `8080`
2. Le CSMS (`JSONServer` de Java-OCA-OCPP) démarre sur `9000`
3. Si `simulator.enabled=true`, le `SimulatorManager` :
   - Crée un `ChargePointSimulator` par borne configurée
   - Chaque simulateur se connecte au CSMS en WebSocket `ws://localhost:9000/ocpp/{id}`
   - Boot séquentiel avec 2s de délai entre chaque borne
   - Démarre le scheduler qui fait vivre les bornes (heartbeat, sessions aléatoires, etc.)

## Logs console attendus au démarrage

```
2026-05-22 08:00:00 INFO  --- CSMS Server started on port 9000 (OCPP 1.6J) ---
2026-05-22 08:00:01 INFO  [SIMULATOR] Starting 5 simulated charge points...
2026-05-22 08:00:01 INFO  [BORNE_A] Connecting to ws://localhost:9000/ocpp/BORNE_A
2026-05-22 08:00:01 INFO  [CSMS] New session: BORNE_A connected
2026-05-22 08:00:01 INFO  [CSMS] BootNotification from BORNE_A: Legrand Green'Up Premium
2026-05-22 08:00:01 INFO  [BORNE_A] Registered ? — Status: Available
2026-05-22 08:00:03 INFO  [BORNE_B] Connecting to ws://localhost:9000/ocpp/BORNE_B
...
2026-05-22 08:00:09 INFO  [SIMULATOR] All charge points online. Simulation running.
2026-05-22 08:00:09 INFO  --- REST API available at http://localhost:8080/api ---
2026-05-22 08:00:09 INFO  --- H2 Console at http://localhost:8080/h2-console ---
```

## Contraintes & détails importants

### OCPP 1.6J Messages — Implémentation requise

**Borne ? CSMS (le CSMS doit handler ces messages) :**
- `BootNotification` ? répondre Accepted, interval=heartbeat-interval
- `Heartbeat` ? répondre currentTime
- `StatusNotification` ? répondre vide, logger le changement d'état
- `Authorize` ? répondre Accepted (toujours, c'est un simulateur)
- `StartTransaction` ? générer un transactionId auto-incrémenté, répondre Accepted
- `StopTransaction` ? calculer énergie, répondre vide
- `MeterValues` ? parser tous les sampledValue, stocker en DB
- `DataTransfer` ? répondre Accepted

**CSMS ? Borne (le simulateur de borne doit handler ces messages) :**
- `RemoteStartTransaction` ? la borne simulée démarre une charge
- `RemoteStopTransaction` ? la borne simulée arrête la charge en cours
- `Reset` ? la borne simulée reboot (déco/reco)
- `UnlockConnector` ? la borne simulée passe le connecteur en Available
- `TriggerMessage` ? la borne envoie le message demandé immédiatement

### Thread Safety
- Le `OcppSessionRegistry` (map chargePointId ? UUID) doit être thread-safe (`ConcurrentHashMap`)
- Les simulateurs tournent sur des threads séparés via `@Scheduled` ou `ScheduledExecutorService`
- Les écritures DB doivent être transactionnelles

### Transaction IDs
- Auto-incrémenté côté CSMS à partir de 1000
- Utiliser un `AtomicInteger`

### IDs de badges RFID simulés
```java
List<String> RFID_TAGS = List.of(
    "RFID-0001", "RFID-0002", "RFID-0003", "RFID-0004", "RFID-0005",
    "RFID-0006", "RFID-0007", "RFID-0008", "RFID-0009", "RFID-0010",
    "RFID-0011", "RFID-0012", "RFID-0013", "RFID-0014", "RFID-0015",
    "RFID-0016", "RFID-0017", "RFID-0018", "RFID-0019", "RFID-0020"
);
```

### Timing de simulation (mode accéléré)
Pour ne pas attendre des heures en dev :
- 1 minute réelle = ~15 minutes simulées (facteur x15)
- Une session de "2 heures" dure ~8 minutes réelles
- Les MeterValues sont envoyés toutes les 10 secondes réelles
- Le heartbeat toutes les 30 secondes réelles

### Gestion des erreurs
- Si une borne simulée perd la connexion WebSocket ? retry avec backoff exponentiel (2s, 4s, 8s, max 30s)
- Logger toute erreur mais ne jamais crasher le serveur
- Les endpoints API retournent 404 pour borne/session introuvable, 400 pour paramètres invalides

## Comment tester

```bash
# Lancer le serveur
./mvnw spring-boot:run

# Vérifier les bornes
curl http://localhost:8080/api/chargepoints | jq

# Vérifier les stats
curl http://localhost:8080/api/stats | jq

# Voir les sessions actives
curl http://localhost:8080/api/sessions/active | jq

# Voir les logs OCPP
curl "http://localhost:8080/api/logs?limit=20" | jq

# Forcer un démarrage de charge
curl -X POST http://localhost:8080/api/chargepoints/BORNE_B/remote-start \
  -H "Content-Type: application/json" \
  -d '{"idTag": "RFID-0042", "connectorId": 1}'

# Déclencher toutes les bornes
curl -X POST http://localhost:8080/api/simulator/scenario \
  -H "Content-Type: application/json" \
  -d '{"scenario": "PEAK_LOAD"}'

# WebSocket test (avec wscat)
wscat -c ws://localhost:8080/ws/live
> SUBSCRIBE /topic/events
```

## Résumé

Ce projet est un **simulateur OCPP 1.6J autonome et complet** :
- Le CSMS reçoit les messages OCPP comme si c'étaient de vraies bornes Legrand
- Les bornes simulées reproduisent le comportement réel (boot, charge, erreurs, heartbeat)
- L'API REST expose tout ce qu'une app de monitoring a besoin
- Le WebSocket `/ws/live` permet le temps réel côté front-end
- La DB H2 in-memory rend le setup zéro-config
- Les scénarios permettent de tester tous les cas d'usage

L'app de monitoring qu'on va développer consommera cette API exactement comme elle consommerait un vrai CSMS connecté à de vraies bornes Legrand.