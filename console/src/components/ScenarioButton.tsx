import { useState } from 'react';
import { postScenario } from '../api/chargePointsApi';
import { useConsoleStore } from '../store/consoleStore';
import type { ScenarioName } from '../types';

type Props = {
  scenario: ScenarioName;
  chargePointId?: string;
  connectorId?: number;
  label: string;
  icon?: string;
  variant?: 'global' | 'card' | 'connector';
};

export function ScenarioButton({ scenario, chargePointId, connectorId, label, icon, variant = 'global' }: Props) {
  const [pending, setPending] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const wsState = useConsoleStore((s) => s.wsState);
  const cp = useConsoleStore((s) => (chargePointId ? s.chargePoints[chargePointId] : undefined));
  const disabled = pending || wsState !== 'CONNECTED' || (cp ? !cp.online : false);

  async function onClick() {
    setPending(true);
    setErr(null);
    try {
      await postScenario({ scenario, chargePointId, connectorId });
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
      : variant === 'connector'
        ? 'px-1.5 py-0.5 text-[10px] bg-slate-800/80 hover:bg-slate-700 text-white'
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
