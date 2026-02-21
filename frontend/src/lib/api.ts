import type {
  AppRole,
  Category,
  Content,
  ContentFlag,
  Lesson,
  Profile,
  Quiz,
  QuizQuestion,
  ThemePreference,
  UserAchievement,
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

export type SearchResult = {
  id: string;
  type: "content" | "lesson" | "profile";
  title: string;
  snippet?: string;
};

export type SaveHistoryDTO = {
  itemId: string;
  lessonId?: string;
  contentId?: string;
  viewedAt: string;
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

export const saveBrowsingHistory = (contentId?: string, lessonId?: string) => {
  const body = { contentId: contentId ?? null, lessonId: lessonId ?? null };
  apiPost<void>(`/users/me/history`, body);
};

export const fetchRecommendations = () =>
  withMockFallback("recommendations", () => mockAiSuggestions, () => apiGet(`/recommendations`));

export const fetchBrowsingHistory = () =>
  // withMockFallback("history", () => mockBrowsingHistory, () => apiGet(`/users/me/history`));
  apiGet<SaveHistoryDTO[]>(`/users/me/history`);

export const clearBrowsingHistory = () => apiDelete<void>(`/users/me/history`);

export const fetchLessons = () =>
  withMockFallback("lessons", () => mockLessons, () => apiGet<Lesson[]>(`/lessons`));

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
    () => apiGet<Lesson>(`/lessons/${lessonId}`)
  );

export const fetchLessonSections = (lessonId: string) =>
  withMockFallback(
    "lesson-sections",
    () => mockLessonSections,
    () => apiGet(`/lessons/${lessonId}/sections`)
  );

export const fetchLessonProgress = () =>
  withMockFallback(
    "lesson-progress",
    () => mockLessonProgressByLessonId,
    () => apiGet<Record<string, number>>(`/users/me/lessons/progress`)
  );

export const fetchUserStats = () =>
  withMockFallback("user-stats", () => mockLessonStats, () => apiGet<UserStats>(`/users/me/stats`));

export const enrollLesson = (lessonId: string) => apiPost<void>(`/lessons/${lessonId}/enroll`);

export const saveLesson = (lessonId: string) => apiPost<void>(`/lessons/${lessonId}/save`);

export const updateLessonProgress = (lessonId: string, progress: number) =>
  apiPut<void>(`/lessons/${lessonId}/progress`, { progress });

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

export const voteContent = (contentId: string, voteType: string) =>
  apiPost<void>(`/content/${contentId}/vote`, { vote_type: voteType });

export const saveContent = (contentId: string) => apiPost<void>(`/content/${contentId}/save`);

export const flagContent = (contentId: string, reason: string, description?: string) =>
  apiPost<void>(`/content/${contentId}/flag`, { reason, description });

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

export const fetchModerationQueue = () =>
  withMockFallback(
    "admin-moderation",
    () => mockModerationQueue,
    () => apiGet<(typeof mockModerationQueue)[number][]>(`/admin/moderation-queue`)
  );

export const fetchContentFlags = () =>
  withMockFallback("admin-flags", () => mockFlags, () => apiGet<ContentFlag[]>(`/admin/flags`));

export const approveContent = (contentId: string) => apiPut<void>(`/admin/content/${contentId}/approve`);

export const updateAdminContent = (contentId: string, payload: Record<string, unknown>) =>
  apiPut<Content>(`/admin/content/${contentId}`, payload);

export const rejectContent = (contentId: string, feedback?: string) =>
  apiPut<void>(`/admin/content/${contentId}/reject`, { feedback });

export const resolveFlag = (flagId: string) => apiPut<void>(`/admin/flags/${flagId}/resolve`);

export const createLesson = (payload: Record<string, unknown>) => apiPost<Lesson>(`/admin/lessons`, payload);

export const createLessonQuiz = (lessonId: string, questions: Partial<QuizQuestion>[]) =>
  apiPost<Quiz>(`/admin/lessons/${lessonId}/quiz`, { questions });
