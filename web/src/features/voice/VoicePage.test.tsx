import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { VoicePage } from './VoicePage';

describe('VoicePage', () => {
  beforeEach(() => {
    try {
      localStorage.clear();
    } catch {
      // ignore
    }
  });

  it('renders the voice page with the demo/simulated banner', () => {
    render(<VoicePage />);
    expect(screen.getByRole('heading', { name: /voice announcements/i })).toBeInTheDocument();
    // Honest labelling: the simulated-data banner must be present.
    expect(screen.getByText(/No backend REST endpoint exists/i)).toBeInTheDocument();
  });

  it('shows Sakar-authored announcements from the catalog', () => {
    render(<VoicePage />);
    expect(screen.getByText('Starting cleaning task.')).toBeInTheDocument();
    expect(screen.getByText('Returning to the charging dock.')).toBeInTheDocument();
  });

  it('toggles mute and persists the setting', () => {
    render(<VoicePage />);
    const mute = screen.getByLabelText(/mute announcements/i) as HTMLInputElement;
    expect(mute.checked).toBe(false);
    fireEvent.click(mute);
    expect(mute.checked).toBe(true);
    // Persisted so a reload keeps the operator's choice.
    expect(localStorage.getItem('sakar.voice.settings')).toContain('"muted":true');
  });
});
