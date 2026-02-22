import type { ReactNode } from 'react';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LessonsPage from '@/pages/LessonsPage';
import { fetchLessonFeed, fetchLessonProgress, fetchUserStats } from '@/lib/api';

vi.mock('@/components/layout/MainLayout', () => ({
  MainLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    fetchLessonFeed: vi.fn(),
    fetchLessonProgress: vi.fn(),
    fetchUserStats: vi.fn(),
  };
});

const mockedFetchLessonFeed = vi.mocked(fetchLessonFeed);
const mockedFetchLessonProgress = vi.mocked(fetchLessonProgress);
const mockedFetchUserStats = vi.mocked(fetchUserStats);

const renderPage = () =>
  render(
    <MemoryRouter>
      <LessonsPage />
    </MemoryRouter>
  );

describe('LessonsPage', () => {
  beforeEach(() => {
    mockedFetchLessonProgress.mockResolvedValue({});
    mockedFetchUserStats.mockResolvedValue({
      lessonsEnrolled: 0,
      lessonsCompleted: 0,
      currentStreak: 0,
      conceptsMastered: 0,
      hoursLearned: 0,
    });
    mockedFetchLessonFeed.mockReset();
  });

  it('shows loading status while lesson feed is pending', async () => {
    let resolveFeed: (value: { items: []; hasMore: boolean; page: number; pageSize: number }) => void = () => {};
    const pendingFeed = new Promise<{ items: []; hasMore: boolean; page: number; pageSize: number }>((resolve) => {
      resolveFeed = resolve;
    });
    mockedFetchLessonFeed.mockReturnValueOnce(pendingFeed);

    renderPage();

    expect(screen.getByText('Loading lessons...')).toBeInTheDocument();

    resolveFeed({ items: [], hasMore: false, page: 1, pageSize: 12 });
    await waitFor(() => expect(mockedFetchLessonFeed).toHaveBeenCalledTimes(1));
  });

  it('shows empty state and accessible filter controls when no lessons are returned', async () => {
    mockedFetchLessonFeed.mockResolvedValueOnce({
      items: [],
      hasMore: false,
      page: 1,
      pageSize: 12,
    });

    renderPage();

    expect(await screen.findByText('No lessons found')).toBeInTheDocument();
    expect(screen.getByLabelText('Search lessons')).toBeInTheDocument();
    expect(screen.getByLabelText('Difficulty')).toBeInTheDocument();
    expect(screen.getByLabelText('Max duration')).toBeInTheDocument();
    expect(screen.getByLabelText('Sort by')).toBeInTheDocument();
  });

  it('shows error state and retries lesson feed request', async () => {
    mockedFetchLessonFeed
      .mockRejectedValueOnce(new Error('network down'))
      .mockResolvedValueOnce({
        items: [],
        hasMore: false,
        page: 1,
        pageSize: 12,
      });

    renderPage();

    expect(await screen.findByText("Couldn't load lesson feed")).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));

    await waitFor(() => expect(mockedFetchLessonFeed).toHaveBeenCalledTimes(2));
  });
});
