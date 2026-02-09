import type { Profile, UserAchievement } from "@/types";

/**
 * DUMMY DATA: Used when VITE_USE_MOCKS=true or when API calls fail in auto mode.
 */
const now = new Date().toISOString();

export const mockProfile: Profile = {
  id: "1",
  user_id: "user1",
  username: "brainrot_learner",
  display_name: "Brain Rot Learner",
  avatar_url: null,
  bio: "Learning Gen Alpha culture one skibidi at a time",
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

export const mockAchievements: UserAchievement[] = [
  {
    id: "1",
    user_id: "user1",
    achievement_name: "First Steps",
    achievement_type: "milestone",
    icon_url: null,
    description: "Complete your first lesson",
    earned_at: now,
  },
  {
    id: "2",
    user_id: "user1",
    achievement_name: "Quiz Master",
    achievement_type: "skill",
    icon_url: null,
    description: "Score 100% on 3 quizzes",
    earned_at: now,
  },
  {
    id: "3",
    user_id: "user1",
    achievement_name: "5 Day Streak",
    achievement_type: "streak",
    icon_url: null,
    description: "Learn for 5 days in a row",
    earned_at: now,
  },
];

export const mockProfileStats = {
  lessonsEnrolled: 5,
  lessonsCompleted: 2,
  quizzesTaken: 8,
  averageScore: 85,
  conceptsMastered: 15,
};
