import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import LessonSectionPage from "@/pages/LessonSectionPage";
import type { Lesson, LessonProgressDetail, LessonSection } from "@/types";

const completeLessonSection = vi.fn();
const fetchLessonById = vi.fn();
const fetchLessonProgressDetail = vi.fn();
const fetchLessonSections = vi.fn();

vi.mock("@/lib/api", () => ({
  completeLessonSection: (...args: unknown[]) => completeLessonSection(...args),
  fetchLessonById: (...args: unknown[]) => fetchLessonById(...args),
  fetchLessonProgressDetail: (...args: unknown[]) => fetchLessonProgressDetail(...args),
  fetchLessonSections: (...args: unknown[]) => fetchLessonSections(...args),
}));

vi.mock("@/components/layout/MainLayout", () => ({
  MainLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/ui/chatbot", () => ({
  default: () => null,
}));

vi.mock("@/components/lesson/LessonMediaDisplay", () => ({
  LessonMediaDisplay: () => <div data-testid="lesson-media-display" />,
}));

const lesson: Lesson = {
  id: "030a6d7f-097c-4a87-9821-2c2fdcf32b81",
  title: "What Sigma Is",
  description: "Quiz lesson",
  category_id: null,
  difficulty_level: 1,
  estimated_duration_minutes: 5,
  xp_reward: 10,
  hearts_cost: 1,
  is_published: true,
  is_active: true,
  is_featured: false,
  creator_id: "creator-1",
  summary: "Understand sigma",
  header_media_url: null,
  badge_name: null,
  badge_icon_url: null,
  learning_objective: null,
  status: "published",
  created_at: "2026-04-01T00:00:00.000Z",
  updated_at: "2026-04-01T00:00:00.000Z",
};

const sections: LessonSection[] = [
  {
    id: "origin",
    title: "Origin",
    content: "Origin content",
    blocks: [],
    order_index: 0,
    duration_minutes: 3,
    completed: true,
  },
  {
    id: "comparison",
    title: "Comparison",
    content: "Comparison content",
    blocks: [],
    order_index: 1,
    duration_minutes: 3,
    completed: true,
  },
];

const progressDetail: LessonProgressDetail = {
  status: "in_progress",
  progressPercentage: 100,
  currentSection: "comparison",
  completedSections: 2,
  totalSections: 2,
  nextSectionId: null,
  isEnrolled: true,
  totalStops: 3,
  completedStops: 2,
  currentStopId: "quiz",
  remainingStops: 1,
  quizStatus: "blocked_hearts",
  heartsRemaining: 0,
  heartsRefillAt: "2026-04-13T10:00:00.000Z",
  nextStopType: "quiz",
};

describe("LessonSectionPage", () => {
  beforeEach(() => {
    completeLessonSection.mockReset();
    fetchLessonById.mockReset();
    fetchLessonProgressDetail.mockReset();
    fetchLessonSections.mockReset();

    fetchLessonById.mockResolvedValue(lesson);
    fetchLessonProgressDetail.mockResolvedValue(progressDetail);
    fetchLessonSections.mockResolvedValue(sections);
  });

  it("shows blocked quiz messaging while keeping the section readable", async () => {
    render(
      <MemoryRouter initialEntries={[`/lessons/${lesson.id}/comparison`]}>
        <Routes>
          <Route path="/lessons/:id/:sectionId" element={<LessonSectionPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Comparison" })).toBeInTheDocument();
    });

    expect(screen.getByText("Comparison content")).toBeInTheDocument();
    expect(screen.getByText(/Quiz is blocked because hearts are empty/i)).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Quiz Blocked" })[0]).toBeDisabled();
  });
});
