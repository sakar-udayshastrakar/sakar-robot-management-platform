import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { FileDownloadPage } from './FileDownloadPage';

describe('FileDownloadPage', () => {
  it('renders exactly one real document row linking to the live API reference, never a fabricated version history', () => {
    render(<FileDownloadPage />);

    expect(screen.getByText('Documents (1)')).toBeInTheDocument();
    expect(screen.getByText('Sakar Cloud Backend API Reference')).toBeInTheDocument();
    expect(screen.getByText('phase-1')).toBeInTheDocument();
    const preview = screen.getByRole('link', { name: 'Preview' });
    expect(preview).toHaveAttribute('href', expect.stringContaining('/swagger-ui.html'));
    expect(preview).toHaveAttribute('target', '_blank');
  });
});
