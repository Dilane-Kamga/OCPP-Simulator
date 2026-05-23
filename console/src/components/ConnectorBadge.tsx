import { motion, AnimatePresence } from 'framer-motion';
import { useConsoleStore } from '../store/consoleStore';
import type { ConnectorStatus } from '../types';
import { ScenarioButton } from './ScenarioButton';

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
  const cp = useConsoleStore((s) => s.chargePoints[chargePointId]);
  const connector = cp?.connectors.find((c) => c.connectorId === connectorId);
  if (!connector) return null;

  const status = connector.status as ConnectorStatus;
  const color = STATUS_COLORS[status] ?? 'bg-gray-500';
  const glow = STATUS_GLOW[status] ?? '';
  const isFaulted = status === 'Faulted';
  const isCharging = status === 'Charging';
  const isLight = cp?.site === 'NEXTERACOM';

  const surface = isLight
    ? 'bg-white border border-slate-200 text-slate-900'
    : 'bg-black/30 border border-white/10 text-white';
  const labelClass = isLight ? 'text-slate-500' : 'text-slate-400';
  const subClass = isLight ? 'text-slate-600' : 'text-slate-400';

  return (
    <motion.div
      animate={isFaulted ? { x: [0, -4, 4, -4, 4, 0] } : { x: 0 }}
      transition={{ duration: 0.3 }}
      className={`relative rounded-xl p-4 ${surface} ${glow}`}
    >
      <div className="flex items-center justify-between mb-2">
        <span className={`text-xs uppercase tracking-wider ${labelClass}`}>C{connectorId}</span>
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
      <div className={`text-sm capitalize ${subClass}`}>{status}</div>
      <div className="flex items-center gap-1 mt-2">
        <ScenarioButton
          scenario="FAULT_ONE"
          chargePointId={chargePointId}
          connectorId={connectorId}
          label="FAULT"
          icon="⚠"
          variant="connector"
        />
        <ScenarioButton
          scenario="START_ONE"
          chargePointId={chargePointId}
          connectorId={connectorId}
          label="START"
          icon="▶"
          variant="connector"
        />
      </div>
      <AnimatePresence>
        {isCharging && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute top-2 right-2 text-yellow-500 text-lg"
          >
            ⚡
          </motion.div>
        )}
        {isFaulted && (
          <motion.div
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0, opacity: 0 }}
            className="absolute top-2 right-2 text-red-500 text-lg"
          >
            ⚠
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
