import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { C40ControlPage } from './C40ControlPage';

// useRobotOptions calls the live /robots endpoint; stub it so the page renders
// deterministically with no network. With no robot selected, the page shows its
// empty state and none of the fetching panels mount — this verifies the page
// composes and renders safely.
vi.mock('../shared/useRobotOptions', () => ({
  useRobotOptions: () => ({ robots: [], loading: false }),
}));

describe('C40ControlPage', () => {
  it('renders the header and prompts for a robot when none is selected', () => {
    render(<C40ControlPage />);
    expect(screen.getByRole('heading', { name: /c40 cleaning control/i })).toBeInTheDocument();
    // Empty-state detail is unique to the no-robot-selected path.
    expect(screen.getByText(/choose a robot above to open its cleaning control console/i)).toBeInTheDocument();
    // The robot picker is present and defaults to the "select" prompt.
    expect(screen.getByLabelText('Robot')).toBeInTheDocument();
  });
});
