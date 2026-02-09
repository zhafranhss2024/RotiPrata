import type { Content, ContentFlag, ModerationQueueItem } from "@/types";

/**
 * DUMMY DATA: Used when VITE_USE_MOCKS=true or when API calls fail in auto mode.
 */
const now = new Date().toISOString();

export const mockAdminStats = {
  totalUsers: 1234,
  activeUsers: 567,
  totalContent: 890,
  pendingModeration: 12,
  totalLessons: 25,
  contentApprovalRate: 85,
};

export const mockModerationQueue: (ModerationQueueItem & { content: Content })[] = [
  {
    id: "1",
    content_id: "1",
    submitted_at: now,
    priority: 1,
    assigned_to: null,
    notes: null,
    content: {
      id: "1",
      creator_id: "user1",
      title: "New Slang: Mewing",
      description: "Explanation of the mewing trend",
      content_type: "video",
      media_url: null,
      thumbnail_url: null,
      category_id: "slang",
      status: "pending",
      learning_objective: "What is mewing",
      origin_explanation: null,
      definition_literal: null,
      definition_used: null,
      older_version_reference: null,
      educational_value_votes: 0,
      view_count: 0,
      is_featured: false,
      reviewed_by: null,
      reviewed_at: null,
      review_feedback: null,
      created_at: now,
      updated_at: now,
    },
  },
  {
    id: "2",
    content_id: "2",
    submitted_at: new Date(Date.now() - 3600000).toISOString(),
    priority: 0,
    assigned_to: null,
    notes: null,
    content: {
      id: "2",
      creator_id: "user2",
      title: "Italian Brainrot Explained",
      description: "The origin and meaning of Italian brainrot memes",
      content_type: "text",
      media_url: null,
      thumbnail_url: null,
      category_id: "meme",
      status: "pending",
      learning_objective: "Italian brainrot",
      origin_explanation: null,
      definition_literal: null,
      definition_used: null,
      older_version_reference: null,
      educational_value_votes: 0,
      view_count: 0,
      is_featured: false,
      reviewed_by: null,
      reviewed_at: null,
      review_feedback: null,
      created_at: new Date(Date.now() - 3600000).toISOString(),
      updated_at: new Date(Date.now() - 3600000).toISOString(),
    },
  },
];

export const mockFlags: ContentFlag[] = [
  {
    id: "1",
    content_id: "3",
    reported_by: "user5",
    reason: "Inappropriate content",
    description: "Contains bad words",
    status: "pending",
    resolved_by: null,
    resolved_at: null,
    created_at: now,
  },
  {
    id: "2",
    content_id: "4",
    reported_by: "user6",
    reason: "Misleading information",
    description: "The explanation is wrong",
    status: "pending",
    resolved_by: null,
    resolved_at: null,
    created_at: new Date(Date.now() - 7200000).toISOString(),
  },
];
