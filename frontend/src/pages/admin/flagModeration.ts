import type { AdminContentFlagGroup } from '@/types';

export const getFlagReportCount = (flag: AdminContentFlagGroup) =>
  Math.max(flag.report_count ?? 0, 0);

export const getFlagReasons = (flag: AdminContentFlagGroup) =>
  Array.from(new Set((flag.reasons ?? []).map((reason) => reason?.trim()).filter((reason): reason is string => Boolean(reason))));

export const getFlagReasonSummary = (flag: AdminContentFlagGroup) => {
  const reasons = getFlagReasons(flag);
  if (reasons.length === 0) {
    return 'Reported content';
  }
  if (reasons.length <= 2) {
    return reasons.join(' · ');
  }
  return `${reasons.slice(0, 2).join(' · ')} +${reasons.length - 2} more`;
};

export const getFlagDescriptionCount = (flag: AdminContentFlagGroup) =>
  Math.max(flag.notes_count ?? 0, 0);
