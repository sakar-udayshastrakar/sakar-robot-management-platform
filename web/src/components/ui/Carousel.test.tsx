import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Carousel } from './Carousel';

const slides = [
  { key: 'a', content: <span>Slide A</span> },
  { key: 'b', content: <span>Slide B</span> },
  { key: 'c', content: <span>Slide C</span> },
];

describe('Carousel', () => {
  it('renders only the active slide visibly, starting with the first', () => {
    render(<Carousel slides={slides} />);

    expect(screen.getByText('Slide A').closest('.sakar-carousel-slide')).toHaveStyle({ display: 'block' });
    expect(screen.getByText('Slide B').closest('.sakar-carousel-slide')).toHaveStyle({ display: 'none' });
  });

  it('advances to the next slide on the next-arrow click', async () => {
    render(<Carousel slides={slides} />);

    await userEvent.click(screen.getByRole('button', { name: 'Next slide' }));

    expect(screen.getByText('Slide B').closest('.sakar-carousel-slide')).toHaveStyle({ display: 'block' });
    expect(screen.getByText('Slide A').closest('.sakar-carousel-slide')).toHaveStyle({ display: 'none' });
  });

  it('wraps around to the last slide on prev from the first', async () => {
    render(<Carousel slides={slides} />);

    await userEvent.click(screen.getByRole('button', { name: 'Previous slide' }));

    expect(screen.getByText('Slide C').closest('.sakar-carousel-slide')).toHaveStyle({ display: 'block' });
  });

  it('jumps to a slide when its dot is clicked', async () => {
    render(<Carousel slides={slides} />);

    await userEvent.click(screen.getByRole('tab', { name: 'Go to slide 3' }));

    expect(screen.getByText('Slide C').closest('.sakar-carousel-slide')).toHaveStyle({ display: 'block' });
  });

  it('renders nothing for zero slides, and no arrows/dots for a single slide', () => {
    const { container, rerender } = render(<Carousel slides={[]} />);
    expect(container).toBeEmptyDOMElement();

    rerender(<Carousel slides={[slides[0]]} />);
    expect(screen.queryByRole('button', { name: 'Next slide' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab')).not.toBeInTheDocument();
  });
});
