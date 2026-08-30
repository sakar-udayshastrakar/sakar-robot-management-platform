import type { ReactNode, SVGProps } from 'react';

// A small, hand-authored inline-SVG icon set (no icon library dependency —
// keeps the bundle lightweight). Stroke-based, 24x24 viewBox, inherits
// currentColor so it follows badge/button/text color automatically.

type IconProps = SVGProps<SVGSVGElement>;

function base(children: ReactNode, props: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      {children}
    </svg>
  );
}

export const Icon = {
  dashboard: (p: IconProps) => base(<><rect x="3" y="3" width="7" height="9" rx="1.5" /><rect x="14" y="3" width="7" height="5" rx="1.5" /><rect x="14" y="12" width="7" height="9" rx="1.5" /><rect x="3" y="16" width="7" height="5" rx="1.5" /></>, p),
  building: (p: IconProps) => base(<><rect x="4" y="3" width="16" height="18" rx="1.5" /><path d="M9 8h1M14 8h1M9 12h1M14 12h1M9 16h1M14 16h1" /></>, p),
  mapPin: (p: IconProps) => base(<><path d="M12 21s-7-6.1-7-11a7 7 0 0 1 14 0c0 4.9-7 11-7 11Z" /><circle cx="12" cy="10" r="2.5" /></>, p),
  robot: (p: IconProps) => base(<><rect x="5" y="9" width="14" height="10" rx="2" /><path d="M9 9V6a3 3 0 0 1 6 0v3" /><circle cx="9.5" cy="14" r="1.2" /><circle cx="14.5" cy="14" r="1.2" /><path d="M3 13h2M19 13h2" /></>, p),
  listCheck: (p: IconProps) => base(<><path d="M4 6h1M4 12h1M4 18h1" /><path d="M9 6h11M9 12h11M9 18h11" /></>, p),
  spray: (p: IconProps) => base(<><path d="M8 21V10a3 3 0 0 1 3-3h1V4h2v3h1a3 3 0 0 1 3 3v11" /><path d="M4 9h1M3 6h1M6 5h1" /></>, p),
  alertTriangle: (p: IconProps) => base(<><path d="M10.3 4.4 2.6 18a2 2 0 0 0 1.7 3h15.4a2 2 0 0 0 1.7-3L13.7 4.4a2 2 0 0 0-3.4 0Z" /><path d="M12 9v4M12 17h.01" /></>, p),
  activity: (p: IconProps) => base(<path d="M3 12h4l2 7 4-14 2 7h6" />, p),
  xCircle: (p: IconProps) => base(<><circle cx="12" cy="12" r="9" /><path d="m9.5 9.5 5 5M14.5 9.5l-5 5" /></>, p),
  fileText: (p: IconProps) => base(<><path d="M7 3h7l4 4v14a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z" /><path d="M14 3v4h4M9 12h6M9 16h6" /></>, p),
  barChart: (p: IconProps) => base(<><path d="M4 20V10M12 20V4M20 20v-7" /><path d="M2 20h20" /></>, p),
  users: (p: IconProps) => base(<><circle cx="9" cy="8" r="3.2" /><path d="M3.5 19a5.5 5.5 0 0 1 11 0" /><path d="M16 8.3a3 3 0 1 1 3.6 2.9M20.5 19a5 5 0 0 0-4-4.9" /></>, p),
  shield: (p: IconProps) => base(<path d="M12 3 5 6v5c0 5 3 8 7 10 4-2 7-5 7-10V6l-7-3Z" />, p),
  clipboard: (p: IconProps) => base(<><rect x="6" y="4" width="12" height="17" rx="1.5" /><rect x="9" y="2.5" width="6" height="3" rx="1" /><path d="M9 11h6M9 15h6" /></>, p),
  settings: (p: IconProps) => base(<><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.9 2.9l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6V21a2 2 0 1 1-4 0v-.2a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.9-2.9l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.6-1H3a2 2 0 1 1 0-4h.2a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.9-2.9l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.6V3a2 2 0 1 1 4 0v.2a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.9 2.9l-.1.1a1.7 1.7 0 0 0-.3 1.9V9c.2.6.8 1 1.6 1H21a2 2 0 1 1 0 4h-.2a1.7 1.7 0 0 0-1.6 1Z" /></>, p),
  search: (p: IconProps) => base(<><circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" /></>, p),
  bell: (p: IconProps) => base(<><path d="M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6Z" /><path d="M10 20a2 2 0 0 0 4 0" /></>, p),
  chevronLeft: (p: IconProps) => base(<path d="M15 18 9 12l6-6" />, p),
  chevronRight: (p: IconProps) => base(<path d="M9 18l6-6-6-6" />, p),
  chevronDown: (p: IconProps) => base(<path d="M6 9l6 6 6-6" />, p),
  x: (p: IconProps) => base(<path d="M18 6 6 18M6 6l12 12" />, p),
  menu: (p: IconProps) => base(<path d="M4 6h16M4 12h16M4 18h16" />, p),
  wifi: (p: IconProps) => base(<><path d="M2 8.5a16 16 0 0 1 20 0" /><path d="M5.5 12.5a11 11 0 0 1 13 0" /><path d="M9 16.3a6 6 0 0 1 6 0" /><path d="M12 20h.01" /></>, p),
  battery: (p: IconProps) => base(<><rect x="2" y="7" width="17" height="10" rx="2" /><path d="M21 10v4" /><path d="M6 10v4" /></>, p),
  plug: (p: IconProps) => base(<><path d="M9 3v5M15 3v5" /><path d="M6.5 8h11l-1 5.5A5 5 0 0 1 11.6 17.4 5 5 0 0 1 7.5 13.5Z" /><path d="M12 17.4V21" /></>, p),
  checkCircle: (p: IconProps) => base(<><circle cx="12" cy="12" r="9" /><path d="m8 12.5 2.5 2.5L16 9" /></>, p),
  alertOctagon: (p: IconProps) => base(<><path d="M7.9 3h8.2L21 7.9v8.2L16.1 21H7.9L3 16.1V7.9Z" /><path d="M12 8v5M12 16h.01" /></>, p),
  logout: (p: IconProps) => base(<><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="M16 17l5-5-5-5M21 12H9" /></>, p),
  refresh: (p: IconProps) => base(<><path d="M21 12a9 9 0 1 1-3-6.7" /><path d="M21 4v5h-5" /></>, p),
  key: (p: IconProps) => base(<><circle cx="8" cy="15" r="4" /><path d="M10.8 12.2 20 3M17 6l2.5 2.5M14 9l2 2" /></>, p),
  lock: (p: IconProps) => base(<><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></>, p),
  gauge: (p: IconProps) => base(<><path d="M12 15V9" /><path d="M4.2 18a9 9 0 1 1 15.6 0" /></>, p),
  timeline: (p: IconProps) => base(<><path d="M4 4v16M4 8h4M4 14h4M4 20h16" /></>, p),
  mapEmpty: (p: IconProps) => base(<><path d="m9 4-6 2.5v13.5l6-2.5 6 2.5 6-2.5V4L15 6.5 9 4Z" /><path d="M9 4v13.5M15 6.5V20" /></>, p),
  plus: (p: IconProps) => base(<path d="M12 5v14M5 12h14" />, p),
  edit: (p: IconProps) => base(<><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" /></>, p),
  play: (p: IconProps) => base(<path d="M7 4.5v15l13-7.5Z" />, p),
  pause: (p: IconProps) => base(<><rect x="6" y="4" width="4" height="16" rx="1" /><rect x="14" y="4" width="4" height="16" rx="1" /></>, p),
  stop: (p: IconProps) => base(<rect x="5" y="5" width="14" height="14" rx="2" />, p),
  dock: (p: IconProps) => base(<><path d="M4 20V9l8-5 8 5v11" /><path d="M9 20v-7h6v7" /></>, p),
  check: (p: IconProps) => base(<path d="m5 12 5 5L20 7" />, p),
};
