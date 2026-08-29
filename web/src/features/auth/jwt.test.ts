import { describe, expect, it } from 'vitest';
import { decodeAccessToken, isExpired } from './jwt';

function fakeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS384' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

describe('decodeAccessToken', () => {
  it('reads the real backend claim names (org, perms, role, email, exp)', () => {
    const token = fakeJwt({
      sub: 'user-1',
      email: 'admin@sakarrobotics.com',
      role: 'ORG_ADMIN',
      perms: ['ROBOT_VIEW', 'ROBOT_CONFIGURE'],
      org: 'org-1',
      org_path: '/org-1/',
      exp: Math.floor(Date.now() / 1000) + 900,
    });

    const decoded = decodeAccessToken(token);

    expect(decoded).not.toBeNull();
    expect(decoded?.userId).toBe('user-1');
    expect(decoded?.email).toBe('admin@sakarrobotics.com');
    expect(decoded?.role).toBe('ORG_ADMIN');
    expect(decoded?.permissions).toEqual(['ROBOT_VIEW', 'ROBOT_CONFIGURE']);
    expect(decoded?.organizationId).toBe('org-1');
  });

  it('returns null for a malformed token', () => {
    expect(decodeAccessToken('not-a-jwt')).toBeNull();
  });

  it('defaults permissions to an empty array when the claim is missing', () => {
    const token = fakeJwt({ sub: 'user-2', email: 'x@y.com', role: 'VIEWER' });
    expect(decodeAccessToken(token)?.permissions).toEqual([]);
  });
});

describe('isExpired', () => {
  it('treats a null decoded token as expired', () => {
    expect(isExpired(null)).toBe(true);
  });

  it('treats a past exp as expired', () => {
    const token = fakeJwt({ sub: 'u', email: 'e', role: 'VIEWER', exp: Math.floor(Date.now() / 1000) - 10 });
    expect(isExpired(decodeAccessToken(token))).toBe(true);
  });

  it('treats a future exp as not expired', () => {
    const token = fakeJwt({ sub: 'u', email: 'e', role: 'VIEWER', exp: Math.floor(Date.now() / 1000) + 3600 });
    expect(isExpired(decodeAccessToken(token))).toBe(false);
  });
});
