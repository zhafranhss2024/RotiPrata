export type ActivityType = 'like' | 'watch' | 'save';

export type LocalActivityItem = {
  id: string;
  itemId: string;
  title: string;
  itemType: 'lesson' | 'content';
  actedAt: string;
};

const STORAGE_KEYS = {
  like: 'rotiprata.activity.likes',
  watch: 'rotiprata.activity.watches',
  save: 'rotiprata.activity.saves',
} as const;

const MAX_ITEMS = 50;

const isBrowser = () => typeof window !== 'undefined' && typeof localStorage !== 'undefined';

const readItems = (type: ActivityType): LocalActivityItem[] => {
  if (!isBrowser()) return [];
  try {
    const raw = localStorage.getItem(STORAGE_KEYS[type]);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const writeItems = (type: ActivityType, items: LocalActivityItem[]) => {
  if (!isBrowser()) return;
  localStorage.setItem(STORAGE_KEYS[type], JSON.stringify(items.slice(0, MAX_ITEMS)));
};

const createItem = (itemId: string, title: string, itemType: 'lesson' | 'content'): LocalActivityItem => ({
  id: `${itemType}-${itemId}`,
  itemId,
  title,
  itemType,
  actedAt: new Date().toISOString(),
});

const upsertActivity = (type: ActivityType, itemId: string, title: string, itemType: 'lesson' | 'content') => {
  const next = createItem(itemId, title, itemType);
  const existing = readItems(type);
  const filtered = existing.filter((item) => item.id !== next.id);
  writeItems(type, [next, ...filtered]);
};

export const getLikeHistory = () => readItems('like');

export const getWatchHistory = () => readItems('watch');

export const getSaveHistory = () => readItems('save');

export const recordLikeActivity = (contentId: string, title: string) => {
  upsertActivity('like', contentId, title || 'Untitled', 'content');
};

export const recordWatchActivity = (contentId: string, title: string) => {
  upsertActivity('watch', contentId, title || 'Untitled', 'content');
};

export const recordSaveActivity = (contentId: string, title: string) => {
  upsertActivity('save', contentId, title || 'Untitled', 'content');
};

export const removeSaveActivity = (contentId: string) => {
  const existing = readItems('save');
  writeItems(
    'save',
    existing.filter((item) => item.id !== `content-${contentId}`)
  );
};
