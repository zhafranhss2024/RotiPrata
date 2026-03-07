import { describe, expect, it } from 'vitest';
import {
  ACTIVE_VISIBILITY_THRESHOLD,
  chooseActiveFeedIndex,
  VIEW_TRACKING_THRESHOLD,
} from './feedActivation';

describe('feed activation thresholds', () => {
  const items = [{ id: 'a' }, { id: 'b' }, { id: 'c' }];

  it('promotes a card once it reaches 30% visibility', () => {
    const ratios = { a: 0.1, b: ACTIVE_VISIBILITY_THRESHOLD, c: 0.2 };
    const next = chooseActiveFeedIndex(items, ratios, -1);
    expect(next).toBe(1);
  });

  it('keeps current card when challenger does not beat hysteresis gap', () => {
    const ratios = { a: 0.42, b: 0.45, c: 0.1 };
    const next = chooseActiveFeedIndex(items, ratios, 0);
    expect(next).toBe(0);
  });

  it('switches when challenger clearly exceeds current visibility', () => {
    const ratios = { a: 0.33, b: 0.45, c: 0.1 };
    const next = chooseActiveFeedIndex(items, ratios, 0);
    expect(next).toBe(1);
  });

  it('view tracking threshold stays at 60%', () => {
    expect(VIEW_TRACKING_THRESHOLD).toBe(0.6);
  });
});
