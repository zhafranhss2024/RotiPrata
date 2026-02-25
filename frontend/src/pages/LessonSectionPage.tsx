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

  const goToRelative = (delta: number) => {
    if (!id || currentIndex < 0) return;
    const nextIndex = currentIndex + delta;
    if (nextIndex < 0 || nextIndex >= sections.length) return;
    navigate(`/lessons/${id}/${sections[nextIndex].id}`);
  };

  const handleCompleteSection = async () => {
    if (!id || !currentSection || currentIndex < 0) return;
    setIsSaving(true);
    setSaveError(null);
    try {
      const updated = await completeLessonSection(id, currentSection.id);
      setProgressDetail(updated);

      const completedIndex = currentIndex;
      const nextIndex = updated.nextSectionId
        ? sections.findIndex((section) => section.id === updated.nextSectionId)
        : -1;

      setAnimatingCompleteIndex(completedIndex);
      setNextPulseIndex(nextIndex >= 0 ? nextIndex : null);

      if (advanceTimeoutRef.current) {
        window.clearTimeout(advanceTimeoutRef.current);
      }

      advanceTimeoutRef.current = window.setTimeout(() => {
        if (updated.nextStopType === "quiz") {
          navigate(`/lessons/${id}/quiz`);
        } else if (updated.nextSectionId) {
          navigate(`/lessons/${id}/${updated.nextSectionId}`);
        } else {
          navigate(`/lessons/${id}`);
        }
      }, 650);
    } catch (saveProgressError) {
      console.warn("Failed to save lesson progress", saveProgressError);
      if (saveProgressError instanceof ApiError && saveProgressError.status === 409) {
        setSaveError(saveProgressError.message || "Complete earlier sections before this one.");
      } else if (saveProgressError instanceof Error) {
        setSaveError(saveProgressError.message);
      } else {
        setSaveError("Unable to save progress right now. Please try again.");
      }
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
    await handleCompleteSection();
  };

  const relativeOffsetX = (relative: number) => {
    if (relative === 0) return 0;
    const distance = Math.abs(relative);
    const magnitude = 18 + (distance % 3) * 8;
    if (relative < 0) {
      return distance % 2 === 1 ? -magnitude : magnitude;
    }
    return distance % 2 === 1 ? magnitude : -magnitude;
  };

  if (isLoading) {
    return (
      <MainLayout>
        <div className="w-full px-4 lg:px-8 py-16 text-center text-mainAccent">
          Loading lesson...
        </div>
      </MainLayout>
    );
  }

  if (error || !lesson || !progressDetail) {
    return (
      <MainLayout>
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
      <MainLayout>
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
    <MainLayout>
      <div className="w-full px-4 lg:px-8 py-6">
        <Link
          to={`/lessons/${id}`}
          className="inline-flex items-center text-mainAccent hover:text-white"
        >
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Lesson Details
        </Link>

        <div className="mt-4 mx-auto max-w-[1420px] grid lg:grid-cols-[170px_minmax(0,1fr)] gap-10 items-start">
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
                const x = relativeOffsetX(relative);
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
                      onClick={() => navigate(`/lessons/${id}/${section.id}`)}
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

          <section className="min-w-0 max-w-4xl w-full justify-self-center pt-6">
            <h2 className="text-4xl text-white mb-7">{currentSection.title}</h2>
            <div className="space-y-4 text-lg leading-9 text-white/95 min-h-[48dvh]">
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
      </div>
    </MainLayout>
  );
};

export default LessonSectionPage;
