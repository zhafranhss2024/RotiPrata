import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import LessonDetailPage from "@/pages/LessonDetailPage";
import type { Lesson, LessonProgressDetail, LessonSection } from "@/types";

const enrollLesson = vi.fn();
const fetchLessonById = vi.fn();
const fetchLessonProgressDetail = vi.fn();
const fetchLessonSections = vi.fn();
const saveLesson = vi.fn();

vi.mock("@/lib/api", () => ({
  enrollLesson: (...args: unknown[]) => enrollLesson(...args),
  fetchLessonById: (...args: unknown[]) => fetchLessonById(...args),
  fetchLessonProgressDetail: (...args: unknown[]) => fetchLessonProgressDetail(...args),
  fetchLessonSections: (...args: unknown[]) => fetchLessonSections(...args),
  saveLesson: (...args: unknown[]) => saveLesson(...args),
}));

vi.mock("@/components/layout/MainLayout", () => ({
  MainLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/ui/chatbot", () => ({
  default: () => null,
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

const progressDetail: LessonProgressDetail = {
  lessonId: lesson.id,
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

describe("LessonDetailPage", () => {
  beforeEach(() => {
    enrollLesson.mockReset();
    fetchLessonById.mockReset();
    fetchLessonProgressDetail.mockReset();
    fetchLessonSections.mockReset();
    saveLesson.mockReset();

    fetchLessonById.mockResolvedValue(lesson);
    fetchLessonProgressDetail.mockResolvedValue(progressDetail);
    fetchLessonSections.mockResolvedValue(sections);
  });

  it("shows that only the quiz is blocked when hearts are empty", async () => {
    render(
      <MemoryRouter initialEntries={[`/lessons/${lesson.id}`]}>
        <Routes>
          <Route path="/lessons/:id" element={<LessonDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("What Sigma Is")).toBeInTheDocument();
    });

    expect(screen.getByRole("button", { name: /No hearts left/i })).toBeDisabled();
    expect(screen.getByText("Lesson sections remain open. Only the quiz is blocked until hearts refill.")).toBeInTheDocument();
  });
});
