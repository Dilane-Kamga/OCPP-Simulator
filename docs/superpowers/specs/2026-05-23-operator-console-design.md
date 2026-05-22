# Operator Console — Design

**Date:** 2026-05-23
**Author:** Dilane Kamga
**Project:** NEXCharge Hackathon — OCPP Simulator sub-project
**Branch:** `feat/ocpp-simulator`

## Goal

Build a browser-based **operator console** that visually showcases the OCPP simulator during the 5-minute hackathon demo. The console makes the simulator's autonomous behavior tangible: bornes booting, charging, faulting, and recovering live, with OCPP frames flowing in real time.

The audience is **hackathon judges**: priority is visually striking, narrative, and self-explanatory. Daily-use ergonomics are not a goal.

## Context

The simulator already exposes:
- REST API (`/api/chargepoints`, `/api/sessions`, `/api/meter-values`, `/api/simulator/scenario`, etc) — Swagger at `/api/swagger-ui/index.html`
- STOMP WebSocket on `/ws/live` with 8 event types: `CHARGE_POINT_CONNECTED`, `CHARGE_POINT_DISCONNECTED`, `STATUS_CHANGE`, `SESSION_STARTED`, `SESSION_STOPPED`, `METER_UPDATE`, `FAULT`, `HEARTBEAT`
- Permissive CORS on `/api/**`

Topology after the 2026-05-23 refactor:
- **2 charge points × 2 connectors = 4 connectors total**
- `BORNE_A` on site `NEX_TOWER` (Accenture totem, dark/purple/red branding)
- `BORNE_B` on site `NEXTERACOM` (Legrand wall-mount, white/grey branding)
- Both Green'Up Control 22 kW

## Success criteria

1. WebSocket real-time updates, no polling, no manual refresh during demo
2. Animated state transitions (Available → Preparing → Charging → Faulted)
3. Scenario buttons functional live (FAULT, RESET, START)
4. WS auto-reconnect with visible badge
5. Bornes visually reflect real hardware (totem dark vs wall white)
6. App loads in browser in <2 seconds
7. Readable from 3 meters at the back of a demo room

## Architecture

```
+---------------------------------------------------------------+
|                       BROWSER (Chrome)                        |
|                                                               |
|  React app (Vite-built bundle)                                |
|  +---------------------------------------------------------+ |
|  |  Zustand store: { chargePoints[], events[], totals }    | |
|  |       ^                                       ^         | |
|  |       |  initial fetch                        |  push   | |
|  |       |  GET /api/chargepoints                |  STOMP  | |
|  +-------|---------------------------------------|---------+ |
|          |                                       |           |
|  +-------|---+   +---------+   +-----------+   +-|-------+   |
|  | App.tsx   |   | Header  |   | SiteColumn|   |WSClient |   |
|  | (router)  |   |(totals) |   |(NEX_TOWER)|   |(STOMP)  |   |
|  +-----------+   +---------+   +-----------+   +---------+   |
|                                                               |
+----------|-------------------|---------------|----------------+
           | HTTP              | HTTP          | WS+STOMP
           | localhost:8080    | localhost:8080| localhost:8080
           v                   v               v
+---------------------------------------------------------------+
|                  SPRING BOOT (port 8080)                      |
|                                                               |
|   /console/*       /api/*               /ws/live              |
|   (static bundle)  (REST)               (STOMP broker)        |
|                                                               |
+---------------------------------------------------------------+
```

**Rules:**
- Zustand store is the client-side source of truth
- 1 REST fetch on mount to hydrate, then pure WS (no polling)
- Components are read-only; each subscribes to its slice for targeted re-renders

## Stack

- **Vite** + **React 18** + **TypeScript**
- **Tailwind CSS** for layout and tokens
- **Zustand** for state
- **@stomp/stompjs** + **sockjs-client** for WS
- **Framer Motion** for state-transition animations
- **Recharts** for power charts
- **Vitest** for store unit tests

## Hosting strategy

- Source lives in `console/` at the repo root (sibling of `src/`)
- Dev: `npm run dev` (Vite, hot-reload, port 5173, proxies `/api` and `/ws/live` to `localhost:8080`)
- Demo: `npm run build` outputs to `src/main/resources/static/console/` → served by Spring at `http://localhost:8080/console/`
- One command for the demo: `./mvnw spring-boot:run`

## Layout — Site split

Full-width header on top with title, total kW aggregated, and **global scenario buttons** (`PEAK_LOAD`, `RESET_ALL` — apply to all charge points). Two equal-width columns below, one per site, each with its own card, chart, and OCPP feed.

**Per-borne scenarios** (`FAULT_ONE`, `RESET`, `START`) live on each `ChargePointCard` (the `[⚠][↻][▶]` row in the ASCII layout) — they target a specific charge point.

```
+----------------------------------------------------------+
| ⚡ NEXCharge Operator Console     [PEAK_LOAD] [RESET_ALL]|
|                                                          |
|              ┌────────────────┐                          |
|              │   14.7 kW      │  ← total parc, anim     |
|              │   POWER DRAWN  │                          |
|              └────────────────┘                          |
+----------------------------+-----------------------------+
|       NEX_TOWER            |       NEXTERACOM            |
|                            |                             |
|   BORNE_A (Accenture)      |   BORNE_B (Legrand)         |
|   ┌────────────────┐       |   ┌────────────────┐        |
|   │  totem dark    │       |   │  wall white    │        |
|   │  [C1] [C2]     │       |   │  [C1] [C2]     │        |
|   │  Charging Avail│       |   │  Avail   Avail │        |
|   │ [⚠][↻][▶]      │       |   │ [⚠][↻][▶]      │        |
|   └────────────────┘       |   └────────────────┘        |
|                            |                             |
|   GRAPHE kW (10 min)       |   GRAPHE kW (10 min)        |
|   /\__/\____               |   ___                       |
|                            |                             |
|   FLUX OCPP (filtré A)     |   FLUX OCPP (filtré B)      |
|   12:34 → BootNotification |   12:34 → Heartbeat         |
|   12:34 ← BootConf Accepted|   12:35 → StatusNotif Avail |
|   12:35 → MeterValues 7.2kW|                             |
+----------------------------+-----------------------------+
```

## File structure

```
console/
└── src/
    ├── App.tsx                       # Layout root
    ├── main.tsx                      # Vite entry
    ├── index.css                     # Tailwind base + tokens
    │
    ├── store/
    │   ├── consoleStore.ts           # Zustand store
    │   └── __tests__/
    │       └── consoleStore.test.ts  # Vitest
    │
    ├── ws/
    │   ├── WSClient.tsx              # invisible STOMP component
    │   └── stompClient.ts            # @stomp/stompjs factory
    │
    ├── api/
    │   └── chargePointsApi.ts        # REST bootstrap calls
    │
    ├── components/
    │   ├── Header.tsx                # title + total kW + global buttons
    │   ├── SiteColumn.tsx            # wrap card + chart + feed for 1 site
    │   ├── ChargePointCard.tsx       # borne render (totem | wall)
    │   ├── ConnectorBadge.tsx        # 1 connector
    │   ├── PowerChart.tsx            # Recharts site curve
    │   ├── OcppFeed.tsx              # filtered scrolling feed
    │   └── ScenarioButton.tsx        # generic POST scenario button
    │
    └── theme/
        └── siteTheme.ts              # NEX_TOWER dark / NEXTERACOM white
```

## Component contracts

| Component | Props | Reads from store |
|---|---|---|
| `<Header />` | none | `totals.totalKw`, `wsState` |
| `<SiteColumn site="NEX_TOWER" />` | `site` | resolves to one `chargePointId` via `chargePoints[].find(cp => cp.site === site)` |
| `<ChargePointCard chargePointId="BORNE_A" />` | `chargePointId` | `chargePoints[chargePointId]` |
| `<ConnectorBadge chargePointId connectorId />` | both (connectorId is the OCPP value 1, 2, …) | `chargePoints[chargePointId].connectors.find(c => c.connectorId === connectorId)` |
| `<PowerChart chargePointId="BORNE_A" />` | `chargePointId` (resolved by `SiteColumn` from `site`) | `powerHistory[chargePointId]` |
| `<OcppFeed chargePointId="BORNE_A" />` | `chargePointId` | `eventsByCp[chargePointId]` (per-cp ring buffer, max 50 each) |
| `<ScenarioButton scenario chargePointId? label icon />` | `scenario`, optional `chargePointId`, `label`, `icon` | none (POSTs to API; relies on global `wsState` to disable when WS down) |

**Store shape (TypeScript-ish):**

```ts
type ConsoleState = {
  chargePoints: Record<string, ChargePoint>;        // keyed by chargePointId
  eventsByCp: Record<string, LiveEvent[]>;          // 50 most recent per cp
  powerHistory: Record<string, PowerPoint[]>;       // 60 points per cp
  totals: { totalKw: number };                      // derived: sum charging connectors
  wsState: 'CONNECTING' | 'CONNECTED' | 'RECONNECTING';
};
```

`connectors` inside `ChargePoint` is an array; lookup by `connectorId` value, not index — connector IDs are OCPP-assigned (1, 2) and may not start at 0.

## Data flow

### Bootstrap

1. `App.tsx` mounts → `WSClient` mounts
2. `WSClient` triggers parallel:
   - `stompClient.connect(ws://localhost:8080/ws/live)`
   - `GET /api/chargepoints` → `store.hydrate()`
   - For each charge point: `GET /api/meter-values/{id}?last=10` (`last` is in **minutes** per backend contract; 10 minutes ≈ 60 points at 10s sampling) → keep only `Power.Active.Import` measurand, push the most recent 60 to `powerHistory[chargePointId]`
3. STOMP subscribes to `/topic/events`
4. UI renders cards from hydrated state while WS warms up

### Steady-state

```
backend                                  frontend store
LiveEventService.publish(STATUS_CHANGE)
   │ STOMP frame
   ▼
WSClient.onMessage()                     store.applyEvent(event)
                                            ├ STATUS_CHANGE → patch chargePoint.status
                                            ├ METER_UPDATE  → patch connector kW + push to powerHistory[cpId]
                                            ├ SESSION_*     → patch connector.activeSession
                                            ├ FAULT         → patch connector.errorCode + isFaulted
                                            ├ HEARTBEAT     → patch chargePoint.lastHeartbeat
                                            └ CONNECTED/DISCONNECTED → patch chargePoint.online
                                            (and: eventsByCp[cpId].unshift(event), trim each cp's list to 50)
```

### Scenario actions

```
ScenarioButton(FAULT_ONE, BORNE_A) onClick:
   POST /api/simulator/scenario { scenario: "FAULT_ONE", chargePointId: "BORNE_A" }
   → backend triggers → backend publishes STATUS_CHANGE+FAULT → store applies
```

**No optimistic updates.** The truth comes from the WS — that's the point of the demo (proving the chain works).

### Power history

- Sliding window of **60 points per charge point**, in-memory
- 1 point per 10s = 10 minutes of history
- Pre-filled at bootstrap via `GET /api/meter-values/{id}?last=10`
- Throttle: chart re-renders at most every **1 second** (debounce buffered METER_UPDATEs)

## Visuals & animations

### Site theme tokens

| Site | Background | Text | Accent | Inspiration |
|---|---|---|---|---|
| `NEX_TOWER` | gradient `#1a0d2e → #2d1b4e` | white | red `#e63946` | Accenture totem |
| `NEXTERACOM` | off-white `#f5f5f5` | dark grey | blue `#0072ce` | Legrand wall-mount |

Both sites share **identical positions, sizes, typography**. Only colors and surface textures differ — the eye sees "same equipment, two contexts".

### Connector states

```
Available    →  green  #22c55e  + soft slow pulsing halo
Preparing    →  yellow #eab308  + intensifying halo
Charging     →  blue   #3b82f6  + intense halo + lightning overlay
Faulted      →  red    #ef4444  + horizontal shake 300ms + flashing halo
Unavailable  →  grey   #6b7280  + 50% opacity
```

### State transitions (Framer Motion)

| Transition | Animation | Duration |
|---|---|---|
| Available → Preparing | green→yellow color fade | 400ms |
| Preparing → Charging | pulsing halo + ⚡ overlay | 600ms |
| * → Faulted | shake + red flash + ⚠ icon scale-in | 800ms |
| Faulted → Available | red→green fade + ✓ icon | 500ms |
| `online: true → false` | grayscale + dim 40% + "OFFLINE" label | 300ms |

### Header total kW

- Center-aligned, large (~48px digit)
- Animated count-up on change (200ms) — never jumps
- Label "POWER DRAWN" in 12px caps below

### Card silhouettes

- `NEX_TOWER` card: tall vertical aspect ratio (mimics the totem)
- `NEXTERACOM` card: more rectangular (mimics the wall-mount)
- Not pixel-perfect copies — just enough that judges link the card to the photos shown

### OCPP feed rendering

- Monospace font
- `→` IN blue, `←` OUT green, `Faulted/Rejected` red
- Auto-scroll to bottom, **paused on hover** (so a judge can read)
- Max 50 events kept **per charge point**, oldest dropped (so a busy BORNE_A doesn't starve BORNE_B's feed)

### Power chart

- Recharts `LineChart`, X = last 60 points, Y = kW
- Area fill under curve with site-color gradient
- No legend (single series), minimal axes

## Error handling

| Case | Behavior |
|---|---|
| WS connect fails on init | Exponential retry 1s → 2s → 4s → 8s → max 10s, indefinite |
| WS disconnects in steady-state | `@stomp/stompjs` auto-reconnect (`reconnectDelay: 2000`) |
| During reconnect | Amber `WS RECONNECTING…` badge in header, data frozen, no fake data |
| After reconnect | Re-fetch `/api/chargepoints` to resync (otherwise store drifts) |
| `/api/chargepoints` fails (bootstrap) | Retry 3× (1s, 2s, 4s), then centered error "Backend unavailable — check `localhost:8080`". No degraded mode. |
| Scenario POST 4xx | Red toast 3s with backend message |
| Scenario POST 5xx / network | Red toast "Server error, retry" |
| `chargePoint.online === false` | Card grayscale, opacity 50%, scenario buttons disabled |
| `chargePoint.site === null` | Fallback to `NEX_TOWER` |
| `connector.currentPowerKw === null` | Display `—` not `0.0 kW` |
| Malformed STOMP event | `console.warn` + ignore. No UI crash. |

## Performance notes

- ~0.4 events/s steady-state (4 connectors × 1 MeterValue / 10s)
- Up to 5–10 events/s during PEAK_LOAD
- Throttle chart updates to 1s
- `useMemo` on event filters in `OcppFeed`
- Stable `key` for list items to prevent cascading re-renders

## Testing strategy

### Unit (Vitest) — store only

```
console/src/store/__tests__/consoleStore.test.ts
  ✓ hydrate() populates chargePoints from REST bootstrap
  ✓ applyEvent(STATUS_CHANGE) patches the right connector (lookup by connectorId, not index)
  ✓ applyEvent(METER_UPDATE) pushes to powerHistory[cpId] + trims to 60 points
  ✓ applyEvent(FAULT) sets isFaulted + errorCode
  ✓ eventsByCp[cpId] kept at max 50 entries per cp (trim oldest)
  ✓ totalKw is correctly derived (sum of charging connectors)
  ✓ event for unknown chargePointId → console.warn + no-op (no crash)
```

~7 tests, ~30 minutes to write.

### Not tested (and why)

| Skipped | Reason |
|---|---|
| Visual components (`ChargePointCard`, `OcppFeed`) | Pure visuals, validated by the human eye in demo |
| `WSClient` | I/O networking, mocking STOMP would test the mock |
| API client (fetch wrappers) | 3-line wrappers around fetch |
| E2E (Cypress / Playwright) | 1-day hackathon scope |
| Framer animations | Visuals, validated by eye |

### Backend coverage (already in place)

The 191 Spring tests already validate that:
- `LiveEventService.publish()` correctly emits to `/topic/events`
- State transitions emit the right events (STATUS_CHANGE, FAULT, etc)
- REST API returns the right shapes

The frontend reads these events as-is.

### Pre-demo smoke checklist (5 min)

1. Backend starts. `localhost:8080/console/` loads in <2s.
2. The 2 bornes appear with the correct theme (NexTower dark / Nexteracom white).
3. Click `PEAK_LOAD` → both bornes go Charging within 1s.
4. Click `FAULT_ONE BORNE_A` → BORNE_A turns red, shakes, recovers green in 30–120s.
5. Header total kW animates, charts rise.
6. Stop the backend → `WS RECONNECTING…` badge appears. Restart backend → auto-resync.
7. Refresh the page → clean rebootstrap.

## Out of scope (YAGNI)

- Authentication / user accounts
- Routing (single page)
- i18n
- localStorage persistence (refresh = rebootstrap)
- Storybook
- CI build of the frontend (manual `npm run build` before demo)
- Mobile responsive (demo runs on a laptop screen)
- Print / export views
- Optimistic updates (truth comes from WS, by design)
- Fake data / degraded mode (would falsify technical proof)

## References

- OCPP simulator design: `docs/superpowers/specs/2026-05-22-ocpp-simulator-design.md`
- Hardware photos: `C:\Users\KAMGA\Downloads\Nouveau dossier (3)\nextower.jpg`, `nexteracom.jpg`
- Hackathon evaluation criteria: memory `reference-hackathon-evaluation-criteria`
- Real hardware topology: memory `project-real-hardware-topology`
