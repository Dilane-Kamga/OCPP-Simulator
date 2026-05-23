import { useEffect, useRef, useState } from 'react';
import { useConsoleStore } from '../store/consoleStore';
import type { LiveEvent } from '../types';

type Props = { chargePointId: string };

function formatLine(e: LiveEvent): { arrow: string; arrowClass: string; action: string; detail: string } {
  const inboundTypes = new Set(['CHARGE_POINT_CONNECTED', 'STATUS_CHANGE', 'METER_UPDATE', 'HEARTBEAT', 'SESSION_STARTED', 'SESSION_STOPPED']);
  const isFault = e.type === 'FAULT';
  const isInbound = inboundTypes.has(e.type);
  const arrow = isInbound ? '→' : '←';
  const arrowClass = isFault ? 'text-red-500' : isInbound ? 'text-blue-500' : 'text-green-500';

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
  const cp = useConsoleStore((s) => s.chargePoints[chargePointId]);
  const events = useConsoleStore((s) => s.eventsByCp[chargePointId] ?? []);
  const ref = useRef<HTMLDivElement>(null);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (paused || !ref.current) return;
    ref.current.scrollTop = 0;
  }, [events, paused]);

  const isLight = cp?.site === 'NEXTERACOM';
  const surface = isLight
    ? 'bg-slate-100 border border-slate-200'
    : 'bg-black/30';
  const tsClass = isLight ? 'text-slate-500' : 'text-slate-500';
  const actionClass = isLight ? 'text-slate-800' : 'text-slate-200';
  const detailClass = isLight ? 'text-slate-600' : 'text-slate-400';
  const emptyClass = isLight ? 'text-slate-500' : 'text-slate-500';

  return (
    <div
      ref={ref}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      className={`font-mono text-xs h-48 overflow-y-auto rounded-lg p-2 space-y-0.5 ${surface}`}
    >
      {events.length === 0 && <div className={emptyClass}>No OCPP traffic yet…</div>}
      {events.map((e, i) => {
        const ts = new Date(e.timestamp).toLocaleTimeString();
        const f = formatLine(e);
        return (
          <div key={`${e.timestamp}-${i}`} className="flex gap-2 whitespace-nowrap">
            <span className={tsClass}>{ts}</span>
            <span className={f.arrowClass}>{f.arrow}</span>
            <span className={actionClass}>{f.action}</span>
            <span className={`${detailClass} truncate`}>{f.detail}</span>
          </div>
        );
      })}
    </div>
  );
}
