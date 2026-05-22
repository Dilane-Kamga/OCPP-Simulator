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
