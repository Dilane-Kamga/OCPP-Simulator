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
