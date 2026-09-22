import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusBadge } from './StatusBadge';

// Robot Connectivity UI Polish slice — StatusBadge is the ONLY component that
// should ever render a robot connectivity badge (see its own header comment).
// These pin its text and semantic-color contract directly, independent of
// any page that happens to embed it.
describe('StatusBadge (robot connectivity)', () => {
  it('renders ONLINE as visible "Online" text with positive/success styling', () => {
    const { container } = render(<StatusBadge status="ONLINE" />);
    expect(screen.getByText('Online')).toBeInTheDocument();
    expect(container.querySelector('.sakar-badge--success')).toBeInTheDocument();
  });

  it('renders OFFLINE as visible "Offline" text with destructive/danger styling, not neutral', () => {
    const { container } = render(<StatusBadge status="OFFLINE" />);
    expect(screen.getByText('Offline')).toBeInTheDocument();
    expect(container.querySelector('.sakar-badge--danger')).toBeInTheDocument();
    expect(container.querySelector('.sakar-badge--neutral')).not.toBeInTheDocument();
  });

  it('renders UNKNOWN as visible "Unknown" text with neutral styling', () => {
    const { container } = render(<StatusBadge status="UNKNOWN" />);
    expect(screen.getByText('Unknown')).toBeInTheDocument();
    expect(container.querySelector('.sakar-badge--neutral')).toBeInTheDocument();
  });

  it('never relies on color alone — the status label text is always present', () => {
    for (const status of ['ONLINE', 'OFFLINE', 'UNKNOWN'] as const) {
      const { unmount } = render(<StatusBadge status={status} />);
      expect(screen.getByText(status === 'ONLINE' ? 'Online' : status === 'OFFLINE' ? 'Offline' : 'Unknown')).toBeVisible();
      unmount();
    }
  });
});
