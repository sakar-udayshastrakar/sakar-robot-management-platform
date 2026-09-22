import { useEffect, useState } from 'react';
import { computeContentBounds, type ContentBounds } from '../../utils/mapContentBounds';

interface MapContentCropResult {
  bounds: ContentBounds | null;
  naturalWidth: number | null;
  naturalHeight: number | null;
}

const EMPTY_RESULT: MapContentCropResult = { bounds: null, naturalWidth: null, naturalHeight: null };

// Decodes the already-fetched map image (the same `blob:` object URL
// useRobotMapImage already produced — this makes no additional network
// request) into an offscreen canvas purely to read its pixel data, then
// runs the pure computeContentBounds over those pixels. The canvas is never
// appended to the DOM; it exists only long enough to call getImageData.
//
// Deliberately fails soft, never hard: if canvas 2D pixel access is
// unavailable (jsdom's test environment; a browser that blocks canvas
// image extraction), the image fails to decode, or the image turns out to
// be blank, this resolves to `{ bounds: null, ... }` rather than throwing —
// callers must treat null as "show the full, uncropped image" and never
// invent a fallback bounding box.
export function useMapContentCrop(imageUrl: string | null): MapContentCropResult {
  const [result, setResult] = useState<MapContentCropResult>(EMPTY_RESULT);

  useEffect(() => {
    if (!imageUrl) {
      setResult(EMPTY_RESULT);
      return;
    }
    let cancelled = false;
    setResult(EMPTY_RESULT);

    const img = new Image();
    img.onload = () => {
      if (cancelled) return;
      try {
        const canvas = document.createElement('canvas');
        canvas.width = img.naturalWidth;
        canvas.height = img.naturalHeight;
        const ctx = canvas.getContext('2d');
        if (!ctx || canvas.width === 0 || canvas.height === 0) {
          return; // leaves EMPTY_RESULT — uncropped fallback
        }
        ctx.drawImage(img, 0, 0);
        const { data } = ctx.getImageData(0, 0, canvas.width, canvas.height);
        const bounds = computeContentBounds(data, canvas.width, canvas.height, { padding: 12 });
        if (cancelled) return;
        setResult({ bounds, naturalWidth: canvas.width, naturalHeight: canvas.height });
      } catch {
        // getImageData can throw (e.g. a tainted canvas) — fall back
        // silently to the uncropped image rather than surfacing an error
        // for what is a purely cosmetic enhancement.
      }
    };
    img.onerror = () => {
      // The visible <img> already surfaces load failures via
      // useRobotMapImage's own error state; this is a silent no-op here.
    };
    img.src = imageUrl;

    return () => {
      cancelled = true;
    };
  }, [imageUrl]);

  return result;
}
