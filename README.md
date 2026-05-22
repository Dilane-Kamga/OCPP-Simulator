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
