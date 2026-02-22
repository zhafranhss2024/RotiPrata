import type { Lesson } from "@/types";

/**
 * DUMMY DATA: Used when VITE_USE_MOCKS=true or when API calls fail in auto mode.
 */
const now = new Date().toISOString();

export const mockLessons: Lesson[] = [
  {
    id: "1",
    created_by: "admin1",
    title: "Gen Alpha Slang 101",
    description: "Learn the basics of Gen Alpha slang and how to use it correctly.",
    header_media_url: null,
    summary: "Master the fundamental slang terms used by Gen Alpha",
    learning_objectives: ["Understand common slang", "Use slang in context", "Recognize meme origins"],
    estimated_minutes: 15,
    xp_reward: 150,
    badge_name: "Slang Starter",
    badge_icon_url: null,
    difficulty_level: 1,
    is_published: true,
    completion_count: 1234,
    origin_content: null,
    definition_content: null,
    usage_examples: null,
    lore_content: null,
    evolution_content: null,
    comparison_content: null,
    created_at: now,
    updated_at: now,
  },
  {
    id: "2",
    created_by: "admin1",
    title: "Meme History: Skibidi Universe",
    description: "Dive deep into the Skibidi Toilet phenomenon and its cultural impact.",
    header_media_url: null,
    summary: "Explore the bizarre world of Skibidi Toilet",
    learning_objectives: ["Know the origin story", "Understand character lore", "Identify spin-offs"],
    estimated_minutes: 20,
    xp_reward: 200,
    badge_name: "Skibidi Scholar",
    badge_icon_url: null,
    difficulty_level: 2,
    is_published: true,
    completion_count: 856,
    origin_content: null,
    definition_content: null,
    usage_examples: null,
    lore_content: null,
    evolution_content: null,
    comparison_content: null,
    created_at: now,
    updated_at: now,
  },
  {
    id: "3",
    created_by: "admin1",
    title: "Understanding Internet Culture",
    description: "From TikTok trends to Discord memes - a comprehensive guide.",
    header_media_url: null,
    summary: "Navigate the ever-changing internet landscape",
    learning_objectives: ["Platform-specific culture", "Trend lifecycle", "Cross-platform memes"],
    estimated_minutes: 30,
    xp_reward: 300,
    badge_name: "Internet Expert",
    badge_icon_url: null,
    difficulty_level: 3,
    is_published: true,
    completion_count: 432,
    origin_content: null,
    definition_content: null,
    usage_examples: null,
    lore_content: null,
    evolution_content: null,
    comparison_content: null,
    created_at: now,
    updated_at: now,
  },
];

export const mockLessonDetail: Lesson = {
  id: "1",
  created_by: "admin1",
  title: "Gen Alpha Slang 101",
  description:
    "Learn the basics of Gen Alpha slang and how to use it correctly. This lesson covers popular terms, origins, and usage in context.",
  header_media_url: null,
  summary: "Master the fundamental slang terms used by Gen Alpha and understand the cultural context behind them.",
  learning_objectives: [
    "Understand common Gen Alpha slang",
    "Use slang correctly in context",
    "Recognize where popular terms originated",
    "Distinguish between similar terms",
  ],
  estimated_minutes: 15,
  xp_reward: 150,
  badge_name: "Slang Starter",
  badge_icon_url: null,
  difficulty_level: 1,
  is_published: true,
  completion_count: 1234,
  origin_content:
    "Gen Alpha slang emerged primarily from social media platforms like TikTok, YouTube Shorts, and gaming communities.",
  definition_content:
    "This lesson covers key terms including: Rizz, Gyatt, Skibidi, Ohio, Fanum Tax, Sus, No Cap, Bet, and more.",
  usage_examples: [
    "That guy has so much rizz, he talked to everyone at the party.",
    "No cap, this is the best pizza I've ever had.",
    "Only in Ohio would you see something that weird.",
  ],
  lore_content:
    "Understanding the why behind slang helps you use it authentically. Most Gen Alpha slang comes from streamer culture.",
  evolution_content:
    "Slang evolves quickly. Rizz started as niche Twitch slang before becoming mainstream.",
  comparison_content:
    "Rizz = Having game (Millennial) | Gyatt = Damn! (Gen X) | No cap = For real (Earlier Gen Z)",
  created_at: now,
  updated_at: now,
};

export const mockLessonSections = [
  { id: "intro", title: "Introduction", completed: true },
  { id: "breakdown", title: "Term Breakdown", completed: true },
  { id: "context", title: "Cultural Context", completed: false },
  { id: "challenge", title: "Micro-Challenge", completed: false },
  { id: "assessment", title: "Final Assessment", completed: false },
];

export const mockLessonProgressByLessonId: Record<string, number> = {
  "1": 75,
  "2": 0,
  "3": 0,
};

export const mockLessonStats = {
  lessonsEnrolled: 3,
  lessonsCompleted: 1,
  currentStreak: 5,
  conceptsMastered: 12,
  hoursLearned: 2.5,
  quizzesTaken: 8,
  averageScore: 85,
};
