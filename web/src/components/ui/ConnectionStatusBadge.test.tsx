import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ConnectionStatusBadge } from './ConnectionStatusBadge';

// Robot Connectivity UI Polish slice — confirms the tooltip wrapper adds
// context without altering StatusBadge's own visible label/color contract
// (that contract stays pinned by StatusBadge.test.tsx, unmodified here).
describe('ConnectionStatusBadge', () => {
  it('renders the underlying ONLINE badge with a positive explanatory tooltip', () => {
    const { container } = render(<ConnectionStatusBadge status="ONLINE" />);
    expect(screen.getByText('Online')).toBeInTheDocument();
    expect(container.querySelector('.sakar-badge--success')).toBeInTheDocument();
    expect(container.querySelector('[title]')?.getAttribute('title')).toMatch(/connected/i);
  });

  it('renders the underlying OFFLINE badge with a "no recent heartbeat" tooltip', () => {
    const { container } = render(<ConnectionStatusBadge status="OFFLINE" />);
    expect(screen.getByText('Offline')).toBeInTheDocument();
    expect(container.querySelector('.sakar-badge--danger')).toBeInTheDocument();
    expect(container.querySelector('[title]')?.getAttribute('title')).toMatch(/no recent heartbeat/i);
  });

  it('renders the underlying UNKNOWN badge with a tooltip that clearly states no heartbeat has ever been received', () => {
    const { container } = render(<ConnectionStatusBadge status="UNKNOWN" />);
    expect(screen.getByText('Unknown')).toBeInTheDocument();
    expect(container.querySelector('.sakar-badge--neutral')).toBeInTheDocument();
    expect(container.querySelector('[title]')?.getAttribute('title')).toMatch(/no heartbeat has been received/i);
  });
});
