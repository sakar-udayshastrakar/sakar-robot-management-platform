import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { TopNav } from './TopNav';
import { trackVisit } from '../../hooks/useRecentlyUsed';
import './layout.css';

// Two-level structure — header spans full width above, sidebar+content
// sit in a row below it — matching the Sakar Robotics Support Portal's
// own AdminLayout.jsx exactly (sticky header, then a flex row of
// sidebar + main).
export function AppShell() {
  const location = useLocation();

  // Records real navigation history for the Dashboard's "Recently Used"
  // card — see useRecentlyUsed.ts's own comment for why this is
  // per-browser only, never sent to the backend.
  useEffect(() => {
    trackVisit(location.pathname);
  }, [location.pathname]);

  return (
    <div className="sakar-shell">
      <TopNav />
      <div className="sakar-shell-body">
        <Sidebar />
        <div className="sakar-shell-main">
          <main className="sakar-shell-content">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}
