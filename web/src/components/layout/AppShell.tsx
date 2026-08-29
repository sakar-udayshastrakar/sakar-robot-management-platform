import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { TopNav } from './TopNav';
import './layout.css';

export function AppShell() {
  return (
    <div className="sakar-shell">
      <Sidebar />
      <div className="sakar-shell-main">
        <TopNav />
        <main className="sakar-shell-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
