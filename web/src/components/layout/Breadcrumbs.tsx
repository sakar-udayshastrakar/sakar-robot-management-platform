import { Link, useLocation } from 'react-router-dom';

function titleCase(segment: string): string {
  if (/^[0-9a-fA-F-]{8,}$/.test(segment)) {
    return `${segment.slice(0, 8)}…`;
  }
  return segment.charAt(0).toUpperCase() + segment.slice(1).replace(/-/g, ' ');
}

export function Breadcrumbs() {
  const location = useLocation();
  const segments = location.pathname.split('/').filter(Boolean);

  if (segments.length === 0) {
    return <div className="sakar-breadcrumbs" />;
  }

  let accumulated = '';
  return (
    <nav className="sakar-breadcrumbs" aria-label="Breadcrumb">
      <Link to="/dashboard">Home</Link>
      {segments.map((segment, index) => {
        accumulated += `/${segment}`;
        const isLast = index === segments.length - 1;
        return (
          <span key={accumulated}>
            <span className="sakar-breadcrumb-sep">/</span>
            {isLast ? (
              <span className="sakar-breadcrumb-current">{titleCase(segment)}</span>
            ) : (
              <Link to={accumulated}>{titleCase(segment)}</Link>
            )}
          </span>
        );
      })}
    </nav>
  );
}
