import React, { useDeferredValue, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Clock3, Flame, Play, Search, Sparkles, Star, Users } from 'lucide-react';
import { MainLayout } from '@/components/layout/MainLayout';
import type { Lesson, LessonHubCategory, LessonHubLesson, LessonHubResponse } from '@/types';
import { fetchLessonHub, searchLessons } from '@/lib/api';
import { cn } from '@/lib/utils';
import Chatbot from '@/components/ui/chatbot';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

const ALL_CATEGORIES_KEY = '__all__';
const UNCATEGORIZED_KEY = '__uncategorized__';
const DEFAULT_CATEGORY_COLOR = '#629dff';
const CULTURAL_REFERENCE_ACCENT = '#c28a1f';

const categoryKey = (category: LessonHubCategory) => category.categoryId ?? UNCATEGORIZED_KEY;

const resolveCategoryAccent = (category: LessonHubCategory | null | undefined) => {
  if (!category) {
    return DEFAULT_CATEGORY_COLOR;
  }
  if (category.type === 'cultural_reference') {
    return CULTURAL_REFERENCE_ACCENT;
  }
  return category.color ?? DEFAULT_CATEGORY_COLOR;
};

const getReadableTextColor = (hexColor: string) => {
  const normalized = hexColor.replace('#', '').trim();
  const expanded = normalized.length === 3
    ? normalized
        .split('')
        .map((char) => `${char}${char}`)
        .join('')
    : normalized;

  if (expanded.length !== 6) {
    return '#ffffff';
  }

  const red = parseInt(expanded.slice(0, 2), 16);
  const green = parseInt(expanded.slice(2, 4), 16);
  const blue = parseInt(expanded.slice(4, 6), 16);
  const luminance = (red * 299 + green * 587 + blue * 114) / 1000;

  return luminance >= 170 ? '#1f2937' : '#ffffff';
};

type FlattenedLesson = LessonHubLesson & {
  categoryId: string | null;
  categoryName: string;
  categoryColor: string | null;
  categoryType: string | null;
  isVirtualCategory: boolean;
};

type VisibleCategory = {
  categoryId: string | null;
  categoryName: string;
  categoryColor: string | null;
  categoryType: string | null;
  isVirtualCategory: boolean;
  lessons: FlattenedLesson[];
};

const getDifficultyLabel = (level: number | null | undefined) => {
  switch (level) {
    case 1:
      return 'Beginner';
    case 2:
      return 'Intermediate';
    case 3:
      return 'Advanced';
    default:
      return 'Lesson';
  }
};

const buildLessonCta = (lesson: LessonHubLesson) => {
  if (lesson.completed) return 'Review Lesson';
  if ((lesson.progressPercentage ?? 0) > 0) return 'Continue Lesson';
  return 'Start Lesson';
};

const buildLessonStatus = (lesson: LessonHubLesson) => {
  if (lesson.completed) return 'Completed';
  if ((lesson.progressPercentage ?? 0) > 0) return `${Math.round(lesson.progressPercentage)}% complete`;
  if (lesson.current) return 'Pick up here';
  return 'Ready to start';
};

const LessonFeedCard = ({ lesson }: { lesson: FlattenedLesson }) => {
  const progress = Math.max(0, Math.min(100, Math.round(lesson.progressPercentage ?? 0)));
  const href = `/lessons/${lesson.lessonId}`;
  const statusLabel = buildLessonStatus(lesson);
  const ctaLabel = buildLessonCta(lesson);
  const accentColor = resolveCategoryAccent({
    categoryId: lesson.categoryId,
    name: lesson.categoryName,
    type: lesson.categoryType,
    color: lesson.categoryColor,
    isVirtual: lesson.isVirtualCategory,
    lessons: [],
  });

  return (
    <article className="overflow-hidden rounded-[28px] border border-mainAlt/35 bg-white/90 shadow-sm transition hover:shadow-md dark:bg-black/20">
      <div className="grid gap-4 p-5 lg:grid-cols-[minmax(0,1fr)_220px]">
        <Link
          to={href}
          className="min-w-0 rounded-[24px] text-left transition hover:bg-mainAlt/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <div className="flex flex-wrap items-center gap-2">
            <span
              className="inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold text-mainAccent dark:text-white"
              style={{ backgroundColor: `${accentColor}22` }}
            >
              {lesson.categoryName}
            </span>
            <span className="inline-flex items-center rounded-full border border-mainAlt/35 px-3 py-1 text-xs font-semibold text-mainAccent/80 dark:text-white/80">
              {getDifficultyLabel(lesson.difficultyLevel)}
            </span>
            <span className="inline-flex items-center gap-1 rounded-full border border-mainAlt/40 px-3 py-1 text-xs text-mainAccent/80 dark:text-white/80">
              {lesson.completed ? <CheckCircle2 className="h-3.5 w-3.5 text-primary" /> : <Sparkles className="h-3.5 w-3.5 text-primary" />}
              {statusLabel}
            </span>
          </div>

          <h3 className="mt-4 text-xl font-semibold leading-tight text-mainAccent dark:text-white">{lesson.title}</h3>
          {lesson.summary ? (
            <p className="mt-2 line-clamp-3 max-w-3xl text-sm leading-6 text-mainAccent/75 dark:text-white/75">
              {lesson.summary}
            </p>
          ) : null}

          <div className="mt-5">
            <div className="flex items-center justify-between text-xs text-mainAccent/75 dark:text-white/75">
              <span>Progress</span>
              <span>{progress}%</span>
            </div>
            <div className="mt-2 h-2 rounded-full bg-mainAlt/20">
              <div
                className="h-full rounded-full transition-all"
                style={{ width: `${progress}%`, backgroundColor: accentColor }}
              />
            </div>
          </div>

          <div className="mt-5 flex flex-wrap gap-2 text-xs text-mainAccent/80 dark:text-white/80">
            <span className="inline-flex items-center gap-1 rounded-full border border-mainAlt/35 px-3 py-1">
              <Clock3 className="h-3.5 w-3.5" />
              {lesson.estimatedMinutes ?? 0} min
            </span>
            <span className="inline-flex items-center gap-1 rounded-full border border-mainAlt/35 px-3 py-1">
              <Star className="h-3.5 w-3.5" />
              {lesson.xpReward ?? 0} XP
            </span>
            <span className="inline-flex items-center gap-1 rounded-full border border-mainAlt/35 px-3 py-1">
              <Users className="h-3.5 w-3.5" />
              {lesson.completionCount ?? 0} learners
            </span>
          </div>
        </Link>

        <div className="flex flex-col justify-between rounded-[24px] border border-mainAlt/25 bg-mainAlt/10 p-4">
          <div>
            <p className="text-xs uppercase tracking-wide text-mainAccent/65 dark:text-white/65">Category</p>
            <p className="mt-1 text-lg font-semibold text-mainAccent dark:text-white">{lesson.categoryName}</p>
          </div>

          <div className="mt-5">
            <Button asChild className="w-full duo-button-primary">
              <Link to={href}>
                <Play className="mr-2 h-4 w-4" />
                {ctaLabel}
              </Link>
            </Button>
          </div>
        </div>
      </div>
    </article>
  );
};

const LessonsPage = () => {
  const [hub, setHub] = useState<LessonHubResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedCategoryId, setSelectedCategoryId] = useState<string>(ALL_CATEGORIES_KEY);
  const [searchQuery, setSearchQuery] = useState('');
  const [matchedLessonIds, setMatchedLessonIds] = useState<Set<string> | null>(null);
  const [resolvedSearchQuery, setResolvedSearchQuery] = useState('');
  const deferredSearchQuery = useDeferredValue(searchQuery.trim());

  useEffect(() => {
    let active = true;
    fetchLessonHub()
      .then((data) => {
        if (!active) return;
        setHub(data);
        setError(null);
      })
      .catch((loadError) => {
        if (!active) return;
        console.warn('Failed to load lesson hub', loadError);
        setError('Unable to load lessons right now.');
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    if (!deferredSearchQuery) {
      return () => {
        active = false;
      };
    }

    searchLessons(deferredSearchQuery)
      .then((results: Lesson[]) => {
        if (!active) return;
        setMatchedLessonIds(new Set(results.map((lesson) => lesson.id)));
        setResolvedSearchQuery(deferredSearchQuery);
      })
      .catch((searchError) => {
        if (!active) return;
        console.warn('Failed to search lessons', searchError);
        setMatchedLessonIds(new Set());
        setResolvedSearchQuery(deferredSearchQuery);
      });

    return () => {
      active = false;
    };
  }, [deferredSearchQuery]);

  const summary = useMemo(
    () => ({
      totalLessons: hub?.summary.totalLessons ?? 0,
      completedLessons: hub?.summary.completedLessons ?? 0,
      currentStreak: hub?.summary.currentStreak ?? 0,
    }),
    [hub]
  );

  const effectiveSelectedCategoryId = useMemo(() => {
    const availableKeys = new Set((hub?.categories ?? []).map(categoryKey));
    if (selectedCategoryId === ALL_CATEGORIES_KEY || availableKeys.has(selectedCategoryId)) {
      return selectedCategoryId;
    }
    return ALL_CATEGORIES_KEY;
  }, [hub, selectedCategoryId]);

  const effectiveIsSearching = Boolean(deferredSearchQuery) && resolvedSearchQuery !== deferredSearchQuery;
  const effectiveMatchedLessonIds =
    deferredSearchQuery && !effectiveIsSearching
      ? matchedLessonIds
      : null;

  const visibleLessons = useMemo(() => {
    const categories = hub?.categories ?? [];
    const scopedCategories =
      effectiveSelectedCategoryId === ALL_CATEGORIES_KEY
        ? categories
        : categories.filter((category) => categoryKey(category) === effectiveSelectedCategoryId);

    return scopedCategories.flatMap((category) =>
      category.lessons
        .filter((lesson) => effectiveMatchedLessonIds === null || effectiveMatchedLessonIds.has(lesson.lessonId))
        .map(
          (lesson): FlattenedLesson => ({
            ...lesson,
            categoryId: category.categoryId,
            categoryName: category.name,
            categoryColor: category.color,
            categoryType: category.type,
            isVirtualCategory: category.isVirtual,
          })
        )
    );
  }, [effectiveMatchedLessonIds, effectiveSelectedCategoryId, hub]);

  const visibleCategories = useMemo<VisibleCategory[]>(() => {
    const categories = hub?.categories ?? [];
    const scopedCategories =
      effectiveSelectedCategoryId === ALL_CATEGORIES_KEY
        ? categories
        : categories.filter((category) => categoryKey(category) === effectiveSelectedCategoryId);

    return scopedCategories
      .map((category) => ({
        categoryId: category.categoryId,
        categoryName: category.name,
        categoryColor: category.color,
        categoryType: category.type,
        isVirtualCategory: category.isVirtual,
        lessons: category.lessons
          .filter((lesson) => effectiveMatchedLessonIds === null || effectiveMatchedLessonIds.has(lesson.lessonId))
          .map(
            (lesson): FlattenedLesson => ({
              ...lesson,
              categoryId: category.categoryId,
              categoryName: category.name,
              categoryColor: category.color,
              categoryType: category.type,
              isVirtualCategory: category.isVirtual,
            })
          ),
      }))
      .filter((category) => category.lessons.length > 0);
  }, [effectiveMatchedLessonIds, effectiveSelectedCategoryId, hub]);

  const visibleLessonCount = visibleLessons.length;

  return (
    <MainLayout>
      <div className="mx-auto w-full max-w-6xl px-4 pt-3 pb-24 lg:pt-5 lg:pb-8 space-y-5">
        <section className="rounded-[32px] border border-mainAlt/30 bg-white/70 p-5 shadow-sm dark:bg-black/20">
          <p className="text-mainAccent text-xs uppercase tracking-wide">Lessons</p>
          <h1 className="mt-2 text-3xl leading-tight text-mainAccent dark:text-white">Lesson library</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-mainAccent/75 dark:text-white/75">
            Scroll through lessons like a feed, then filter by category or search to narrow the list.
          </p>

          <div className="mt-4 flex flex-wrap gap-2">
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

          <div className="mt-5 relative">
            <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-mainAccent/60 dark:text-white/60" />
            <Input
              type="search"
              value={searchQuery}
              onChange={(event) => {
                const nextQuery = event.target.value;
                setSearchQuery(nextQuery);
                if (!nextQuery.trim()) {
                  setMatchedLessonIds(null);
                  setResolvedSearchQuery('');
                }
              }}
              placeholder="Search lessons across all categories"
              className="h-12 rounded-2xl border-mainAlt/35 bg-white/80 pl-11 text-mainAccent placeholder:text-mainAccent/55 dark:bg-black/20 dark:text-white"
            />
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            <Button
              type="button"
              variant={effectiveSelectedCategoryId === ALL_CATEGORIES_KEY ? 'default' : 'outline'}
              onClick={() => setSelectedCategoryId(ALL_CATEGORIES_KEY)}
              className={cn(
                'rounded-full',
                effectiveSelectedCategoryId === ALL_CATEGORIES_KEY ? 'text-white' : 'text-mainAccent dark:text-white'
              )}
            >
              All
            </Button>
            {(hub?.categories ?? []).map((category) => {
              const key = categoryKey(category);
              const isActive = key === effectiveSelectedCategoryId;
              const accentColor = resolveCategoryAccent(category);
              return (
                <Button
                  key={key}
                  type="button"
                  variant={isActive ? 'default' : 'outline'}
                  onClick={() => setSelectedCategoryId(key)}
                  className={cn('rounded-full', !isActive && 'text-mainAccent dark:text-white')}
                  style={
                    isActive
                      ? {
                          backgroundColor: accentColor,
                          color: getReadableTextColor(accentColor),
                        }
                      : undefined
                  }
                >
                  {category.name}
                </Button>
              );
            })}
          </div>

          <p className="mt-4 text-sm text-mainAccent/70 dark:text-white/70">
            {effectiveIsSearching
              ? `Searching${deferredSearchQuery ? ` for "${deferredSearchQuery}"` : ''}...`
              : deferredSearchQuery
                ? `${visibleLessonCount} lesson${visibleLessonCount === 1 ? '' : 's'} found for "${deferredSearchQuery}".`
                : `${visibleLessonCount} lesson${visibleLessonCount === 1 ? '' : 's'} visible.`}
          </p>
        </section>

        {isLoading && (
          <div className="space-y-4">
            {Array.from({ length: 2 }).map((_, index) => (
              <div key={index} className="rounded-[28px] border border-mainAlt/20 bg-white/60 p-5 shadow-sm animate-pulse dark:bg-black/10">
                <div className="h-5 w-40 rounded bg-mainAlt/40" />
                <div className="mt-4 h-56 rounded-3xl bg-mainAlt/20" />
              </div>
            ))}
          </div>
        )}

        {!isLoading && error && <div className="rounded-2xl border border-destructive/20 bg-destructive/5 p-4 text-sm text-destructive">{error}</div>}

        {!isLoading && !error && visibleLessons.length === 0 && (
          <div className="rounded-[28px] border border-dashed border-mainAlt/35 bg-white/70 px-6 py-12 text-center shadow-sm dark:bg-black/20">
            <p className="text-lg font-semibold text-mainAccent dark:text-white">No lessons found</p>
            <p className="mt-2 text-sm text-mainAccent/70 dark:text-white/70">
              Try another search term or switch to a different category filter.
            </p>
          </div>
        )}

        {!isLoading && !error && visibleLessons.length > 0 && (
          effectiveSelectedCategoryId === ALL_CATEGORIES_KEY ? (
            <div className="space-y-4">
              {visibleLessons.map((lesson) => (
                <LessonFeedCard
                  key={`${lesson.categoryId ?? UNCATEGORIZED_KEY}-${lesson.lessonId}`}
                  lesson={lesson}
                />
              ))}
            </div>
          ) : (
            <div className="space-y-4">
              {visibleCategories.map((category) => {
                const accentColor = resolveCategoryAccent({
                  categoryId: category.categoryId,
                  name: category.categoryName,
                  type: category.categoryType,
                  color: category.categoryColor,
                  isVirtual: category.isVirtualCategory,
                  lessons: [],
                });

                return (
                  <section
                    key={category.categoryId ?? UNCATEGORIZED_KEY}
                    className="rounded-[28px] border border-mainAlt/25 bg-white/75 p-5 shadow-sm dark:bg-black/20"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p className="text-xs uppercase tracking-wide text-mainAccent/60 dark:text-white/60">Category path</p>
                        <h2 className="mt-1 text-2xl font-semibold text-mainAccent dark:text-white">{category.categoryName}</h2>
                      </div>
                      <span
                        className="inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold text-mainAccent dark:text-white"
                        style={{ backgroundColor: `${accentColor}22` }}
                      >
                        {category.lessons.length} lesson{category.lessons.length === 1 ? '' : 's'}
                      </span>
                    </div>

                    <div className="mt-4 space-y-4">
                      {category.lessons.map((lesson) => (
                        <LessonFeedCard key={`${category.categoryId ?? UNCATEGORIZED_KEY}-${lesson.lessonId}`} lesson={lesson} />
                      ))}
                    </div>
                  </section>
                );
              })}
            </div>
          )
        )}
      </div>
      <Chatbot />
    </MainLayout>
  );
};

export default LessonsPage;
