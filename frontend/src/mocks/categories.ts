import type { Category } from "@/types";

/**
 * DUMMY DATA: Used when VITE_USE_MOCKS=true or when API calls fail in auto mode.
 */
export const mockCategories: Category[] = [
  { id: "1", name: "Slang", type: "slang", description: null, icon_url: null, color: "#FF6B6B", created_at: "" },
  { id: "2", name: "Memes", type: "meme", description: null, icon_url: null, color: "#4ECDC4", created_at: "" },
  { id: "3", name: "Dance Trends", type: "dance_trend", description: null, icon_url: null, color: "#45B7D1", created_at: "" },
  { id: "4", name: "Social Practices", type: "social_practice", description: null, icon_url: null, color: "#96CEB4", created_at: "" },
  { id: "5", name: "Cultural References", type: "cultural_reference", description: null, icon_url: null, color: "#FFEAA7", created_at: "" },
];
