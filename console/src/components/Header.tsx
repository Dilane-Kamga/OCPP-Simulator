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
