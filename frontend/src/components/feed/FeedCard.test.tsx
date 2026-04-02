import { fireEvent, render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { Content } from '@/types';
import { FeedCard } from './FeedCard';

vi.mock('./FeedVideoPlayer', () => ({
  FeedVideoPlayer: () => <div data-testid="feed-video-player" />,
}));

const buildContent = (): Content => ({
  id: 'content-1',
  creator_id: 'creator-1',
  title: 'Swipe test video',
  description: 'Swipe left to learn more.',
  content_type: 'video',
  media_url: 'https://cdn.example.com/video.mp4',
  thumbnail_url: 'https://cdn.example.com/video.jpg',
  category_id: null,
  status: 'approved',
  learning_objective: null,
  origin_explanation: null,
  definition_literal: null,
  definition_used: null,
  older_version_reference: null,
  educational_value_votes: 10,
  view_count: 0,
  is_featured: false,
  reviewed_by: null,
  reviewed_at: null,
  review_feedback: null,
  created_at: '2026-03-25T00:00:00.000Z',
  updated_at: '2026-03-25T00:00:00.000Z',
});

describe('FeedCard', () => {
  it('opens learn more on a right-to-left swipe', () => {
    const onLearnMoreClick = vi.fn();
    const { container } = render(
      <FeedCard content={buildContent()} commentCount={0} onLearnMoreClick={onLearnMoreClick} />
    );

    const card = container.firstElementChild;
    expect(card).not.toBeNull();

    fireEvent.touchStart(card as Element, {
      touches: [{ clientX: 260, clientY: 200 }],
    });
    fireEvent.touchEnd(card as Element, {
      changedTouches: [{ clientX: 140, clientY: 210 }],
    });

    expect(onLearnMoreClick).toHaveBeenCalledTimes(1);
  });

  it('does not treat action button taps as a swipe gesture', () => {
    const onLearnMoreClick = vi.fn();
    const onSave = vi.fn();

    const { getByText } = render(
      <FeedCard
        content={buildContent()}
        commentCount={0}
        onLearnMoreClick={onLearnMoreClick}
        onSave={onSave}
      />
    );

    const saveButton = getByText('Save').closest('button');
    expect(saveButton).not.toBeNull();

    fireEvent.touchStart(saveButton as Element, {
      touches: [{ clientX: 280, clientY: 560 }],
    });
    fireEvent.touchEnd(saveButton as Element, {
      changedTouches: [{ clientX: 282, clientY: 562 }],
    });
    fireEvent.click(saveButton as Element);

    expect(onLearnMoreClick).not.toHaveBeenCalled();
    expect(onSave).toHaveBeenCalledTimes(1);
  });
});
