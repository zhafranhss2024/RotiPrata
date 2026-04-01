import type { Content, LeaderboardEntry, LeaderboardResponse, Profile, ProfileContentCollection, UserBadge } from "@/types";
import { mockContents } from "@/mocks/content";

/**
 * DUMMY DATA: Used when VITE_USE_MOCKS=true or when API calls fail in auto mode.
 */
const now = new Date().toISOString();

export const mockProfile: Profile = {
  id: "1",
  user_id: "user1",
  display_name: "Brain Rot Learner",
  avatar_url: null,
  bio: "Posting chaos, saving brainrot, and collecting lesson badges.",
  date_of_birth: "1990-05-15",
  is_gen_alpha: false,
  theme_preference: "system",
  is_verified: false,
  reputation_points: 250,
  current_streak: 5,
  longest_streak: 12,
  last_activity_date: now,
  total_hours_learned: 4.5,
  created_at: now,
  updated_at: now,
};

export const mockProfileBadges: UserBadge[] = [
  {
    lessonId: "lesson-1",
    lessonTitle: "What is \"No Cap\"?",
    badgeName: "Slang Starter",
    badgeIconUrl: null,
    earned: true,
    earnedAt: now,
  },
  {
    lessonId: "lesson-2",
    lessonTitle: "Italian Brainrot",
    badgeName: "Skibidi Scholar",
    badgeIconUrl: null,
    earned: true,
    earnedAt: new Date(Date.now() - 86400000).toISOString(),
  },
  {
    lessonId: "lesson-3",
    lessonTitle: "What is the \"67\" Meme?",
    badgeName: "Meme Historian",
    badgeIconUrl: null,
    earned: false,
    earnedAt: null,
  },
  {
    lessonId: "lesson-4",
    lessonTitle: "Deadass",
    badgeName: "Truth Teller",
    badgeIconUrl: null,
    earned: false,
    earnedAt: null,
  },
];

const postedVideo: Content = {
  ...mockContents[0],
  id: "profile-post-video",
  creator_id: "user1",
  title: "Skibidi recap edit",
  description: "My chaotic recap of the week in brainrot.",
  status: "approved",
  is_submitted: true,
  creator: mockProfile,
};

const postedImage: Content = {
  ...mockContents[2],
  id: "profile-post-image",
  creator_id: "user1",
  title: "Ohio starter pack",
  description: "Reaction image dump from my explore saves.",
  status: "pending",
  is_submitted: true,
  creator: mockProfile,
};

const postedText: Content = {
  ...mockContents[1],
  id: "profile-post-text",
  creator_id: "user1",
  title: "Rizz glossary notes",
  description: "Text post of phrases I keep hearing in the feed.",
  status: "rejected",
  is_submitted: true,
  creator: mockProfile,
};

const savedVideo: Content = {
  ...mockContents[0],
  id: "profile-saved-video",
  creator_id: "user2",
  title: "Gyat explained fast",
  description: "A saved video for later rewatch.",
};

const likedVideo: Content = {
  ...mockContents[0],
  id: "profile-liked-video",
  creator_id: "user3",
  title: "Meme iceberg speedrun",
  description: "A liked video from the main feed.",
};

export const mockProfileCollections: Record<ProfileContentCollection, Content[]> = {
  posted: [postedVideo, postedImage, postedText],
  saved: [savedVideo],
  liked: [likedVideo],
};

export const mockProfileStats = {
  lessonsEnrolled: 5,
  lessonsCompleted: 2,
  quizzesTaken: 8,
  averageScore: 85,
  conceptsMastered: 15,
};

const mockLeaderboardEntries: LeaderboardEntry[] = [
  {
    rank: 1,
    userId: "user9",
    displayName: "xpqueen",
    avatarUrl: null,
    xp: 1200,
    currentStreak: 18,
    isCurrentUser: false,
  },
  {
    rank: 2,
    userId: "user7",
    displayName: "brainboss",
    avatarUrl: null,
    xp: 980,
    currentStreak: 12,
    isCurrentUser: false,
  },
  {
    rank: 2,
    userId: "user8",
    displayName: "brainbuddy",
    avatarUrl: null,
    xp: 980,
    currentStreak: 10,
    isCurrentUser: false,
  },
  {
    rank: 4,
    userId: "user3",
    displayName: "gyrofan",
    avatarUrl: null,
    xp: 820,
    currentStreak: 9,
    isCurrentUser: false,
  },
  {
    rank: 5,
    userId: "user1",
    displayName: "Brain Rot Learner",
    avatarUrl: null,
    xp: 250,
    currentStreak: 5,
    isCurrentUser: true,
  },
  {
    rank: 6,
    userId: "user4",
    displayName: "zerochill",
    avatarUrl: null,
    xp: 0,
    currentStreak: 1,
    isCurrentUser: false,
  },
];

export const buildMockLeaderboardResponse = (page = 1, pageSize = 20, query = ""): LeaderboardResponse => {
  const normalizedQuery = query.trim().toLowerCase();
  const filtered = mockLeaderboardEntries.filter((entry) => {
    if (!normalizedQuery) {
      return true;
    }
    return (entry.displayName ?? "").toLowerCase().includes(normalizedQuery.replace(/^@/, ""));
  });
  const normalizedPageSize = Math.max(1, Math.min(pageSize, 50));
  const maxPage = filtered.length === 0 ? 1 : Math.ceil(filtered.length / normalizedPageSize);
  const normalizedPage = Math.max(1, Math.min(page, maxPage));
  const start = (normalizedPage - 1) * normalizedPageSize;
  const items = filtered.slice(start, start + normalizedPageSize);

  return {
    items,
    page: normalizedPage,
    pageSize: normalizedPageSize,
    hasNext: normalizedPage < maxPage,
    totalCount: filtered.length,
    query,
    currentUser: mockLeaderboardEntries.find((entry) => entry.isCurrentUser) ?? null,
  };
};
