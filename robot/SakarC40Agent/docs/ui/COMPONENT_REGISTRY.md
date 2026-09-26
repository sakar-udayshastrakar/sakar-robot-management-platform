# Component Registry

**Before writing a new composable, search this file and the codebase for an equivalent one.**
Do not create `XyzCard2`, `XyzCardNew`, or a second `PinKey`-shaped composable in a different file
just because the existing one is `private` to another file - either reuse it (make it
non-private and import it) or note here why a separate one is justified.

## Shared components (`operator-ui/.../common/CommonComponents.kt`)

These are genuinely shared (public, imported across many screens). Always check here first.

| Component | Purpose | Used by (examples) |
|---|---|---|
| `CapabilityBadge(capability)` | Renders REAL/SIMULATED/LOCAL/GATED/UNAVAILABLE as a colored pill. **Every screen showing robot data must use this**, not a bespoke label. | Diagnostics, Manual Drive, Debug screen, Super User panes |
| `SakarTopBar(title, onBack, trailing)` | Standard back-arrow + title app bar. | Almost every full-screen destination |
| `BigActionCard(title, subtitle, icon, ...)` | Large touch-friendly dashboard tile. | HomeScreen quick actions |
| `SectionCard(title?, content)` | White rounded card with optional title, the base unit of most settings/diagnostic screens. | Settings screens, Super User panes, Debug screen |
| `StatusPill(label, value, tint)` | Label-over-value stat display. | HomeScreen |
| `InlineBanner(message, isWarning, onClick?)` | Warning/info banner row with icon. | Start Cleaning, Resource Management, Debug screen, System Settings |
| `EmptyState(message)` | Centered "nothing here" placeholder. | Network scan results, Resource Management "Online Scenes" |
| `SuccessDot()` | Small green dot. | (utility) |
| `CheckmarkIcon()` | Green check icon. | Resource Management profile selection |
| `ScreenPadding` (val) | Standard content padding constant. | Most screens |

## Reference-matched Super User components (`operator-ui/.../superuser/`)

| Component | File | Purpose | Reused elsewhere? |
|---|---|---|---|
| `SuperUserSidebar` / `SidebarRow` | `SuperUserHomeScreen.kt` (private) | The persistent left sidebar (icon + colored label, selected-state accent bar) matching the vendor Super User reference. | No - single use, but the pattern (icon+label+selected-bar row) is the template for any future sidebar; do not hand-roll a second version. |
| `SuperUserNetworkPane`, `SuperUserResourceManagementPane`, `SuperUserGeneralSettingsPane`, `SuperUserRobotDebuggingPane`, `SuperUserSystemSettingsPane` | `SuperUserPanes.kt` | The 5 reference-matched inline sections shown in the sidebar's content pane. | These are the ONE owner for each admin section - do not create alternate versions. |
| `ToggleRow`, `ValueRow`, `SectionLabel` | `SuperUserPanes.kt` (private) | Small label+switch / label+value row helpers used across the 5 panes. | **Known near-duplicate**: `RobotHardwareDebugScreen.kt` has its own `DebugValueRow` doing the same job because these are file-private. Not yet consolidated - see "Known duplication" below. |
| `SuperUserPinKey` | `SuperUserAuth.kt` (private) | 72dp circular keypad button (digit or icon). | **Known near-duplicate** of `IdleLock.kt`'s `PinKey` (same visual spec: 72dp circle, same colors). Kept separate deliberately because the two screens must never share code that could blur "real auth" vs "fake idle-lock unlock" - see IdleLock.kt's own doc comment. Do not merge without preserving that separation. |

## Debug-screen components (`operator-ui/.../superuser/RobotHardwareDebugScreen.kt`)

| Component | Purpose | Reused elsewhere? |
|---|---|---|
| `DebugValueRow`, `DebugSpeedTestCard`, `DebugStepperCard`, `DebugActuatorSection` | Reference-matched rows/cards for the hardware Debug dashboard (label+value, speed-level buttons, +/- stepper, toggleable actuator section). | Private to this file. **Known near-duplicate** of `SuperUserPanes.kt`'s `ValueRow`/`ToggleRow` (see above) - both render a label-left/value-right row with the same styling. Candidate for a future shared `common/` promotion; not done yet to avoid touching working screens mid-audit. |
| `EmbeddedDrivePad`, `EmbeddedDriveButton`, `EmbeddedStopButton` | The Debug screen's embedded joystick. | Wraps `ManualDriveViewModel` (shared with `manual_drive_motion_control`) rather than reimplementing motion control - this is the correct pattern: **new presentation, same ViewModel/backend**. |

## Known duplication (not yet consolidated)

| Pattern | Locations | Why it exists | Recommended action |
|---|---|---|---|
| Label-left/value-right row | `SuperUserPanes.kt: ValueRow`, `RobotHardwareDebugScreen.kt: DebugValueRow` | Both are `private fun`s added in the same work session, in different files, before this registry existed. | Promote one to `common/CommonComponents.kt` (e.g. `LabelValueRow`) and have both files import it. Not done automatically by this audit per the "no functionality removed automatically" rule - flagged for a future, explicitly-scoped cleanup pass. |
| Circular PIN keypad button | `IdleLock.kt: PinKey`, `SuperUserAuth.kt: SuperUserPinKey` | Intentionally kept separate - see the row above. | Do NOT merge. Document the reason (already done, in both files' doc comments) so a future pass doesn't "helpfully" merge real auth with the fake idle-lock pad. |

## Rule for new components

Before adding a new composable that renders a card, row, badge, or button:
1. `search_code` (sakar-robot-dev-mcp) for the shape you're about to build (e.g. "label.*Switch" or "CircleShape.*72").
2. Check this file.
3. If an equivalent exists and is `public`: import and reuse it.
4. If an equivalent exists but is `private` to another file: either promote it to `common/` (if truly identical) or add a short note here explaining why a separate one is justified (as done above for the two PIN keypads).
5. Never name a new component `<Existing>2`, `<Existing>New`, or `<Existing>Updated`.
