import { useEffect, useState, type ReactNode } from 'react';

export interface CarouselSlide {
  key: string;
  content: ReactNode;
}

interface CarouselProps {
  slides: CarouselSlide[];
  autoplayMs?: number;
  ariaLabel?: string;
}

// A plain, dependency-free carousel — this app has no carousel/slider
// library, and adding one for a single hero banner wasn't worth the weight.
// Real prev/next navigation, clickable dot indicators, and optional
// autoplay (paused on hover), all keyboard/screen-reader accessible.
export function Carousel({ slides, autoplayMs, ariaLabel = 'Carousel' }: CarouselProps) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (!autoplayMs || paused || slides.length <= 1) return;
    const timer = setInterval(() => setIndex((i) => (i + 1) % slides.length), autoplayMs);
    return () => clearInterval(timer);
  }, [autoplayMs, paused, slides.length]);

  useEffect(() => {
    if (index >= slides.length) setIndex(0);
  }, [slides.length, index]);

  if (slides.length === 0) return null;

  function go(delta: number) {
    setIndex((i) => (i + delta + slides.length) % slides.length);
  }

  return (
    <div
      className="sakar-carousel"
      role="region"
      aria-label={ariaLabel}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
    >
      <div className="sakar-carousel-viewport">
        {slides.map((slide, i) => (
          <div key={slide.key} className="sakar-carousel-slide" style={{ display: i === index ? 'block' : 'none' }} aria-hidden={i !== index}>
            {slide.content}
          </div>
        ))}
        {slides.length > 1 && (
          <>
            <button type="button" className="sakar-carousel-arrow sakar-carousel-arrow--prev" aria-label="Previous slide" onClick={() => go(-1)}>
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6" /></svg>
            </button>
            <button type="button" className="sakar-carousel-arrow sakar-carousel-arrow--next" aria-label="Next slide" onClick={() => go(1)}>
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6" /></svg>
            </button>
          </>
        )}
      </div>
      {slides.length > 1 && (
        <div className="sakar-carousel-dots" role="tablist" aria-label="Slide selector">
          {slides.map((slide, i) => (
            <button
              key={slide.key}
              type="button"
              role="tab"
              aria-selected={i === index}
              aria-label={`Go to slide ${i + 1}`}
              className={`sakar-carousel-dot${i === index ? ' sakar-carousel-dot--active' : ''}`}
              onClick={() => setIndex(i)}
            />
          ))}
        </div>
      )}
    </div>
  );
}
