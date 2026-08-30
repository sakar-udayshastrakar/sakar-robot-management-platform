import { useEffect, useState } from 'react';

const KEY = 'sakar.sidebarCollapsed';

export function useSidebarCollapse() {
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem(KEY) === 'true';
    } catch {
      return false;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(KEY, String(collapsed));
    } catch {
      // ignore — a private-mode browser may block storage; collapse state
      // simply won't persist across reloads.
    }
  }, [collapsed]);

  return { collapsed, toggle: () => setCollapsed((c) => !c) };
}
