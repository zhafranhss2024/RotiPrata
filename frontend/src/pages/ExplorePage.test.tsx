import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { render, screen, waitFor } from '@testing-library/react';
import ExplorePage from './ExplorePage';

const fetchBrowsingHistory = vi.fn();
const fetchFeed = vi.fn();
const fetchRecommendations = vi.fn();
const saveBrowsingHistory = vi.fn();
const searchContent = vi.fn();
const clearBrowsingHistory = vi.fn();

vi.mock('@/lib/api', () => ({
  fetchBrowsingHistory: (...args: unknown[]) => fetchBrowsingHistory(...args),
  fetchFeed: (...args: unknown[]) => fetchFeed(...args),
  fetchRecommendations: (...args: unknown[]) => fetchRecommendations(...args),
  saveBrowsingHistory: (...args: unknown[]) => saveBrowsingHistory(...args),
  searchContent: (...args: unknown[]) => searchContent(...args),
  clearBrowsingHistory: (...args: unknown[]) => clearBrowsingHistory(...args),
}));

vi.mock('@/components/layout/MainLayout', () => ({
  MainLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('@/components/feed/FeedContainer', () => ({
  FeedContainer: ({ contents }: { contents: Array<{ id: string; title: string }> }) => (
    <div data-testid="feed-container">{contents.map((content) => content.title).join(', ')}</div>
  ),
}));

describe('ExplorePage', () => {
  beforeEach(() => {
    fetchBrowsingHistory.mockResolvedValue([]);
    fetchFeed.mockResolvedValue({ items: [], hasMore: false, nextCursor: null });
    fetchRecommendations.mockResolvedValue({
      items: [
        {
          id: 'recommended-1',
          creator_id: 'creator-1',
          title: 'Recommended Video',
          description: 'Fresh lesson clip',
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
          educational_value_votes: 0,
          view_count: 0,
          is_featured: false,
          reviewed_by: null,
          reviewed_at: null,
          review_feedback: null,
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        },
      ],
    });
    searchContent.mockResolvedValue([]);
    saveBrowsingHistory.mockImplementation(() => undefined);
    clearBrowsingHistory.mockResolvedValue(undefined);
  });

  it('renders recommendations before any search', async () => {
    render(
      <MemoryRouter>
        <ExplorePage />
      </MemoryRouter>
    );

    expect(screen.getByText('Recommended for you')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Recommended Video')).toBeInTheDocument();
    });
  });
});
