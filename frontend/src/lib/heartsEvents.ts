import type { LessonHeartsStatus } from "@/types";

const HEARTS_UPDATED_EVENT = "hearts:updated";

export const emitHeartsUpdated = (hearts: LessonHeartsStatus) => {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent<LessonHeartsStatus>(HEARTS_UPDATED_EVENT, { detail: hearts }));
};

export const subscribeToHeartsUpdated = (handler: (hearts: LessonHeartsStatus) => void) => {
  if (typeof window === "undefined") {
    return () => undefined;
  }

  const listener = (event: Event) => {
    const customEvent = event as CustomEvent<LessonHeartsStatus>;
    if (!customEvent.detail) return;
    handler(customEvent.detail);
  };

  window.addEventListener(HEARTS_UPDATED_EVENT, listener as EventListener);
  return () => window.removeEventListener(HEARTS_UPDATED_EVENT, listener as EventListener);
};
