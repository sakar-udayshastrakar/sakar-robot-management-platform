# Claude UI Rules

Permanent, top-level rules for any Claude session touching UI in this project. These rules exist
so that UI work stays governed by evidence (reference screenshots, existing code, the registries in
this directory) instead of improvisation, and so mistakes made once are not repeated by a future
session that never saw this conversation. Read this file before starting any UI task. When a rule
here conflicts with a habit that feels natural or convenient, the rule wins.

1. **Reference screenshots are the visual source of truth.** See `references/README.md` and
   `REFERENCE_UI_MAP.md`. A reference image outranks memory, prior code, and assumption.
2. **Do not redesign, improvise, or add UI not present in the reference.** If the reference doesn't
   show it, don't build it - ask instead of guessing.
3. **Do not remove reference UI elements without an explicit documented reason.** A removal needs a
   row in `UI_CHANGE_LEDGER.md` explaining why, and ideally an explicit user instruction.
4. **One feature has one owner screen.** See `FEATURE_REGISTRY.md`'s `owner_screen` field. Multiple
   entry points into the same owner are fine; a second implementation of the same feature is not.
5. **Never duplicate an existing feature.** Check `FEATURE_REGISTRY.json`/`.md` and
   `COMPONENT_REGISTRY.md` before writing anything that might already exist.
6. **Search existing code before creating a new component.** Reuse over reimplementation, always.
7. **Preserve existing functionality unless explicitly asked to remove it.** A UI change is not
   license to delete underlying screens/routes/ViewModels/repositories - see the no-auto-delete
   policy already in effect across these registries (orphan, don't delete).
8. **Reference branding may be replaced only when required for Sakar branding.** This is the only
   category of "reference element replaced," and it must be logged as a branding exception (see the
   `branding_exceptions` field in the reference-screen schema below).
9. **Runtime values must remain real; never fabricate hardware/system status.** A value the app
   cannot actually read must be shown as unavailable/blocked, never invented.
10. **Before UI modification, run `ui_preflight`.** Confirms whether the feature/screen/route/
    component already exists before you write a line of code.
11. **After UI modification, run `ui_duplicate_check` and visual verification.** Build, install,
    screenshot, and compare against the reference before calling anything done.
12. **Do not report UI COMPLETE until the reference comparison passes.** "Looks close" is not
    "matches" - measure it.
13. **Do not classify reference elements as "artifacts" based on subjective judgment.** If it's
    visible in the reference, it's real, including blank space (see rule 14). Only an explicit,
    documented user decision can exclude a reference element.
14. **Empty space and system-bar relationships are part of the reference geometry.** Margins, gaps,
    and unused space below/around content are measured and reproduced like any other element, not
    treated as accidental or safe to discard.
15. **Do not modify unrelated screens.** A task scoped to one screen touches that screen's files
    (and shared components genuinely exclusive to it) only.

## Why this file exists

Earlier sessions in this project repeatedly rebuilt reference-matched screens from scratch,
introduced duplicate entry points, and once dismissed a reference's own blank-space proportion as
a "device artifact" and omitted it from a rebuild - a judgment call the user then had to explicitly
override. This file exists to make that class of mistake structurally harder to repeat.
