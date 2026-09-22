export interface ContentBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface ComputeContentBoundsOptions {
  // A pixel is treated as background ("white") when every one of R/G/B is
  // at or above this value. The stored map PNGs are black line-art on a
  // white canvas (verified directly against the real synced Demo Piece
  // map: 570x763, non-background content bounded to roughly x:144-424,
  // y:167-615 — about 29% of the canvas area), so a near-white threshold
  // reliably separates content from background without any vendor- or
  // robot-specific assumption.
  backgroundThreshold?: number;
  // Uniform padding (in source pixels) added around the detected bounds on
  // every side, clamped to the image extents, so line-art sitting right at
  // the edge of the bounding box isn't cropped flush against the viewport.
  padding?: number;
}

// Pure, DOM-free bounding-box detection over raw RGBA pixel data. This is a
// purely visual computation over pixel *color* — it has nothing to do with
// robot/world coordinates, a map origin, or a resolution, and must never be
// treated as one. It does not read or depend on any backend field; it only
// looks at the image's own pixels. Returns null when no non-background
// pixel is found (a genuinely blank image), so a caller can fall back to
// showing the full, uncropped image rather than guessing a crop.
export function computeContentBounds(
  data: Uint8ClampedArray | Uint8Array,
  width: number,
  height: number,
  options: ComputeContentBoundsOptions = {},
): ContentBounds | null {
  const threshold = options.backgroundThreshold ?? 250;
  const padding = options.padding ?? 0;

  let minX = width;
  let minY = height;
  let maxX = -1;
  let maxY = -1;

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      const r = data[i];
      const g = data[i + 1];
      const b = data[i + 2];
      const a = data[i + 3];
      if (a === 0) {
        // Fully transparent — treated as background, never as content.
        continue;
      }
      if (r < threshold || g < threshold || b < threshold) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }

  if (maxX < minX || maxY < minY) {
    return null;
  }

  const x = Math.max(0, minX - padding);
  const y = Math.max(0, minY - padding);
  const right = Math.min(width, maxX + 1 + padding);
  const bottom = Math.min(height, maxY + 1 + padding);

  return { x, y, width: right - x, height: bottom - y };
}

export interface CropStyle {
  // The container's own aspect-ratio (bounds.width / bounds.height) — not
  // the full image's aspect ratio. The container should size itself to
  // this ratio (e.g. CSS `aspect-ratio`) and clip overflow.
  containerAspectRatio: number;
  // The <img>'s CSS width, as a percentage of the container's width. Height
  // should be left as `auto` so the browser derives it from the image's own
  // intrinsic (natural) aspect ratio — never computed or asserted here.
  imgWidthPercent: number;
  // The <img>'s CSS `left`/`top` offsets, as negative percentages of the
  // container's width/height respectively, so the bounds region aligns
  // exactly with the container's visible (clipped) area.
  imgLeftPercent: number;
  imgTopPercent: number;
}

// Pure percentage math for a single, well-defined, deterministic crop+scale
// transform: "show only `bounds` from the full `naturalWidth`x`naturalHeight`
// image, scaled uniformly to fill the container." Expressed entirely as
// percentages of the container's own size, so it never depends on (and
// therefore is never invalidated by) the container's actual runtime pixel
// size — the same percentages are correct at any viewport width.
//
// This is the single transform any future overlay (an area boundary, a
// back/charging point, a robot position) must reuse once real source-pixel
// coordinates are ever confirmed safe to render: to place a source point at
// (px, py), its position within this same cropped viewport is
// ((px - bounds.x) / bounds.width * 100)% from the left and
// ((py - bounds.y) / bounds.height * 100)% from the top — exactly the same
// ratios computed below for the image itself.
export function computeCropStyle(bounds: ContentBounds, naturalWidth: number, naturalHeight: number): CropStyle | null {
  if (bounds.width <= 0 || bounds.height <= 0 || naturalWidth <= 0 || naturalHeight <= 0) {
    return null;
  }
  return {
    containerAspectRatio: bounds.width / bounds.height,
    imgWidthPercent: (naturalWidth / bounds.width) * 100,
    imgLeftPercent: -(bounds.x / bounds.width) * 100,
    imgTopPercent: -(bounds.y / bounds.height) * 100,
  };
}
