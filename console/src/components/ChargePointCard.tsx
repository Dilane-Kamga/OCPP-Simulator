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
        <ScenarioButton scenario="DISCONNECT_ONE" chargePointId={cp.chargePointId} label="DISCONNECT" icon="↯" variant="card" />
      </div>
    </motion.div>
  );
}
