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
          const idle = newStatus === 'Available' || newStatus === 'Finishing' || newStatus === 'Unavailable';
          set((s) => ({
            chargePoints: {
              ...s.chargePoints,
              [chargePointId]: {
                ...cp,
                connectors: cp.connectors.map((c) =>
                  c.connectorId === event.connectorId
                    ? idle
                      ? { ...c, status: newStatus, currentPowerKw: 0, currentAmps: 0 }
                      : { ...c, status: newStatus }
                    : c
                ),
              },
            },
          }));
        }
        break;
      }

      case 'METER_UPDATE': {
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
