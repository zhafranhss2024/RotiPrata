import type {
  AdminLessonDraftResponse,
  AdminPublishLessonResult,
  AdminQuizQuestionDraft,
  AdminStepValidationResult,
  AppRole,
  Category,
  Content,
  ContentFlag,
  Lesson,
  LessonHubResponse,
  LessonHeartsStatus,
  LessonProgressDetail,
  LessonQuizAnswerPayload,
  LessonQuizAnswerResult,
  LessonQuizState,
  LessonSection,
  Profile,
  Quiz,
  QuizQuestion,
  ThemePreference,
  UserAchievement,
  WizardStepKey,
} from "@/types";
import {
  apiDelete,
  apiGet,
  apiPost,
  apiPatch,
  apiPut,
  apiUpload,
  shouldAutoFallbackToMocks,
  shouldUseMocks,
} from "@/lib/apiClient";
import { mockContents } from "@/mocks/content";
import { mockCategories } from "@/mocks/categories";
import {
  mockLessonDetail,
  mockLessonHub,
  mockLessonProgressDetailByLessonId,
  mockLessonProgressByLessonId,
  mockLessons,
  mockLessonSections,
  mockLessonStats,
} from "@/mocks/lessons";
import { mockProfile, mockAchievements } from "@/mocks/profile";
import { mockAdminStats, mockFlags, mockModerationQueue } from "@/mocks/admin";
import {
  mockAiSuggestions,
  mockBrowsingHistory,
  mockSearchResults,
  mockTrendingContent,
} from "@/mocks/explore";
import { mockAuthUser, mockRoles } from "@/mocks/auth";

export type FeedResponse = {
  items: Content[];
  hasMore: boolean;
};

export type LessonFeedDifficultyFilter = "all" | "beginner" | "intermediate" | "advanced";
export type LessonFeedDurationFilter = "all" | "short" | "medium" | "long";
export type LessonFeedSort = "popular" | "newest" | "shortest" | "highest_xp";

export type LessonFeedParams = {
  query?: string;
  difficulty?: LessonFeedDifficultyFilter;
  duration?: LessonFeedDurationFilter;
  sort?: LessonFeedSort;
  page?: number;
  pageSize?: number;
};

export type LessonFeedResponse = {
  items: Lesson[];
  hasMore: boolean;
  page: number;
  pageSize: number;
};

export type AdminQuizQuestionTypeMeta = {
  type: string;
  label: string;
  optionsExample: string;
  answerExample: string;
};

export type AdminStepSavePayload = {
  lesson?: Record<string, unknown>;
  questions?: AdminQuizQuestionDraft[];
};

type CompleteLessonSectionResponse = {
  progress: LessonProgressDetail;
};

export type SearchResult = {
  id: string;
  type: "content" | "lesson" | "profile";
  title: string;
  snippet?: string;
};

export type GetHistoryDTO = {
  id: string;
  item_id: string;
  title?: string | null;
  content_id?: string | null;
  lesson_id?: string | null;
  viewed_at: string;
}

export type UserStats = {
  lessonsEnrolled: number;
  lessonsCompleted: number;
  currentStreak?: number;
  conceptsMastered: number;
  hoursLearned?: number;
  quizzesTaken?: number;
  averageScore?: number;
};

export type AuthSessionResponse = {
  accessToken?: string;
  refreshToken?: string;
  tokenType?: string;
  expiresIn?: number;
  userId?: string;
  email?: string;
  requiresEmailConfirmation?: boolean;
  message?: string;
};

export type DisplayNameAvailabilityResponse = {
  available: boolean;
  normalized: string;
};

export type ContentMediaStartResponse = {
  contentId: string;
  status: string;
  pollUrl: string;
};

export type ContentMediaStatusResponse = {
  status: string;
  hlsUrl?: string | null;
  thumbnailUrl?: string | null;
  errorMessage?: string | null;
};

export type ContentComment = {
  id: string;
  contentId: string;
  userId: string;
  parentId?: string | null;
  body: string;
  author: string;
  createdAt: string;
  updatedAt?: string | null;
};

const withMockFallback = async <T>(
  label: string,
  fallback: () => T,
  request: () => Promise<T>,
  options?: { allowAutoFallback?: boolean }
): Promise<T> => {
  if (shouldUseMocks()) {
    return fallback();
  }
  try {
    return await request();
  } catch (error) {
    const allowAutoFallback = options?.allowAutoFallback ?? true;
    if (shouldAutoFallbackToMocks() && allowAutoFallback) {
      console.warn(`[mocks] ${label} -> falling back to dummy data`, error);
      return fallback();
    }
    throw error;
  }
};

const buildMockLessonProgressDetail = (lessonId: string): LessonProgressDetail => {
  const existing = mockLessonProgressDetailByLessonId[lessonId];
  if (existing) {
    return existing;
  }
  return {
    status: "not_started",
    progressPercentage: 0,
    currentSection: null,
    completedSections: 0,
    totalSections: mockLessonSections.length,
    nextSectionId: mockLessonSections[0]?.id ?? null,
    isEnrolled: false,
    totalStops: mockLessonSections.length + 1,
    completedStops: 0,
    currentStopId: null,
    remainingStops: mockLessonSections.length + 1,
    quizStatus: "locked",
    heartsRemaining: 5,
    heartsRefillAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    nextStopType: "section",
  };
};

const DEFAULT_LESSON_FEED_PAGE = 1;
const DEFAULT_LESSON_FEED_PAGE_SIZE = 12;
const MAX_LESSON_FEED_PAGE_SIZE = 50;

const normalizeLessonFeedPage = (page?: number) =>
  page && page > 0 ? Math.floor(page) : DEFAULT_LESSON_FEED_PAGE;

const normalizeLessonFeedPageSize = (pageSize?: number) => {
  if (!pageSize || pageSize < 1) {
    return DEFAULT_LESSON_FEED_PAGE_SIZE;
  }
  return Math.min(MAX_LESSON_FEED_PAGE_SIZE, Math.floor(pageSize));
};

const filterLessonsLocally = (lessons: Lesson[], params: LessonFeedParams): Lesson[] => {
  const query = params.query?.trim().toLowerCase();
  const difficulty = params.difficulty ?? "all";
  const duration = params.duration ?? "all";
  const sort = params.sort ?? "popular";

  let filtered = lessons.filter((lesson) => {
    if (query) {
      const title = lesson.title?.toLowerCase() ?? "";
      const description = lesson.description?.toLowerCase() ?? "";
      const summary = lesson.summary?.toLowerCase() ?? "";
      if (!title.includes(query) && !description.includes(query) && !summary.includes(query)) {
        return false;
      }
    }

    if (difficulty === "beginner" && lesson.difficulty_level !== 1) return false;
    if (difficulty === "intermediate" && lesson.difficulty_level !== 2) return false;
    if (difficulty === "advanced" && lesson.difficulty_level !== 3) return false;

    if (duration === "short" && lesson.estimated_minutes > 10) return false;
    if (duration === "medium" && (lesson.estimated_minutes < 11 || lesson.estimated_minutes > 20)) return false;
    if (duration === "long" && lesson.estimated_minutes < 21) return false;

    return true;
  });

  filtered = [...filtered];
  switch (sort) {
    case "newest":
      filtered.sort(
        (a, b) =>
          new Date(b.created_at ?? 0).getTime() - new Date(a.created_at ?? 0).getTime()
      );
      break;
    case "shortest":
      filtered.sort((a, b) => a.estimated_minutes - b.estimated_minutes);
      break;
    case "highest_xp":
      filtered.sort((a, b) => b.xp_reward - a.xp_reward);
      break;
    case "popular":
    default:
      filtered.sort((a, b) => b.completion_count - a.completion_count);
      break;
  }

  return filtered;
};

const buildLessonFeedPath = (params: LessonFeedParams = {}) => {
  const page = normalizeLessonFeedPage(params.page);
  const pageSize = normalizeLessonFeedPageSize(params.pageSize);
  const query = params.query?.trim();
  const searchParams = new URLSearchParams();

  if (query) {
    searchParams.set("query", query);
  }
  if (params.difficulty && params.difficulty !== "all") {
    searchParams.set("difficulty", params.difficulty);
  }
  if (params.duration && params.duration !== "all") {
    searchParams.set("duration", params.duration);
  }
  if (params.sort && params.sort !== "popular") {
    searchParams.set("sort", params.sort);
  }
  searchParams.set("page", String(page));
  searchParams.set("pageSize", String(pageSize));

  const queryString = searchParams.toString();
  return queryString ? `/lessons/feed?${queryString}` : "/lessons/feed";
};

export const fetchFeed = (page = 1) =>
  withMockFallback(
    "feed",
    () => ({ items: mockContents, hasMore: false }),
    () => apiGet<FeedResponse>(`/feed?page=${page}`)
  );

export const fetchTrendingContent = () =>
  withMockFallback("trending", () => mockTrendingContent, () => apiGet(`/trending`));

export const searchContent = (query: string, filter?: string | null) =>
  withMockFallback(
    "search",
    // () => mockSearchResults,
    () => [],
    () => apiGet<SearchResult[]>(`/search?query=${encodeURIComponent(query)}&filter=${filter || ""}`)
  );

export const saveBrowsingHistory = (contentId?: string, lessonId?: string, title?: string) => {
  const body = { contentId: contentId ?? null, lessonId: lessonId ?? null, title: title ?? null };
  apiPost<void>(`/users/me/history`, body);
};

export const fetchRecommendations = () =>
  withMockFallback("recommendations", () => mockAiSuggestions, () => apiGet(`/recommendations`));

export const fetchBrowsingHistory = () =>
  // withMockFallback("history", () => mockBrowsingHistory, () => apiGet(`/users/me/history`));
  apiGet<GetHistoryDTO[]>(`/users/me/history`);

export const clearBrowsingHistory = () => apiDelete<void>(`/users/me/history`);

export const fetchLessonFeed = (params: LessonFeedParams = {}) =>
  withMockFallback(
    "lesson-feed",
    () => {
      const page = normalizeLessonFeedPage(params.page);
      const pageSize = normalizeLessonFeedPageSize(params.pageSize);
      const filtered = filterLessonsLocally(mockLessons, params);
      const offset = (page - 1) * pageSize;
      const pageRows = filtered.slice(offset, offset + pageSize + 1);
      const hasMore = pageRows.length > pageSize;
      const items = hasMore ? pageRows.slice(0, pageSize) : pageRows;
      return { items, hasMore, page, pageSize };
    },
    () => apiGet<LessonFeedResponse>(buildLessonFeedPath(params))
  );

export const fetchLessons = () =>
  withMockFallback("lessons", () => mockLessons, () => apiGet<Lesson[]>(`/lessons`));

export const fetchLessonHub = () =>
  withMockFallback(
    "lesson-hub",
    () => mockLessonHub,
    () => apiGet<LessonHubResponse>(`/lessons/hub`),
    { allowAutoFallback: false }
  );

export const searchLessons = (query: string) =>
  withMockFallback(
    "lesson-search",
    () => mockLessons,
    () => apiGet<Lesson[]>(`/lessons/search?q=${encodeURIComponent(query)}`)
  );

export const fetchLessonById = (lessonId: string) =>
  withMockFallback(
    "lesson-detail",
    () => mockLessonDetail,
    () => apiGet<Lesson>(`/lessons/${lessonId}`),
    { allowAutoFallback: false }
  );

export const fetchLessonSections = (lessonId: string) =>
  withMockFallback(
    "lesson-sections",
    () => mockLessonSections,
    () => apiGet<LessonSection[]>(`/lessons/${lessonId}/sections`),
    { allowAutoFallback: false }
  );

export const fetchLessonProgressDetail = (lessonId: string) =>
  withMockFallback(
    "lesson-progress-detail",
    () => buildMockLessonProgressDetail(lessonId),
    () => apiGet<LessonProgressDetail>(`/lessons/${lessonId}/progress`),
    { allowAutoFallback: false }
  );

export const fetchLessonProgress = () =>
  withMockFallback(
    "lesson-progress",
    () => mockLessonProgressByLessonId,
    () => apiGet<Record<string, number>>(`/users/me/lessons/progress`),
    { allowAutoFallback: false }
  );

export const fetchUserStats = () =>
  withMockFallback("user-stats", () => mockLessonStats, () => apiGet<UserStats>(`/users/me/stats`));

export const fetchUserHearts = () =>
  withMockFallback(
    "user-hearts",
    () => ({
      heartsRemaining: 5,
      heartsRefillAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    } as LessonHeartsStatus),
    () => apiGet<LessonHeartsStatus>(`/users/me/hearts`),
    { allowAutoFallback: false }
  );

export const enrollLesson = (lessonId: string) => apiPost<void>(`/lessons/${lessonId}/enroll`);

export const saveLesson = (lessonId: string) => apiPost<void>(`/lessons/${lessonId}/save`);

export const updateLessonProgress = (lessonId: string, progress: number) =>
  apiPut<void>(`/lessons/${lessonId}/progress`, { progress });

export const completeLessonSection = (lessonId: string, sectionId: string) =>
  withMockFallback(
    "lesson-complete-section",
    () => {
      const current = buildMockLessonProgressDetail(lessonId);
      const sectionIndex = mockLessonSections.findIndex((section) => section.id === sectionId);
      if (sectionIndex < 0) {
        throw new Error("Lesson section not found");
      }
      if (sectionIndex > current.completedSections) {
        throw new Error("Complete earlier sections first");
      }
      if (sectionIndex < current.completedSections) {
        return current;
      }

      const totalSections = current.totalSections || mockLessonSections.length;
      const completedSections = Math.min(totalSections, current.completedSections + 1);
      const totalStops = totalSections + 1;
      const completedStops = Math.min(totalStops, completedSections);
      const progressPercentage =
        totalStops > 0 ? Math.round((completedStops * 100) / totalStops) : 0;
      const status = "in_progress";
      const currentSection = mockLessonSections[completedSections - 1]?.id ?? null;
      const nextSectionId =
        completedSections < totalSections ? mockLessonSections[completedSections]?.id ?? null : null;
      const nextStopType = completedSections < totalSections ? "section" : "quiz";
      const currentStopId =
        completedSections >= totalSections
          ? "quiz"
          : (mockLessonSections[completedSections - 1]?.id ?? null);

      const updated: LessonProgressDetail = {
        status,
        progressPercentage,
        currentSection,
        completedSections,
        totalSections,
        nextSectionId,
        isEnrolled: true,
        totalStops,
        completedStops,
        currentStopId,
        remainingStops: Math.max(0, totalStops - completedStops),
        quizStatus: completedSections < totalSections ? "locked" : "available",
        heartsRemaining: current.heartsRemaining ?? 5,
        heartsRefillAt: current.heartsRefillAt ?? new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
        nextStopType,
      };
      mockLessonProgressDetailByLessonId[lessonId] = updated;
      mockLessonProgressByLessonId[lessonId] = updated.progressPercentage;
      return updated;
    },
    async () => {
      const response = await apiPost<CompleteLessonSectionResponse>(
        `/lessons/${lessonId}/sections/${sectionId}/complete`
      );
      return response.progress;
    },
    { allowAutoFallback: false }
  );

export const fetchLessonQuizState = (lessonId: string) =>
  withMockFallback(
    "lesson-quiz-state",
    () => ({
      attemptId: "mock-attempt-1",
      status: "in_progress",
      questionIndex: 0,
      totalQuestions: 1,
      correctCount: 0,
      earnedScore: 0,
      maxScore: 10,
      currentQuestion: {
        questionId: "mock-q1",
        questionType: "multiple_choice",
        questionText: "What is the best description of this lesson topic?",
        payload: {
          choices: [
            { id: "A", text: "A greeting" },
            { id: "B", text: "A slang term used in context" },
            { id: "C", text: "A place" },
            { id: "D", text: "A number" },
          ],
        },
        explanation: "This lesson focuses on slang meaning and context.",
        points: 10,
        orderIndex: 0,
        mediaUrl: null,
      },
      hearts: {
        heartsRemaining: 5,
        heartsRefillAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
      },
      canAnswer: true,
      canRestart: false,
      wrongQuestionIds: [],
    } as LessonQuizState),
    () => apiGet<LessonQuizState>(`/lessons/${lessonId}/quiz/state`),
    { allowAutoFallback: false }
  );

export const submitLessonQuizAnswer = (lessonId: string, payload: LessonQuizAnswerPayload) =>
  withMockFallback(
    "lesson-quiz-answer",
    () => ({
      attemptId: payload.attemptId,
      status: "passed",
      correct: true,
      explanation: "Correct.",
      questionIndex: 1,
      totalQuestions: 1,
      correctCount: 1,
      earnedScore: 10,
      maxScore: 10,
      passed: true,
      quizCompleted: true,
      blockedByHearts: false,
      nextQuestion: null,
      hearts: {
        heartsRemaining: 5,
        heartsRefillAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
      },
      wrongQuestionIds: [],
    } as LessonQuizAnswerResult),
    () => apiPost<LessonQuizAnswerResult>(`/lessons/${lessonId}/quiz/answer`, payload),
    { allowAutoFallback: false }
  );

export const restartLessonQuiz = (lessonId: string, mode: "wrong_only" | "full" = "wrong_only") =>
  withMockFallback(
    "lesson-quiz-restart",
    () => ({
      attemptId: "mock-attempt-2",
      status: "in_progress",
      questionIndex: 0,
      totalQuestions: 1,
      correctCount: 0,
      earnedScore: 0,
      maxScore: 10,
      currentQuestion: {
        questionId: "mock-q1",
        questionType: "multiple_choice",
        questionText: "What is the best description of this lesson topic?",
        payload: {
          choices: [
            { id: "A", text: "A greeting" },
            { id: "B", text: "A slang term used in context" },
            { id: "C", text: "A place" },
            { id: "D", text: "A number" },
          ],
        },
        explanation: "This lesson focuses on slang meaning and context.",
        points: 10,
        orderIndex: 0,
        mediaUrl: null,
      },
      hearts: {
        heartsRemaining: 5,
        heartsRefillAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
      },
      canAnswer: true,
      canRestart: false,
      wrongQuestionIds: [],
    } as LessonQuizState),
    () => apiPost<LessonQuizState>(`/lessons/${lessonId}/quiz/restart?mode=${encodeURIComponent(mode)}`),
    { allowAutoFallback: false }
  );

export const fetchCategories = () =>
  withMockFallback("categories", () => mockCategories, () => apiGet<Category[]>(`/categories`));

export const fetchTags = (query: string) =>
  withMockFallback(
    "tags",
    () => [],
    () => apiGet<string[]>(`/tags?query=${encodeURIComponent(query)}`)
  );

export const startContentMediaUpload = (formData: FormData) =>
  apiUpload<ContentMediaStartResponse>(`/content/media/start`, formData);

export const startContentMediaLink = (sourceUrl: string) =>
  apiPost<ContentMediaStartResponse>(`/content/media/start-link`, { sourceUrl });

export const fetchContentMediaStatus = (contentId: string) =>
  apiGet<ContentMediaStatusResponse>(`/content/${contentId}/media`);

export const updateDraftContent = (contentId: string, payload: Record<string, unknown>) =>
  apiPatch<Content>(`/content/${contentId}`, payload);

export const submitContent = (contentId: string, payload: Record<string, unknown>) =>
  apiPost<Content>(`/content/${contentId}/submit`, payload);

export const fetchContentQuiz = (contentId: string) =>
  withMockFallback("content-quiz", () => null, () => apiGet<Quiz>(`/content/${contentId}/quiz`));

export const trackContentView = (contentId: string) => apiPost<void>(`/content/${contentId}/view`);

export const likeContent = (contentId: string) => apiPost<void>(`/content/${contentId}/like`);

export const unlikeContent = (contentId: string) => apiDelete<void>(`/content/${contentId}/like`);

// Deprecated compatibility wrapper while older components still call voteContent.
export const voteContent = (contentId: string, _voteType: string) =>
  apiPost<void>(`/content/${contentId}/like`);

export const saveContent = (contentId: string) => apiPost<void>(`/content/${contentId}/save`);

export const unsaveContent = (contentId: string) => apiDelete<void>(`/content/${contentId}/save`);

export const shareContent = (contentId: string) => apiPost<void>(`/content/${contentId}/share`);

export const flagContent = (contentId: string, reason: string, description?: string) =>
  apiPost<void>(`/content/${contentId}/flag`, { reason, description });

export const fetchContentComments = (contentId: string, limit = 50, offset = 0) =>
  apiGet<ContentComment[]>(`/content/${contentId}/comments?limit=${limit}&offset=${offset}`);

export const postContentComment = (contentId: string, body: string, parentId?: string | null) =>
  apiPost<ContentComment>(`/content/${contentId}/comments`, { body, parentId: parentId ?? null });

export const fetchProfile = () =>
  withMockFallback("profile", () => mockProfile, () => apiGet<Profile>(`/users/me`));

export const fetchAchievements = () =>
  withMockFallback(
    "achievements",
    () => mockAchievements,
    () => apiGet<UserAchievement[]>(`/users/me/achievements`)
  );

export const fetchThemePreference = () =>
  withMockFallback(
    "theme",
    () => mockProfile.theme_preference,
    () => apiGet<ThemePreference>(`/users/me/preferences`)
  );

export const updateThemePreference = (theme: ThemePreference) =>
  apiPut<void>(`/users/me/preferences`, { theme_preference: theme });

export const loginUser = (email: string, password: string) =>
  withMockFallback(
    "auth-login",
    () => ({
      accessToken: "mock-token",
      refreshToken: "mock-refresh",
      tokenType: "bearer",
      expiresIn: 3600,
    }),
    () => apiPost<AuthSessionResponse>(`/auth/login`, { email, password }),
    { allowAutoFallback: false }
  );

export const registerUser = (
  email: string,
  password: string,
  displayName: string,
  isGenAlpha?: boolean
) =>
  withMockFallback(
    "auth-register",
    () => ({
      accessToken: "mock-token",
      refreshToken: "mock-refresh",
      tokenType: "bearer",
      expiresIn: 3600,
      userId: "mock-user",
      email,
    }),
    () => apiPost<AuthSessionResponse>(`/auth/register`, { email, password, displayName, isGenAlpha }),
    { allowAutoFallback: false }
  );

export const logoutUser = () => apiPost<void>(`/auth/logout`);

export const requestPasswordReset = (email: string, redirectTo?: string) =>
  withMockFallback(
    "auth-forgot-password",
    () => undefined as void,
    () => apiPost<void>(`/auth/forgot-password`, { email, redirectTo }),
    { allowAutoFallback: false }
  );

export const resetPassword = (accessToken: string, newPassword: string) =>
  withMockFallback(
    "auth-reset-password",
    () => undefined as void,
    () => apiPost<void>(`/auth/reset-password`, { accessToken, password: newPassword }),
    { allowAutoFallback: false }
  );

export const buildGoogleOAuthUrl = (redirectTo: string) => {
  const base = (import.meta.env.VITE_API_BASE_URL ?? "/api").replace(/\/$/, "");
  const target = `${base}/auth/login/google?redirectTo=${encodeURIComponent(redirectTo)}`;
  return target;
};

export const checkDisplayNameAvailability = (displayName: string) =>
  withMockFallback(
    "auth-display-name-available",
    () => ({ available: true, normalized: displayName.trim().toLowerCase() }),
    () => apiGet<DisplayNameAvailabilityResponse>(`/auth/username-available?displayName=${encodeURIComponent(displayName)}`),
    { allowAutoFallback: false }
  );

export const fetchCurrentUser = () =>
  withMockFallback("auth-me", () => mockAuthUser, () => apiGet<Profile>(`/auth/me`));

export const fetchUserRoles = () =>
  withMockFallback("auth-roles", () => mockRoles, () => apiGet<AppRole[]>(`/users/me/roles`));

export const fetchAdminStats = () =>
  withMockFallback("admin-stats", () => mockAdminStats, () => apiGet(`/admin/stats`));

const normalizeModerationQueueTags = <T extends { content?: Record<string, unknown> | null }>(
  items: T[]
): T[] =>
  items.map((item) => {
    const content = item.content;
    if (!content || typeof content !== "object") {
      return item;
    }

    const existingTags = Array.isArray(content.tags)
      ? content.tags
          .map((value) => String(value ?? "").trim())
          .filter((value) => value.length > 0)
      : [];

    const relatedTags = Array.isArray(content.content_tags)
      ? content.content_tags
          .map((row) =>
            row && typeof row === "object" ? String((row as Record<string, unknown>).tag ?? "").trim() : ""
          )
          .filter((value) => value.length > 0)
      : [];

    const merged = Array.from(new Set([...existingTags, ...relatedTags]));
    return {
      ...item,
      content: {
        ...content,
        tags: merged,
      },
    };
  });

export const fetchModerationQueue = () =>
  withMockFallback(
    "admin-moderation",
    () => mockModerationQueue,
    () =>
      apiGet<(typeof mockModerationQueue)[number][]>(`/admin/moderation-queue`).then(
        normalizeModerationQueueTags
      )
  );

export const fetchContentFlags = () =>
  withMockFallback("admin-flags", () => mockFlags, () => apiGet<ContentFlag[]>(`/admin/flags`));

export const approveContent = (contentId: string) => apiPut<void>(`/admin/content/${contentId}/approve`);

export const updateAdminContent = (contentId: string, payload: Record<string, unknown>) =>
  apiPut<Content>(`/admin/content/${contentId}`, payload);

export const rejectContent = (contentId: string, feedback?: string) =>
  apiPut<void>(`/admin/content/${contentId}/reject`, { feedback });

export const resolveFlag = (flagId: string) => apiPut<void>(`/admin/flags/${flagId}/resolve`);


export const fetchAdminLessons = () =>
  withMockFallback("admin-lessons", () => mockLessons, () => apiGet<Lesson[]>(`/admin/lessons`), { allowAutoFallback: false });

export const fetchAdminLessonById = (lessonId: string) =>
  withMockFallback(
    "admin-lesson-detail",
    () => mockLessonDetail,
    () => apiGet<Lesson>(`/admin/lessons/${lessonId}`),
    { allowAutoFallback: false }
  );

export const fetchAdminLessonQuizQuestions = (lessonId: string) =>
  withMockFallback(
    "admin-lesson-quiz",
    () => [],
    () => apiGet<QuizQuestion[]>(`/admin/lessons/${lessonId}/quiz`),
    { allowAutoFallback: false }
  );

export const fetchAdminQuizQuestionTypes = () =>
  withMockFallback(
    "admin-quiz-question-types",
    () => [
      {
        type: "multiple_choice",
        label: "Single-select choices (A/B/C/D)",
        optionsExample: '{ "choices": { "A": "", "B": "" } }',
        answerExample: "A",
      },
      {
        type: "true_false",
        label: "Boolean true/false question",
        optionsExample: "{}",
        answerExample: "true",
      },
      {
        type: "cloze",
        label: "Fill blank(s) with provided choices",
        optionsExample: '{ "blankOptions": { "blank1": { "A": "", "B": "" } } }',
        answerExample: '{ "blank1": "A" }',
      },
      {
        type: "word_bank",
        label: "Build answer from ordered token list",
        optionsExample: '{ "tokens": [ { "id": "t1", "text": "hello" }, { "id": "t2", "text": "world" } ] }',
        answerExample: "[\"t1\",\"t2\"]",
      },
      {
        type: "conversation",
        label: "Choose best reply for each turn",
        optionsExample: '{ "turns": [ { "id": "turn_1", "prompt": "", "replies": [ { "id": "r1", "text": "" } ] } ] }',
        answerExample: '{ "turn_1": "r1" }',
      },
      {
        type: "match_pairs",
        label: "Match left and right items",
        optionsExample: '{ "left": [ { "id": "l1", "text": "" } ], "right": [ { "id": "r1", "text": "" } ] }',
        answerExample: '{ "l1": "r1" }',
      },
      {
        type: "short_text",
        label: "Free text answer compared server-side",
        optionsExample: '{ "placeholder": "Type answer", "minLength": 1, "maxLength": 120 }',
        answerExample: '{"accepted":["example answer"]}',
      },
    ] as AdminQuizQuestionTypeMeta[],
    () => apiGet<AdminQuizQuestionTypeMeta[]>(`/admin/quiz/question-types`),
    { allowAutoFallback: false }
  );

const toAdminQuestionPayload = (question: AdminQuizQuestionDraft) => {
  const payload = { ...question } as Record<string, unknown>;
  delete payload.clientId;
  return payload;
};

export const createAdminLessonDraft = (payload: Record<string, unknown> = {}) =>
  withMockFallback(
    "admin-lesson-draft-create",
    () => {
      const lessonId = `mock-lesson-${Date.now()}`;
      return {
        lessonId,
        lessonSnapshot: {
          id: lessonId,
          title: String(payload.title ?? "Untitled Lesson"),
          description: "",
          summary: "",
          learning_objectives: [],
          estimated_minutes: 15,
          xp_reward: 100,
          badge_name: "",
          difficulty_level: 1,
          origin_content: "",
          definition_content: "",
          usage_examples: [],
          lore_content: "",
          evolution_content: "",
          comparison_content: "",
          is_published: false,
        },
        completeness: {
          basics: false,
          content: false,
          quiz_setup: false,
          quiz_builder: false,
          review_publish: false,
        },
      } as AdminLessonDraftResponse;
    },
    () => apiPost<AdminLessonDraftResponse>(`/admin/lessons/draft`, payload),
    { allowAutoFallback: false }
  );

export const saveAdminLessonDraftStep = (
  lessonId: string,
  step: WizardStepKey,
  payload: AdminStepSavePayload
) =>
  withMockFallback(
    "admin-lesson-draft-step-save",
    () => ({
      step,
      stepValid: true,
      errors: [],
      lessonSnapshot: payload.lesson ?? {},
      completeness: {
        basics: true,
        content: true,
        quiz_setup: true,
        quiz_builder: true,
        review_publish: true,
      },
    } as AdminStepValidationResult),
    () =>
      apiPut<AdminStepValidationResult>(
        `/admin/lessons/${lessonId}/draft/step/${step}`,
        {
          lesson: payload.lesson ?? {},
          questions: (payload.questions ?? []).map(toAdminQuestionPayload),
        }
      ),
    { allowAutoFallback: false }
  );

export const publishAdminLesson = (
  lessonId: string,
  payload: AdminStepSavePayload
) =>
  withMockFallback(
    "admin-lesson-publish",
    () => ({
      success: true,
      firstInvalidStep: null,
      errors: [],
      lessonSnapshot: payload.lesson ?? {},
    } as AdminPublishLessonResult),
    () =>
      apiPost<AdminPublishLessonResult>(`/admin/lessons/${lessonId}/publish`, {
        lesson: payload.lesson ?? {},
        questions: (payload.questions ?? []).map(toAdminQuestionPayload),
      }),
    { allowAutoFallback: false }
  );

export const updateLesson = (lessonId: string, payload: Record<string, unknown>) =>
  apiPut<Lesson>(`/admin/lessons/${lessonId}`, payload);

export const deleteLesson = (lessonId: string) => apiDelete<void>(`/admin/lessons/${lessonId}`);

export const createLesson = (payload: Record<string, unknown>) => apiPost<Lesson>(`/admin/lessons`, payload);

export const createLessonQuiz = (lessonId: string, questions: Partial<QuizQuestion>[]) =>
  apiPost<Quiz>(`/admin/lessons/${lessonId}/quiz`, { questions });

export const replaceAdminLessonQuiz = (lessonId: string, questions: Partial<QuizQuestion>[]) =>
  apiPut<QuizQuestion[]>(`/admin/lessons/${lessonId}/quiz`, { questions });


