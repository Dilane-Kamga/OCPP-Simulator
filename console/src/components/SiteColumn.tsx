import { useConsoleStore } from '../store/consoleStore';
import { ChargePointCard } from './ChargePointCard';
import { PowerChart } from './PowerChart';
import { OcppFeed } from './OcppFeed';
import { siteThemeOf, type SiteId } from '../theme/siteTheme';

type Props = { site: SiteId };

export function SiteColumn({ site }: Props) {
  const cp = useConsoleStore((s) =>
    Object.values(s.chargePoints).find((c) => c.site === site)
  );
  const theme = siteThemeOf(site);

  return (
    <section className={`flex-1 min-w-0 flex flex-col gap-4 p-5 ${theme.bgClass}`}>
      <h2 className="text-sm font-bold uppercase tracking-widest opacity-70">{theme.label}</h2>
      {cp ? (
        <>
          <ChargePointCard chargePointId={cp.chargePointId} />
          <div>
            <h3 className="text-xs uppercase tracking-widest opacity-60 mb-1">Power</h3>
            <PowerChart chargePointId={cp.chargePointId} />
          </div>
          <div>
            <h3 className="text-xs uppercase tracking-widest opacity-60 mb-1">OCPP traffic</h3>
            <OcppFeed chargePointId={cp.chargePointId} />
          </div>
        </>
      ) : (
        <div className={`${theme.cardClass} p-5 text-center opacity-70`}>
          No charge point assigned to {theme.label} yet.
        </div>
      )}
    </section>
  );
}
