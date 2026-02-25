import type { Lesson, LessonHubResponse, LessonProgressDetail } from "@/types";

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
  {
    id: "intro",
    title: "Origin",
    content:
      "Gen Alpha slang emerged from TikTok clips, stream chat culture, and short-form remix trends.",
    order_index: 1,
    duration_minutes: 3,
    completed: false,
  },
  {
    id: "definition",
    title: "Definition",
    content:
      "Terms like rizz, gyatt, and no cap signal social fluency, tone, and in-group context more than literal meaning.",
    order_index: 2,
    duration_minutes: 3,
    completed: false,
  },
  {
    id: "usage",
    title: "Usage Examples",
    content:
      "That streamer has rizz. No cap, that clip is wild. Only in Ohio would this happen.",
    order_index: 3,
    duration_minutes: 3,
    completed: false,
  },
  {
    id: "lore",
    title: "Lore",
    content:
      "Many phrases are born in niche communities, then mainstream creators simplify and spread them.",
    order_index: 4,
    duration_minutes: 3,
    completed: false,
  },
  {
    id: "evolution",
    title: "Evolution",
    content:
      "Slang shifts fast. Meanings broaden and references fade as terms cross platforms and age groups.",
    order_index: 5,
    duration_minutes: 3,
    completed: false,
  },
  {
    id: "comparison",
    title: "Comparison",
    content:
      "Rizz roughly maps to having game, while no cap maps to for real in earlier generations.",
    order_index: 6,
    duration_minutes: 3,
    completed: false,
  },
];

export const mockLessonProgressByLessonId: Record<string, number> = {
  "1": 75,
  "2": 0,
  "3": 0,
};

export const mockLessonProgressDetailByLessonId: Record<string, LessonProgressDetail> = {
  "1": {
    status: "in_progress",
    progressPercentage: 57,
    currentSection: "lore",
    completedSections: 4,
    totalSections: 6,
    nextSectionId: "evolution",
    isEnrolled: true,
    totalStops: 7,
    completedStops: 4,
    currentStopId: "lore",
    remainingStops: 3,
    quizStatus: "locked",
    heartsRemaining: 5,
    heartsRefillAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    nextStopType: "section",
  },
  "2": {
    status: "not_started",
    progressPercentage: 0,
    currentSection: null,
    completedSections: 0,
    totalSections: 6,
    nextSectionId: "intro",
    isEnrolled: false,
    totalStops: 7,
    completedStops: 0,
    currentStopId: null,
    remainingStops: 7,
    quizStatus: "locked",
    heartsRemaining: 5,
    heartsRefillAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    nextStopType: "section",
  },
  "3": {
    status: "not_started",
    progressPercentage: 0,
    currentSection: null,
    completedSections: 0,
    totalSections: 6,
    nextSectionId: "intro",
    isEnrolled: false,
    totalStops: 7,
    completedStops: 0,
    currentStopId: null,
    remainingStops: 7,
    quizStatus: "locked",
    heartsRemaining: 5,
    heartsRefillAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    nextStopType: "section",
  },
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




export const mockLessonHub: LessonHubResponse = {
  units: [
    {
      unitId: "unit-1",
      title: "Unit 1",
      orderIndex: 1,
      accentColor: "green",
      lessons: mockLessons.map((lesson, index) => {
        const progress = mockLessonProgressByLessonId[lesson.id] ?? 0;
        return {
          lessonId: lesson.id,
          title: lesson.title,
          difficultyLevel: lesson.difficulty_level,
          estimatedMinutes: lesson.estimated_minutes,
          xpReward: lesson.xp_reward,
          completionCount: lesson.completion_count,
          progressPercentage: progress,
          completed: progress >= 100,
          current: index === 0,
          visuallyLocked: index > 0 && progress < 100,
        };
      }),
    },
  ],
  summary: {
    totalLessons: mockLessons.length,
    completedLessons: Object.values(mockLessonProgressByLessonId).filter((pct) => pct >= 100).length,
    currentStreak: 5,
  },
};
