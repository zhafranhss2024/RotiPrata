import React, { useEffect, useMemo, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Check, Clock3, Play, Star, Users } from 'lucide-react';
import type { Lesson, LessonProgressDetail } from '@/types';
import {
  enrollLesson,
  fetchLessonById,
  fetchLessonProgressDetail,
  fetchLessonSections,
  saveLesson,
} from '@/lib/api';
import { cn } from '@/lib/utils';
import Chatbot from '@/components/ui/chatbot';

const formatRefill = (value?: string | null) => {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleString();
};

const QUIZ_STOP_ID = 'quiz';
const QUIZ_STOP_LABEL = 'Quiz';

const normalizeLabel = (value: string | null | undefined) => {
  const normalized = value?.trim();
  return normalized && normalized.length > 0 ? normalized : null;
};

const sectionOrderValue = (orderIndex: number | null | undefined) =>
  typeof orderIndex === 'number' && Number.isFinite(orderIndex)
    ? orderIndex
    : Number.MAX_SAFE_INTEGER;

const LessonDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [lesson, setLesson] = useState<Lesson | null>(null);
  const [sectionStops, setSectionStops] = useState<Array<{ id: string; label: string }>>([]);
  const [progressDetail, setProgressDetail] = useState<LessonProgressDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isStarting, setIsStarting] = useState(false);

  useEffect(() => {
    if (!id) return;
    let active = true;

    Promise.all([fetchLessonById(id), fetchLessonProgressDetail(id), fetchLessonSections(id)])
      .then(([lessonData, progress, sectionData]) => {
        if (!active) return;
        const sortedSections = [...sectionData].sort(
          (left, right) => sectionOrderValue(left.order_index) - sectionOrderValue(right.order_index)
        );
        const normalizedSections = sortedSections.map((section, index) => ({
          id: section.id,
          label: normalizeLabel(section.title) ?? `Topic ${index + 1}`,
        }));
        setLesson(lessonData);
        setProgressDetail(progress);
        setSectionStops(normalizedSections);
      })
      .catch((loadError) => {
        if (!active) return;
        console.warn('Failed to load lesson detail', loadError);
        setError('Failed to load lesson details. Please try again.');
      })
      .finally(() => {
        if (active) {
          setIsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [id]);

  const handleSave = async () => {
    if (!id) return;
    try {
      await saveLesson(id);
    } catch (saveError) {
      console.warn('Save lesson failed', saveError);
    }
  };

  const getDifficultyLabel = (level: number) => {
    switch (level) {
      case 1:
        return 'Beginner';
      case 2:
        return 'Intermediate';
      case 3:
        return 'Advanced';
      default:
        return 'Beginner';
    }
  };

  const displayStops = useMemo(() => {
    const sectionList = [...sectionStops];
    const fallbackStopCount = sectionList.length > 0 ? sectionList.length : 1;
    const totalStopsFromProgress = Math.max(1, progressDetail?.totalStops ?? fallbackStopCount);
    if (totalStopsFromProgress > sectionList.length) {
      sectionList.push({ id: QUIZ_STOP_ID, label: QUIZ_STOP_LABEL });
    }
    while (sectionList.length < totalStopsFromProgress) {
      sectionList.push({
        id: `topic-${sectionList.length + 1}`,
        label: `Topic ${sectionList.length + 1}`,
      });
    }
    return sectionList.slice(0, totalStopsFromProgress);
  }, [progressDetail?.totalStops, sectionStops]);

  if (isLoading) {
    return (
      <MainLayout>
        <div className="mx-auto max-w-3xl px-4 py-16 text-center text-mainAccent">Loading lesson...</div>
      </MainLayout>
    );
  }

  if (error || !lesson) {
    return (
      <MainLayout>
        <div className="mx-auto max-w-3xl px-4 py-10">
          <div className="rounded-2xl p-6 text-center space-y-4">
            <p className="text-statusStrong">{error ?? 'Unable to load this lesson.'}</p>
            <Button asChild className="duo-button-primary">
              <Link to="/lessons">Back to Lessons</Link>
            </Button>
          </div>
        </div>
      </MainLayout>
    );
  }

  const isEnrolled = progressDetail?.isEnrolled ?? false;
  const progress = progressDetail?.progressPercentage ?? 0;
  const completedStops = progressDetail?.completedStops ?? 0;
  const currentStopId = progressDetail?.currentStopId ?? null;
  const heartsRefill = formatRefill(progressDetail?.heartsRefillAt);
  const quizBlocked = progressDetail?.quizStatus === 'blocked_hearts';

  const resolveNextPath = (detail: LessonProgressDetail | null) => {
    if (!id || !detail) return null;
    if (detail.status === 'completed') {
      const firstSectionId = sectionStops[0]?.id;
      return firstSectionId ? `/lessons/${id}/${firstSectionId}` : null;
    }
    if (detail.nextStopType === 'quiz') {
      return `/lessons/${id}/quiz`;
    }
    if (detail.nextSectionId) {
      return `/lessons/${id}/${detail.nextSectionId}`;
    }
    if (detail.currentSection) {
      return `/lessons/${id}/${detail.currentSection}`;
    }
    return null;
  };

  const ctaLabel =
    progressDetail?.status === 'completed'
      ? 'Review Lesson'
      : progressDetail?.nextStopType === 'quiz'
        ? progressDetail.quizStatus === 'in_progress'
          ? 'Continue Quiz'
          : 'Start Quiz'
        : progress > 0
          ? 'Continue Lesson'
          : 'Start Lesson';

  const handleStartLesson = async () => {
    if (!id) return;
    setIsStarting(true);
    setError(null);
    try {
      let detail = progressDetail;
      if (!isEnrolled) {
        await enrollLesson(id);
        detail = await fetchLessonProgressDetail(id);
        setProgressDetail(detail);
      }

      const nextPath = resolveNextPath(detail);
      if (nextPath) {
        navigate(nextPath);
      } else {
        setError('Lesson sections are not available yet');
      }
    } catch (startError) {
      console.warn('Unable to start lesson', startError);
      setError('Unable to start lesson right now. Please try again.');
    } finally {
      setIsStarting(false);
    }
  };

  return (
    <MainLayout>
      <div className="mx-auto w-full max-w-5xl px-4 py-6">
        <Link to="/lessons" className="inline-flex items-center text-mainAccent hover:text-mainAccent dark:hover:text-white">
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Lessons
        </Link>

        <section className="mx-auto mt-6 max-w-2xl rounded-3xl px-6 py-7">
          <div className="flex items-start justify-between gap-3">
            <span className="rounded-full border border-mainAlt bg-mainDark px-3 py-1 text-xs text-mainAccent">
              {getDifficultyLabel(lesson.difficulty_level)}
            </span>
            <button
              type="button"
              onClick={handleSave}
              className="rounded-full border border-mainAlt px-3 py-1 text-xs text-mainAccent dark:text-white hover:bg-mainAlt"
            >
              Save
            </button>
          </div>

          <h1 className="mt-4 text-4xl text-mainAccent dark:text-white leading-tight">{lesson.title}</h1>
          <p className="mt-2 text-mainAccent/80 dark:text-white/80 leading-7">{lesson.summary ?? lesson.description}</p>

          <div className="mt-5 flex flex-wrap gap-2 text-sm">
            <span className="inline-flex items-center gap-1 rounded-full border border-mainAlt px-3 py-1 text-mainAccent dark:text-white">
              <Clock3 className="h-4 w-4" />
              {lesson.estimated_minutes}m
            </span>
            <span className="inline-flex items-center gap-1 rounded-full border border-mainAlt px-3 py-1 text-mainAccent dark:text-white">
              <Star className="h-4 w-4" />
              {lesson.xp_reward} XP
            </span>
            <span className="inline-flex items-center gap-1 rounded-full border border-mainAlt px-3 py-1 text-mainAccent dark:text-white">
              <Users className="h-4 w-4" />
              {lesson.completion_count} learners
            </span>
          </div>

          <div className="mt-6 rounded-2xl p-4">
            <div className="flex items-center justify-between">
              <p className="text-sm uppercase tracking-wide text-mainAccent">Path Progress</p>
              <span className="text-sm text-mainAccent dark:text-white">{progress}%</span>
            </div>

            <div className="mt-3 overflow-x-auto pb-1 scrollbar-hide">
              <div className="flex min-w-max items-start">
                {displayStops.map((stop, index) => {
                  const isDone = index < completedStops;
                  const isCurrent =
                    !isDone &&
                    (currentStopId === stop.id ||
                      (!currentStopId && index === completedStops && completedStops < displayStops.length));
                  return (
                    <React.Fragment key={stop.id}>
                      <div className="w-[76px] sm:w-[90px]">
                        <div
                          className={cn(
                            'mx-auto h-10 w-10 rounded-full border flex items-center justify-center text-xs',
                            isDone
                              ? 'border-[#b51f3d] bg-duoGreen text-white'
                              : isCurrent
                                ? 'border-mainAccent bg-mainAccent text-main'
                                : 'border-mainAlt text-mainAccent'
                          )}
                          title={stop.label}
                        >
                          {isDone ? <Check className="h-4 w-4" /> : index + 1}
                        </div>
                        <p
                          className="mt-2 min-h-[2.5rem] px-1 text-center text-[10px] leading-4 text-mainAccent/85 break-words dark:text-white/85 sm:text-[11px]"
                          title={stop.label}
                        >
                          {stop.label}
                        </p>
                      </div>
                      {index < displayStops.length - 1 ? (
                        <div
                          className={cn(
                            'mx-1 mt-5 h-[2px] w-5 sm:w-8',
                            index < completedStops - 1 ? 'bg-duoGreen' : 'bg-mainAlt'
                          )}
                        />
                      ) : null}
                    </React.Fragment>
                  );
                })}
              </div>
            </div>

          </div>

          <div className="mt-6">
            {quizBlocked && isEnrolled ? (
              <Button className="w-full h-14 text-base" disabled>
                {heartsRefill ? `No hearts left. Refill at ${heartsRefill}` : 'No hearts left. Try again later.'}
              </Button>
            ) : (
              <Button
                onClick={() => {
                  void handleStartLesson();
                }}
                className="w-full h-14 duo-button-primary text-base"
                disabled={isStarting}
              >
                <Play className="h-5 w-5 mr-2" />
                {isStarting ? 'Starting...' : ctaLabel}
              </Button>
            )}
          </div>
        </section>

      </div>
      <Chatbot />
    </MainLayout>
  );
};

export default LessonDetailPage;
