import { describe, expect, it } from 'vitest';
import type { AdminContentFlagGroup } from '@/types';
import {
  getFlagDescriptionCount,
  getFlagReasonSummary,
  getFlagReasons,
  getFlagReportCount,
} from '@/pages/admin/flagModeration';

const buildFlagGroup = (overrides: Partial<AdminContentFlagGroup> = {}): AdminContentFlagGroup => ({
  id: 'report-3',
  content_id: 'content-1',
  status: 'pending',
  created_at: '2026-04-01T10:00:00.000Z',
  report_count: 3,
  notes_count: 2,
  reasons: ['Harassment or hate', 'Inappropriate content'],
  ...overrides,
});

describe('flagModeration helpers', () => {
  it('deduplicates reasons and summarizes them for grouped cards', () => {
    const flag = buildFlagGroup();

    expect(getFlagReasons(flag)).toEqual(['Harassment or hate', 'Inappropriate content']);
    expect(getFlagReasonSummary(flag)).toBe('Harassment or hate · Inappropriate content');
  });

  it('counts reports and reporter notes from the grouped payload', () => {
    const flag = buildFlagGroup({ report_count: 5 });

    expect(getFlagReportCount(flag)).toBe(5);
    expect(getFlagDescriptionCount(flag)).toBe(2);
  });

  it('collapses long reason lists into a short summary', () => {
    const flag = buildFlagGroup({
      reasons: ['Harassment or hate', 'Inappropriate content', 'Misleading information'],
    });

    expect(getFlagReasonSummary(flag)).toBe('Harassment or hate · Inappropriate content +1 more');
  });
});
