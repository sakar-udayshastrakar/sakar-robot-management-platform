# References

This directory holds the actual reference material that `REFERENCE_UI_MAP.md`,
`FEATURE_REGISTRY.json`, and `CLAUDE_UI_RULES.md` all point back to. It is the evidence, not a mood
board.

## Rules for this directory

- **Reference screenshots are the source of truth for a screen's UI**, not inspiration, not a
  starting point to riff on, and not one option among several. If a screenshot shows it, that is
  what the screen looks like. If a screenshot doesn't show it, it doesn't belong on the screen
  without an explicit, documented instruction otherwise.
- **Screenshots here are not runtime app assets.** Nothing in this directory is bundled into the
  app, referenced by app code, or shipped in a build. They exist purely for comparison during
  development and review.
- **Measurements belong in `measurements/`, not next to the screenshot.** A screenshot is the raw
  evidence; a measurement file is the derived, written-down analysis of that evidence (bounds,
  fractions, colors, spacing). Keep the two separate so the raw source is never accidentally
  overwritten by derived data.
- **One reference screen has one authoritative reference image.** If a screen has multiple
  screenshots (e.g. different states), one is designated authoritative for layout/geometry
  purposes and the rest are supporting evidence - do not average or blend them into a
  from-memory composite.
- **Reference images must not be modified or overwritten without explicit approval.** Don't crop,
  annotate, recompress, or replace a file in `screenshots/` as a side effect of other work. If a
  reference image needs to change (new screenshot supersedes an old one, a mistake was corrected),
  that is its own explicit, approved action - and the change belongs in `UI_CHANGE_LEDGER.md`.

## Layout

- `screenshots/` - the reference images themselves, one authoritative image per reference screen
  (plus any explicitly-labeled supporting/secondary screenshots for that same screen).
- `measurements/` - derived measurement files (bounds, fractions, spacing, colors) produced by
  analyzing a screenshot. See `REFERENCE_UI_MAP.md`'s reference-screen schema for the
  `measurement_file` field that links a screen to its measurement file here.

## Naming

Until a project-wide convention is established, name files so the mapping from screenshot to
measurement file to screen is obvious at a glance (e.g. a shared base name per screen, screenshots
in `screenshots/`, its measurements in `measurements/`). `REFERENCE_UI_MAP.md`'s per-screen entry is
the authoritative pointer either way - the filename is a convenience, not the source of truth for
which file belongs to which screen.
