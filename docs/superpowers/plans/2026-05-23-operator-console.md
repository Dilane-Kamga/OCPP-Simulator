# Operator Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- []`) syntax for tracking.

**Goal:** Build a browser-based operator console served by Spring Boot at `/console/`, showing the OCPP simulator's live state via STOMP, with site-split layout, animations, and scenario triggers — designed for a 5-minute hackathon judges demo.

**Architecture:** Vite + React 18 + TypeScript SPA in `console/` at the repo root, building to `src/main/resources/static/console/` so Spring serves it on `localhost:8080/console/`. Single Zustand store hydrated from REST then patched live by STOMP events. Two `SiteColumn` components (NEX_TOWER, NEXTERACOM) consuming filtered slices.

**Tech Stack:** Vite 5, React 18, TypeScript 5, Tailwind CSS 3, Zustand 4, @stomp/stompjs 7, sockjs-client 1, Framer Motion 11, Recharts 2, Vitest 1.

**Spec:** `docs/superpowers/specs/2026-05-23-operator-console-design.md`

---

## File structure (locked at planning time)

```
console/                                 ← new sibling of src/
├── package.json
├── tsconfig.json
├── vite.config.ts
├── tailwind.config.js
├── postcss.config.js
├── index.html
├── public/
│   └── favicon.svg
└── src/
    ├── main.tsx                         # Vite entry
    ├── App.tsx                          # Layout root
    ├── index.css                        # Tailwind directives + global tokens
    ├── types.ts                         # shared TypeScript types
    ├── store/
    │   ├── consoleStore.ts              # Zustand store
    │   └── __tests__/
    │       └── consoleStore.test.ts     # Vitest, 7 tests
    ├── ws/
    │   ├── stompClient.ts               # @stomp/stompjs factory
    │   └── WSClient.tsx                 # invisible component, mounts on App
    ├── api/
    │   └── chargePointsApi.ts           # REST bootstrap calls
    ├── components/
    │   ├── Header.tsx
    │   ├── SiteColumn.tsx
    │   ├── ChargePointCard.tsx
    │   ├── ConnectorBadge.tsx
    │   ├── PowerChart.tsx
    │   ├── OcppFeed.tsx
    │   └── ScenarioButton.tsx
    └── theme/
        └── siteTheme.ts                 # NEX_TOWER / NEXTERACOM tokens
```

**Backend changes:** none — the existing `/api/**` REST + `/ws/live` STOMP + permissive CORS already satisfy the console's needs. The only Spring-side addition is implicit: Spring serves `src/main/resources/static/console/` automatically once the build artifact lives there.

---

## Task 1: Scaffold the Vite project

**Files:**
- Create: `console/package.json`
- Create: `console/tsconfig.json`
- Create: `console/vite.config.ts`
- Create: `console/index.html`
- Create: `console/src/main.tsx`
- Create: `console/src/App.tsx`
- Create: `console/.gitignore`

- [ ] **Step 1: Create package.json**

```json
{
  "name": "nexcharge-operator-console",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "@stomp/stompjs": "^7.0.0",
    "framer-motion": "^11.0.0",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "recharts": "^2.12.0",
    "sockjs-client": "^1.6.1",
    "zustand": "^4.5.0"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.4.0",
    "@testing-library/react": "^16.0.0",
    "@types/react": "^18.3.0",
    "@types/react-dom": "^18.3.0",
    "@types/sockjs-client": "^1.5.4",
    "@vitejs/plugin-react": "^4.3.0",
    "autoprefixer": "^10.4.19",
    "jsdom": "^24.0.0",
    "postcss": "^8.4.38",
    "tailwindcss": "^3.4.4",
    "typescript": "^5.4.5",
    "vite": "^5.2.13",
    "vitest": "^1.6.0"
  }
}
```

- [ ] **Step 2: Create tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src"]
}
```

- [ ] **Step 3: Create vite.config.ts**

The config does three things: (1) builds into `../src/main/resources/static/console/` so Spring serves the bundle; (2) sets `base: '/console/'` so asset URLs are correct when served by Spring; (3) proxies `/api` and `/ws/live` to `localhost:8080` for dev.

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/console/',
  build: {
    outDir: '../src/main/resources/static/console',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws/live': { target: 'ws://localhost:8080', ws: true },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
  },
});
```

- [ ] **Step 4: Create index.html**

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/console/favicon.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>NEXCharge Operator Console</title>
  </head>
  <body class="bg-slate-950 text-slate-100">
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 5: Create src/main.tsx**

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

- [ ] **Step 6: Create src/App.tsx (placeholder, fleshed out in later tasks)**

```tsx
export default function App() {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <h1 className="text-2xl font-bold">NEXCharge Operator Console — bootstrap</h1>
    </div>
  );
}
```

- [ ] **Step 7: Create console/.gitignore**

```
node_modules/
dist/
.vite/
*.log
```

- [ ] **Step 8: Install deps and verify dev server starts**

Run from `console/`:
```bash
npm install
npm run dev
```
Expected: Vite prints `Local: http://localhost:5173/console/`, opening it shows "NEXCharge Operator Console — bootstrap".

Stop the server (Ctrl+C).

- [ ] **Step 9: Verify production build outputs to Spring static dir**

Run from `console/`:
```bash
npm run build
```
Expected: build completes, `src/main/resources/static/console/index.html` exists.

```bash
ls ../src/main/resources/static/console/
```
Expected: shows `index.html`, `assets/`.

- [ ] **Step 10: Commit**

```bash
git add console/.gitignore console/package.json console/package-lock.json console/tsconfig.json console/vite.config.ts console/index.html console/src/main.tsx console/src/App.tsx
git commit -m "feat(console): scaffold Vite + React + TypeScript project"
```

---

## Task 2: Tailwind + theme tokens

**Files:**
- Create: `console/tailwind.config.js`
- Create: `console/postcss.config.js`
- Create: `console/src/index.css`
- Create: `console/src/theme/siteTheme.ts`

- [ ] **Step 1: Create tailwind.config.js**

```js
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        nextower: {
          bg1: '#1a0d2e',
          bg2: '#2d1b4e',
          accent: '#e63946',
        },
        nexteracom: {
          bg: '#f5f5f5',
          text: '#1f2937',
          accent: '#0072ce',
        },
      },
      fontFamily: {
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
    },
  },
  plugins: [],
};
```

- [ ] **Step 2: Create postcss.config.js**

```js
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```

- [ ] **Step 3: Create src/index.css**

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

html, body, #root {
  height: 100%;
}

body {
  font-family: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
}
```

- [ ] **Step 4: Create src/theme/siteTheme.ts**

```ts
export type SiteId = 'NEX_TOWER' | 'NEXTERACOM';

export type SiteTheme = {
  bgClass: string;       // tailwind classes for column background
  textClass: string;
  accent: string;        // hex for SVG / inline styles
  cardClass: string;     // tailwind classes for card surface
  label: string;         // human-readable site name
};

export const SITE_THEMES: Record<SiteId, SiteTheme> = {
  NEX_TOWER: {
    bgClass: 'bg-gradient-to-b from-nextower-bg1 to-nextower-bg2 text-white',
    textClass: 'text-white',
    accent: '#e63946',
    cardClass: 'bg-black/40 border border-white/10 rounded-2xl shadow-2xl',
    label: 'NEX Tower',
  },
  NEXTERACOM: {
    bgClass: 'bg-nexteracom-bg text-nexteracom-text',
    textClass: 'text-nexteracom-text',
    accent: '#0072ce',
    cardClass: 'bg-white border border-slate-200 rounded-2xl shadow-md',
    label: 'Nexteracom',
  },
};

export function siteThemeOf(site: SiteId | null | undefined): SiteTheme {
  return SITE_THEMES[site ?? 'NEX_TOWER'];
}
```

- [ ] **Step 5: Verify build still passes**

Run from `console/`:
```bash
npm run build
```
Expected: build completes, no errors.

- [ ] **Step 6: Commit**

```bash
git add console/tailwind.config.js console/postcss.config.js console/src/index.css console/src/theme/siteTheme.ts
git commit -m "feat(console): add Tailwind + site theme tokens"
```

---

## Task 3: Shared TypeScript types

**Files:**
- Create: `console/src/types.ts`

These types mirror the backend DTOs (`ChargePointDto`, `ConnectorDto`, `LiveEventDto`). Keep names aligned with the JSON wire format (camelCase, matching what Jackson emits).

- [ ] **Step 1: Create src/types.ts**

```ts
import type { SiteId } from './theme/siteTheme';

export type ChargePointStatus =
  | 'Available' | 'Preparing' | 'Charging' | 'Faulted' | 'Unavailable' | 'Reserved' | 'SuspendedEV' | 'SuspendedEVSE' | 'Finishing';

export type ConnectorStatus = ChargePointStatus;

export type Connector = {
  connectorId: number;            // OCPP value (1, 2)
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
  site: SiteId | null;
  vendor: string | null;
  model: string | null;
  serialNumber: string | null;
  firmwareVersion: string | null;
  status: ChargePointStatus;
  online: boolean;
  lastHeartbeat: string | null;
  registeredAt: string | null;
  errorCode: string | null;
  connectors: Connector[];
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

export type PowerPoint = { t: number; kw: number };  // t = epoch ms

export type WsState = 'CONNECTING' | 'CONNECTED' | 'RECONNECTING';

export type ScenarioName =
  | 'START_ALL' | 'FAULT_ONE' | 'STOP_ALL' | 'DISCONNECT_ONE' | 'PEAK_LOAD' | 'RESET_ALL';
```

- [ ] **Step 2: Commit**

```bash
git add console/src/types.ts
git commit -m "feat(console): add shared TypeScript types mirroring backend DTOs"
```

---

## Task 4: Zustand store + tests (TDD)

**Files:**
- Create: `console/src/store/consoleStore.ts`
- Create: `console/src/store/__tests__/consoleStore.test.ts`
- Create: `console/src/test-setup.ts`

The store is the only piece with logic; we TDD it.

- [ ] **Step 1: Create test-setup.ts**

```ts
import '@testing-library/jest-dom';
```

- [ ] **Step 2: Write failing tests**

Create `console/src/store/__tests__/consoleStore.test.ts`:

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useConsoleStore } from '../consoleStore';
import type { ChargePoint, LiveEvent } from '../../types';

const sampleCp: ChargePoint = {
  chargePointId: 'BORNE_A',
  site: 'NEX_TOWER',
  vendor: 'Legrand',
  model: "Green'Up Control",
  serialNumber: 'LGR-NXT-001',
  firmwareVersion: '2.1.0',
  status: 'Available',
  online: true,
  lastHeartbeat: '2026-05-23T10:00:00Z',
  registeredAt: '2026-05-23T09:00:00Z',
  errorCode: 'NoError',
  connectors: [
    { connectorId: 1, status: 'Available', currentPowerKw: 0, currentAmps: 0, voltage: 230, temperatureCelsius: 22, totalEnergyKwh: 0, errorCode: 'NoError', blocked: false, blockedReason: null, blockedAt: null },
    { connectorId: 2, status: 'Available', currentPowerKw: 0, currentAmps: 0, voltage: 230, temperatureCelsius: 22, totalEnergyKwh: 0, errorCode: 'NoError', blocked: false, blockedReason: null, blockedAt: null },
  ],
};

function makeEvent(partial: Partial<LiveEvent>): LiveEvent {
  return {
    type: 'STATUS_CHANGE',
    chargePointId: 'BORNE_A',
    connectorId: 1,
    data: {},
    timestamp: '2026-05-23T10:00:00Z',
    ...partial,
  };
}

describe('consoleStore', () => {
  beforeEach(() => {
    useConsoleStore.getState().reset();
  });

  it('hydrate populates chargePoints keyed by id', () => {
    useConsoleStore.getState().hydrate([sampleCp]);
    expect(useConsoleStore.getState().chargePoints['BORNE_A']).toEqual(sampleCp);
  });

  it('applyEvent STATUS_CHANGE patches connector status by connectorId', () => {
    useConsoleStore.getState().hydrate([sampleCp]);
    useConsoleStore.getState().applyEvent(makeEvent({
      type: 'STATUS_CHANGE',
      connectorId: 2,
      data: { status: 'Charging' },
    }));
    const conn2 = useConsoleStore.getState().chargePoints['BORNE_A'].connectors.find(c => c.connectorId === 2)!;
    expect(conn2.status).toBe('Charging');
  });

  it('applyEvent METER_UPDATE pushes to powerHistory and trims to 60 points', () => {
    useConsoleStore.getState().hydrate([sampleCp]);
    for (let i = 0; i < 70; i++) {
      useConsoleStore.getState().applyEvent(makeEvent({
        type: 'METER_UPDATE',
        connectorId: 1,
        // Backend wire shape: { readings: { measurand: rawValue, ... }, transactionId }.
        // Power.Active.Import is in watts (W), Voltage in V, Current.Import in A.
        data: { readings: { 'Power.Active.Import': 7000 } },
        timestamp: new Date(Date.UTC(2026, 4, 23, 10, 0, i)).toISOString(),
      }));
    }
    expect(useConsoleStore.getState().powerHistory['BORNE_A'].length).toBe(60);
    expect(useConsoleStore.getState().powerHistory['BORNE_A'][0].kw).toBeCloseTo(7.0);
  });

  it('applyEvent METER_UPDATE updates the connector currentPowerKw/Amps/Voltage from readings', () => {
    useConsoleStore.getState().hydrate([sampleCp]);
    useConsoleStore.getState().applyEvent(makeEvent({
      type: 'METER_UPDATE',
      connectorId: 1,
      data: {
        readings: {
          'Power.Active.Import': 7200,
          'Current.Import': 31.3,
          'Voltage': 230.2,
        },
      },
    }));
    const conn1 = useConsoleStore.getState().chargePoints['BORNE_A'].connectors.find(c => c.connectorId === 1)!;
    expect(conn1.currentPowerKw).toBeCloseTo(7.2);
    expect(conn1.currentAmps).toBeCloseTo(31.3);
    expect(conn1.voltage).toBeCloseTo(230.2);
  });

  it('applyEvent FAULT sets errorCode', () => {
    useConsoleStore.getState().hydrate([sampleCp]);
    useConsoleStore.getState().applyEvent(makeEvent({
      type: 'FAULT',
      connectorId: 1,
      data: { errorCode: 'GroundFailure' },
    }));
    const conn1 = useConsoleStore.getState().chargePoints['BORNE_A'].connectors.find(c => c.connectorId === 1)!;
    expect(conn1.errorCode).toBe('GroundFailure');
  });

  it('eventsByCp keeps at most 50 entries per charge point, oldest dropped', () => {
    useConsoleStore.getState().hydrate([sampleCp]);
    for (let i = 0; i < 60; i++) {
      useConsoleStore.getState().applyEvent(makeEvent({ type: 'HEARTBEAT', data: { i } }));
    }
    expect(useConsoleStore.getState().eventsByCp['BORNE_A'].length).toBe(50);
    expect((useConsoleStore.getState().eventsByCp['BORNE_A'][0].data as any).i).toBe(59);
  });

  it('totalKw is the sum of currentPowerKw across all connectors', () => {
    const cp: ChargePoint = {
      ...sampleCp,
      connectors: [
        { ...sampleCp.connectors[0], currentPowerKw: 7.2 },
        { ...sampleCp.connectors[1], currentPowerKw: 3.5 },
      ],
    };
    useConsoleStore.getState().hydrate([cp]);
    expect(useConsoleStore.getState().totalKw()).toBeCloseTo(10.7);
  });

  it('event for unknown chargePointId triggers console.warn and is a no-op', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    useConsoleStore.getState().hydrate([sampleCp]);
    useConsoleStore.getState().applyEvent(makeEvent({ chargePointId: 'BORNE_X' }));
    expect(warn).toHaveBeenCalled();
    expect(useConsoleStore.getState().chargePoints['BORNE_X']).toBeUndefined();
    warn.mockRestore();
  });
});
```

- [ ] **Step 3: Run tests, verify they fail**

Run from `console/`:
```bash
npm test
```
Expected: tests fail with "Cannot find module '../consoleStore'" or similar.

- [ ] **Step 4: Implement consoleStore.ts**

Create `console/src/store/consoleStore.ts`:

```ts
import { create } from 'zustand';
import type { ChargePoint, LiveEvent, PowerPoint, WsState } from '../types';

const MAX_EVENTS_PER_CP = 50;
const MAX_POWER_POINTS = 60;

type State = {
  chargePoints: Record<string, ChargePoint>;
  eventsByCp: Record<string, LiveEvent[]>;
  powerHistory: Record<string, PowerPoint[]>;
  wsState: WsState;
};

type Actions = {
  hydrate: (cps: ChargePoint[]) => void;
  prefillPower: (chargePointId: string, points: PowerPoint[]) => void;
  applyEvent: (event: LiveEvent) => void;
  setWsState: (ws: WsState) => void;
  totalKw: () => number;
  reset: () => void;
};

const initialState: State = {
  chargePoints: {},
  eventsByCp: {},
  powerHistory: {},
  wsState: 'CONNECTING',
};

export const useConsoleStore = create<State & Actions>((set, get) => ({
  ...initialState,

  hydrate: (cps) => {
    const chargePoints: Record<string, ChargePoint> = {};
    const eventsByCp: Record<string, LiveEvent[]> = {};
    const powerHistory: Record<string, PowerPoint[]> = {};
    for (const cp of cps) {
      chargePoints[cp.chargePointId] = cp;
      eventsByCp[cp.chargePointId] = [];
      powerHistory[cp.chargePointId] = [];
    }
    set({ chargePoints, eventsByCp, powerHistory });
  },

  prefillPower: (chargePointId, points) => {
    set((s) => ({
      powerHistory: { ...s.powerHistory, [chargePointId]: points.slice(-MAX_POWER_POINTS) },
    }));
  },

  applyEvent: (event) => {
    const { chargePointId } = event;
    const cp = get().chargePoints[chargePointId];
    if (!cp) {
      console.warn('[store] event for unknown chargePointId:', chargePointId, event);
      return;
    }

    set((s) => {
      const existing = s.eventsByCp[chargePointId] ?? [];
      const trimmed = [event, ...existing].slice(0, MAX_EVENTS_PER_CP);
      return { eventsByCp: { ...s.eventsByCp, [chargePointId]: trimmed } };
    });

    const data = event.data as Record<string, any>;

    switch (event.type) {
      case 'CHARGE_POINT_CONNECTED':
        set((s) => ({
          chargePoints: { ...s.chargePoints, [chargePointId]: { ...cp, online: true } },
        }));
        break;

      case 'CHARGE_POINT_DISCONNECTED':
        set((s) => ({
          chargePoints: { ...s.chargePoints, [chargePointId]: { ...cp, online: false } },
        }));
        break;

      case 'STATUS_CHANGE': {
        const newStatus = data.status as ChargePoint['status'];
        if (event.connectorId == null) {
          set((s) => ({
            chargePoints: { ...s.chargePoints, [chargePointId]: { ...cp, status: newStatus } },
          }));
        } else {
          set((s) => ({
            chargePoints: {
              ...s.chargePoints,
              [chargePointId]: {
                ...cp,
                connectors: cp.connectors.map((c) =>
                  c.connectorId === event.connectorId ? { ...c, status: newStatus } : c
                ),
              },
            },
          }));
        }
        break;
      }

      case 'METER_UPDATE': {
        // Backend payload (CsmsEventHandler#handleMeterValuesRequest):
        //   data.readings: { 'Power.Active.Import': watts, 'Current.Import': amps,
        //                    'Voltage': v, 'Energy.Active.Import.Register': wh, 'Temperature': c }
        //   data.transactionId
        const readings = (data.readings ?? {}) as Record<string, number>;
        const watts = readings['Power.Active.Import'];
        const kw = typeof watts === 'number' ? watts / 1000 : 0;
        const amps = readings['Current.Import'];
        const volts = readings['Voltage'];
        const t = Date.parse(event.timestamp);
        if (event.connectorId != null) {
          set((s) => ({
            chargePoints: {
              ...s.chargePoints,
              [chargePointId]: {
                ...cp,
                connectors: cp.connectors.map((c) =>
                  c.connectorId === event.connectorId
                    ? {
                        ...c,
                        currentPowerKw: typeof watts === 'number' ? kw : c.currentPowerKw,
                        currentAmps: typeof amps === 'number' ? amps : c.currentAmps,
                        voltage: typeof volts === 'number' ? volts : c.voltage,
                      }
                    : c
                ),
              },
            },
          }));
        }
        if (typeof watts === 'number') {
          set((s) => {
            const prev = s.powerHistory[chargePointId] ?? [];
            const next = [...prev, { t, kw }].slice(-MAX_POWER_POINTS);
            return { powerHistory: { ...s.powerHistory, [chargePointId]: next } };
          });
        }
        break;
      }

      case 'FAULT': {
        const errorCode = (data.errorCode as string) ?? 'Faulted';
        set((s) => ({
          chargePoints: {
            ...s.chargePoints,
            [chargePointId]: {
              ...cp,
              errorCode,
              connectors: cp.connectors.map((c) =>
                c.connectorId === event.connectorId ? { ...c, errorCode } : c
              ),
            },
          },
        }));
        break;
      }

      case 'HEARTBEAT':
        set((s) => ({
          chargePoints: {
            ...s.chargePoints,
            [chargePointId]: { ...cp, lastHeartbeat: event.timestamp, online: true },
          },
        }));
        break;

      case 'SESSION_STARTED':
      case 'SESSION_STOPPED':
        // session UI uses status events; we just keep the event in the feed.
        break;
    }
  },

  setWsState: (ws) => set({ wsState: ws }),

  totalKw: () => {
    const cps = Object.values(get().chargePoints);
    let sum = 0;
    for (const cp of cps) {
      for (const c of cp.connectors) {
        if (typeof c.currentPowerKw === 'number') sum += c.currentPowerKw;
      }
    }
    return sum;
  },

  reset: () => set(initialState),
}));
```

- [ ] **Step 5: Run tests, verify all pass**

Run from `console/`:
```bash
npm test
```
Expected: `8 passed`.

- [ ] **Step 6: Commit**

```bash
git add console/src/test-setup.ts console/src/store/consoleStore.ts console/src/store/__tests__/consoleStore.test.ts
git commit -m "feat(console): add Zustand store with 8 unit tests (TDD)"
```

---

## Task 5: REST bootstrap client

**Files:**
- Create: `console/src/api/chargePointsApi.ts`

- [ ] **Step 1: Create chargePointsApi.ts**

The console proxies `/api` to `localhost:8080` in dev (Vite proxy) and is served by Spring at `/console/` in prod, so we can use relative URLs.

```ts
import type { ChargePoint, PowerPoint } from '../types';

export async function fetchChargePoints(): Promise<ChargePoint[]> {
  const res = await fetch('/api/chargepoints');
  if (!res.ok) throw new Error(`GET /api/chargepoints → ${res.status}`);
  return res.json();
}

type MeterValueRow = {
  timestamp: string;
  measurand: string;
  value: number;
};

export async function fetchPowerHistory(chargePointId: string, lastMinutes: number): Promise<PowerPoint[]> {
  const url = `/api/meter-values/${encodeURIComponent(chargePointId)}?last=${lastMinutes}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`GET ${url} → ${res.status}`);
  const rows: MeterValueRow[] = await res.json();
  return rows
    .filter((r) => r.measurand === 'Power.Active.Import')
    .map((r) => ({ t: Date.parse(r.timestamp), kw: r.value / 1000 }))
    .sort((a, b) => a.t - b.t);
}

export type ScenarioPayload = { scenario: string; chargePointId?: string };

export async function postScenario(payload: ScenarioPayload): Promise<void> {
  const res = await fetch('/api/simulator/scenario', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(body || `POST /api/simulator/scenario → ${res.status}`);
  }
}
```

- [ ] **Step 2: Build to verify TypeScript compiles**

Run from `console/`:
```bash
npm run build
```
Expected: build completes.

- [ ] **Step 3: Commit**

```bash
git add console/src/api/chargePointsApi.ts
git commit -m "feat(console): add REST bootstrap client (chargepoints, meter-values, scenarios)"
```

---

## Task 6: STOMP client + WSClient component

**Files:**
- Create: `console/src/ws/stompClient.ts`
- Create: `console/src/ws/WSClient.tsx`

- [ ] **Step 1: Create stompClient.ts**

```ts
import { Client, type IFrame } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export type StompHandlers = {
  onConnect: () => void;
  onDisconnect: () => void;
  onMessage: (body: unknown) => void;
};

export function createStompClient(handlers: StompHandlers): Client {
  const client = new Client({
    webSocketFactory: () => {
      const wsUrl = `${window.location.protocol === 'https:' ? 'https' : 'http'}://${window.location.host}/ws/live`;
      return new SockJS(wsUrl) as unknown as WebSocket;
    },
    reconnectDelay: 2000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: (_frame: IFrame) => {
      client.subscribe('/topic/events', (msg) => {
        try {
          handlers.onMessage(JSON.parse(msg.body));
        } catch (e) {
          console.warn('[stomp] malformed message body', msg.body, e);
        }
      });
      handlers.onConnect();
    },
    onWebSocketClose: () => handlers.onDisconnect(),
    onStompError: (frame) => console.warn('[stomp] error frame', frame.headers, frame.body),
  });
  return client;
}
```

- [ ] **Step 2: Create WSClient.tsx**

WSClient implements two spec requirements: bootstrap retries 3× with backoff and shows a "Backend unavailable" full-screen error after exhausting them; on every (re)connect of the WS, it re-runs the bootstrap so the store doesn't drift after the backend restarts mid-demo.

```tsx
import { useEffect, useRef, useState } from 'react';
import { useConsoleStore } from '../store/consoleStore';
import { createStompClient } from './stompClient';
import { fetchChargePoints, fetchPowerHistory } from '../api/chargePointsApi';
import type { ChargePoint, LiveEvent } from '../types';

const RETRY_DELAYS_MS = [1000, 2000, 4000];

async function fetchChargePointsWithRetry(): Promise<ChargePoint[]> {
  let lastErr: unknown;
  for (let attempt = 0; attempt <= RETRY_DELAYS_MS.length; attempt++) {
    try {
      return await fetchChargePoints();
    } catch (e) {
      lastErr = e;
      if (attempt < RETRY_DELAYS_MS.length) {
        await new Promise((r) => setTimeout(r, RETRY_DELAYS_MS[attempt]));
      }
    }
  }
  throw lastErr;
}

export function WSClient() {
  const hydrate = useConsoleStore((s) => s.hydrate);
  const prefillPower = useConsoleStore((s) => s.prefillPower);
  const applyEvent = useConsoleStore((s) => s.applyEvent);
  const setWsState = useConsoleStore((s) => s.setWsState);
  const [bootstrapError, setBootstrapError] = useState<string | null>(null);
  const cancelledRef = useRef(false);

  useEffect(() => {
    cancelledRef.current = false;

    async function bootstrap() {
      try {
        const cps = await fetchChargePointsWithRetry();
        if (cancelledRef.current) return;
        hydrate(cps);
        setBootstrapError(null);
        await Promise.all(
          cps.map(async (cp) => {
            try {
              const pts = await fetchPowerHistory(cp.chargePointId, 10);
              if (!cancelledRef.current) prefillPower(cp.chargePointId, pts);
            } catch (e) {
              console.warn('[bootstrap] meter-values prefill failed for', cp.chargePointId, e);
            }
          })
        );
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        if (!cancelledRef.current) setBootstrapError(msg);
      }
    }

    bootstrap();

    const client = createStompClient({
      onConnect: () => {
        setWsState('CONNECTED');
        // Re-fetch on (re)connect so the store resyncs after a backend restart.
        bootstrap();
      },
      onDisconnect: () => setWsState('RECONNECTING'),
      onMessage: (body) => applyEvent(body as LiveEvent),
    });
    setWsState('CONNECTING');
    client.activate();

    return () => {
      cancelledRef.current = true;
      client.deactivate();
    };
  }, [hydrate, prefillPower, applyEvent, setWsState]);

  if (bootstrapError) {
    return (
      <div className="fixed inset-0 z-[1000] flex items-center justify-center bg-slate-950/95">
        <div className="max-w-md p-6 text-center bg-red-950/40 border border-red-500/40 rounded-xl">
          <div className="text-2xl font-bold text-red-300 mb-2">Backend unavailable</div>
          <div className="text-sm text-slate-300">
            Check that Spring is running on <span className="font-mono">localhost:8080</span>.
          </div>
          <div className="text-xs text-slate-500 mt-2 font-mono break-all">{bootstrapError}</div>
        </div>
      </div>
    );
  }

  return null;
}
```

- [ ] **Step 3: Build to verify**

Run from `console/`:
```bash
npm run build
```
Expected: build completes.

- [ ] **Step 4: Commit**

```bash
git add console/src/ws/stompClient.ts console/src/ws/WSClient.tsx
git commit -m "feat(console): add STOMP client + bootstrap WSClient component"
```

---

## Task 7: Header component

**Files:**
- Create: `console/src/components/Header.tsx`

- [ ] **Step 1: Create Header.tsx**

```tsx
import { motion } from 'framer-motion';
import { useConsoleStore } from '../store/consoleStore';
import { ScenarioButton } from './ScenarioButton';

export function Header() {
  const totalKw = useConsoleStore((s) => s.totalKw());
  const wsState = useConsoleStore((s) => s.wsState);

  return (
    <header className="w-full bg-slate-950 text-white border-b border-white/10">
      <div className="flex items-center justify-between px-6 pt-4">
        <h1 className="text-xl font-bold flex items-center gap-2">
          <span className="text-yellow-300">⚡</span> NEXCharge Operator Console
        </h1>
        <div className="flex items-center gap-3">
          {wsState !== 'CONNECTED' && (
            <span className="px-3 py-1 rounded-full bg-amber-500/20 text-amber-300 text-xs font-mono">
              WS {wsState}
            </span>
          )}
          <ScenarioButton scenario="PEAK_LOAD" label="PEAK LOAD" icon="⚡" />
          <ScenarioButton scenario="RESET_ALL" label="RESET ALL" icon="↻" />
        </div>
      </div>
      <div className="flex flex-col items-center py-4">
        <motion.div
          key={Math.round(totalKw * 10)}
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.2 }}
          className="text-5xl font-bold tabular-nums"
        >
          {totalKw.toFixed(1)} kW
        </motion.div>
        <div className="text-xs uppercase tracking-widest text-slate-400 mt-1">Power Drawn</div>
      </div>
    </header>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add console/src/components/Header.tsx
git commit -m "feat(console): add Header with animated total kW + WS state badge"
```

---

## Task 8: ScenarioButton component

**Files:**
- Create: `console/src/components/ScenarioButton.tsx`

- [ ] **Step 1: Create ScenarioButton.tsx**

```tsx
import { useState } from 'react';
import { postScenario } from '../api/chargePointsApi';
import { useConsoleStore } from '../store/consoleStore';
import type { ScenarioName } from '../types';

type Props = {
  scenario: ScenarioName;
  chargePointId?: string;
  label: string;
  icon?: string;
  variant?: 'global' | 'card';
};

export function ScenarioButton({ scenario, chargePointId, label, icon, variant = 'global' }: Props) {
  const [pending, setPending] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const wsState = useConsoleStore((s) => s.wsState);
  const cp = chargePointId ? useConsoleStore((s) => s.chargePoints[chargePointId]) : undefined;
  const disabled = pending || wsState !== 'CONNECTED' || (cp && !cp.online);

  async function onClick() {
    setPending(true);
    setErr(null);
    try {
      await postScenario({ scenario, chargePointId });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setErr(msg);
      window.setTimeout(() => setErr(null), 3000);
    } finally {
      setPending(false);
    }
  }

  const base = 'inline-flex items-center gap-1 rounded-lg font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed';
  const sized =
    variant === 'global'
      ? 'px-3 py-1.5 text-sm bg-white/10 hover:bg-white/20 text-white'
      : 'px-2 py-1 text-xs bg-slate-800/80 hover:bg-slate-700 text-white';

  return (
    <div className="relative">
      <button onClick={onClick} disabled={disabled} className={`${base} ${sized}`}>
        {icon && <span aria-hidden>{icon}</span>}
        {label}
      </button>
      {err && (
        <div className="absolute right-0 top-full mt-1 z-50 max-w-xs rounded bg-red-600 text-white text-xs px-2 py-1 shadow-lg">
          {err}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add console/src/components/ScenarioButton.tsx
git commit -m "feat(console): add ScenarioButton with disabled state + 3s error toast"
```

---

## Task 9: ConnectorBadge component

**Files:**
- Create: `console/src/components/ConnectorBadge.tsx`

- [ ] **Step 1: Create ConnectorBadge.tsx**

```tsx
import { motion, AnimatePresence } from 'framer-motion';
import { useConsoleStore } from '../store/consoleStore';
import type { ConnectorStatus } from '../types';

type Props = { chargePointId: string; connectorId: number };

const STATUS_COLORS: Record<string, string> = {
  Available: 'bg-green-500',
  Preparing: 'bg-yellow-500',
  Charging: 'bg-blue-500',
  Faulted: 'bg-red-500',
  Unavailable: 'bg-gray-500',
  Reserved: 'bg-purple-500',
  SuspendedEV: 'bg-orange-500',
  SuspendedEVSE: 'bg-orange-500',
  Finishing: 'bg-cyan-500',
};

const STATUS_GLOW: Record<string, string> = {
  Available: 'shadow-[0_0_20px_rgba(34,197,94,0.5)]',
  Preparing: 'shadow-[0_0_25px_rgba(234,179,8,0.7)]',
  Charging: 'shadow-[0_0_30px_rgba(59,130,246,0.9)]',
  Faulted: 'shadow-[0_0_25px_rgba(239,68,68,0.9)]',
  Unavailable: '',
};

export function ConnectorBadge({ chargePointId, connectorId }: Props) {
  const connector = useConsoleStore((s) =>
    s.chargePoints[chargePointId]?.connectors.find((c) => c.connectorId === connectorId)
  );
  if (!connector) return null;

  const status = connector.status as ConnectorStatus;
  const color = STATUS_COLORS[status] ?? 'bg-gray-500';
  const glow = STATUS_GLOW[status] ?? '';
  const isFaulted = status === 'Faulted';
  const isCharging = status === 'Charging';

  return (
    <motion.div
      animate={isFaulted ? { x: [0, -4, 4, -4, 4, 0] } : { x: 0 }}
      transition={{ duration: 0.3 }}
      className={`relative rounded-xl p-4 bg-black/30 border border-white/10 ${glow}`}
    >
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs uppercase tracking-wider text-slate-400">C{connectorId}</span>
        <motion.span
          key={status}
          initial={{ scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          className={`inline-block w-3 h-3 rounded-full ${color}`}
          style={{ filter: isCharging ? 'drop-shadow(0 0 4px currentColor)' : undefined }}
        />
      </div>
      <div className="text-2xl font-bold tabular-nums">
        {connector.currentPowerKw == null ? '—' : `${connector.currentPowerKw.toFixed(1)} kW`}
      </div>
      <div className="text-sm text-slate-400 capitalize">{status}</div>
      <AnimatePresence>
        {isCharging && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute top-2 right-2 text-yellow-300 text-lg"
          >
            ⚡
          </motion.div>
        )}
        {isFaulted && (
          <motion.div
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0, opacity: 0 }}
            className="absolute top-2 right-2 text-red-400 text-lg"
          >
            ⚠
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add console/src/components/ConnectorBadge.tsx
git commit -m "feat(console): add ConnectorBadge with state-driven color, glow, and shake on fault"
```

---

## Task 10: ChargePointCard component

**Files:**
- Create: `console/src/components/ChargePointCard.tsx`

- [ ] **Step 1: Create ChargePointCard.tsx**

```tsx
import { motion } from 'framer-motion';
import { useConsoleStore } from '../store/consoleStore';
import { ConnectorBadge } from './ConnectorBadge';
import { ScenarioButton } from './ScenarioButton';
import { siteThemeOf } from '../theme/siteTheme';

type Props = { chargePointId: string };

export function ChargePointCard({ chargePointId }: Props) {
  const cp = useConsoleStore((s) => s.chargePoints[chargePointId]);
  if (!cp) return null;
  const theme = siteThemeOf(cp.site);
  const offline = !cp.online;

  return (
    <motion.div
      animate={offline ? { opacity: 0.5, filter: 'grayscale(80%)' } : { opacity: 1, filter: 'none' }}
      transition={{ duration: 0.3 }}
      className={`${theme.cardClass} p-5 ${cp.site === 'NEX_TOWER' ? 'min-h-[420px]' : 'min-h-[360px]'}`}
    >
      <div className="flex items-center justify-between mb-3">
        <div>
          <div className="text-lg font-bold flex items-center gap-2">
            {cp.chargePointId}
            {offline && <span className="text-xs px-2 py-0.5 bg-red-600 text-white rounded">OFFLINE</span>}
          </div>
          <div className={`text-xs ${theme.textClass} opacity-70`}>
            {cp.vendor} {cp.model} {cp.firmwareVersion ? `· fw ${cp.firmwareVersion}` : ''}
          </div>
        </div>
        <span className={`w-3 h-3 rounded-full ${cp.online ? 'bg-green-400' : 'bg-red-400'}`} />
      </div>

      <div className="grid grid-cols-2 gap-3 mb-4">
        {cp.connectors.map((c) => (
          <ConnectorBadge key={c.connectorId} chargePointId={cp.chargePointId} connectorId={c.connectorId} />
        ))}
      </div>

      <div className="flex items-center gap-2">
        <ScenarioButton scenario="FAULT_ONE" chargePointId={cp.chargePointId} label="FAULT" icon="⚠" variant="card" />
        <ScenarioButton scenario="DISCONNECT_ONE" chargePointId={cp.chargePointId} label="DISCONNECT" icon="↯" variant="card" />
        <ScenarioButton scenario="START_ALL" label="START" icon="▶" variant="card" />
      </div>
    </motion.div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add console/src/components/ChargePointCard.tsx
git commit -m "feat(console): add ChargePointCard with site-aware silhouette + offline grayscale"
```

---

## Task 11: PowerChart component

**Files:**
- Create: `console/src/components/PowerChart.tsx`

- [ ] **Step 1: Create PowerChart.tsx**

```tsx
import { useEffect, useState } from 'react';
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useConsoleStore } from '../store/consoleStore';
import { siteThemeOf } from '../theme/siteTheme';

const THROTTLE_MS = 1000;

type Props = { chargePointId: string };

export function PowerChart({ chargePointId }: Props) {
  const live = useConsoleStore((s) => s.powerHistory[chargePointId] ?? []);
  const cp = useConsoleStore((s) => s.chargePoints[chargePointId]);
  const [points, setPoints] = useState(live);

  useEffect(() => {
    const t = window.setTimeout(() => setPoints(live), THROTTLE_MS);
    return () => window.clearTimeout(t);
  }, [live]);

  const accent = siteThemeOf(cp?.site).accent;
  const data = points.map((p) => ({ t: p.t, kw: Number(p.kw.toFixed(2)) }));
  const gradId = `grad-${chargePointId}`;

  return (
    <div className="h-40 w-full">
      <ResponsiveContainer>
        <AreaChart data={data}>
          <defs>
            <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={accent} stopOpacity={0.6} />
              <stop offset="100%" stopColor={accent} stopOpacity={0} />
            </linearGradient>
          </defs>
          <XAxis dataKey="t" hide />
          <YAxis width={32} tick={{ fontSize: 10 }} domain={[0, 'auto']} />
          <Tooltip
            labelFormatter={(t) => new Date(t as number).toLocaleTimeString()}
            formatter={(v: number) => [`${v.toFixed(2)} kW`, 'Power']}
          />
          <Area type="monotone" dataKey="kw" stroke={accent} strokeWidth={2} fill={`url(#${gradId})`} isAnimationActive={false} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add console/src/components/PowerChart.tsx
git commit -m "feat(console): add PowerChart with site-color gradient and 1s throttle"
```

---

## Task 12: OcppFeed component

**Files:**
- Create: `console/src/components/OcppFeed.tsx`

- [ ] **Step 1: Create OcppFeed.tsx**

```tsx
import { useEffect, useRef, useState } from 'react';
import { useConsoleStore } from '../store/consoleStore';
import type { LiveEvent } from '../types';

type Props = { chargePointId: string };

function formatLine(e: LiveEvent): { arrow: string; arrowClass: string; action: string; detail: string } {
  const inboundTypes = new Set(['CHARGE_POINT_CONNECTED', 'STATUS_CHANGE', 'METER_UPDATE', 'HEARTBEAT', 'SESSION_STARTED', 'SESSION_STOPPED']);
  const isFault = e.type === 'FAULT';
  const isInbound = inboundTypes.has(e.type);
  const arrow = isInbound ? '→' : '←';
  const arrowClass = isFault ? 'text-red-400' : isInbound ? 'text-blue-400' : 'text-green-400';

  let detail = '';
  const data = e.data as Record<string, any>;
  if (e.type === 'STATUS_CHANGE') detail = `${data.status ?? ''} c=${e.connectorId ?? '-'}`;
  else if (e.type === 'METER_UPDATE') {
    const watts = (data.readings as Record<string, number> | undefined)?.['Power.Active.Import'];
    const kw = typeof watts === 'number' ? watts / 1000 : 0;
    detail = `${kw.toFixed(2)} kW c=${e.connectorId ?? '-'}`;
  }
  else if (e.type === 'FAULT') detail = data.errorCode ?? 'Faulted';
  else if (e.type === 'SESSION_STARTED') detail = `tx=${data.transactionId ?? '?'} ${data.idTag ?? ''}`;
  else if (e.type === 'SESSION_STOPPED') detail = `tx=${data.transactionId ?? '?'}`;

  return { arrow, arrowClass, action: e.type, detail };
}

export function OcppFeed({ chargePointId }: Props) {
  const events = useConsoleStore((s) => s.eventsByCp[chargePointId] ?? []);
  const ref = useRef<HTMLDivElement>(null);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (paused || !ref.current) return;
    ref.current.scrollTop = 0;  // newest is at index 0; we render reversed
  }, [events, paused]);

  return (
    <div
      ref={ref}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      className="font-mono text-xs h-48 overflow-y-auto bg-black/30 rounded-lg p-2 space-y-0.5"
    >
      {events.length === 0 && <div className="text-slate-500">No OCPP traffic yet…</div>}
      {events.map((e, i) => {
        const ts = new Date(e.timestamp).toLocaleTimeString();
        const f = formatLine(e);
        return (
          <div key={`${e.timestamp}-${i}`} className="flex gap-2 whitespace-nowrap">
            <span className="text-slate-500">{ts}</span>
            <span className={f.arrowClass}>{f.arrow}</span>
            <span className="text-slate-200">{f.action}</span>
            <span className="text-slate-400 truncate">{f.detail}</span>
          </div>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add console/src/components/OcppFeed.tsx
git commit -m "feat(console): add OcppFeed with IN/OUT colors and pause-on-hover"
```

---

## Task 13: SiteColumn composition + final App layout

**Files:**
- Create: `console/src/components/SiteColumn.tsx`
- Modify: `console/src/App.tsx`

- [ ] **Step 1: Create SiteColumn.tsx**

```tsx
import { useConsoleStore } from '../store/consoleStore';
import { ChargePointCard } from './ChargePointCard';
import { PowerChart } from './PowerChart';
import { OcppFeed } from './OcppFeed';
import { siteThemeOf, type SiteId } from '../theme/siteTheme';

type Props = { site: SiteId };

export function SiteColumn({ site }: Props) {
  const cp = useConsoleStore((s) =>
    Object.values(s.chargePoints).find((c) => c.site === site)
  );
  const theme = siteThemeOf(site);

  return (
    <section className={`flex-1 min-w-0 flex flex-col gap-4 p-5 ${theme.bgClass}`}>
      <h2 className="text-sm font-bold uppercase tracking-widest opacity-70">{theme.label}</h2>
      {cp ? (
        <>
          <ChargePointCard chargePointId={cp.chargePointId} />
          <div>
            <h3 className="text-xs uppercase tracking-widest opacity-60 mb-1">Power</h3>
            <PowerChart chargePointId={cp.chargePointId} />
          </div>
          <div>
            <h3 className="text-xs uppercase tracking-widest opacity-60 mb-1">OCPP traffic</h3>
            <OcppFeed chargePointId={cp.chargePointId} />
          </div>
        </>
      ) : (
        <div className={`${theme.cardClass} p-5 text-center opacity-70`}>
          No charge point assigned to {theme.label} yet.
        </div>
      )}
    </section>
  );
}
```

- [ ] **Step 2: Replace src/App.tsx**

```tsx
import { Header } from './components/Header';
import { SiteColumn } from './components/SiteColumn';
import { WSClient } from './ws/WSClient';

export default function App() {
  return (
    <div className="min-h-screen flex flex-col">
      <WSClient />
      <Header />
      <main className="flex-1 flex flex-col md:flex-row">
        <SiteColumn site="NEX_TOWER" />
        <SiteColumn site="NEXTERACOM" />
      </main>
    </div>
  );
}
```

- [ ] **Step 3: Run dev server to verify it boots**

In one terminal, from repo root:
```bash
./mvnw spring-boot:run
```
Expected: backend up on 8080.

In another terminal, from `console/`:
```bash
npm run dev
```
Expected: opens `http://localhost:5173/console/` showing header + 2 site columns. Bornes appear within ~10s as they boot. Total kW updates as connectors charge.

Stop both servers (Ctrl+C in each).

- [ ] **Step 4: Run unit tests**

```bash
npm test
```
Expected: `7 passed`.

- [ ] **Step 5: Commit**

```bash
git add console/src/components/SiteColumn.tsx console/src/App.tsx
git commit -m "feat(console): wire up SiteColumn + final App layout"
```

---

## Task 14: Production build verification

**Files:**
- Modify: `.gitignore` (ensure built bundle is ignored)

- [ ] **Step 1: Add console build artifact to .gitignore**

Read current `.gitignore`. If `src/main/resources/static/console/` is not already ignored (it isn't), append:

```
src/main/resources/static/console/
console/dist/
```

(Edit the existing file at the repo root.)

- [ ] **Step 2: Build the console bundle**

From `console/`:
```bash
npm run build
```
Expected: `vite build` completes, output goes to `src/main/resources/static/console/`.

- [ ] **Step 3: Start Spring and verify the bundle is served**

From repo root:
```bash
./mvnw spring-boot:run
```

In a browser, open `http://localhost:8080/console/`.

Expected: same UI as dev mode (header, 2 site columns). Network tab shows bundle loaded from `localhost:8080`, not 5173.

Stop the server.

- [ ] **Step 4: Smoke checklist (manual, ~5 min)**

Per the spec's pre-demo checklist:
1. Backend running, `localhost:8080/console/` loads in <2s.
2. The 2 bornes appear with correct themes (NexTower dark / Nexteracom white) within 10s of backend boot.
3. Click `PEAK_LOAD` → both bornes go Charging within 1s; observe blue halos and ⚡ overlays on the connector badges.
4. Click `FAULT` on BORNE_A's card → BORNE_A turns red, shakes, shows ⚠. Recovers green within 30–120s.
5. Header total kW count-ups smoothly. Charts rise.
6. Stop the backend (Ctrl+C) → `WS RECONNECTING` badge appears in the header. Restart backend → console resyncs (re-fetches `/api/chargepoints`).
7. Refresh the page (F5) → clean rebootstrap, bornes reappear.

If any step fails, fix the underlying component before committing.

- [ ] **Step 5: Commit**

```bash
git add .gitignore
git commit -m "chore: ignore console build artifacts"
```

---

## Task 15: Update CLAUDE.md with console build instructions

**Files:**
- Modify: `CLAUDE.md` (append a "Console" section)

- [ ] **Step 1: Append to CLAUDE.md**

Append the following section at the end of the existing `CLAUDE.md`:

```markdown
## Operator Console

Browser-based demo console. Source in `console/`, builds to `src/main/resources/static/console/`, served by Spring at `http://localhost:8080/console/`.

**Dev (hot-reload):**
```bash
cd console
npm install         # first time only
npm run dev         # http://localhost:5173/console/
```
Vite proxies `/api` and `/ws/live` to `localhost:8080`, so the Spring backend must be running.

**Demo (single command):**
```bash
cd console && npm run build && cd ..
./mvnw spring-boot:run
```
Open `http://localhost:8080/console/`.

**Tests (Vitest, store only):**
```bash
cd console
npm test
```

**Spec:** `docs/superpowers/specs/2026-05-23-operator-console-design.md`
**Plan:** `docs/superpowers/plans/2026-05-23-operator-console.md`
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(claude): document operator console build/dev/test commands"
```

---

## Summary

15 tasks, ordered so that each commit leaves the project in a green state:

1. Scaffold Vite project
2. Tailwind + theme tokens
3. Shared TypeScript types
4. Zustand store + tests (TDD, the only logic-heavy piece)
5. REST bootstrap client
6. STOMP client + WSClient
7. Header
8. ScenarioButton
9. ConnectorBadge
10. ChargePointCard
11. PowerChart
12. OcppFeed
13. SiteColumn + final App
14. Production build verification + smoke test
15. Document in CLAUDE.md

**No backend changes.** All hooks (REST, STOMP, CORS) already exist on `feat/ocpp-simulator`.
