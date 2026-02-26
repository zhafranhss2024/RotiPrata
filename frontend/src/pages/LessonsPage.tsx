import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { Check, Flame, Lock, Star } from 'lucide-react';
import type { LessonHubLesson, LessonHubResponse } from '@/types';
import { fetchLessonHub } from '@/lib/api';
import { cn } from '@/lib/utils';

const HORIZONTAL_SWING = 92;
const NODE_STEP_HEIGHT = 104;
const PATH_WIDTH = 320;
const PATH_CENTER_X = PATH_WIDTH / 2;
const NODE_CONNECTOR_GAP = 42;

const nodeOffsetX = (index: number) => (index % 2 === 0 ? -HORIZONTAL_SWING : HORIZONTAL_SWING);

const DottedPath = ({ lessonCount }: { lessonCount: number }) => {
  if (lessonCount < 2) return null;
  const points = Array.from({ length: lessonCount }, (_, index) => ({
    x: PATH_CENTER_X + nodeOffsetX(index),
    y: index * NODE_STEP_HEIGHT + NODE_STEP_HEIGHT / 2,
  }));
  const segments = points
    .slice(0, -1)
    .map((startPoint, index) => {
      const endPoint = points[index + 1];
      const dx = endPoint.x - startPoint.x;
      const dy = endPoint.y - startPoint.y;
      const distance = Math.hypot(dx, dy);
      if (distance <= NODE_CONNECTOR_GAP * 2) {
        return null;
      }
      const ux = dx / distance;
      const uy = dy / distance;
      return {
        x1: startPoint.x + ux * NODE_CONNECTOR_GAP,
        y1: startPoint.y + uy * NODE_CONNECTOR_GAP,
        x2: endPoint.x - ux * NODE_CONNECTOR_GAP,
        y2: endPoint.y - uy * NODE_CONNECTOR_GAP,
      };
    })
    .filter(
      (
        segment
      ): segment is {
        x1: number;
        y1: number;
        x2: number;
        y2: number;
      } => segment !== null
    );
  const height = lessonCount * NODE_STEP_HEIGHT;

  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-0"
      width={PATH_WIDTH}
      height={height}
      viewBox={`0 0 ${PATH_WIDTH} ${height}`}
    >
      {segments.map((segment, index) => (
        <line
          key={`connector-${index}`}
          x1={segment.x1}
          y1={segment.y1}
          x2={segment.x2}
          y2={segment.y2}
          stroke="rgba(98, 157, 255, 0.75)"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeDasharray="2 10"
        />
      ))}
    </svg>
  );
};

const LessonNode = ({ lesson, index }: { lesson: LessonHubLesson; index: number }) => {
  const isCurrent = lesson.current;
  const isCompleted = lesson.completed;
  const isLocked = lesson.visuallyLocked;
  const ringProgress = Math.max(
    0,
    Math.min(100, Math.round(isCompleted ? 100 : lesson.progressPercentage ?? 0))
  );
  const ringFillDeg = `${ringProgress * 3.6}deg`;

  const classes = isLocked
    ? 'border-duoGrayBorder text-mainAccent/45 dark:text-white/65'
    : isCompleted
      ? 'bg-duoGreen border-[#b51f3d] text-white'
      : isCurrent
        ? 'bg-mainAccent border-mainAccent text-main'
        : 'border-mainAlt text-mainAccent dark:text-white';

  return (
    <div
      className="absolute left-0 right-0 h-20 flex justify-center"
      style={{ top: index * NODE_STEP_HEIGHT, transform: `translateX(${nodeOffsetX(index)}px)` }}
    >
      <Link to={`/lessons/${lesson.lessonId}`} aria-label={lesson.title} className="relative group focus:outline-none">
        <div
          className={cn('h-[74px] w-[76px] rounded-full p-[3px] transition-transform', isLocked ? 'opacity-80' : '')}
          style={{ background: `conic-gradient(#fe2c55 ${ringFillDeg}, rgba(246, 139, 155, 0.35) ${ringFillDeg} 360deg)` }}
        >
          <div
          className={cn(
            'h-16 w-[68px] rounded-full border-2 flex items-center justify-center transition-transform hover:scale-[1.03] active:translate-y-[5px] active:shadow-none',
            classes
          )}
        >
          {isCompleted ? (
            <Check className="h-6 w-6" />
          ) : isLocked ? (
            <Lock className="h-5 w-5" />
          ) : isCurrent ? (
            <Star className="h-6 w-6 fill-current" />
          ) : (
            <span className="text-lg">{index + 1}</span>
          )}
        </div>
        </div>
        <div className="pointer-events-none absolute z-30 -top-9 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-xl bg-mainDark px-3 py-1 text-xs text-mainAccent opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100">
          {lesson.title}
        </div>
      </Link>
    </div>
  );
};

const LessonsPage = () => {
  const [hub, setHub] = useState<LessonHubResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    fetchLessonHub()
      .then((data) => {
        if (!active) return;
        setHub(data);
      })
      .catch((loadError) => {
        if (!active) return;
        console.warn('Failed to load lesson hub', loadError);
        setError('Unable to load lesson hub right now.');
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const summary = useMemo(
    () => ({
      totalLessons: hub?.summary.totalLessons ?? 0,
      completedLessons: hub?.summary.completedLessons ?? 0,
      currentStreak: hub?.summary.currentStreak ?? 0,
    }),
    [hub]
  );

  return (
    <MainLayout>
      <div className="mx-auto w-full max-w-4xl px-4 pt-2 pb-24 lg:pt-4 lg:pb-8 space-y-4">
        <section className="p-2">
          <p className="text-mainAccent text-xs uppercase tracking-wide">Lesson Path</p>
          <h1 className="text-3xl text-mainAccent dark:text-white leading-tight mt-1">Keep Moving Forward</h1>
          <div className="mt-3 flex flex-wrap gap-2">
            <span className="inline-flex items-center gap-1 rounded-full bg-mainAlt/40 px-3 py-1 text-xs text-mainAccent dark:text-white/90">
              Lessons {summary.totalLessons}
            </span>
            <span className="inline-flex items-center gap-1 rounded-full bg-mainAlt/40 px-3 py-1 text-xs text-mainAccent dark:text-white/90">
              Completed {summary.completedLessons}
            </span>
            <span className="inline-flex items-center gap-1 rounded-full bg-mainAlt/40 px-3 py-1 text-xs text-mainAccent dark:text-white/90">
              <Flame className="h-3.5 w-3.5 text-primary" />
              {summary.currentStreak}
            </span>
          </div>
        </section>

        {isLoading && (
          <div className="space-y-4">
            {Array.from({ length: 2 }).map((_, idx) => (
              <div key={idx} className="rounded-2xl p-5 animate-pulse">
                <div className="h-5 w-36 rounded bg-mainAlt/70" />
                <div className="mt-4 h-56 rounded bg-mainAlt/20" />
              </div>
            ))}
          </div>
        )}

        {!isLoading && error && (
          <div className="rounded-2xl p-4 text-sm text-statusStrong">{error}</div>
        )}

        {!isLoading && !error && hub && (
          <div className="space-y-6">
            {hub.units.map((unit) => (
              <section key={unit.unitId} className="space-y-3">
                <div className="py-5 overflow-visible">
                  <div
                    className="relative mx-auto"
                    style={{ width: PATH_WIDTH, height: unit.lessons.length * NODE_STEP_HEIGHT }}
                  >
                    <DottedPath lessonCount={unit.lessons.length} />
                    {unit.lessons.map((lesson, index) => (
                      <LessonNode key={lesson.lessonId} lesson={lesson} index={index} />
                    ))}
                  </div>
                </div>
              </section>
            ))}
          </div>
        )}
      </div>
    </MainLayout>
  );
};

export default LessonsPage;
