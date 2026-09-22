import { describe, expect, it } from 'vitest';
import { formatHeartbeat } from './formatHeartbeat';

describe('formatHeartbeat', () => {
  it('returns "Never received" with no absolute timestamp when lastSeenAt is null', () => {
    expect(formatHeartbeat(null)).toEqual({ relative: 'Never received', absolute: null });
  });

  it('returns "Just now" for a timestamp under 45 seconds old', () => {
    const now = Date.parse('2026-09-22T12:00:00.000Z');
    const lastSeenAt = new Date(now - 30_000).toISOString();
    expect(formatHeartbeat(lastSeenAt, now).relative).toBe('Just now');
  });

  it('formats minutes ago for a timestamp under an hour old', () => {
    const now = Date.parse('2026-09-22T12:00:00.000Z');
    const lastSeenAt = new Date(now - 5 * 60_000).toISOString();
    expect(formatHeartbeat(lastSeenAt, now).relative).toBe('5 min ago');
  });

  it('formats hours ago (singular) for exactly one hour old', () => {
    const now = Date.parse('2026-09-22T12:00:00.000Z');
    const lastSeenAt = new Date(now - 60 * 60_000).toISOString();
    expect(formatHeartbeat(lastSeenAt, now).relative).toBe('1 hr ago');
  });

  it('formats hours ago (plural) for a timestamp under a day old', () => {
    const now = Date.parse('2026-09-22T12:00:00.000Z');
    const lastSeenAt = new Date(now - 5 * 60 * 60_000).toISOString();
    expect(formatHeartbeat(lastSeenAt, now).relative).toBe('5 hrs ago');
  });

  it('formats days ago for anything a day or older', () => {
    const now = Date.parse('2026-09-22T12:00:00.000Z');
    const lastSeenAt = new Date(now - 3 * 24 * 60 * 60_000).toISOString();
    expect(formatHeartbeat(lastSeenAt, now).relative).toBe('3 days ago');
  });

  it('always includes the absolute timestamp alongside a relative label, for use as secondary/tooltip information', () => {
    const now = Date.parse('2026-09-22T12:00:00.000Z');
    const lastSeenAt = new Date(now - 5 * 60_000).toISOString();
    const result = formatHeartbeat(lastSeenAt, now);
    expect(result.absolute).toBe(new Date(lastSeenAt).toLocaleString());
  });

  it('clamps a future timestamp (clock skew) to "Just now" rather than a negative duration', () => {
    const now = Date.parse('2026-09-22T12:00:00.000Z');
    const lastSeenAt = new Date(now + 10_000).toISOString();
    expect(formatHeartbeat(lastSeenAt, now).relative).toBe('Just now');
  });
});
