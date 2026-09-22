import { describe, expect, it } from 'vitest';
import { computeContentBounds, computeCropStyle } from './mapContentBounds';

// Builds a flat RGBA buffer for a width x height canvas, white (255,255,255,255)
// everywhere except the given rectangles, which are painted black.
function makeImage(width: number, height: number, rects: { x: number; y: number; w: number; h: number }[]): Uint8ClampedArray {
  const data = new Uint8ClampedArray(width * height * 4).fill(255);
  for (const rect of rects) {
    for (let y = rect.y; y < rect.y + rect.h; y++) {
      for (let x = rect.x; x < rect.x + rect.w; x++) {
        const i = (y * width + x) * 4;
        data[i] = 0;
        data[i + 1] = 0;
        data[i + 2] = 0;
        data[i + 3] = 255;
      }
    }
  }
  return data;
}

describe('computeContentBounds', () => {
  it('returns null for a fully blank (all-white) image — never invents a crop', () => {
    const data = makeImage(100, 80, []);
    expect(computeContentBounds(data, 100, 80)).toBeNull();
  });

  it('returns the tight bounding box of a single content rectangle', () => {
    const data = makeImage(200, 150, [{ x: 40, y: 30, w: 60, h: 50 }]);
    expect(computeContentBounds(data, 200, 150)).toEqual({ x: 40, y: 30, width: 60, height: 50 });
  });

  it('returns the union bounding box spanning multiple disjoint content regions', () => {
    // Mirrors the real stored map: several disconnected wall/line segments,
    // not one solid blob — the bbox must cover all of them, not just one.
    const data = makeImage(300, 300, [
      { x: 10, y: 10, w: 5, h: 5 },
      { x: 250, y: 260, w: 5, h: 5 },
    ]);
    expect(computeContentBounds(data, 300, 300)).toEqual({ x: 10, y: 10, width: 245, height: 255 });
  });

  it('treats fully transparent pixels as background regardless of color', () => {
    const data = new Uint8ClampedArray(10 * 10 * 4).fill(255);
    // A "black" pixel that is fully transparent (alpha 0) must not count.
    const i = (5 * 10 + 5) * 4;
    data[i] = 0;
    data[i + 1] = 0;
    data[i + 2] = 0;
    data[i + 3] = 0;
    expect(computeContentBounds(data, 10, 10)).toBeNull();
  });

  it('expands the bounds by the requested padding, clamped to the image edges', () => {
    const data = makeImage(100, 100, [{ x: 40, y: 40, w: 10, h: 10 }]);
    expect(computeContentBounds(data, 100, 100, { padding: 5 })).toEqual({ x: 35, y: 35, width: 20, height: 20 });
    // Padding near an edge clamps rather than going negative / out of bounds.
    const edgeData = makeImage(100, 100, [{ x: 0, y: 0, w: 3, h: 3 }]);
    expect(computeContentBounds(edgeData, 100, 100, { padding: 5 })).toEqual({ x: 0, y: 0, width: 8, height: 8 });
  });

  it('respects a custom background threshold instead of a hardcoded one', () => {
    // A mid-gray (200,200,200) pixel: background at the default threshold
    // (250) but content once the caller lowers the threshold.
    const data = new Uint8ClampedArray(10 * 10 * 4).fill(255);
    const i = (5 * 10 + 5) * 4;
    data[i] = 200;
    data[i + 1] = 200;
    data[i + 2] = 200;
    data[i + 3] = 255;
    expect(computeContentBounds(data, 10, 10)).toEqual({ x: 5, y: 5, width: 1, height: 1 });
    expect(computeContentBounds(data, 10, 10, { backgroundThreshold: 100 })).toBeNull();
  });
});

describe('computeCropStyle', () => {
  it('computes a deterministic transform for the real stored map dimensions (570x763)', () => {
    // The real, measured bounding box for the actual synced Demo Piece PNG.
    const bounds = { x: 144, y: 167, width: 281, height: 449 };
    const style = computeCropStyle(bounds, 570, 763);
    expect(style).not.toBeNull();
    expect(style!.containerAspectRatio).toBeCloseTo(281 / 449, 5);
    expect(style!.imgWidthPercent).toBeCloseTo((570 / 281) * 100, 5);
    expect(style!.imgLeftPercent).toBeCloseTo(-(144 / 281) * 100, 5);
    expect(style!.imgTopPercent).toBeCloseTo(-(167 / 449) * 100, 5);
  });

  it('produces a different, still-correct transform for a differently-shaped (landscape) map — nothing about the transform assumes the Demo Piece\'s portrait dimensions', () => {
    const bounds = { x: 20, y: 10, width: 400, height: 100 };
    const style = computeCropStyle(bounds, 500, 200);
    expect(style).not.toBeNull();
    expect(style!.containerAspectRatio).toBeCloseTo(4, 5); // 400/100
    expect(style!.imgWidthPercent).toBeCloseTo(125, 5); // 500/400*100
    expect(style!.imgLeftPercent).toBeCloseTo(-5, 5); // -(20/400)*100
    expect(style!.imgTopPercent).toBeCloseTo(-10, 5); // -(10/100)*100
  });

  it('the same transform, reapplied, always yields the same result (deterministic — safe for a future overlay to reuse)', () => {
    const bounds = { x: 12, y: 34, width: 200, height: 150 };
    const a = computeCropStyle(bounds, 640, 480);
    const b = computeCropStyle(bounds, 640, 480);
    expect(a).toEqual(b);
  });

  it('returns null rather than dividing by zero for degenerate (zero-size) bounds or image dimensions', () => {
    expect(computeCropStyle({ x: 0, y: 0, width: 0, height: 10 }, 100, 100)).toBeNull();
    expect(computeCropStyle({ x: 0, y: 0, width: 10, height: 10 }, 0, 100)).toBeNull();
  });
});
