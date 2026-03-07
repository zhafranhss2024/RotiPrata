export const ACTIVE_VISIBILITY_THRESHOLD = 0.3;
export const VIEW_TRACKING_THRESHOLD = 0.6;
export const ACTIVE_SWITCH_HYSTERESIS = 0.05;

export interface FeedActivationItem {
  id: string;
}

export const chooseActiveFeedIndex = (
  items: FeedActivationItem[],
  visibilityRatios: Record<string, number>,
  currentActiveIndex: number
) => {
  let bestIndex = -1;
  let bestRatio = -1;

  items.forEach((item, index) => {
    const ratio = visibilityRatios[item.id] ?? 0;
    if (ratio >= ACTIVE_VISIBILITY_THRESHOLD && ratio > bestRatio) {
      bestRatio = ratio;
      bestIndex = index;
    }
  });

  if (bestIndex < 0) {
    return -1;
  }

  if (currentActiveIndex < 0 || currentActiveIndex >= items.length) {
    return bestIndex;
  }

  if (bestIndex === currentActiveIndex) {
    return currentActiveIndex;
  }

  const currentId = items[currentActiveIndex]?.id;
  const currentRatio = currentId ? visibilityRatios[currentId] ?? 0 : 0;

  if (currentRatio < ACTIVE_VISIBILITY_THRESHOLD) {
    return bestIndex;
  }

  if (bestRatio >= currentRatio + ACTIVE_SWITCH_HYSTERESIS) {
    return bestIndex;
  }

  return currentActiveIndex;
};
