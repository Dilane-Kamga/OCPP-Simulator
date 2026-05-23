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

export type ScenarioPayload = { scenario: string; chargePointId?: string; connectorId?: number };

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
