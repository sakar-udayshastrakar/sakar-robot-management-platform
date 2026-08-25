# mobile/

**Status: NOT YET IMPLEMENTED.** Reserved for the Sakar mobile application (Flutter — recommended default per the master requirements document; no existing Sakar mobile standard was found to override this).

**Core architecture rule this application must follow:** the mobile application talks to Sakar Backend only — identical rule to `web/`. It enforces the same RBAC model as web for the same user; a permission a user lacks on web, they lack on mobile.

Specification sources:
- `docs/requirements/SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` — Part 7.B (Applications — Mobile), Part 31 (UI Requirements)
- `docs/api/SAKAR_ROBOT_PLATFORM_API_SPEC.md` — the only interface this application is authorized to call

**Current phase: DOCUMENTATION / ARCHITECTURE PREPARATION.** No code exists here yet.
