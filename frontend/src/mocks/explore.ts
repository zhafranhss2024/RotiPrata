/**
 * DUMMY DATA: Used when VITE_USE_MOCKS=true or when API calls fail in auto mode.
 */
export const mockTrendingContent = [
  { id: "1", title: "Skibidi Toilet", category: "Memes", views: "50K" },
  { id: "2", title: "Rizz", category: "Slang", views: "80K" },
  { id: "3", title: "Ohio Meme", category: "Memes", views: "65K" },
];

export const mockAiSuggestions = [
  { id: "1", title: "Based on your interest in Slang", items: ["Cap", "No Cap", "Sus"] },
  { id: "2", title: "Popular this week", items: ["Italian Brainrot", "Grimace Shake", "Sigma"] },
];

export const mockBrowsingHistory = [
  { id: "1", title: "Rizz", type: "content" as const, viewedAt: "2 hours ago" },
  { id: "2", title: "Slang 101 Lesson", type: "lesson" as const, viewedAt: "1 day ago" },
];

export const mockSearchResults = [
  { id: "1", type: "content" as const, title: "Rizz", snippet: "Short for charisma." },
  { id: "2", type: "lesson" as const, title: "Gen Alpha Slang 101", snippet: "Learn the basics of slang." },
];
