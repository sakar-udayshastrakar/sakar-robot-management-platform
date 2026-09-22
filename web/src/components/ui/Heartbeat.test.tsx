import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Heartbeat } from './Heartbeat';

describe('Heartbeat', () => {
  it('renders "Never received" with no tooltip when lastSeenAt is null', () => {
    const { container } = render(<Heartbeat lastSeenAt={null} />);
    expect(screen.getByText('Never received')).toBeInTheDocument();
    expect(container.querySelector('[title]')).not.toBeInTheDocument();
  });

  it('renders relative text as the visible content, with the absolute timestamp as a tooltip', () => {
    const lastSeenAt = new Date(Date.now() - 2 * 60_000).toISOString();
    render(<Heartbeat lastSeenAt={lastSeenAt} />);
    const el = screen.getByText('2 min ago');
    expect(el).toBeInTheDocument();
    expect(el).toHaveAttribute('title', new Date(lastSeenAt).toLocaleString());
  });
});
