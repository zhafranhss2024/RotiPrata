import { describe, expect, it, vi, beforeEach } from 'vitest';
import type { ReactNode } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LessonsPage from '@/pages/LessonsPage';

vi.mock('@/components/layout/MainLayout', () => ({
  MainLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock('@/lib/api', () => ({
  fetchLessons: vi.fn(),
  fetchLessonProgress: vi.fn(),
  fetchUserStats: vi.fn(),
  searchLessons: vi.fn(),
}));

import { fetchLessonProgress, fetchLessons, fetchUserStats } from '@/lib/api';

const renderPage = () =>
  render(
    <MemoryRouter>
      <LessonsPage />
    </MemoryRouter>
  );

describe('LessonsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('renders lesson cards from API', async () => {
    vi.mocked(fetchLessons).mockResolvedValue([
      {
        id: 'lesson-1',
        created_by: 'u1',
        title: 'Brainrot Origins',
        description: 'Understand where it began.',
        header_media_url: null,
        summary: null,
        learning_objectives: null,
        estimated_minutes: 10,
        xp_reward: 100,
        badge_name: null,
        badge_icon_url: null,
        difficulty_level: 1,
        is_published: true,
        completion_count: 12,
        origin_content: null,
        definition_content: null,
        usage_examples: null,
        lore_content: null,
        evolution_content: null,
        comparison_content: null,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      },
    ]);
    vi.mocked(fetchLessonProgress).mockResolvedValue({ 'lesson-1': 40 });
    vi.mocked(fetchUserStats).mockResolvedValue({
      lessonsEnrolled: 1,
      lessonsCompleted: 0,
      currentStreak: 1,
      conceptsMastered: 3,
      hoursLearned: 0.8,
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getAllByText('Brainrot Origins').length).toBeGreaterThan(0);
    });
    expect(screen.getByText('40% complete')).toBeInTheDocument();
  });

  it('shows empty state when no lessons are returned', async () => {
    vi.mocked(fetchLessons).mockResolvedValue([]);
    vi.mocked(fetchLessonProgress).mockResolvedValue({});
    vi.mocked(fetchUserStats).mockResolvedValue({
      lessonsEnrolled: 0,
      lessonsCompleted: 0,
      currentStreak: 0,
      conceptsMastered: 0,
      hoursLearned: 0,
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('No lessons matched your filters.')).toBeInTheDocument();
    });
  });
});
