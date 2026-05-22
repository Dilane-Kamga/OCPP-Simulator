# OCPP 1.6J Simulator — Design Spec

**Project** : NEXCharge — Simulateur de bornes & CSMS local
**Date** : 2026-05-22
**Statut** : Design validé — prêt pour planning d'implémentation
**Source** : `Claude.MD` à la racine du repo (spec brute) + brainstorming itératif

---

## 1. Contexte et objectif

Construire un serveur Java Spring Boot autonome qui simule un parc de 5 bornes Legrand via le protocole OCPP 1.6J, exposant une API REST + WebSocket que l'app de monitoring NEXCharge consommera. Tout tourne en local (port 8080 REST, port 9000 OCPP), sans Docker ni borne physique.

Le simulateur fait partie de l'écosystème **NEXCharge** (hackathon Accenture Mauritius). Il permet à l'équipe front-end et IA de développer/tester sans dépendance aux bornes réelles. Une fois le projet en production, ce simulateur reste utile pour les tests d'intégration et la démo.

---

## 2. Décisions d'architecture (vs spec brute `Claude.MD`)

Le design ci-dessous diverge volontairement de `Claude.MD` sur plusieurs points, à la suite du brainstorming :

| Sujet | `Claude.MD` | Design retenu | Raison |
|---|---|---|---|
| Base de données | H2 in-memory | **H2 file** (`./data/csms.mv.db` + `AUTO_SERVER=TRUE`) | Persistance entre redémarrages demandée par l'utilisateur. Cleanup manuel (script `clean.sh`). |
| Java | 17+ | **Java 21 LTS** + virtual threads | Cohérence avec NEXCharge core. Virtual threads pour 5 simulateurs concurrents sans coût mémoire. |
| Package racine | `com.monapp.csms` | **`com.accenture.nexcharge.simulator`** | Cohérence avec NEXCharge (`com.accenture.nexcharge.*`). |
| OCPP-J version | `1.1.0` | **`1.0.2`** | `1.1.0` n'existe pas sur Maven Central pour `OCPP-J` (vérifié). Coquille de la spec brute. |
| `auto-session-probability` | `0.15` | **`0.05`** | À 15%, toutes les bornes finissent en `Charging` rapidement → pas de mix d'états visible pendant la démo. |
| Tests | non spécifié | **TDD complet** (`superpowers:test-driven-development`) | Demande utilisateur. |
| `ddl-auto` | `create-drop` | **`update`** | Évite de perdre les data au restart. |

Toutes les autres décisions de `Claude.MD` (architecture WebSocket réelle, format API, state machine, courbe CC/CV, scénarios, etc.) sont conservées telles quelles.

---

## 3. Stack technique

- **Java 21 LTS** + Maven (avec wrapper `./mvnw`)
- **Spring Boot 3.3.0** : `spring-boot-starter-web`, `-websocket`, `-data-jpa`
- **H2 file** + Spring Data JPA / Hibernate
- **Java-OCA-OCPP** :
  - `eu.chargetime.ocpp:v1_6:1.1.0`
  - `eu.chargetime.ocpp:OCPP-J:1.0.2`
- **Lombok** + Jackson (auto)
- **JUnit 5 + Mockito + AssertJ** + Spring Test (`@SpringBootTest`, `@DataJpaTest`, `@WebMvcTest`)

---

## 4. Architecture haut niveau

```
+-----------------------------------------------------------------+
|                       JVM (un seul process)                      |
|                                                                  |
|  +--------------------+         +-----------------------------+  |
|  |  CSMS Server       | <--ws-->|  ChargePointSimulator x5    |  |
|  |  (JSONServer       |         |  - state machine            |  |
|  |   port 9000)       |         |  - ChargingProfile (CC/CV)  |  |
|  |  CsmsEventHandler  |         |  - retry exp backoff        |  |
|  +---------+----------+         +--------------+--------------+  |
|            |                                   |                 |
|            v                                   v                 |
|  +-------------------------------------------------------------+ |
|  |   Service layer (Spring beans)                              | |
|  |   ChargePointService / SessionService / MeterService /      | |
|  |   StatsService / LiveEventService / ScenarioService         | |
|  +------+--------------------------------------------+---------+ |
|         |                                            |           |
|         v                                            v           |
|  +-------------+                    +----------------------+     |
|  |  REST API   |                    |   WebSocket STOMP    |     |
|  |  (port 8080)|                    |   /ws/live           |     |
|  |  15 endpts  |                    |   /topic/events      |     |
|  +------+------+                    +-----------+----------+     |
|         |                                       |                |
|         +-------------------+-------------------+                |
|                             v                                    |
|                  +-----------------------+                       |
|                  |   H2 File Database    |                       |
|                  |   ./data/csms.mv.db   |                       |
|                  |   AUTO_SERVER=TRUE    |                       |
|                  +-----------------------+                       |
+------------------------------------------------------------------+
```

Le CSMS et les simulateurs partagent la JVM mais communiquent via une **vraie connexion WebSocket OCPP-J**, pas par appel direct in-process. Cela garantit qu'une vraie borne Legrand peut se substituer à n'importe quel simulateur sans modification du CSMS.

---

## 5. Modèle de données (H2)

5 tables, exactement comme dans `Claude.MD` section "Modèle de données". Pas de modification.

- `charge_points` (PK `charge_point_id` VARCHAR)
- `connectors` (PK auto, FK `charge_point_id`)
- `charging_sessions` (PK auto, `transaction_id` UNIQUE INT)
- `meter_readings` (PK auto, indexée sur `charge_point_id` + `timestamp` desc)
- `ocpp_logs` (PK auto, indexée sur `charge_point_id` + `timestamp` desc)

**Index recommandés** (à créer via JPA `@Index` sur les entities) :
- `meter_readings(charge_point_id, timestamp)` — pour `GET /api/meter-values/{id}?last=N`
- `ocpp_logs(charge_point_id, timestamp)` — pour `GET /api/logs?...`
- `charging_sessions(status)` — pour `GET /api/sessions/active`
- `charging_sessions(transaction_id)` UNIQUE — pour les lookups OCPP

---

## 6. Composants critiques

### 6.1 CSMS Server (`ocpp/CsmsServer.java`)

- `@Component` Spring, `@PostConstruct` démarre `JSONServer` sur port 9000
- Enregistre `CsmsEventHandler` comme `ServerCoreEventHandler`
- Maintient `OcppSessionRegistry` (`ConcurrentHashMap<chargePointId, UUID>`)
- `@PreDestroy` ferme proprement les sessions

### 6.2 CsmsEventHandler (`ocpp/CsmsEventHandler.java`)

Implémente `ServerCoreEventHandler`. Chaque méthode :
1. Persiste le message reçu dans `ocpp_logs` (direction `IN`)
2. Met à jour les entities concernées (transactionnel)
3. Publie un `LiveEventDto` via `LiveEventService` (sans bloquer)
4. Retourne la confirmation OCPP appropriée

| Méthode | Action |
|---|---|
| `handleBootNotificationRequest` | Upsert `ChargePointEntity`, retourne `Accepted` + `interval=heartbeat-interval` |
| `handleHeartbeatRequest` | Maj `last_heartbeat`, retourne `currentTime` |
| `handleStatusNotificationRequest` | Maj `ConnectorEntity.status` + `error_code`, publie `STATUS_CHANGE` |
| `handleAuthorizeRequest` | Toujours `Accepted` (simulateur) |
| `handleStartTransactionRequest` | `AtomicInteger` (start 1000) génère `transactionId`, crée `ChargingSessionEntity`, publie `SESSION_STARTED` |
| `handleStopTransactionRequest` | Calcule `energy_delivered_kwh`, marque session `Completed`, publie `SESSION_STOPPED` |
| `handleMeterValuesRequest` | Parse `SampledValue[]`, batch-insert `MeterReadingEntity`, publie `METER_UPDATE` |
| `handleDataTransferRequest` | `Accepted` (no-op) |

### 6.3 ChargePointSimulator (`simulator/ChargePointSimulator.java`)

Une instance par borne. Champs :

```java
private final String chargePointId;
private final ChargePointConfig config;
private final JSONClient ocppClient;
private final ScheduledExecutorService scheduler;  // Executors.newScheduledThreadPool(2, virtualThreadFactory)
private volatile State state = State.BOOTING;
private final ChargingProfile activeProfile;       // null si pas en CHARGING
private final AtomicInteger retryDelaySeconds = new AtomicInteger(2);
```

État interne (`State` enum) : `BOOTING, AVAILABLE, PREPARING, CHARGING, FAULTED`. Toutes transitions explicites via `transitionTo(State next)` qui :
1. Valide la transition (table de transitions valides)
2. Log la transition
3. Met à jour la DB (via service)
4. Publie un `STATUS_CHANGE` event

Le client OCPP du simulateur implémente `ClientCoreEventHandler` pour recevoir les commandes serveur :
- `RemoteStartTransaction` → force `AVAILABLE → PREPARING → CHARGING` immédiatement (sans passer par le dice 5% du tick global)
- `RemoteStopTransaction` → `CHARGING → AVAILABLE` (avec `StopTransaction` ; `stop_reason=Remote`)
- `Reset(Hard|Soft)` → close WebSocket, sleep 3-5s, reconnect, full BOOTING cycle
- `UnlockConnector` → `* → AVAILABLE` (force, peu importe l'état actuel)
- `TriggerMessage` → envoie immédiatement le message demandé

**Retry connexion** : backoff exponentiel `2s, 4s, 8s, 16s, 30s, 30s, ...` (cap 30s). Reset du delay sur connexion réussie.

### 6.4 ChargingProfile (`simulator/ChargingProfile.java`)

Pure (pas de Spring, testable unitairement). Représente une charge active.

```java
public class ChargingProfile {
    final double maxPowerKw;
    final double accelerationFactor;  // 15
    private double simulatedSocPercent = 0.0;
    private double totalEnergyDeliveredKwh = 0.0;

    public PowerSnapshot tick(Duration realElapsed) { ... }
    // calcule puissance selon SoC (4 phases CC/CV), bruit gaussien ±3%,
    // voltage 230V + uniform(0, 5), current = power/voltage,
    // temperature = 20 + (power/maxPower)*25
    // incrémente totalEnergyDeliveredKwh

    public boolean isComplete() { return simulatedSocPercent >= 100.0; }
}
```

Phases (selon SoC simulé, qui avance à `realElapsed × accelerationFactor`) :

| SoC | Puissance |
|---|---|
| 0% → 5% | rampe linéaire `0 → maxPowerKw` (sur ~2 min simulé) |
| 5% → 80% | constant `maxPowerKw` (mode CC) |
| 80% → 95% | décroissance `maxPowerKw → 0.3 × maxPowerKw` (CV) |
| 95% → 100% | décroissance `→ 0.1 × maxPowerKw` (top-off) |

Bruit gaussien ±3% appliqué à la puissance après calcul de phase.

### 6.5 SimulatorManager (`simulator/SimulatorManager.java`)

- `@PostConstruct` (mais avec `@DependsOn("csmsServer")`) : crée 1 `ChargePointSimulator` par borne
- Démarrage séquentiel : `Thread.sleep(2000)` entre chaque
- Tick global `@Scheduled(fixedRate=30000)` :
  - Pour chaque simu en `AVAILABLE` : roll dice `auto-session-probability` (5%) → `transitionTo(PREPARING)`
  - Pour chaque simu en `AVAILABLE/CHARGING` : roll dice `random-event-probability` (2%) → `transitionTo(FAULTED)`
- `@PreDestroy` : ferme tous les simulateurs proprement

### 6.6 LiveEventService

```java
@Service
public class LiveEventService {
    private final SimpMessagingTemplate template;

    public void publish(LiveEventDto event) {
        template.convertAndSend("/topic/events", event);
    }
}
```

8 types d'événements : `CHARGE_POINT_CONNECTED, CHARGE_POINT_DISCONNECTED, STATUS_CHANGE, SESSION_STARTED, SESSION_STOPPED, METER_UPDATE, FAULT, HEARTBEAT`.

### 6.7 SimulatorScenarioService (`simulator/SimulatorScenarioService.java`)

6 scénarios déclenchables via `POST /api/simulator/scenario` :

| Scénario | Action |
|---|---|
| `START_ALL` | Pour chaque simu en `Available` : skip dice, force `transitionTo(PREPARING)` |
| `STOP_ALL` | Pour chaque simu en `Charging` : envoie `RemoteStopTransaction` au CSMS qui forward |
| `FAULT_ONE` | Choisit 1 simu random non-Faulted, `transitionTo(FAULTED)` |
| `DISCONNECT_ONE` | Choisit 1 simu random, ferme la WebSocket → trigger retry après 5s |
| `PEAK_LOAD` | Pour chaque simu en `Charging`/`Faulted` : `transitionTo(AVAILABLE)`. Puis pour chaque simu : force `AVAILABLE → PREPARING → CHARGING`. Toutes les bornes en charge simultanément. |
| `RESET_ALL` | Envoie `Reset(Soft)` à chaque borne via le CSMS |

Si `chargePointId` fourni dans le body : ne s'applique qu'à cette borne. Sinon : toutes.

---

## 7. REST API — 15 endpoints

Spec exacte conforme à `Claude.MD` section "REST API". Récap :

| Méthode | Path | Description |
|---|---|---|
| GET | `/api/chargepoints` | Toutes les bornes + connecteurs inline |
| GET | `/api/chargepoints/{id}` | Une borne (404 si inconnu) |
| GET | `/api/chargepoints/{id}/connectors` | Connecteurs d'une borne |
| GET | `/api/sessions?status=&from=&to=&chargePointId=` | Filtres optionnels |
| GET | `/api/sessions/active` | Shortcut `status=Active` |
| GET | `/api/sessions/{id}` | Une session (404 si inconnu) |
| GET | `/api/meter-values/{cpId}?connectorId=&last=` | Historique mesures (`last` en min) |
| GET | `/api/stats` | Métriques live agrégées |
| GET | `/api/logs?chargePointId=&action=&direction=&last=&limit=` | Logs OCPP |
| POST | `/api/chargepoints/{id}/remote-start` | `RemoteStartTransaction` |
| POST | `/api/chargepoints/{id}/remote-stop` | `RemoteStopTransaction` |
| POST | `/api/chargepoints/{id}/reset` | `Reset(Soft|Hard)` |
| POST | `/api/chargepoints/{id}/unlock` | `UnlockConnector` |
| POST | `/api/simulator/scenario` | Déclenche un scénario |

**Conventions transverses** :
- Dates ISO-8601 UTC (`Instant` Java + Jackson `JavaTimeModule`)
- CORS `allowedOrigins("*")` sur `/api/**` et `/ws/**`
- Codes : 200 / 201 (created) / 202 (async) / 400 (params invalides) / 404 (inconnu) / 409 (conflict, ex: remote-start sur borne déjà en charge) / 500 (avec body `{error, message}`)
- Default `limit=100`, max `limit=1000` sur `/api/logs`

---

## 8. WebSocket /ws/live

- Endpoint STOMP : `/ws/live`
- Topic : `/topic/events`
- Format événement : `{ type, chargePointId, connectorId?, data, timestamp }`
- Pas de filtrage côté serveur — le client filtre par `type` ou `chargePointId`

---

## 9. State machine (résumé)

```
                +----------+
       reset    | BOOTING  |
       ------> +----------+
       |              | BootNotification accepted
       |              v
       |   +-------------------+
       |   |    AVAILABLE      | <-----------+
       |   +-------------------+             |
       |          | dice 5% / RemoteStart    |
       |          v                          | StopTransaction
       |   +-------------------+             | (Local|Remote|EVDisconnected)
       |   |    PREPARING      |             |
       |   +-------------------+             |
       |          | StartTransaction OK      |
       |          v                          |
       |   +-------------------+             |
       |   |    CHARGING       | ------------+
       |   | MeterValues 10s   |
       |   +-------------------+
       |          | dice 2% / Reset
       |          v
       |   +-------------------+
       +-- |    FAULTED        |
           +-------------------+
           auto-recover 30-120s
```

---

## 10. Configuration `application.yml`

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:./data/csms;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
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
  acceleration-factor: 15
  heartbeat-interval-seconds: 30
  meter-interval-seconds: 10
  auto-session-probability: 0.05
  random-event-probability: 0.02
  charge-points:
    - { id: BORNE_A, vendor: Legrand, model: "Green'Up Premium", serial: LGR-2024-001, max-power-kw: 7.4, connectors: 1, firmware: "1.4.2" }
    - { id: BORNE_B, vendor: Legrand, model: "Green'Up Premium", serial: LGR-2024-002, max-power-kw: 7.4, connectors: 1, firmware: "1.4.2" }
    - { id: BORNE_C, vendor: Legrand, model: "Green'Up Control", serial: LGR-2024-003, max-power-kw: 22.0, connectors: 2, firmware: "2.1.0" }
    - { id: BORNE_D, vendor: Legrand, model: "Green'Up Premium", serial: LGR-2024-004, max-power-kw: 7.4, connectors: 1, firmware: "1.4.2" }
    - { id: BORNE_E, vendor: Legrand, model: "Green'Up Control", serial: LGR-2024-005, max-power-kw: 22.0, connectors: 2, firmware: "2.1.0" }
  rfid-tags: [RFID-0001, RFID-0002, RFID-0003, RFID-0004, RFID-0005, RFID-0006, RFID-0007, RFID-0008, RFID-0009, RFID-0010, RFID-0011, RFID-0012, RFID-0013, RFID-0014, RFID-0015, RFID-0016, RFID-0017, RFID-0018, RFID-0019, RFID-0020]
```

---

## 11. Stratégie de tests (TDD)

Approche TDD strict via `superpowers:test-driven-development` : Red → Green → Refactor à chaque étape.

| Couche | Type | Couverture |
|---|---|---|
| `ChargingProfile` | unit pur | 4 phases CC/CV, bruit ±3%, énergie monotone, tick avec accélération |
| `ChargePointSimulator` state machine | unit + mocks | toutes transitions valides, transitions invalides rejetées, recovery FAULTED |
| `CsmsEventHandler` | unit + mocks repos | chaque méthode OCPP retourne la bonne réponse, persiste, publie l'event |
| Repositories JPA | `@DataJpaTest` | `findByStatus`, `findActive`, agrégations stats |
| Controllers REST | `@WebMvcTest` + MockMvc | happy path + 404/400/409 |
| `LiveEventService` + WebSocket | `@SpringBootTest` + STOMP test client | abonnement `/topic/events`, réception après `StatusNotification` |
| End-to-end | `@SpringBootTest` + JSONClient | 1 simu boot → CSMS reçoit → StartTransaction → MeterValues → StopTransaction |

---

## 12. Structure du projet

```
simulateur/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/wrapper/
├── application.yml
├── data/                                   # H2 file (gitignored)
├── .gitignore                              # data/, target/, .idea/, *.log
├── README.md                               # quickstart + curl examples
├── clean.sh                                # rm -rf data/ target/
├── docs/superpowers/specs/
│   └── 2026-05-22-ocpp-simulator-design.md
├── src/main/java/com/accenture/nexcharge/simulator/
│   ├── CsmsApplication.java
│   ├── config/
│   │   ├── SimulatorProperties.java
│   │   ├── WebSocketConfig.java
│   │   └── CorsConfig.java
│   ├── model/
│   │   ├── entity/{ChargePoint,Connector,ChargingSession,MeterReading,OcppLog}Entity.java
│   │   ├── enums/{ChargePoint,Connector,Session}Status.java
│   │   └── dto/{ChargePoint,Session,MeterValue,Stats,OcppLog,LiveEvent,RemoteStart,RemoteStop,Scenario}*.java
│   ├── repository/{ChargePoint,Connector,ChargingSession,MeterReading,OcppLog}Repository.java
│   ├── ocpp/
│   │   ├── CsmsServer.java
│   │   ├── CsmsEventHandler.java
│   │   └── OcppSessionRegistry.java
│   ├── simulator/
│   │   ├── ChargePointSimulator.java
│   │   ├── SimulatorManager.java
│   │   ├── ChargingProfile.java
│   │   └── SimulatorScenarioService.java
│   ├── service/
│   │   ├── ChargePointService.java
│   │   ├── SessionService.java
│   │   ├── MeterService.java
│   │   ├── StatsService.java
│   │   └── LiveEventService.java
│   └── controller/
│       ├── ChargePointController.java
│       ├── SessionController.java
│       ├── MeterController.java
│       ├── StatsController.java
│       ├── LogController.java
│       ├── RemoteCommandController.java
│       └── SimulatorController.java
└── src/test/java/com/accenture/nexcharge/simulator/
    ├── simulator/{ChargingProfileTest, ChargePointSimulatorStateMachineTest}.java
    ├── ocpp/CsmsEventHandlerTest.java
    ├── controller/*ControllerTest.java
    ├── repository/*RepositoryTest.java
    ├── websocket/LiveEventWebSocketTest.java
    └── integration/EndToEndSimulationIT.java
```

---

## 13. Critères d'acceptance

1. `./mvnw clean install` passe sans erreur (compile + tests)
2. `./mvnw spring-boot:run` démarre sans aucune config externe (zéro variable d'environnement)
3. À T+15s : `curl http://localhost:8080/api/chargepoints | jq '.[].status'` retourne 5 bornes en `Available` (toutes bootées)
4. Après `POST /api/simulator/scenario {"scenario":"START_ALL"}` : `curl /api/sessions/active | jq 'length'` >= 1 en < 10s
5. `wscat -c ws://localhost:8080/ws/live` puis `SUBSCRIBE /topic/events` reçoit des `METER_UPDATE` toutes les 10s pour chaque borne en charge
6. `POST /api/chargepoints/BORNE_B/remote-start {"idTag":"RFID-0042","connectorId":1}` → la borne passe en CHARGING en < 5s
7. `POST /api/simulator/scenario {"scenario":"FAULT_ONE"}` → 1 borne en `Faulted` visible dans `GET /api/chargepoints`
8. `./mvnw test` : 100% des tests JUnit passent
9. Une vraie borne (Legrand ou simulateur tiers) peut se connecter à `ws://localhost:9000/ocpp/{id}` et participer normalement (le CSMS ne discrimine pas les bornes simulées)
10. Le fichier `data/csms.mv.db` survit à un restart (`./mvnw spring-boot:run`, Ctrl+C, relancer → données présentes)

---

## 14. Hors-scope (non-livrables)

- **Authentification** : pas de JWT/OIDC. Le simulateur tourne en local. L'auth viendra dans le core NEXCharge.
- **Cleanup automatique des `meter_readings`** : décision utilisateur — pas de retention. À gérer manuellement avec `clean.sh`.
- **Profils de charge configurables par scénario** (rapide, lent, smart-charging) : la courbe CC/CV de base suffit pour ce sprint.
- **Smart Charging Profile (OCPP feature)** : `SetChargingProfile` non implémenté.
- **Local Auth List** : `SendLocalList`, `GetLocalListVersion` non implémentés.
- **Firmware Management** : `UpdateFirmware`, `FirmwareStatusNotification` non implémentés.
- **Diagnostics** : `GetDiagnostics`, `DiagnosticsStatusNotification` non implémentés.

Ces features pourront être ajoutées dans un sprint ultérieur si l'app de monitoring en a besoin.
