import { describe, expect, it } from 'vitest';
import { buildLessonFeedQuery } from '@/lib/api';

describe('buildLessonFeedQuery', () => {
  it('uses defaults when optional filters are omitted', () => {
    const query = buildLessonFeedQuery();
    expect(query).toBe('page=1&pageSize=12&sort=newest');
  });

  it('includes filters and trims search text', () => {
    const query = buildLessonFeedQuery({
      page: 2,
      pageSize: 20,
      q: '  skibidi  ',
      difficulty: 3,
      maxMinutes: 30,
      sort: 'popular',
    });

    expect(query).toContain('page=2');
    expect(query).toContain('pageSize=20');
    expect(query).toContain('q=skibidi');
    expect(query).toContain('difficulty=3');
    expect(query).toContain('maxMinutes=30');
    expect(query).toContain('sort=popular');
  });
});
