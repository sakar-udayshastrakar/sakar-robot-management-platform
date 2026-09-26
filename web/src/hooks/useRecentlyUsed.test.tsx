import { describe, expect, it, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { trackVisit, useRecentlyUsed } from './useRecentlyUsed';

describe('useRecentlyUsed', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('returns nothing before any real page has been visited', () => {
    const { result } = renderHook(() => useRecentlyUsed());
    expect(result.current).toEqual([]);
  });

  it('lists a visited real nav page, most-recent-first, after trackVisit', async () => {
    trackVisit('/robots');
    trackVisit('/iot/elevator-management');

    const { result } = renderHook(() => useRecentlyUsed());

    await waitFor(() => expect(result.current.map((i) => i.path)).toEqual(['/iot/elevator-management', '/robots']));
  });

  it('ignores a path with no real nav entry, never inventing a fake one', () => {
    trackVisit('/robots/not-a-real-sub-path-xyz');

    const { result } = renderHook(() => useRecentlyUsed());

    expect(result.current).toEqual([]);
  });

  it('never records the Dashboard itself, so it cannot list a shortcut back to itself', () => {
    trackVisit('/dashboard');

    const { result } = renderHook(() => useRecentlyUsed());

    expect(result.current).toEqual([]);
  });

  it('deduplicates a repeat visit by moving it to the front instead of listing it twice', async () => {
    trackVisit('/robots');
    trackVisit('/iot/elevator-management');
    trackVisit('/robots');

    const { result } = renderHook(() => useRecentlyUsed());

    await waitFor(() => expect(result.current.map((i) => i.path)).toEqual(['/robots', '/iot/elevator-management']));
  });
});
