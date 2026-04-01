import { describe, expect, it } from "vitest";
import type { Content } from "@/types";
import { buildSimilarContentList, SIMILAR_CONTENT_LIMIT } from "@/lib/similarContent";

const buildVideo = (
  id: string,
  createdAt: string,
  tags: string[]
): Content => ({
  id,
  creator_id: `creator-${id}`,
  title: `Video ${id}`,
  description: `Description ${id}`,
  content_type: "video",
  media_url: `https://cdn.example.com/${id}.mp4`,
  thumbnail_url: `https://cdn.example.com/${id}.jpg`,
  category_id: null,
  status: "approved",
  learning_objective: null,
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
  created_at: createdAt,
  updated_at: createdAt,
  tags,
});

describe("buildSimilarContentList", () => {
  it("excludes the current video, prioritizes tag matches, and caps the result size", () => {
    const allContents = [
      buildVideo("current", "2026-03-20T10:00:00.000Z", ["67", "meme"]),
      buildVideo("shared-two", "2026-03-21T10:00:00.000Z", ["67", "meme"]),
      buildVideo("shared-one-newer", "2026-03-22T10:00:00.000Z", ["67"]),
      buildVideo("shared-one-older", "2026-03-19T10:00:00.000Z", ["67"]),
      buildVideo("shared-third", "2026-03-18T10:00:00.000Z", ["meme"]),
      buildVideo("shared-fourth", "2026-03-17T10:00:00.000Z", ["meme"]),
      buildVideo("shared-fifth", "2026-03-16T10:00:00.000Z", ["67"]),
      buildVideo("fallback-a", "2026-03-23T10:00:00.000Z", ["other"]),
      buildVideo("fallback-b", "2026-03-18T10:00:00.000Z", ["other"]),
      buildVideo("fallback-c", "2026-03-17T10:00:00.000Z", ["other"]),
      buildVideo("fallback-d", "2026-03-16T10:00:00.000Z", ["other"]),
      buildVideo("fallback-e", "2026-03-15T10:00:00.000Z", ["other"]),
      buildVideo("fallback-f", "2026-03-14T10:00:00.000Z", ["other"]),
    ];

    const result = buildSimilarContentList(allContents, "current", 12);

    expect(result).toHaveLength(SIMILAR_CONTENT_LIMIT);
    expect(result.map((content) => content.id)).toEqual([
      "shared-two",
      "shared-one-newer",
      "shared-one-older",
      "shared-third",
      "shared-fourth",
      "shared-fifth",
    ]);
    expect(result.some((content) => content.id === "current")).toBe(false);
  });

  it("returns an empty list when the current video has no tags", () => {
    const allContents = [
      buildVideo("current", "2026-03-20T10:00:00.000Z", []),
      buildVideo("recent", "2026-03-23T10:00:00.000Z", ["x"]),
      buildVideo("older", "2026-03-19T10:00:00.000Z", ["y"]),
      buildVideo("oldest", "2026-03-18T10:00:00.000Z", ["z"]),
    ];

    const result = buildSimilarContentList(allContents, "current", 3);

    expect(result).toEqual([]);
  });
});
