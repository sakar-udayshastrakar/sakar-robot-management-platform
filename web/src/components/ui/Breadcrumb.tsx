import { Fragment } from 'react';
import { Link } from 'react-router-dom';

export interface Crumb {
  label: string;
  to?: string;
}

// Shared breadcrumb trail. The styles already existed (previously local to
// robots.css for Robot Detail) and now live in components.css so any page
// can use the same trail — Keenon puts one above every sub-page, and
// Cleaning is the first non-robot page to adopt it.
export function Breadcrumb({ items }: { items: Crumb[] }) {
  return (
    <nav className="sakar-breadcrumb" aria-label="Breadcrumb">
      {items.map((item, i) => (
        <Fragment key={`${item.label}-${i}`}>
          {i > 0 && <span aria-hidden="true">/</span>}
          {item.to ? (
            <Link to={item.to}>{item.label}</Link>
          ) : (
            <span className={i === items.length - 1 ? 'sakar-breadcrumb-current' : undefined}>{item.label}</span>
          )}
        </Fragment>
      ))}
    </nav>
  );
}
