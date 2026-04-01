import type { Content } from "@/types";

export const SIMILAR_CONTENT_LIMIT = 6;

const toTimestamp = (value: string | null | undefined) => {
  const timestamp = value ? new Date(value).getTime() : 0;
  return Number.isFinite(timestamp) ? timestamp : 0;
};

const normalizeTags = (tags?: string[]) =>
  new Set(
    (tags ?? [])
      .map((tag) => String(tag ?? "").trim())
      .filter((tag) => tag.length > 0)
  );

export const buildSimilarContentList = (
  allContents: Content[],
  currentContentId: string,
  limit = SIMILAR_CONTENT_LIMIT
) => {
  const normalizedLimit = Number.isFinite(limit) ? Math.floor(limit) : SIMILAR_CONTENT_LIMIT;
  const boundedLimit = Math.max(
    0,
    Math.min(SIMILAR_CONTENT_LIMIT, normalizedLimit > 0 ? normalizedLimit : SIMILAR_CONTENT_LIMIT)
  );
  const videoContents = allContents.filter((content) => content.content_type === "video");
  const currentContent = videoContents.find((content) => content.id === currentContentId);

  if (!currentContent) {
    return [];
  }

  const currentTags = normalizeTags(currentContent.tags);
  if (currentTags.size === 0) {
    return [];
  }

  const candidates = videoContents.filter((content) => content.id !== currentContentId);
  const scoredCandidates = candidates.map((content) => {
    const tags = normalizeTags(content.tags);
    let sharedTagCount = 0;
    currentTags.forEach((tag) => {
      if (tags.has(tag)) {
        sharedTagCount += 1;
      }
    });
    return { content, sharedTagCount };
  });

  const matched = scoredCandidates
    .filter((entry) => entry.sharedTagCount > 0)
    .sort((left, right) => {
      if (right.sharedTagCount !== left.sharedTagCount) {
        return right.sharedTagCount - left.sharedTagCount;
      }
      const createdAtCompare = toTimestamp(right.content.created_at) - toTimestamp(left.content.created_at);
      if (createdAtCompare !== 0) {
        return createdAtCompare;
      }
      return right.content.id.localeCompare(left.content.id);
    })
    .map((entry) => entry.content);

  return matched.slice(0, boundedLimit);
};
