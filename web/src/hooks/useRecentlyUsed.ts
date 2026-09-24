import { useEffect, useState } from 'react';
import { ALL_NAV_ITEMS, type NavItem } from '../components/layout/navConfig';

const STORAGE_KEY = 'sakar:recently-used';
const MAX_ENTRIES = 6;

// Real per-browser navigation history — every page you actually visit (that
// has a real nav entry) gets recorded here, most-recent-first, deduplicated
// by path. This is a per-viewer convenience (localStorage), never synced or
// read back by the backend — see trackVisit's call site in AppShell.
export function trackVisit(pathname: string) {
  const item = ALL_NAV_ITEMS.find((i) => i.path === pathname);
  if (!item || pathname === '/dashboard') return;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const existing: string[] = raw ? JSON.parse(raw) : [];
    const next = [pathname, ...existing.filter((p) => p !== pathname)].slice(0, MAX_ENTRIES);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    // Private browsing / storage blocked — recently-used is a convenience, never required.
  }
}

export function useRecentlyUsed(): NavItem[] {
  const [paths, setPaths] = useState<string[]>([]);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      setPaths(raw ? JSON.parse(raw) : []);
    } catch {
      setPaths([]);
    }
  }, []);

  return paths
    .map((p) => ALL_NAV_ITEMS.find((i) => i.path === p))
    .filter((i): i is NavItem => i !== undefined);
}
