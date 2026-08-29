import type { PermissionCode, RoleName } from '../../types/permissions';

// Decodes (never verifies) the access token's payload purely to drive UI
// rendering — which nav items to show, whose name to display. The backend
// re-validates the signature and re-checks every permission on every
// request via @PreAuthorize; nothing here is ever treated as an
// authorization decision. See SecurityConfig.java / JwtService.java.
export interface DecodedAccessToken {
  userId: string;
  email: string;
  role: RoleName;
  permissions: PermissionCode[];
  organizationId: string | null;
  organizationPath: string | null;
  expiresAt: number | null;
}

export function decodeAccessToken(token: string): DecodedAccessToken | null {
  try {
    const [, payloadB64] = token.split('.');
    if (!payloadB64) {
      return null;
    }
    const normalized = payloadB64.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=');
    const json = atob(padded);
    const claims = JSON.parse(json) as Record<string, unknown>;

    // Claim names mirror JwtService.java's CLAIM_* constants exactly: "org"
    // (not "org_id") and "perms" (not "permissions").
    return {
      userId: String(claims.sub ?? ''),
      email: String(claims.email ?? ''),
      role: (claims.role as RoleName) ?? 'VIEWER',
      permissions: Array.isArray(claims.perms) ? (claims.perms as PermissionCode[]) : [],
      organizationId: (claims.org as string | undefined) ?? null,
      organizationPath: (claims.org_path as string | undefined) ?? null,
      expiresAt: typeof claims.exp === 'number' ? claims.exp * 1000 : null,
    };
  } catch {
    return null;
  }
}

export function isExpired(decoded: DecodedAccessToken | null): boolean {
  if (!decoded?.expiresAt) {
    return true;
  }
  return Date.now() >= decoded.expiresAt;
}
