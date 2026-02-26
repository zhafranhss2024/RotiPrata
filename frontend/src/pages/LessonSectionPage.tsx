import React, { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { MainLayout } from "@/components/layout/MainLayout";
import { Button } from "@/components/ui/button";
import { ArrowDown, ArrowLeft, ArrowUp, Check, Star } from "lucide-react";
import type { Lesson, LessonProgressDetail, LessonSection } from "@/types";
import {
  completeLessonSection,
  fetchLessonById,
  fetchLessonProgressDetail,
  fetchLessonSections,
} from "@/lib/api";
import { ApiError } from "@/lib/apiClient";
import { cn } from "@/lib/utils";

const STEP_GAP = 70;
const MAX_VISIBLE_DISTANCE = 3;
const STEP_HORIZONTAL_OFFSET = 26;
const NAV_GESTURE_LOCK_MS = 420;
const WHEEL_DELTA_THRESHOLD = 20;
const SWIPE_DELTA_THRESHOLD = 45;
const ADVANCE_NAV_DELAY_MS = 120;

const LessonSectionPage = () => {
  const { id, sectionId } = useParams<{ id: string; sectionId: string }>();
  const navigate = useNavigate();
  const [lesson, setLesson] = useState<Lesson | null>(null);
  const [sections, setSections] = useState<LessonSection[]>([]);
  const [progressDetail, setProgressDetail] = useState<LessonProgressDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [animatingCompleteIndex, setAnimatingCompleteIndex] = useState<number | null>(null);
  const [nextPulseIndex, setNextPulseIndex] = useState<number | null>(null);
  const advanceTimeoutRef = useRef<number | null>(null);
  const gestureLockRef = useRef(false);
  const touchStartYRef = useRef<number | null>(null);

  useEffect(() => {
    if (!id) return;
    let active = true;

    Promise.all([fetchLessonById(id), fetchLessonSections(id), fetchLessonProgressDetail(id)])
      .then(([lessonData, sectionData, progress]) => {
        if (!active) return;
        const sortedSections = [...sectionData].sort((a, b) => a.order_index - b.order_index);
        setLesson(lessonData);
        setSections(sortedSections);
        setProgressDetail(progress);
      })
      .catch((loadError) => {
        if (!active) return;
        console.warn("Failed to load lesson section", loadError);
        setError("Failed to load lesson content. Please try again.");
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
      if (advanceTimeoutRef.current) {
        window.clearTimeout(advanceTimeoutRef.current);
      }
    };
  }, [id]);

  const currentIndex = useMemo(
    () => sections.findIndex((section) => section.id === sectionId),
    [sections, sectionId]
  );

  useEffect(() => {
    if (!id || !sectionId || sections.length === 0 || currentIndex !== -1) return;
    navigate(`/lessons/${id}/${sections[0].id}`, { replace: true });
  }, [id, sectionId, sections, currentIndex, navigate]);

  const totalSections = sections.length;
  const currentSection =
    currentIndex >= 0 && currentIndex < totalSections ? sections[currentIndex] : null;
  const completedSections = progressDetail?.completedSections ?? 0;
  const hasPrevious = currentIndex > 0;
  const hasNext = currentIndex >= 0 && currentIndex < totalSections - 1;
  const quizReady =
    progressDetail?.nextStopType === "quiz" &&
    progressDetail?.quizStatus !== "blocked_hearts";
  const showTakeQuizButton = Boolean(quizReady && currentIndex === totalSections - 1);

  const goToRelative = (delta: number) => {
    if (!id || currentIndex < 0) return;
    const nextIndex = currentIndex + delta;
    if (nextIndex < 0 || nextIndex >= sections.length) return;
    navigate(`/lessons/${id}/${sections[nextIndex].id}`);
  };

  const scheduleAdvanceNavigation = (updated: LessonProgressDetail, requestedSectionId?: string | null) => {
    if (!id) return;
    if (advanceTimeoutRef.current) {
      window.clearTimeout(advanceTimeoutRef.current);
    }

    advanceTimeoutRef.current = window.setTimeout(() => {
      if (requestedSectionId) {
        const requestedIndex = sections.findIndex((section) => section.id === requestedSectionId);
        // Allow jump only to stops that are unlocked after this completion.
        if (requestedIndex >= 0 && requestedIndex <= updated.completedSections) {
          navigate(`/lessons/${id}/${requestedSectionId}`);
          return;
        }
      }

      if (updated.nextSectionId) {
        navigate(`/lessons/${id}/${updated.nextSectionId}`);
      }
    }, ADVANCE_NAV_DELAY_MS);
  };

  const completeCurrentSection = async (): Promise<LessonProgressDetail | null> => {
    if (!id || !currentSection || currentIndex < 0) return null;
    const completedIndex = currentIndex;
    const optimisticNextIndex = completedIndex + 1 < sections.length ? completedIndex + 1 : -1;
    setAnimatingCompleteIndex(completedIndex);
    setNextPulseIndex(optimisticNextIndex >= 0 ? optimisticNextIndex : null);
    setIsSaving(true);
    setSaveError(null);
    try {
      const updated = await completeLessonSection(id, currentSection.id);
      setProgressDetail(updated);
      const nextIndex = updated.nextSectionId
        ? sections.findIndex((section) => section.id === updated.nextSectionId)
        : -1;
      if (nextIndex >= 0) {
        setNextPulseIndex(nextIndex);
      }
      return updated;
    } catch (saveProgressError) {
      console.warn("Failed to save lesson progress", saveProgressError);
      if (saveProgressError instanceof ApiError && saveProgressError.status === 409) {
        setSaveError(saveProgressError.message || "Complete earlier sections before this one.");
      } else if (saveProgressError instanceof Error) {
        setSaveError(saveProgressError.message);
      } else {
        setSaveError("Unable to save progress right now. Please try again.");
      }
      return null;
    } finally {
      setIsSaving(false);
    }
  };

  const handleDownAdvance = async () => {
    if (isSaving || currentIndex < 0) return;
    if (currentIndex < completedSections) {
      goToRelative(1);
      return;
    }
    const updated = await completeCurrentSection();
    if (!updated) return;
    scheduleAdvanceNavigation(updated);
  };

  const lockNavigationGesture = () => {
    gestureLockRef.current = true;
    window.setTimeout(() => {
      gestureLockRef.current = false;
    }, NAV_GESTURE_LOCK_MS);
  };

  const handleSnapNavigate = (direction: 1 | -1) => {
    if (gestureLockRef.current || isSaving || currentIndex < 0) {
      return;
    }
    lockNavigationGesture();
    if (direction > 0) {
      void handleDownAdvance();
      return;
    }
    goToRelative(-1);
  };

  const handleRailWheel = (event: React.WheelEvent<HTMLDivElement>) => {
    if (Math.abs(event.deltaY) < WHEEL_DELTA_THRESHOLD) {
      return;
    }
    event.preventDefault();
    handleSnapNavigate(event.deltaY > 0 ? 1 : -1);
  };

  const handleRailTouchStart = (event: React.TouchEvent<HTMLDivElement>) => {
    touchStartYRef.current = event.touches[0]?.clientY ?? null;
  };

  const handleRailTouchEnd = (event: React.TouchEvent<HTMLDivElement>) => {
    const startY = touchStartYRef.current;
    const endY = event.changedTouches[0]?.clientY ?? null;
    touchStartYRef.current = null;
    if (startY == null || endY == null) {
      return;
    }
    const delta = startY - endY;
    if (Math.abs(delta) < SWIPE_DELTA_THRESHOLD) {
      return;
    }
    handleSnapNavigate(delta > 0 ? 1 : -1);
  };

  const handleStopSelect = async (targetIndex: number) => {
    if (!id || isSaving || targetIndex < 0 || targetIndex >= sections.length) {
      return;
    }

    const targetSectionId = sections[targetIndex].id;
    if (targetIndex <= completedSections) {
      navigate(`/lessons/${id}/${targetSectionId}`);
      return;
    }

    // If user is on an already completed section, move to the current unlocked stop.
    if (currentIndex < completedSections) {
      const unlocked = sections[completedSections];
      if (unlocked) {
        navigate(`/lessons/${id}/${unlocked.id}`);
      }
      return;
    }

    // Mirror down-button behavior: save current progress first, then navigate.
    const updated = await completeCurrentSection();
    if (!updated) return;
    scheduleAdvanceNavigation(updated, targetSectionId);
  };

  const railOffsetX = (index: number) =>
    index % 2 === 0 ? -STEP_HORIZONTAL_OFFSET : STEP_HORIZONTAL_OFFSET;

  if (isLoading) {
    return (
      <MainLayout className="overflow-hidden">
        <div className="w-full px-4 lg:px-8 py-16 text-center text-mainAccent">
          Loading lesson...
        </div>
      </MainLayout>
    );
  }

  if (error || !lesson || !progressDetail) {
    return (
      <MainLayout className="overflow-hidden">
        <div className="w-full px-4 lg:px-8 py-10">
          <div className="rounded-2xl p-6 text-center space-y-4">
            <p className="text-red-200">{error ?? "Unable to load this lesson."}</p>
            <Button asChild className="duo-button-primary">
              <Link to="/lessons">Back to Lesson Hub</Link>
            </Button>
          </div>
        </div>
      </MainLayout>
    );
  }

  if (!currentSection) {
    return (
      <MainLayout className="overflow-hidden">
        <div className="w-full px-4 lg:px-8 py-10">
          <div className="text-center space-y-4">
            <p className="text-white/80">This lesson does not have any readable sections yet.</p>
            <Button asChild className="duo-button-primary">
              <Link to={`/lessons/${id}`}>Back to Lesson</Link>
            </Button>
          </div>
        </div>
      </MainLayout>
    );
  }

  return (
    <MainLayout className="overflow-hidden">
      <div className="w-full h-full overflow-hidden px-4 lg:px-8 py-6 pb-28 lg:pb-10">
        <Link
          to={`/lessons/${id}`}
          className="inline-flex items-center text-mainAccent hover:text-white"
        >
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Lesson Details
        </Link>

        <div
          className="mt-4 mx-auto max-w-[1420px] grid lg:grid-cols-[170px_minmax(0,1fr)] gap-10 items-start lg:h-[calc(100dvh-8.5rem)]"
          onWheel={handleRailWheel}
          onTouchStart={handleRailTouchStart}
          onTouchEnd={handleRailTouchEnd}
        >
          <aside className="hidden lg:flex flex-col items-center pt-4 sticky top-24">
            <button
              type="button"
              aria-label="Previous step"
              onClick={() => goToRelative(-1)}
              disabled={!hasPrevious || isSaving}
              className="h-9 w-9 rounded-full border border-mainAlt bg-main text-mainAccent disabled:opacity-40 mb-3"
            >
              <ArrowUp className="h-4 w-4 mx-auto" />
            </button>

            <div className="relative h-[460px] w-[120px] overflow-hidden">
              {sections.map((section, index) => {
                const relative = index - currentIndex;
                const distance = Math.abs(relative);
                const isVisible = distance <= MAX_VISIBLE_DISTANCE;
                const opacity = distance === 0 ? 1 : distance === 1 ? 0.72 : distance === 2 ? 0.42 : 0.2;
                const scale = distance === 0 ? 1 : distance === 1 ? 0.86 : distance === 2 ? 0.72 : 0.6;
                const y = relative * STEP_GAP;
                const x = railOffsetX(index);
                const isCompleted = index < completedSections;
                const isCurrent = relative === 0;
                const isAnimatingComplete = animatingCompleteIndex === index;
                const isNextPulse = nextPulseIndex === index;

                const baseClasses = isCompleted
                  ? "bg-duoGreen border-[#b51f3d] text-white shadow-mainCircleShadow"
                  : isCurrent
                    ? "bg-mainAccent border-mainAccent text-main shadow-mainCircleShadow"
                    : "bg-main border-mainAlt text-white/85 shadow-mainCircleShadow";

                return (
                  <div
                    key={section.id}
                    className={cn("absolute left-1/2 top-1/2 transition-all duration-500", !isVisible && "pointer-events-none")}
                    style={{
                      transform: `translate(-50%, -50%) translate(${x}px, ${y}px) scale(${scale})`,
                      opacity: isVisible ? opacity : 0,
                    }}
                  >
                    <button
                      type="button"
                      onClick={() => {
                        void handleStopSelect(index);
                      }}
                      disabled={isSaving}
                      className={cn(
                        "relative h-16 w-[68px] rounded-full border-2 flex items-center justify-center transition-transform duration-200 active:translate-y-[5px] active:shadow-none",
                        baseClasses,
                        isCurrent && "animate-stop-current ring-4 ring-mainAccent/25",
                        isAnimatingComplete && "animate-stop-fill",
                        isNextPulse && "animate-stop-next"
                      )}
                    >
                      {isCompleted ? (
                        <Check className="h-6 w-6" />
                      ) : isCurrent ? (
                        <Star className="h-6 w-6 fill-current" />
                      ) : (
                        <span className="text-lg">{index + 1}</span>
                      )}
                    </button>
                  </div>
                );
              })}
            </div>

            <button
              type="button"
              aria-label="Next step"
              onClick={handleDownAdvance}
              disabled={isSaving || (currentIndex < completedSections && !hasNext)}
              className="h-9 w-9 rounded-full border border-mainAlt bg-main text-mainAccent disabled:opacity-40 mt-3"
            >
              <ArrowDown className="h-4 w-4 mx-auto" />
            </button>
          </aside>

          <section className="min-w-0 max-w-4xl w-full justify-self-center pt-6 lg:h-full lg:flex lg:flex-col overflow-hidden">
            <h2 className="text-4xl text-white mb-7 flex-none">{currentSection.title}</h2>
            <div className="space-y-4 text-lg leading-9 text-white/95 min-h-[48dvh] lg:min-h-0 lg:flex-1 lg:pr-2">
              {currentSection.content
                .split("\n")
                .filter((line) => line.trim().length > 0)
                .map((line, index) => (
                  <p key={`${currentSection.id}-${index}`}>{line}</p>
                ))}
            </div>
          </section>
        </div>

        {saveError ? (
          <div className="rounded-xl p-3 text-sm text-red-200 mt-4">
            {saveError}
          </div>
        ) : null}

        {showTakeQuizButton ? (
          <div className="fixed inset-x-0 bottom-[calc(var(--bottom-nav-height)+var(--safe-area-bottom)+0.75rem)] lg:bottom-6 z-30 flex justify-center px-4 pointer-events-none">
            <Button
              type="button"
              onClick={() => navigate(`/lessons/${id}/quiz`)}
              className="duo-button-primary h-12 px-8 w-full max-w-sm pointer-events-auto"
            >
              Take Quiz
            </Button>
          </div>
        ) : null}
      </div>
    </MainLayout>
  );
};

export default LessonSectionPage;
