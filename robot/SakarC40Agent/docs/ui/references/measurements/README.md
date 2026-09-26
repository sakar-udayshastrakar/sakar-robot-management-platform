# Measurements

This directory holds one JSON measurement file per reference screenshot, named `<reference_id>.json`
matching the `reference_id` values assigned in
[`REFERENCE_SCREEN_INVENTORY.md`](../REFERENCE_SCREEN_INVENTORY.md).

## What a measurement file is

A measurement file is a literal, pixel-based record of what a specific reference screenshot shows -
its canvas size, the bounding boxes of its major regions and elements, and the spacing/alignment
relationships between them. It is derived evidence about one static image, not a design spec and
not an implementation plan.

## What a measurement file is not

- It is not a decision about whether the screen belongs in the Sakar application.
- It is not a duplicate/orphaned determination.
- It is not a route, feature, or ownership assignment (those live in `ROUTE_REGISTRY.md`,
  `FEATURE_REGISTRY.json`, and `COMPONENT_REGISTRY.md`, and are decided separately).
- It is not an implementation recommendation.
- It does not measure every individual character or tiny icon - small/decorative icons that appear
  as a cluster (e.g. a row of toolbar icons) are recorded as one grouped element with a note, per
  the measurement pass's own instructions.

## Schema

```json
{
  "reference_id": "...",
  "image_path": "...",
  "canvas": { "width": 0, "height": 0 },
  "regions": { "header": { "x": 0, "y": 0, "width": 0, "height": 0 } },
  "elements": [
    { "id": "...", "type": "logo|text|icon|button|card|image|dialog|sidebar|status|empty_space|other", "x": 0, "y": 0, "width": 0, "height": 0, "notes": "direct visual observation only" }
  ],
  "relationships": [
    { "type": "equal_width", "elements": ["a", "b"] }
  ],
  "unknowns": []
}
```

- `image_path` is relative to `docs/ui/references/screenshots/`, matching the paths used in
  `REFERENCE_SCREEN_INVENTORY.md`.
- `regions` are the coarse, named areas of the screen (header, main content, sidebar, a dialog
  panel, etc.) - a convenience grouping, not an exhaustive element list.
- `elements` are individual measured items within those regions. Every element's `x`/`y`/`width`/
  `height` is a pixel measurement taken directly from the image (via visual inspection and/or
  pixel-color analysis), never estimated from a similar-looking screen.
- `relationships` record alignment/spacing findings (equal widths/heights, gaps, margins,
  alignment) that are useful for validating a future implementation but are not, by themselves,
  layout instructions.
- `unknowns` lists anything that could not be measured with confidence, so it is never confused
  with a deliberately-omitted value. An empty `unknowns` array is a claim that everything relevant
  was measured with confidence - it is not the default and should not be left empty just to look
  complete.

## Rule: mark UNKNOWN rather than invent

If a boundary is ambiguous (soft gradient falloff, occluded by other content, cut off by the
screenshot/viewport, or otherwise not confidently readable), it goes in `unknowns`, not into a
guessed `x`/`y`/`width`/`height` value. A wrong number that looks precise is worse than an honest
"not measured."

## Coverage

Measurement files exist only for the screens explicitly scoped into a measurement batch. A missing
`<reference_id>.json` for an entry in `REFERENCE_SCREEN_INVENTORY.md` means it has not been
measured yet, not that it was found to need no measurement.
