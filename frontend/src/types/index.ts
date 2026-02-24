// Type definitions for Rotiprata application
// These types mirror the backend database schema (Supabase + Java API).

export type AppRole = 'user' | 'admin';
export type ContentStatus = 'pending' | 'approved' | 'rejected';
export type ContentType = 'video' | 'image' | 'text';
export type CategoryType = 'slang' | 'meme' | 'dance_trend' | 'social_practice' | 'cultural_reference';
export type ThemePreference = 'light' | 'dark' | 'system';

export interface Profile {
  id: string;
  user_id: string;
  display_name: string | null;
  avatar_url: string | null;
  bio: string | null;
  date_of_birth: string | null;
  is_gen_alpha: boolean;
  theme_preference: ThemePreference;
  is_verified: boolean;
  reputation_points: number;
  current_streak: number;
  longest_streak: number;
  last_activity_date: string | null;
  total_hours_learned: number;
  created_at: string;
  updated_at: string;
}

export interface UserRole {
  id: string;
  user_id: string;
  role: AppRole;
  assigned_at: string;
  assigned_by: string | null;
}

export interface Category {
  id: string;
  name: string;
  type: CategoryType;
  description: string | null;
  icon_url: string | null;
  color: string | null;
  created_at: string;
}

export interface Content {
  id: string;
  creator_id: string;
  title: string;
  description: string | null;
  content_type: ContentType;
  media_url: string | null;
  thumbnail_url: string | null;
  category_id: string | null;
  status: ContentStatus;
  learning_objective: string | null;
  origin_explanation: string | null;
  definition_literal: string | null;
  definition_used: string | null;
  older_version_reference: string | null;
  educational_value_votes: number;
  view_count: number;
  is_featured: boolean;
  reviewed_by: string | null;
  reviewed_at: string | null;
  review_feedback: string | null;
  created_at: string;
  updated_at: string;
  // Joined data
  category?: Category;
  creator?: Profile;
  tags?: string[];
}

export interface ContentTag {
  id: string;
  content_id: string;
  tag: string;
  created_at: string;
}

export interface Lesson {
  id: string;
  created_by: string;
  title: string;
  description: string | null;
  header_media_url: string | null;
  summary: string | null;
  learning_objectives: string[] | null;
  estimated_minutes: number;
  xp_reward: number;
  badge_name: string | null;
  badge_icon_url: string | null;
  difficulty_level: number;
  is_published: boolean;
  is_active?: boolean;
  archived_at?: string | null;
  completion_count: number;
  origin_content: string | null;
  definition_content: string | null;
  usage_examples: string[] | null;
  lore_content: string | null;
  evolution_content: string | null;
  comparison_content: string | null;
  created_at: string;
  updated_at: string;
}

export interface LessonSection {
  id: string;
  title: string;
  content: string;
  order_index: number;
  duration_minutes: number;
  completed: boolean;
}

export interface Quiz {
  id: string;
  lesson_id: string | null;
  content_id: string | null;
  title: string;
  description: string | null;
  quiz_type: string;
  time_limit_seconds: number | null;
  passing_score: number;
  is_active?: boolean;
  archived_at?: string | null;
  created_by: string;
  created_at: string;
  updated_at: string;
  questions?: QuizQuestion[];
}

export interface QuizQuestion {
  id: string;
  quiz_id: string;
  question_text: string;
  question_type: string;
  media_url: string | null;
  options: Record<string, string> | null;
  correct_answer: string;
  explanation: string | null;
  points: number;
  order_index: number;
  created_at: string;
}

export interface UserLessonProgress {
  id: string;
  user_id: string;
  lesson_id: string;
  status: string;
  progress_percentage: number;
  current_section: string | null;
  started_at: string | null;
  completed_at: string | null;
  last_accessed_at: string;
  created_at: string;
  lesson?: Lesson;
}

export interface UserQuizResult {
  id: string;
  user_id: string;
  quiz_id: string;
  score: number;
  max_score: number;
  percentage: number;
  passed: boolean;
  answers: Record<string, string> | null;
  time_taken_seconds: number | null;
  attempted_at: string;
}

export interface UserAchievement {
  id: string;
  user_id: string;
  achievement_name: string;
  achievement_type: string | null;
  icon_url: string | null;
  description: string | null;
  earned_at: string;
}

export interface SavedContent {
  id: string;
  user_id: string;
  content_id: string | null;
  lesson_id: string | null;
  saved_at: string;
  content?: Content;
  lesson?: Lesson;
}

export interface BrowsingHistory {
  id: string;
  user_id: string;
  content_id: string | null;
  lesson_id: string | null;
  viewed_at: string;
  content?: Content;
  lesson?: Lesson;
}

export interface ContentFlag {
  id: string;
  content_id: string;
  reported_by: string;
  reason: string;
  description: string | null;
  status: string;
  resolved_by: string | null;
  resolved_at: string | null;
  created_at: string;
}

export interface ModerationQueueItem {
  id: string;
  content_id: string;
  submitted_at: string;
  priority: number;
  assigned_to: string | null;
  notes: string | null;
  content?: Content;
}

export interface ContentVote {
  id: string;
  content_id: string;
  user_id: string;
  vote_type: string;
  created_at: string;
}

export interface UserConceptMastered {
  id: string;
  user_id: string;
  content_id: string;
  mastered_at: string;
}

// Analytics types for dashboard
export interface UserAnalytics {
  totalLessonsEnrolled: number;
  lessonsCompleted: number;
  currentStreak: number;
  conceptsMastered: number;
  hoursLearned: number;
  quizzesPassed: number;
  averageQuizScore: number;
  achievements: UserAchievement[];
}

export interface AdminAnalytics {
  totalUsers: number;
  activeUsers: number;
  totalContent: number;
  pendingModeration: number;
  totalLessons: number;
  contentApprovalRate: number;
}

// Navigation types
export interface NavItem {
  label: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  activeIcon?: React.ComponentType<{ className?: string }>;
}
