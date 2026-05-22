import { useEffect, useState } from 'react';
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useConsoleStore } from '../store/consoleStore';
import { siteThemeOf } from '../theme/siteTheme';

const THROTTLE_MS = 1000;

type Props = { chargePointId: string };

export function PowerChart({ chargePointId }: Props) {
  const live = useConsoleStore((s) => s.powerHistory[chargePointId] ?? []);
  const cp = useConsoleStore((s) => s.chargePoints[chargePointId]);
  const [points, setPoints] = useState(live);

  useEffect(() => {
    const t = window.setTimeout(() => setPoints(live), THROTTLE_MS);
    return () => window.clearTimeout(t);
  }, [live]);

  const accent = siteThemeOf(cp?.site).accent;
  const data = points.map((p) => ({ t: p.t, kw: Number(p.kw.toFixed(2)) }));
  const gradId = `grad-${chargePointId}`;

  return (
    <div className="h-40 w-full">
      <ResponsiveContainer>
        <AreaChart data={data}>
          <defs>
            <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={accent} stopOpacity={0.6} />
              <stop offset="100%" stopColor={accent} stopOpacity={0} />
            </linearGradient>
          </defs>
          <XAxis dataKey="t" hide />
          <YAxis width={32} tick={{ fontSize: 10 }} domain={[0, 'auto']} />
          <Tooltip
            labelFormatter={(t) => new Date(t as number).toLocaleTimeString()}
            formatter={(v: number) => [`${v.toFixed(2)} kW`, 'Power']}
          />
          <Area type="monotone" dataKey="kw" stroke={accent} strokeWidth={2} fill={`url(#${gradId})`} isAnimationActive={false} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
