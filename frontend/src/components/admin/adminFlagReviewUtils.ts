import type { AdminContentFlagGroup, Content } from '@/types';

export const ADMIN_FLAG_TAKEDOWN_REASON_MAX_LENGTH = 500;

const stripControlCharacters = (value: string) =>
  Array.from(value)
    .filter((character) => {
      const code = character.charCodeAt(0);
      return code > 31 && code !== 127;
    })
    .join('');

export const sanitizeAdminFlagInputValue = (value: string, maxLength: number) => {
  const cleaned = stripControlCharacters(value);
  if (maxLength > 0 && cleaned.length > maxLength) {
    return cleaned.slice(0, maxLength);
  }
  return cleaned;
};

export const normalizeAdminFlagText = (value: string, maxLength: number) => {
  const cleaned = sanitizeAdminFlagInputValue(value, maxLength);
  return cleaned.replace(/\s+/g, ' ').trim();
};

export const formatAdminFlagContentStatus = (status?: string | null) => {
  switch ((status ?? '').toLowerCase()) {
    case 'approved':
    case 'accepted':
      return 'Approved';
    case 'pending':
      return 'Pending';
    case 'rejected':
      return 'Taken down';
    default:
      return status ?? 'Unknown';
  }
};

export const buildFlagReviewFallbackTitle = (contentId: string) => `Content ${contentId}`;

export const getFlagContentTitle = (content?: Content | null, contentId?: string | null) =>
  content?.title ?? buildFlagReviewFallbackTitle(contentId ?? 'unknown');

export const getFlagCreatorDisplayName = (content?: Content | null) =>
  content?.creator?.display_name ?? 'anonymous';

export const getFlagActionUnavailableMessage = (
  review:
    | Pick<AdminContentFlagGroup, 'status'>
    | { actionableFlagId: string | null; canResolve: boolean; canTakeDown: boolean; status: string | null }
    | null
) => {
  if (!review) {
    return null;
  }

  if ('actionableFlagId' in review) {
    if (review.actionableFlagId && (review.canResolve || review.canTakeDown)) {
      return null;
    }
    return 'No open moderation action is available for this historical flag item.';
  }

  return review.status?.toLowerCase() === 'pending'
    ? null
    : 'No open moderation action is available for this historical flag item.';
};
