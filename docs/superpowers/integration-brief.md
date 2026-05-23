# NEXCharge simulator — integration brief for the main app

This file is the brief to hand to the Claude Code session of the consumer app.
It tells it how to talk to the OCPP simulator we built.

OpenAPI spec (machine-readable): see `openapi.json` next to this file
(19 endpoints, 20 schemas, fetched from a running server).

---

## 1. Run the simulator

```
cd C:\Projects\Hackathon\simulateur
./mvnw spring-boot:run
```

- REST + WebSocket: http://localhost:8080
- OCPP wire (bornes ↔ CSMS): port 9100 — DO NOT touch from the app
- Swagger UI: http://localhost:8080/api/docs
- Reference console: http://localhost:8080/console/

Reset DB completely: stop Spring, `rm -rf data/`, restart.

---

## 2. Topology served by the simulator

- 2 bornes: `BORNE_A` (site `NEX_TOWER`), `BORNE_B` (site `NEXTERACOM`)
- Each: Legrand Green'Up Control 22 kW, **2 connectors** (C1 + C2)
- 20 RFID tags pre-seeded: `RFID-0001` … `RFID-0020`
- Auto-sessions ~20% per 30s tick; faults ~2%
- H2 file mode → state persists between restarts

**Concurrency** (recently added, important for your UI):
- C1 and C2 of a borne can charge simultaneously
- Load is balanced: combined draw never exceeds the borne's max kW
- A connector can fault without taking down the other side
- The borne goes globally `Faulted` only if all connectors faulted

---

## 3. Endpoints (REST)

### Reads
```
GET /api/chargepoints                                 → list with connectors inline
GET /api/chargepoints/{id}                            → 1 borne (404 if unknown)
GET /api/chargepoints/{id}/connectors
GET /api/sessions?status={Active|Completed|all}&chargePointId={id}&from={iso}&to={iso}
GET /api/sessions/active
GET /api/sessions/{id}
GET /api/meter-values/{chargePointId}?connectorId={n}&last={minutes}
GET /api/stats
GET /api/logs?chargePointId={id}&action={...}&direction={IN|OUT}&last={min}&limit={n}
```

### Commands (CSMS → borne)
```
POST /api/chargepoints/{id}/remote-start   { "idTag":"RFID-0001", "connectorId":1 }
POST /api/chargepoints/{id}/remote-stop    { "transactionId":1001 }
POST /api/chargepoints/{id}/reset          { "type":"Soft"|"Hard" }
POST /api/chargepoints/{id}/unlock         { "connectorId":1 }
POST /api/chargepoints/{id}/change-configuration { "key":"HeartbeatInterval", "value":"60" }
POST /api/chargepoints/{id}/get-configuration    { "keys":["HeartbeatInterval"] }   // keys optional
```

### Demo scenarios
```
POST /api/simulator/scenario  { "scenario": "<NAME>", "chargePointId":"<id>", "connectorId": <n> }

  START_ALL                                   start a session on every available borne
  START_ONE       {chargePointId, connectorId}     start on this exact connector
  STOP_ALL                                    stop every active session
  FAULT_ONE       {chargePointId, connectorId?}    fault 1 connector (or C1 if not set)
  DISCONNECT_ONE  {chargePointId}             reboot the whole borne (WS drop + reconnect)
  PEAK_LOAD                                   start every available + recover faulted
  RESET_ALL                                   reboot every borne
```

CORS is wide open on `/api/**` — call from any origin/port.

---

## 4. WebSocket (STOMP over SockJS)

- SockJS endpoint: `http://localhost:8080/ws/live`  (NOT `ws://`)
- Subscribe destination: `/topic/events`
- Each message is JSON:

```json
{
  "type": "METER_UPDATE",
  "chargePointId": "BORNE_A",
  "connectorId": 1,
  "data": { "readings": { "Power.Active.Import": 7200, "Current.Import": 31.3, "Voltage": 230.2 } },
  "timestamp": "2026-05-23T14:30:10Z"
}
```

Event `type` values:
```
CHARGE_POINT_CONNECTED, CHARGE_POINT_DISCONNECTED,
STATUS_CHANGE, SESSION_STARTED, SESSION_STOPPED,
METER_UPDATE, FAULT, HEARTBEAT
```

---

## 5. TypeScript types (copy as-is)

```ts
// types/simulator.ts
export type ConnectorStatus =
  | 'Available' | 'Preparing' | 'Charging' | 'Faulted'
  | 'Unavailable' | 'Reserved' | 'SuspendedEV' | 'SuspendedEVSE' | 'Finishing';

export type ChargePointStatus = ConnectorStatus;  // same vocabulary

export type Connector = {
  connectorId: number;
  status: ConnectorStatus;
  currentPowerKw: number | null;
  currentAmps: number | null;
  voltage: number | null;
  temperatureCelsius: number | null;
  totalEnergyKwh: number | null;
  errorCode: string | null;
  blocked: boolean;
  blockedReason: string | null;
  blockedAt: string | null;
};

export type ChargePoint = {
  chargePointId: string;
  site: string | null;
  vendor: string;
  model: string;
  serialNumber: string;
  firmwareVersion: string;
  status: ChargePointStatus;
  online: boolean;
  lastHeartbeat: string;
  registeredAt: string;
  errorCode: string;
  connectors: Connector[];
};

export type Session = {
  id: number;
  transactionId: number;
  chargePointId: string;
  connectorId: number;
  idTag: string;
  startTime: string;
  stopTime: string | null;
  meterStartWh: number;
  meterStopWh: number | null;
  energyDeliveredKwh: number;
  stopReason: string | null;
  status: 'Active' | 'Completed' | 'Error';
  durationMinutes: number;
};

export type MeterValueRow = {
  timestamp: string;
  connectorId: number;
  transactionId: number | null;
  measurand: string;   // 'Power.Active.Import' | 'Energy.Active.Import.Register' | 'Current.Import' | 'Voltage' | 'Temperature'
  value: number;
  unit: string;        // 'W' | 'Wh' | 'A' | 'V' | 'Celsius'
};

export type Stats = {
  totalChargePoints: number;
  onlineChargePoints: number;
  chargingNow: number;
  availableNow: number;
  faultedNow: number;
  activeSessionsCount: number;
  totalPowerKw: number;
  todayEnergyKwh: number;
  todaySessionsCount: number;
  todaySessionsCompleted: number;
  averageSessionDurationMinutes: number;
  averageEnergyPerSessionKwh: number;
};

export type LiveEventType =
  | 'CHARGE_POINT_CONNECTED' | 'CHARGE_POINT_DISCONNECTED'
  | 'STATUS_CHANGE' | 'SESSION_STARTED' | 'SESSION_STOPPED'
  | 'METER_UPDATE' | 'FAULT' | 'HEARTBEAT';

export type LiveEvent = {
  type: LiveEventType;
  chargePointId: string;
  connectorId: number | null;
  data: Record<string, unknown>;
  timestamp: string;
};

export type ScenarioName =
  | 'START_ALL' | 'START_ONE' | 'STOP_ALL'
  | 'FAULT_ONE' | 'DISCONNECT_ONE' | 'PEAK_LOAD' | 'RESET_ALL';
```

---

## 6. REST client (copy as-is)

```ts
// api/simulatorApi.ts
import type { ChargePoint, Session, MeterValueRow, Stats, ScenarioName } from '../types/simulator';

const BASE = import.meta.env.VITE_SIMULATOR_BASE ?? 'http://localhost:8080';

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    ...init,
  });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`${init?.method ?? 'GET'} ${path} → ${res.status} ${body}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const simulatorApi = {
  listChargePoints: () => http<ChargePoint[]>('/api/chargepoints'),
  getChargePoint:   (id: string) => http<ChargePoint>(`/api/chargepoints/${encodeURIComponent(id)}`),
  listSessions:     (q?: { status?: 'Active'|'Completed'|'all'; chargePointId?: string; from?: string; to?: string }) => {
    const sp = new URLSearchParams();
    if (q?.status)         sp.set('status', q.status);
    if (q?.chargePointId)  sp.set('chargePointId', q.chargePointId);
    if (q?.from)           sp.set('from', q.from);
    if (q?.to)             sp.set('to', q.to);
    return http<Session[]>(`/api/sessions${sp.toString() ? `?${sp}` : ''}`);
  },
  activeSessions:   () => http<Session[]>('/api/sessions/active'),
  meterValues:      (chargePointId: string, opts?: { connectorId?: number; lastMinutes?: number }) => {
    const sp = new URLSearchParams();
    if (opts?.connectorId != null) sp.set('connectorId', String(opts.connectorId));
    if (opts?.lastMinutes != null) sp.set('last', String(opts.lastMinutes));
    return http<MeterValueRow[]>(`/api/meter-values/${encodeURIComponent(chargePointId)}${sp.toString() ? `?${sp}` : ''}`);
  },
  stats:            () => http<Stats>('/api/stats'),

  remoteStart: (id: string, body: { idTag: string; connectorId: number }) =>
    http<{ status: string; message: string }>(`/api/chargepoints/${encodeURIComponent(id)}/remote-start`,
      { method: 'POST', body: JSON.stringify(body) }),
  remoteStop:  (id: string, body: { transactionId: number }) =>
    http<{ status: string; message: string }>(`/api/chargepoints/${encodeURIComponent(id)}/remote-stop`,
      { method: 'POST', body: JSON.stringify(body) }),
  reset:       (id: string, body: { type: 'Soft' | 'Hard' }) =>
    http<{ status: string; message: string }>(`/api/chargepoints/${encodeURIComponent(id)}/reset`,
      { method: 'POST', body: JSON.stringify(body) }),
  unlock:      (id: string, body: { connectorId: number }) =>
    http<{ status: string; message: string }>(`/api/chargepoints/${encodeURIComponent(id)}/unlock`,
      { method: 'POST', body: JSON.stringify(body) }),

  scenario: (body: { scenario: ScenarioName; chargePointId?: string; connectorId?: number }) =>
    http<{ status: string; message: string }>('/api/simulator/scenario',
      { method: 'POST', body: JSON.stringify(body) }),
};
```

---

## 7. STOMP client (copy as-is)

Install:
```
npm install @stomp/stompjs sockjs-client
npm install -D @types/sockjs-client
```

```ts
// ws/simulatorStomp.ts
import { Client, type IFrame } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { LiveEvent } from '../types/simulator';

const BASE = import.meta.env.VITE_SIMULATOR_BASE ?? 'http://localhost:8080';

export type StompHandlers = {
  onConnect: () => void;
  onDisconnect: () => void;
  onMessage: (event: LiveEvent) => void;
};

export function createSimulatorStompClient(handlers: StompHandlers): Client {
  const client = new Client({
    webSocketFactory: () => new SockJS(`${BASE}/ws/live`) as unknown as WebSocket,
    reconnectDelay: 2000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: (_f: IFrame) => {
      client.subscribe('/topic/events', (msg) => {
        try {
          handlers.onMessage(JSON.parse(msg.body) as LiveEvent);
        } catch (e) {
          console.warn('[simulator-stomp] malformed body', msg.body, e);
        }
      });
      handlers.onConnect();
    },
    onWebSocketClose: () => handlers.onDisconnect(),
    onStompError: (frame) => console.warn('[simulator-stomp] error', frame.headers, frame.body),
  });
  return client;
}
```

Usage in a React component:
```tsx
useEffect(() => {
  const client = createSimulatorStompClient({
    onConnect:    () => console.log('connected to simulator'),
    onDisconnect: () => console.log('lost simulator connection'),
    onMessage:    (e) => store.applyEvent(e),
  });
  client.activate();
  return () => { client.deactivate(); };
}, []);
```

---

## 8. Smoke test (run FIRST)

```bash
curl http://localhost:8080/api/chargepoints | jq             # 2 bornes online
curl http://localhost:8080/api/stats | jq                    # KPIs
curl -X POST http://localhost:8080/api/simulator/scenario \
  -H "Content-Type: application/json" \
  -d '{"scenario":"START_ONE","chargePointId":"BORNE_A","connectorId":1}'

# wait 5-10s
curl http://localhost:8080/api/chargepoints/BORNE_A | jq     # C1 should be Charging
```

---

## 9. Gotchas

- No auth — don't add any client-side.
- Dates are ISO-8601 UTC with trailing `Z`.
- MeterValues can arrive before the matching session is visible in `/api/sessions/active` (race is normal in OCPP). Don't crash if you see an unknown transactionId; ignore until the session appears.
- `RemoteStop` returns `{"status":"Rejected","message":"Charge point not charging"}` if the session ended already (auto-stop on SoC=100% or random `EVDisconnected`). Handle the Rejected case.
- `DISCONNECT_ONE` cuts the OCPP WebSocket of the borne → both connectors go offline together. That's physically correct (one borne = one WS).
- Auto-sessions and auto-faults run on background ticks — your UI must reconcile from STOMP events, not assume your last command is the only source of state changes.
- For a deterministic demo: stop Spring, `rm -rf data/`, lower `simulator.auto-session-probability` and `simulator.random-event-probability` in `application.yml` to 0.0, then drive everything via `/api/simulator/scenario`.

---

## 10. Full spec

Backend behaviour, DTOs, OCPP message handling, state machine: see
`C:\Projects\Hackathon\simulateur\CLAUDE.md`.
