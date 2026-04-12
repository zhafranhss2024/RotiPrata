import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import LessonQuizPage from "@/pages/LessonQuizPage";
import type { Lesson, LessonProgressDetail, LessonQuizState } from "@/types";

const fetchLessonById = vi.fn();
const fetchLessonProgressDetail = vi.fn();
const fetchLessonQuizState = vi.fn();
const restartLessonQuiz = vi.fn();
const submitLessonQuizAnswer = vi.fn();
const emitHeartsUpdated = vi.fn();

vi.mock("@/lib/api", () => ({
  fetchLessonById: (...args: unknown[]) => fetchLessonById(...args),
  fetchLessonProgressDetail: (...args: unknown[]) => fetchLessonProgressDetail(...args),
  fetchLessonQuizState: (...args: unknown[]) => fetchLessonQuizState(...args),
  restartLessonQuiz: (...args: unknown[]) => restartLessonQuiz(...args),
  submitLessonQuizAnswer: (...args: unknown[]) => submitLessonQuizAnswer(...args),
}));

vi.mock("@/lib/heartsEvents", () => ({
  emitHeartsUpdated: (...args: unknown[]) => emitHeartsUpdated(...args),
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

vi.mock("@/components/quiz/MatchPairsBoard", () => ({
  MatchPairsBoard: () => <div data-testid="match-pairs-board" />,
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
  progressPercentage: 100,
  completedSections: 2,
  totalSections: 2,
  totalStops: 3,
  completedStops: 2,
  currentStopId: "quiz",
  remainingStops: 1,
  quizStatus: "available",
  heartsRemaining: 5,
  heartsRefillAt: null,
  nextStopType: "quiz",
};

const quizState: LessonQuizState = {
  attemptId: "attempt-1",
  status: "in_progress",
  questionIndex: 0,
  totalQuestions: 1,
  correctCount: 0,
  earnedScore: 0,
  maxScore: 10,
  currentQuestion: {
    questionId: "question-1",
    questionType: "multiple_choice",
    questionText: "What does sigma usually mean in this lesson?",
    explanation: "It is slang.",
    points: 10,
    orderIndex: 0,
    mediaUrl: null,
    payload: {
      choices: [
        { id: "A", text: "A slang term" },
        { id: "B", text: "A breakfast food" },
      ],
    },
  },
  hearts: {
    heartsRemaining: 5,
    heartsRefillAt: null,
  },
  canAnswer: true,
  canRestart: false,
  wrongQuestionIds: [],
};

const LessonProgressProbe = () => {
  const [heartsRemaining, setHeartsRemaining] = useState<number | null>(null);

  useEffect(() => {
    void fetchLessonProgressDetail(lesson.id).then((detail: LessonProgressDetail) => {
      setHeartsRemaining(detail.heartsRemaining);
    });
  }, []);

  return <div>{`Lesson hearts: ${heartsRemaining ?? "loading"}`}</div>;
};

describe("LessonQuizPage", () => {
  beforeEach(() => {
    fetchLessonById.mockReset();
    fetchLessonProgressDetail.mockReset();
    fetchLessonQuizState.mockReset();
    restartLessonQuiz.mockReset();
    submitLessonQuizAnswer.mockReset();
    emitHeartsUpdated.mockReset();

    fetchLessonById.mockResolvedValue(lesson);
    fetchLessonProgressDetail.mockResolvedValue(progressDetail);
    fetchLessonQuizState.mockResolvedValue(quizState);

    vi.stubGlobal(
      "Audio",
      vi.fn(() => ({
        play: vi.fn().mockResolvedValue(undefined),
      }))
    );
  });

  it("renders the current question for the lesson quiz route", async () => {
    render(
      <MemoryRouter initialEntries={[`/lessons/${lesson.id}/quiz`]}>
        <Routes>
          <Route path="/lessons/:id/quiz" element={<LessonQuizPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => expect(fetchLessonQuizState).toHaveBeenCalledWith(lesson.id));
    await waitFor(() => {
      expect(screen.getByText("What does sigma usually mean in this lesson?")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "Check" })).toBeInTheDocument();
  });

  it("shows a recovery state when the quiz has no current question", async () => {
    fetchLessonQuizState.mockResolvedValue({
      ...quizState,
      currentQuestion: null,
      canRestart: true,
    } satisfies LessonQuizState);

    render(
      <MemoryRouter initialEntries={[`/lessons/${lesson.id}/quiz`]}>
        <Routes>
          <Route path="/lessons/:id/quiz" element={<LessonQuizPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("This quiz is missing its current question. Restart it or go back to the lesson.")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "Restart Quiz" })).toBeInTheDocument();
  });

  it("uses the hearts returned by the server after a wrong answer", async () => {
    submitLessonQuizAnswer.mockResolvedValue({
      attemptId: "attempt-1",
      status: "in_progress",
      correct: false,
      explanation: "Incorrect.",
      questionIndex: 1,
      totalQuestions: 1,
      correctCount: 0,
      earnedScore: 0,
      maxScore: 10,
      passed: false,
      quizCompleted: false,
      blockedByHearts: false,
      nextQuestion: null,
      hearts: {
        heartsRemaining: 5,
        heartsRefillAt: null,
      },
      wrongQuestionIds: ["question-1"],
    });

    render(
      <MemoryRouter initialEntries={[`/lessons/${lesson.id}/quiz`]}>
        <Routes>
          <Route path="/lessons/:id/quiz" element={<LessonQuizPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("What does sigma usually mean in this lesson?")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /A breakfast food/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check" }));

    await waitFor(() => {
      expect(submitLessonQuizAnswer).toHaveBeenCalled();
    });
    expect(screen.getByText("Not quite")).toBeInTheDocument();
    expect(screen.getAllByText("5").length).toBeGreaterThan(0);
    expect(emitHeartsUpdated).toHaveBeenCalledWith({
      heartsRemaining: 5,
      heartsRefillAt: null,
    });
  });

  it("keeps the deducted hearts after exiting back to the lesson page", async () => {
    fetchLessonProgressDetail
      .mockResolvedValueOnce(progressDetail)
      .mockResolvedValueOnce({
        ...progressDetail,
        heartsRemaining: 4,
      } satisfies LessonProgressDetail);
    submitLessonQuizAnswer.mockResolvedValue({
      attemptId: "attempt-1",
      status: "in_progress",
      correct: false,
      explanation: "Incorrect.",
      questionIndex: 1,
      totalQuestions: 1,
      correctCount: 0,
      earnedScore: 0,
      maxScore: 10,
      passed: false,
      quizCompleted: false,
      blockedByHearts: false,
      nextQuestion: null,
      hearts: {
        heartsRemaining: 4,
        heartsRefillAt: null,
      },
      wrongQuestionIds: ["question-1"],
    });

    render(
      <MemoryRouter initialEntries={[`/lessons/${lesson.id}/quiz`]}>
        <Routes>
          <Route path="/lessons/:id/quiz" element={<LessonQuizPage />} />
          <Route path="/lessons/:id" element={<LessonProgressProbe />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("What does sigma usually mean in this lesson?")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /A breakfast food/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check" }));

    await waitFor(() => {
      expect(screen.getByText("Not quite")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("link", { name: "Exit" }));

    await waitFor(() => {
      expect(screen.getByText("Lesson hearts: 4")).toBeInTheDocument();
    });
    expect(fetchLessonProgressDetail).toHaveBeenCalledTimes(2);
  });

  it("shows the blocked hearts screen instead of the missing-question recovery state", async () => {
    fetchLessonQuizState.mockResolvedValue({
      ...quizState,
      status: "blocked_hearts",
      currentQuestion: null,
      hearts: {
        heartsRemaining: 0,
        heartsRefillAt: "2026-04-13T10:00:00.000Z",
      },
      canAnswer: false,
      canRestart: false,
    } satisfies LessonQuizState);

    render(
      <MemoryRouter initialEntries={[`/lessons/${lesson.id}/quiz`]}>
        <Routes>
          <Route path="/lessons/:id/quiz" element={<LessonQuizPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("No hearts left")).toBeInTheDocument();
    });
    expect(screen.getByText(/Lesson content stays available/i)).toBeInTheDocument();
    expect(screen.queryByText("This quiz is missing its current question. Restart it or go back to the lesson.")).not.toBeInTheDocument();
  });
});
