export type SiteId = 'NEX_TOWER' | 'NEXTERACOM';

export type SiteTheme = {
  bgClass: string;       // tailwind classes for column background
  textClass: string;
  accent: string;        // hex for SVG / inline styles
  cardClass: string;     // tailwind classes for card surface
  label: string;         // human-readable site name
};

export const SITE_THEMES: Record<SiteId, SiteTheme> = {
  NEX_TOWER: {
    bgClass: 'bg-gradient-to-b from-nextower-bg1 to-nextower-bg2 text-white',
    textClass: 'text-white',
    accent: '#e63946',
    cardClass: 'bg-black/40 border border-white/10 rounded-2xl shadow-2xl',
    label: 'NEX Tower',
  },
  NEXTERACOM: {
    bgClass: 'bg-nexteracom-bg text-nexteracom-text',
    textClass: 'text-nexteracom-text',
    accent: '#0072ce',
    cardClass: 'bg-white border border-slate-200 rounded-2xl shadow-md',
    label: 'Nexteracom',
  },
};

export function siteThemeOf(site: SiteId | null | undefined): SiteTheme {
  return SITE_THEMES[site ?? 'NEX_TOWER'];
}
