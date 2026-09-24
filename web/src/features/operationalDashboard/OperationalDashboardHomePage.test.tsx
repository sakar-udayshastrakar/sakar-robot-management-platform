import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { OperationalDashboardHomePage } from './OperationalDashboardHomePage';

const navigateSpy = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => navigateSpy };
});

function renderPage() {
  return render(
    <MemoryRouter>
      <OperationalDashboardHomePage />
    </MemoryRouter>,
  );
}

describe('OperationalDashboardHomePage', () => {
  it('renders all four navigation cards', () => {
    renderPage();

    expect(screen.getByText('Operation Ranking')).toBeInTheDocument();
    expect(screen.getByText('Store Real-Time Data Statistics')).toBeInTheDocument();
    expect(screen.getByText('Use Retention Analytics')).toBeInTheDocument();
    expect(screen.getByText('Hotel Task Record')).toBeInTheDocument();
  });

  it('navigates to the Operation Ranking page on click', async () => {
    renderPage();

    await userEvent.click(screen.getByText('Operation Ranking'));

    expect(navigateSpy).toHaveBeenCalledWith('/operational-dashboard/operation-ranking');
  });
});
