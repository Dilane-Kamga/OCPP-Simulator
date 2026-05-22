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
    ref.current.scrollTop = 0;
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
