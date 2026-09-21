import { Link } from 'react-router-dom';
import { EmptyState } from '../../components/ui/States';
// Both pages below use .sakar-auth-page, which is defined in auth.css.
// Without this import they only rendered correctly when the login page
// happened to have loaded that stylesheet first.
import '../auth/auth.css';

export function ForbiddenPage() {
  return (
    <div className="sakar-auth-page">
      <EmptyState
        title="403 — Forbidden"
        detail="Your role does not have permission to view this page."
        action={<Link to="/dashboard" className="sakar-btn sakar-btn--primary">Back to dashboard</Link>}
      />
    </div>
  );
}

export function NotFoundPage() {
  return (
    <div className="sakar-auth-page">
      <EmptyState
        title="404 — Not found"
        detail="This page does not exist."
        action={<Link to="/dashboard" className="sakar-btn sakar-btn--primary">Back to dashboard</Link>}
      />
    </div>
  );
}
