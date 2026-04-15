import type {
  AdminAnalytics,
  AdminContentFlagReportPage,
  AdminContentFlagGroup,
  AdminFlagReview,
  AdminUserDetail,
  AdminLessonDraftResponse,
  AdminPublishLessonResult,
  AdminQuizQuestionDraft,
  AdminStepValidationResult,
  AdminUserSummary,
  AppRole,
  Category,
  Content,
  LeaderboardResponse,
  Lesson,
  LessonMediaStartResponse,
  LessonMediaStatusResponse,
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
  UserBadge,
  WizardStepKey,
  ProfileContentCollection,
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
import { buildMockLeaderboardResponse, mockProfile, mockProfileBadges, mockProfileCollections } from "@/mocks/profile";
import { mockAdminAnalytics, mockAdminStats, mockAdminUserDetails, mockAdminUsers, mockFlags, mockModerationQueue } from "@/mocks/admin";
import {
  mockBrowsingHistory,
  mockSearchResults,
  mockTrendingContent,
} from "@/mocks/explore";
import { mockAuthUser, mockRoles } from "@/mocks/auth";
import { buildSimilarContentList, SIMILAR_CONTENT_LIMIT } from "@/lib/similarContent";

export type ChatResponse = {
  reply: string;
};

export type ChatbotMessageDTO = {
  role: string;
  message: string;
  timestamp: string;
}

export type FeedResponse = {
  items: Content[];
  hasMore: boolean;
  nextCursor?: string | null;
};

export type RecommendationResponse = {
  items: Content[];
};

export type ContentQuizSubmitResult = {
  score: number;
  maxScore: number;
  percentage: number;
  passed: boolean;
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
  query: string;
  searched_at: string;
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

export type LoginStreakTouchResponse = {
  currentStreak: number;
  longestStreak: number;
  lastActivityDate: string;
  touchedToday: boolean;
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

export type ContentPlaybackEventPayload = {
  startupMs?: number | null;
  stallCount?: number | null;
  stalledMs?: number | null;
  watchMs?: number | null;
  playSuccess?: boolean | null;
  autoplayBlockedCount?: number | null;
  networkType?: string | null;
  userAgent?: string | null;
};

export type FlagByDate = {
  date: string;
  count: number;
}

export type AvgReviewTimeDTO = {
  avgReviewTime: number;
}

export type TopFlagUser = {
  user_id: string;
  display_name: string | null;
  flag_count: number;
};

export type TopFlagContentItem = {
  content_id: string;
  content_title: string;
  flag_count: number;
};

export type AuditLogItem = {
  admin_id: string;
  admin_name: string;
  action: string;
  target_id: string;
  target_type: string;
  description: string;
  created_at: string;
};

export type AdminFlagReviewQueryOptions = {
  month?: string;
  year?: string;
};

const getMockContentById = (contentId: string) =>
  mockContents.find((content) => content.id === contentId) ?? mockContents[0];

const lessonCreatedAtValue = (lesson: Pick<Lesson, "created_at">) => {
  const timestamp = new Date(lesson.created_at ?? "").getTime();
  return Number.isFinite(timestamp) ? timestamp : 0;
};

const sortLessonsForDisplay = (lessons: Lesson[]) =>
  [...lessons].sort((left, right) => {
    const createdAtDiff = lessonCreatedAtValue(right) - lessonCreatedAtValue(left);
    if (createdAtDiff !== 0) {
      return createdAtDiff;
    }
    return (left.title ?? "").localeCompare(right.title ?? "");
  });

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

export const fetchFeed = (cursor: string | null = null, limit = 20) =>
  withMockFallback(
    "feed",
    () => ({ items: mockContents, hasMore: false, nextCursor: null }),
    () => {
      const params = new URLSearchParams();
      params.set("limit", String(limit));
      if (cursor) {
        params.set("cursor", cursor);
      }
      return apiGet<FeedResponse>(`/feed?${params.toString()}`);
    }
  );

export const fetchTrendingContent = () =>
  withMockFallback("trending", () => mockTrendingContent, () => apiGet(`/trending`));

export { SIMILAR_CONTENT_LIMIT };

export const searchContent = (query: string, filter?: string | null) =>
  withMockFallback(
    "search",
    () => [],
    () => apiGet<SearchResult[]>(`/search-results?query=${encodeURIComponent(query)}&filter=${filter || ""}`)
  );

export const sendChatMessage = (message: string) =>
  apiPost<ChatResponse>(`/chat`, message);

export const getChatHistory = () =>
  apiGet<ChatbotMessageDTO[]>(`/users/me/chat`);

export const startNewChat = () =>
  apiDelete<void>(`/users/me/chat`);

export const saveBrowsingHistory = (query: string) => {
  const body = { query, searched_at: new Date().toISOString() }
  apiPost<void>(`/users/me/history`, body);
};

export const fetchRecommendations = (limit = 24) =>
  withMockFallback(
    "recommendations",
    () => ({ items: mockContents.slice(0, Math.min(limit, mockContents.length)) }),
    () => apiGet<RecommendationResponse>(`/recommendations?limit=${limit}`)
  );

export const fetchBrowsingHistory = () =>
  apiGet<GetHistoryDTO[]>(`/users/me/history`);

export const clearBrowsingHistory = (id: string) => apiDelete<void>(`/users/me/history/${id}`);

export const fetchLessonFeed = (params: LessonFeedParams = {}) =>
  withMockFallback(
    "lesson-feed",
    () => {
      const page = normalizeLessonFeedPage(params.page);
      const pageSize = normalizeLessonFeedPageSize(params.pageSize);
      const filtered = filterLessonsLocally(
        mockLessons.filter((lesson) => lesson.is_published),
        params
      );
      const offset = (page - 1) * pageSize;
      const pageRows = filtered.slice(offset, offset + pageSize + 1);
      const hasMore = pageRows.length > pageSize;
      const items = hasMore ? pageRows.slice(0, pageSize) : pageRows;
      return { items, hasMore, page, pageSize };
    },
    () => apiGet<LessonFeedResponse>(buildLessonFeedPath(params))
  );

export const fetchLessons = () =>
  withMockFallback(
    "lessons",
    () => mockLessons.filter((lesson) => lesson.is_published),
    () => apiGet<Lesson[]>(`/lessons`)
  );

export const fetchLessonHub = () =>
  withMockFallback(
    "lesson-hub",
    () => {
      const publishedLessons = mockLessons.filter((lesson) => lesson.is_published);
      const categories = mockCategories
        .slice()
        .sort((left, right) => left.name.localeCompare(right.name))
        .map((category) => {
          const lessons = sortLessonsForDisplay(
            publishedLessons.filter((lesson) => lesson.category_id === category.id)
          )
            .map((lesson) => ({
              lessonId: lesson.id,
              title: lesson.title,
              summary: lesson.summary,
              difficultyLevel: lesson.difficulty_level,
              estimatedMinutes: lesson.estimated_minutes,
              xpReward: lesson.xp_reward,
              completionCount: lesson.completion_count,
              progressPercentage: mockLessonProgressByLessonId[lesson.id] ?? 0,
              completed: (mockLessonProgressByLessonId[lesson.id] ?? 0) >= 100,
              current: (mockLessonProgressByLessonId[lesson.id] ?? 0) > 0,
              visuallyLocked: false,
            }));

          return {
            categoryId: category.id,
            name: category.name,
            type: category.type,
            color: category.color,
            isVirtual: false,
            lessons,
          };
        });

      const uncategorizedLessons = sortLessonsForDisplay(
        publishedLessons.filter((lesson) => lesson.category_id === null)
      )
        .map((lesson) => ({
          lessonId: lesson.id,
          title: lesson.title,
          summary: lesson.summary,
          difficultyLevel: lesson.difficulty_level,
          estimatedMinutes: lesson.estimated_minutes,
          xpReward: lesson.xp_reward,
          completionCount: lesson.completion_count,
          progressPercentage: mockLessonProgressByLessonId[lesson.id] ?? 0,
          completed: (mockLessonProgressByLessonId[lesson.id] ?? 0) >= 100,
          current: (mockLessonProgressByLessonId[lesson.id] ?? 0) > 0,
          visuallyLocked: false,
        }));

      return {
        categories: uncategorizedLessons.length > 0
          ? [
              ...categories,
              {
                categoryId: null,
                name: "Uncategorized",
                type: "other",
                color: "#6b7280",
                isVirtual: true,
                lessons: uncategorizedLessons,
              },
            ]
          : categories,
        summary: {
          totalLessons: publishedLessons.length,
          completedLessons: publishedLessons.filter((lesson) => (mockLessonProgressByLessonId[lesson.id] ?? 0) >= 100).length,
          currentStreak: mockLessonHub.summary.currentStreak,
        },
      };
    },
    () => apiGet<LessonHubResponse>(`/lessons/hub`),
    { allowAutoFallback: false }
  );

export const searchLessons = (query: string) =>
  withMockFallback(
    "lesson-search",
    () =>
      mockLessons.filter((lesson) => {
        if (!lesson.is_published) {
          return false;
        }
        const normalizedQuery = query.trim().toLowerCase();
        if (!normalizedQuery) {
          return true;
        }
        const title = lesson.title?.toLowerCase() ?? "";
        const description = lesson.description?.toLowerCase() ?? "";
        const summary = lesson.summary?.toLowerCase() ?? "";
        return title.includes(normalizedQuery) || description.includes(normalizedQuery) || summary.includes(normalizedQuery);
      }),
    () => apiGet<Lesson[]>(`/lessons/search-results?q=${encodeURIComponent(query)}`)
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

export const startLessonMediaUpload = (lessonId: string, formData: FormData) =>
  apiUpload<LessonMediaStartResponse>(`/admin/lessons/${lessonId}/media-uploads`, formData);

export const startLessonMediaLink = (
  lessonId: string,
  payload: { sourceUrl: string; mediaKind: "image" | "gif" | "video" }
) =>
  apiPost<LessonMediaStartResponse>(`/admin/lessons/${lessonId}/media-link-imports`, payload);

export const fetchLessonMediaStatus = (lessonId: string, assetId: string) =>
  apiGet<LessonMediaStatusResponse>(`/admin/lessons/${lessonId}/media/${assetId}`);

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

export const enrollLesson = (lessonId: string) => apiPut<void>(`/lessons/${lessonId}/enrollment`);

export const saveLesson = (lessonId: string) => apiPut<void>(`/lessons/${lessonId}/saved`);

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
        `/lessons/${lessonId}/sections/${sectionId}/completion`
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
    () => apiPost<LessonQuizAnswerResult>(`/lessons/${lessonId}/quiz/answers`, payload),
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
    () => apiPost<LessonQuizState>(`/lessons/${lessonId}/quiz-attempts?mode=${encodeURIComponent(mode)}`),
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
  apiUpload<ContentMediaStartResponse>(`/content/uploads`, formData);

export const startContentMediaLink = (sourceUrl: string) =>
  apiPost<ContentMediaStartResponse>(`/content/link-imports`, { sourceUrl });

export const fetchContentMediaStatus = (contentId: string) =>
  apiGet<ContentMediaStatusResponse>(`/content/${contentId}/media`);

export const fetchContentById = (contentId: string) =>
  withMockFallback(
    "content-by-id",
    () => getMockContentById(contentId),
    () => apiGet<Content>(`/content/${contentId}`)
  );

export const fetchSimilarContent = (contentId: string, limit = SIMILAR_CONTENT_LIMIT) =>
  withMockFallback(
    "content-similar",
    () => buildSimilarContentList(mockContents, contentId, limit),
    () => apiGet<Content[]>(`/content/${contentId}/similar?limit=${Math.min(SIMILAR_CONTENT_LIMIT, Math.max(1, Math.floor(limit)))}`)
  );

export const updateDraftContent = (contentId: string, payload: Record<string, unknown>) =>
  apiPatch<Content>(`/content/${contentId}`, payload);

export const submitContent = (contentId: string, payload: Record<string, unknown>) =>
  apiPost<Content>(`/content/${contentId}/submission`, payload);

export const fetchContentQuiz = (contentId: string) =>
  withMockFallback("content-quiz", () => null, () => apiGet<Quiz>(`/content/${contentId}/quiz`));

export const submitContentQuiz = (
  contentId: string,
  payload: { answers: Record<string, string>; timeTakenSeconds?: number | null }
) =>
  apiPost<ContentQuizSubmitResult>(`/content/${contentId}/quiz-submissions`, payload);

export const fetchAdminContentQuiz = (contentId: string) =>
  withMockFallback(
    "admin-content-quiz",
    () => [],
    () => apiGet<QuizQuestion[]>(`/admin/content/${contentId}/quiz`),
    { allowAutoFallback: false }
  );

export const saveAdminContentQuiz = (contentId: string, questions: Partial<QuizQuestion>[]) =>
  apiPut<QuizQuestion[]>(`/admin/content/${contentId}/quiz`, { questions });

export const trackContentView = (contentId: string) => apiPost<void>(`/content/${contentId}/views`);

export const trackContentPlaybackEvent = (contentId: string, payload: ContentPlaybackEventPayload) =>
  apiPost<void>(`/content/${contentId}/playback-events`, payload);

export const likeContent = (contentId: string) => apiPost<void>(`/content/${contentId}/likes`);

export const unlikeContent = (contentId: string) => apiDelete<void>(`/content/${contentId}/likes`);

export const saveContent = (contentId: string) => apiPost<void>(`/content/${contentId}/saves`);

export const unsaveContent = (contentId: string) => apiDelete<void>(`/content/${contentId}/saves`);

export const shareContent = (contentId: string) => apiPost<void>(`/content/${contentId}/shares`);

export const flagContent = (contentId: string, reason: string, description?: string) =>
  apiPost<void>(`/content/${contentId}/flags`, { reason, description });

export const fetchContentComments = (contentId: string, limit = 50, offset = 0) =>
  apiGet<ContentComment[]>(`/content/${contentId}/comments?limit=${limit}&offset=${offset}`);

export const postContentComment = (contentId: string, body: string, parentId?: string | null) =>
  apiPost<ContentComment>(`/content/${contentId}/comments`, { body, parentId: parentId ?? null });

export const deleteContentComment = (contentId: string, commentId: string) =>
  apiDelete<void>(`/content/${contentId}/comments/${commentId}`);

export const fetchProfile = () =>
  withMockFallback("profile", () => mockProfile, () => apiGet<Profile>(`/users/me`));

export const updateProfile = (payload: { display_name?: string; is_gen_alpha?: boolean }) =>
  apiPut<Profile>(`/users/me`, payload);

export const fetchUserBadges = () =>
  withMockFallback(
    "badges",
    () => mockProfileBadges,
    () => apiGet<UserBadge[]>(`/users/me/badges`)
  );

export const fetchLeaderboard = (page = 1, pageSize = 20, query = "") =>
  withMockFallback(
    "leaderboard",
    () => buildMockLeaderboardResponse(page, pageSize, query),
    () =>
      apiGet<LeaderboardResponse>(
        `/users/leaderboard?page=${Math.max(1, Math.floor(page))}&pageSize=${Math.max(1, Math.floor(pageSize))}&query=${encodeURIComponent(query)}`
      )
  );

export const fetchProfileContentCollection = (collection: ProfileContentCollection) =>
  withMockFallback(
    `profile-content-${collection}`,
    () => mockProfileCollections[collection],
    () => apiGet<Content[]>(`/users/me/content?collection=${encodeURIComponent(collection)}`)
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
    () => apiPost<AuthSessionResponse>(`/auth/sessions`, { email, password }),
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
    () => apiPost<AuthSessionResponse>(`/auth/registrations`, { email, password, displayName, isGenAlpha }),
    { allowAutoFallback: false }
  );

export const logoutUser = () => apiDelete<void>(`/auth/session`);

export const touchLoginStreak = (timezone?: string | null) =>
  apiPut<LoginStreakTouchResponse>(`/auth/login-streak`, timezone ? { timezone } : {});

export const requestPasswordReset = (email: string, redirectTo?: string) =>
  withMockFallback(
    "auth-forgot-password",
    () => undefined as void,
    () => apiPost<void>(`/auth/password-reset-requests`, { email, redirectTo }),
    { allowAutoFallback: false }
  );

export const resetPassword = (accessToken: string, newPassword: string) =>
  withMockFallback(
    "auth-reset-password",
    () => undefined as void,
    () => apiPut<void>(`/auth/password`, { accessToken, password: newPassword }),
    { allowAutoFallback: false }
  );

export const checkDisplayNameAvailability = (displayName: string) =>
  withMockFallback(
    "auth-display-name-available",
    () => ({ available: true, normalized: displayName.trim().toLowerCase() }),
    () => apiGet<DisplayNameAvailabilityResponse>(`/auth/display-name-availability?displayName=${encodeURIComponent(displayName)}`),
    { allowAutoFallback: false }
  );

export const fetchCurrentUser = () =>
  withMockFallback("auth-me", () => mockAuthUser, () => apiGet<Profile>(`/users/me`));

export const fetchUserRoles = () =>
  withMockFallback("auth-roles", () => mockRoles, () => apiGet<AppRole[]>(`/users/me/roles`));

export const fetchAdminStats = () =>
  withMockFallback("admin-stats", () => mockAdminStats, () => apiGet(`/admin/stats`));

export const fetchAdminUsers = () =>
  withMockFallback(
    "admin-users",
    () => mockAdminUsers,
    () => apiGet<AdminUserSummary[]>(`/admin/users`),
    { allowAutoFallback: false }
  );

export const updateAdminUserRole = (userId: string, role: AppRole) =>
  withMockFallback(
    "admin-user-role",
    () => {
      const target = mockAdminUsers.find((user) => user.userId === userId);
      if (!target) {
        throw new Error("User not found");
      }
      const isDemotingAdmin = target.roles.includes("admin") && role !== "admin";
      const adminCount = mockAdminUsers.filter((user) => user.roles.includes("admin")).length;
      if (isDemotingAdmin && userId === mockAuthUser.user_id) {
        throw new Error("You cannot remove your own admin role");
      }
      if (isDemotingAdmin && adminCount <= 1) {
        throw new Error("At least one admin is required");
      }
      target.roles = [role];
      const detail = mockAdminUserDetails[userId];
      if (detail) {
        detail.summary.roles = [role];
      }
      return target;
    },
    () => apiPut<AdminUserSummary>(`/admin/users/${userId}/role`, { role }),
    { allowAutoFallback: false }
  );

export const fetchAdminUserDetail = (userId: string) =>
  withMockFallback(
    "admin-user-detail",
    () => mockAdminUserDetails[userId] ?? {
      summary: mockAdminUsers.find((user) => user.userId === userId) ?? mockAdminUsers[0],
      suspendedUntil: null,
      activity: {
        postedContentCount: 0,
        likedContentCount: 0,
        savedContentCount: 0,
        commentCount: 0,
        enrolledLessonCount: 0,
        completedLessonCount: 0,
        badgeCount: 0,
        browsingCount: 0,
        searchCount: 0,
        chatMessageCount: 0,
      },
      postedContent: [],
      likedContent: [],
      savedContent: [],
      comments: [],
      lessonProgress: [],
      badges: [],
      browsingHistory: [],
      searchHistory: [],
      chatHistory: [],
    } satisfies AdminUserDetail,
    () => apiGet<AdminUserDetail>(`/admin/users/${userId}`),
    { allowAutoFallback: false }
  );

export const updateAdminUserStatus = (userId: string, status: "active" | "suspended") =>
  withMockFallback(
    "admin-user-status",
    () => {
      const target = mockAdminUsers.find((user) => user.userId === userId);
      if (!target) {
        throw new Error("User not found");
      }
      target.status = status;
      return target;
    },
    () => apiPut<AdminUserSummary>(`/admin/users/${userId}/status`, { status }),
    { allowAutoFallback: false }
  );

export const resetAdminUserLessonProgress = (userId: string, lessonId: string) =>
  apiDelete<void>(`/admin/users/${userId}/lessons/${lessonId}/progress`);

export const fetchAdminAnalytics = () =>
  withMockFallback(
    "admin-analytics",
    () => mockAdminAnalytics,
    () => apiGet<AdminAnalytics>(`/admin/analytics`),
    { allowAutoFallback: false }
  );

const normalizeNestedContentTags = <T extends { content?: Record<string, unknown> | null }>(
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

const normalizeFlagReview = (review: AdminFlagReview): AdminFlagReview => {
  if (!review.content) {
    return review;
  }

  const [normalized] = normalizeNestedContentTags([{ content: review.content }]);
  return {
    ...review,
    content: normalized.content as Content,
  };
};

const buildAdminFlagReviewQuery = (options?: AdminFlagReviewQueryOptions) => {
  const params = new URLSearchParams();
  if (options?.month) {
    params.set("month", options.month);
  }
  if (options?.year) {
    params.set("year", options.year);
  }
  const query = params.toString();
  return query ? `?${query}` : "";
};

const buildMockAdminFlagReview = (
  contentId: string,
  options?: AdminFlagReviewQueryOptions
): AdminFlagReview => {
  const pendingOnly = !options?.month || !options?.year;
  const filteredFlags = mockFlags.filter((flag) => {
    if (flag.content_id !== contentId) {
      return false;
    }
    if (pendingOnly) {
      return flag.status === "pending";
    }
    const createdAt = flag.created_at ? new Date(flag.created_at) : null;
    if (!createdAt || Number.isNaN(createdAt.getTime())) {
      return false;
    }
    return (
      createdAt.getUTCFullYear().toString() === options.year &&
      String(createdAt.getUTCMonth() + 1).padStart(2, "0") === options.month
    );
  });

  if (filteredFlags.length === 0) {
    throw new Error("Flag review not found");
  }

  const allFlagsForContent = mockFlags.filter((flag) => flag.content_id === contentId);
  const actionableFlagId =
    allFlagsForContent.find((flag) => flag.status === "pending")?.id ?? null;
  const latestFlag = filteredFlags[0];

  return normalizeFlagReview({
    contentId,
    content: latestFlag.content,
    status: actionableFlagId ? "pending" : latestFlag.status ?? null,
    reportCount: filteredFlags.reduce((sum, flag) => sum + Math.max(flag.report_count ?? 0, 0), 0),
    notesCount: filteredFlags.reduce((sum, flag) => sum + Math.max(flag.notes_count ?? 0, 0), 0),
    reasons: Array.from(
      new Set(filteredFlags.flatMap((flag) => flag.reasons ?? []).map((reason) => String(reason).trim()).filter(Boolean))
    ),
    latestReportAt: latestFlag.created_at ?? null,
    actionableFlagId,
    canResolve: actionableFlagId !== null,
    canTakeDown: actionableFlagId !== null,
  });
};

export const fetchModerationQueue = () =>
  withMockFallback(
    "admin-moderation",
    () => mockModerationQueue,
    () =>
      apiGet<(typeof mockModerationQueue)[number][]>(`/admin/moderation-queue`).then(
        normalizeNestedContentTags
      )
  );

export const fetchContentFlags = () =>
  withMockFallback(
    "admin-flags",
    () => mockFlags,
    () => apiGet<AdminContentFlagGroup[]>(`/admin/flags`).then(normalizeNestedContentTags)
  );

export const fetchFlagReports = (flagId: string, page = 1, query = "") =>
  withMockFallback(
    "admin-flag-reports",
    () => {
      const flag = mockFlags.find((item) => item.id === flagId);
      const normalizedQuery = query.trim().toLowerCase().replace(/^@/, "");
      const filtered = (flag?.reports ?? []).filter((report) => {
        if (!normalizedQuery) {
          return true;
        }
        const displayName = report.reporter?.display_name?.toLowerCase() ?? "";
        return displayName.includes(normalizedQuery);
      });
      const pageSize = 5;
      const start = Math.max(0, page - 1) * pageSize;
      const items = filtered.slice(start, start + pageSize);
      return {
        items,
        page,
        page_size: pageSize,
        has_next: start + pageSize < filtered.length,
        query,
      } satisfies AdminContentFlagReportPage;
    },
    () =>
      apiGet<AdminContentFlagReportPage>(
        `/admin/flags/${flagId}/reports?page=${Math.max(1, Math.floor(page))}&query=${encodeURIComponent(query)}`
      )
  );

export const fetchAdminFlagReview = (
  contentId: string,
  options?: AdminFlagReviewQueryOptions
) =>
  withMockFallback(
    "admin-flag-review",
    () => buildMockAdminFlagReview(contentId, options),
    () =>
      apiGet<AdminFlagReview>(
        `/admin/flags/content/${contentId}/review${buildAdminFlagReviewQuery(options)}`
      ).then(normalizeFlagReview)
  );

export const fetchAdminFlagReviewReports = (
  contentId: string,
  page = 1,
  query = "",
  options?: AdminFlagReviewQueryOptions
) =>
  withMockFallback(
    "admin-flag-review-reports",
    () => {
      const normalizedQuery = query.trim().toLowerCase().replace(/^@/, "");
      const pendingOnly = !options?.month || !options?.year;
      const matchingFlags = mockFlags.filter((flag) => {
        if (flag.content_id !== contentId) {
          return false;
        }
        if (pendingOnly) {
          return flag.status === "pending";
        }
        const createdAt = flag.created_at ? new Date(flag.created_at) : null;
        if (!createdAt || Number.isNaN(createdAt.getTime())) {
          return false;
        }
        return (
          createdAt.getUTCFullYear().toString() === options.year &&
          String(createdAt.getUTCMonth() + 1).padStart(2, "0") === options.month
        );
      });
      const filteredReports = matchingFlags
        .flatMap((flag) => flag.reports ?? [])
        .filter((report) => {
          if (!normalizedQuery) {
            return true;
          }
          const displayName = report.reporter?.display_name?.toLowerCase() ?? "";
          const reportedBy = report.reported_by?.toLowerCase?.() ?? "";
          return displayName.includes(normalizedQuery) || reportedBy.includes(normalizedQuery);
        })
        .sort((left, right) => {
          const leftTime = new Date(left.created_at).getTime();
          const rightTime = new Date(right.created_at).getTime();
          return rightTime - leftTime;
        });
      const pageSize = 5;
      const start = Math.max(0, page - 1) * pageSize;
      const items = filteredReports.slice(start, start + pageSize);
      return {
        items,
        page,
        page_size: pageSize,
        has_next: start + pageSize < filteredReports.length,
        query,
      } satisfies AdminContentFlagReportPage;
    },
    () =>
      apiGet<AdminContentFlagReportPage>(
        `/admin/flags/content/${contentId}/reports?page=${Math.max(1, Math.floor(page))}&query=${encodeURIComponent(query)}${buildAdminFlagReviewQuery(options).replace("?", "&")}`
      )
  );

export const approveContent = (contentId: string) =>
  apiPut<void>(`/admin/content/${contentId}/review`, { status: "approved" });

export const updateAdminContent = (contentId: string, payload: Record<string, unknown>) =>
  apiPut<Content>(`/admin/content/${contentId}`, payload);

export const rejectContent = (contentId: string, feedback?: string) =>
  apiPut<void>(`/admin/content/${contentId}/review`, { status: "rejected", feedback });

export const resolveFlag = (flagId: string) =>
  apiPut<void>(`/admin/flags/${flagId}/resolution`, { status: "resolved" });

export const takeDownFlag = (flagId: string, feedback?: string) =>
  apiPut<void>(`/admin/flags/${flagId}/resolution`, { status: "taken_down", feedback });


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

const canonicalMultipleChoiceKey = (index: number) => String.fromCharCode(65 + index);

const normalizeMultipleChoiceQuestionPayload = (question: AdminQuizQuestionDraft) => {
  const options = (question.options ?? {}) as Record<string, unknown>;
  const rawChoices = ((options.choices as Record<string, unknown> | undefined) ?? options) as Record<string, unknown>;
  const nonEmptyChoices = Object.entries(rawChoices)
    .map(([choiceId, text]) => [choiceId.trim().toUpperCase(), String(text ?? "").trim()] as const)
    .filter(([, text]) => text.length > 0)
    .sort(([leftId], [rightId]) => leftId.localeCompare(rightId, undefined, { numeric: true, sensitivity: "base" }));

  const remappedChoices = Object.fromEntries(
    nonEmptyChoices.map(([_, text], index) => [canonicalMultipleChoiceKey(index), text])
  );

  const normalizedCorrectAnswer = (() => {
    const existingCorrect = question.correct_answer?.trim().toUpperCase();
    if (!existingCorrect) {
      return question.correct_answer;
    }
    const oldIndex = nonEmptyChoices.findIndex(([choiceId]) => choiceId === existingCorrect);
    return oldIndex >= 0 ? canonicalMultipleChoiceKey(oldIndex) : question.correct_answer;
  })();

  return {
    ...question,
    options: {
      ...options,
      choices: remappedChoices,
    },
    correct_answer: normalizedCorrectAnswer,
  } satisfies AdminQuizQuestionDraft;
};

const toAdminQuestionPayload = (question: AdminQuizQuestionDraft) => {
  const normalizedQuestion =
    question.question_type === "multiple_choice"
      ? normalizeMultipleChoiceQuestionPayload(question)
      : question;

  return {
    question_type: normalizedQuestion.question_type,
    question_text: normalizedQuestion.question_text,
    explanation: normalizedQuestion.explanation,
    points: normalizedQuestion.points,
    order_index: normalizedQuestion.order_index,
    options: normalizedQuestion.options,
    correct_answer: normalizedQuestion.correct_answer,
    media_url: normalizedQuestion.media_url,
  } satisfies Record<string, unknown>;
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
    () => apiPost<AdminLessonDraftResponse>(`/admin/lesson-drafts`, payload),
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
        `/admin/lesson-drafts/${lessonId}/steps/${step}`,
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
      apiPost<AdminPublishLessonResult>(`/admin/lessons/${lessonId}/publication`, {
        lesson: payload.lesson ?? {},
        questions: (payload.questions ?? []).map(toAdminQuestionPayload),
      }),
    { allowAutoFallback: false }
  );

export const updateLesson = (lessonId: string, payload: Record<string, unknown>) =>
  withMockFallback(
    "admin-lesson-update",
    () => {
      const lesson = mockLessons.find((item) => item.id === lessonId);
      if (!lesson) {
        throw new Error("Lesson not found");
      }
      Object.assign(lesson, payload, { updated_at: new Date().toISOString() });
      if (mockLessonDetail.id === lessonId) {
        Object.assign(mockLessonDetail, payload, { updated_at: lesson.updated_at });
      }
      return lesson;
    },
    () => apiPut<Lesson>(`/admin/lessons/${lessonId}`, payload),
    { allowAutoFallback: false }
  );

export const deleteLesson = (lessonId: string) => apiDelete<void>(`/admin/lessons/${lessonId}`);

export const createLesson = (payload: Record<string, unknown>) => apiPost<Lesson>(`/admin/lessons`, payload);

export const createLessonQuiz = (lessonId: string, questions: Partial<QuizQuestion>[]) =>
  apiPost<Quiz>(`/admin/lessons/${lessonId}/quiz`, { questions });

export const replaceAdminLessonQuiz = (lessonId: string, questions: Partial<QuizQuestion>[]) =>
  apiPut<QuizQuestion[]>(`/admin/lessons/${lessonId}/quiz`, { questions });

export const getFlaggedContentStats = (month: string, year: string) =>
  apiGet<FlagByDate[]>(`/admin/analytics/flags?month=${month}&year=${year}`);

export const getAvgReviewTimeStats = (month: string, year: string) =>
  apiGet<AvgReviewTimeDTO>(`/admin/analytics/avg-review-time?month=${month}&year=${year}`);

export const getTopFlagUsers = (month: string, year: string) =>
  apiGet<TopFlagUser[]>(`/admin/analytics/top-flag-users?month=${month}&year=${year}`);

export const getTopFlagContent = (month: string, year: string) =>
  apiGet<TopFlagContentItem[]>(`/admin/analytics/top-flag-contents?month=${month}&year=${year}`);

export const getAuditLogs = (month: string, year: string) =>
  apiGet<AuditLogItem[]>(`/admin/analytics/audit-logs?month=${month}&year=${year}`);
