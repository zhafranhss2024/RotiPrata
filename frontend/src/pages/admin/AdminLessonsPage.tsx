import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, EyeOff, Loader2, Pencil, Trash2 } from 'lucide-react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Button } from '@/components/ui/button';
import { deleteLesson, fetchAdminLessons, fetchCategories, updateLesson } from '@/lib/api';
import type { Category, Lesson } from '@/types';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';

const ALL_CATEGORIES_KEY = '__all__';
const UNCATEGORIZED_KEY = '__uncategorized__';

type CategoryBucket = {
  key: string;
  label: string;
  color: string | null;
  isVirtual: boolean;
  lessons: Lesson[];
};

type PendingUnpublish = {
  lessonId: string;
  lessonTitle: string;
};

const lessonCreatedAtValue = (lesson: Lesson) => {
  const timestamp = new Date(lesson.created_at ?? '').getTime();
  return Number.isFinite(timestamp) ? timestamp : 0;
};

const sortLessons = (lessons: Lesson[]) =>
  [...lessons].sort((left, right) => {
    if ((left.is_published ? 1 : 0) !== (right.is_published ? 1 : 0)) {
      return left.is_published ? -1 : 1;
    }
    const createdAtDiff = lessonCreatedAtValue(right) - lessonCreatedAtValue(left);
    if (createdAtDiff !== 0) {
      return createdAtDiff;
    }
    return left.title.localeCompare(right.title);
  });

const buildCategoryBuckets = (categories: Category[], lessons: Lesson[]) => {
  const sortedLessons = sortLessons(lessons);
  const buckets: CategoryBucket[] = categories.map((category) => ({
    key: category.id,
    label: category.name,
    color: category.color,
    isVirtual: false,
    lessons: sortedLessons.filter((lesson) => lesson.category_id === category.id),
  }));

  const uncategorizedLessons = sortedLessons.filter((lesson) => lesson.category_id === null);
  if (uncategorizedLessons.length > 0) {
    buckets.push({
      key: UNCATEGORIZED_KEY,
      label: 'Uncategorized',
      color: '#6b7280',
      isVirtual: true,
      lessons: uncategorizedLessons,
    });
  }

  return buckets;
};

const LessonCard = ({
  lesson,
  onDelete,
  onUnpublish,
  isDeleting,
  isUnpublishing,
}: {
  lesson: Lesson;
  onDelete: (lesson: Lesson) => void;
  onUnpublish: (lesson: Lesson) => void;
  isDeleting: boolean;
  isUnpublishing: boolean;
}) => (
  <article className="flex flex-col gap-4 rounded-2xl border bg-background/80 p-4 shadow-sm lg:flex-row lg:items-start lg:justify-between">
    <div className="min-w-0 flex-1">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="truncate text-lg font-semibold text-mainAccent dark:text-white">{lesson.title}</h3>
        <span
          className={`rounded-full px-2.5 py-1 text-xs font-medium ${
            lesson.is_published
              ? 'border border-primary/35 bg-primary/15 text-primary'
              : 'border border-statusSoft bg-statusSoft text-statusStrong'
          }`}
        >
          {lesson.is_published ? 'Published' : 'Draft'}
        </span>
      </div>
      <p className="mt-2 text-sm leading-6 text-muted-foreground">{lesson.summary || lesson.description || 'No summary yet.'}</p>
      <div className="mt-3 flex flex-wrap gap-2 text-xs text-mainAccent/80 dark:text-white/80">
        <span className="rounded-full border border-mainAlt/35 px-3 py-1">{lesson.estimated_minutes} min</span>
        <span className="rounded-full border border-mainAlt/35 px-3 py-1">{lesson.xp_reward} XP</span>
        <span className="rounded-full border border-mainAlt/35 px-3 py-1">{lesson.completion_count} learners</span>
      </div>
    </div>

    <div className="flex shrink-0 flex-wrap gap-2">
      <Link to={`/admin/lessons/${lesson.id}/edit`}>
        <Button size="sm" className="h-9 border border-mainAlt bg-transparent text-mainAccent hover:bg-mainAlt/35 dark:text-white">
          <Pencil className="mr-1 h-4 w-4" /> Edit
        </Button>
      </Link>
      {lesson.is_published ? (
        <Button
          size="sm"
          variant="outline"
          onClick={() => onUnpublish(lesson)}
          disabled={isUnpublishing}
          className="h-9 border border-mainAlt text-mainAccent dark:text-white"
        >
          <EyeOff className="mr-1 h-4 w-4" /> Unpublish
        </Button>
      ) : null}
      <Button
        size="sm"
        onClick={() => onDelete(lesson)}
        disabled={isDeleting}
        className="h-9 bg-primary text-white hover:bg-primary/90"
      >
        <Trash2 className="mr-1 h-4 w-4" /> Delete
      </Button>
    </div>
  </article>
);

const AdminLessonsPage = () => {
  const [lessons, setLessons] = useState<Lesson[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedCategoryKey, setSelectedCategoryKey] = useState<string>(ALL_CATEGORIES_KEY);
  const [pendingUnpublish, setPendingUnpublish] = useState<PendingUnpublish | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUnpublishingLesson, setIsUnpublishingLesson] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([fetchAdminLessons(), fetchCategories()])
      .then(([loadedLessons, loadedCategories]) => {
        setLessons(sortLessons(loadedLessons));
        setCategories(loadedCategories);
        setLoadError(null);
      })
      .catch((error) => {
        console.warn('Failed to load admin lessons', error);
        setLoadError(error instanceof Error ? error.message : 'Failed to load lessons');
      })
      .finally(() => setIsLoading(false));
  }, []);

  const categoryBuckets = useMemo(() => buildCategoryBuckets(categories, lessons), [categories, lessons]);

  const effectiveSelectedCategoryKey = useMemo(() => {
    const availableKeys = new Set(categoryBuckets.map((bucket) => bucket.key));
    if (selectedCategoryKey === ALL_CATEGORIES_KEY || availableKeys.has(selectedCategoryKey)) {
      return selectedCategoryKey;
    }
    return ALL_CATEGORIES_KEY;
  }, [categoryBuckets, selectedCategoryKey]);

  const visibleBuckets = useMemo(() => {
    if (effectiveSelectedCategoryKey === ALL_CATEGORIES_KEY) {
      return categoryBuckets;
    }
    return categoryBuckets.filter((bucket) => bucket.key === effectiveSelectedCategoryKey);
  }, [categoryBuckets, effectiveSelectedCategoryKey]);

  const handleDelete = async (lesson: Lesson) => {
    const ok = window.confirm(`Delete lesson "${lesson.title}"? This cannot be undone.`);
    if (!ok) {
      return;
    }
    setIsDeleting(true);
    try {
      await deleteLesson(lesson.id);
      setLessons((prev) => prev.filter((item) => item.id !== lesson.id));
    } catch (error) {
      console.warn('Delete lesson failed', error);
      setLoadError(error instanceof Error ? error.message : 'Failed to delete lesson');
    } finally {
      setIsDeleting(false);
    }
  };

  const handleUnpublish = (lesson: Lesson) => {
    setPendingUnpublish({
      lessonId: lesson.id,
      lessonTitle: lesson.title,
    });
  };

  const handleUnpublishConfirm = async () => {
    if (!pendingUnpublish) {
      return;
    }

    setIsUnpublishingLesson(true);
    setLoadError(null);
    try {
      const updatedLesson = await updateLesson(pendingUnpublish.lessonId, { is_published: false });
      setLessons((prev) => sortLessons(prev.map((lesson) => (lesson.id === updatedLesson.id ? updatedLesson : lesson))));
      setPendingUnpublish(null);
    } catch (error) {
      console.warn('Failed to unpublish lesson', error);
      setLoadError(error instanceof Error ? error.message : 'Failed to unpublish lesson');
    } finally {
      setIsUnpublishingLesson(false);
    }
  };

  return (
    <MainLayout>
      <div className="container max-w-5xl mx-auto px-4 py-6 md:py-8 pb-safe space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Link to="/admin" className="text-muted-foreground hover:text-foreground">
              <ArrowLeft className="h-5 w-5" />
            </Link>
            <div>
              <h1 className="text-2xl font-bold">Manage Lessons</h1>
              <p className="text-muted-foreground">Lessons are grouped by category now. Ordering is handled automatically, so the old path-order controls are gone.</p>
            </div>
          </div>

          <Link to="/admin/lessons/create">
            <Button>Create Lesson</Button>
          </Link>
        </div>

        {loadError && <p className="text-sm text-destructive">{loadError}</p>}

        <section className="space-y-4 rounded-2xl border bg-card/40 p-4">
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant={effectiveSelectedCategoryKey === ALL_CATEGORIES_KEY ? 'default' : 'outline'}
              onClick={() => setSelectedCategoryKey(ALL_CATEGORIES_KEY)}
              className={effectiveSelectedCategoryKey === ALL_CATEGORIES_KEY ? 'text-white' : 'text-mainAccent dark:text-white'}
            >
              All
            </Button>
            {categoryBuckets.map((bucket) => {
              const isActive = bucket.key === effectiveSelectedCategoryKey;
              return (
                <Button
                  key={bucket.key}
                  type="button"
                  variant={isActive ? 'default' : 'outline'}
                  onClick={() => setSelectedCategoryKey(bucket.key)}
                  className={isActive ? 'text-white' : 'text-mainAccent dark:text-white'}
                  style={isActive ? { backgroundColor: bucket.color ?? '#629dff' } : undefined}
                >
                  {bucket.label}
                </Button>
              );
            })}
          </div>

          {isLoading ? (
            <div className="flex items-center gap-2 text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading lessons...
            </div>
          ) : visibleBuckets.length === 0 ? (
            <div className="rounded-xl border border-dashed px-4 py-8 text-sm text-muted-foreground">
              No lesson categories available yet.
            </div>
          ) : (
            <div className="space-y-5">
              {visibleBuckets.map((bucket) => (
                <section key={bucket.key} className="rounded-2xl border bg-background/40 p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p className="text-xs uppercase tracking-wide text-muted-foreground">
                        {bucket.isVirtual ? 'Uncategorized lessons' : 'Category'}
                      </p>
                      <h2 className="text-xl font-semibold text-mainAccent dark:text-white">{bucket.label}</h2>
                    </div>
                    <span
                      className="rounded-full px-3 py-1 text-xs font-medium text-mainAccent dark:text-white"
                      style={{ backgroundColor: `${bucket.color ?? '#629dff'}22` }}
                    >
                      {bucket.lessons.length} lesson{bucket.lessons.length === 1 ? '' : 's'}
                    </span>
                  </div>

                  {bucket.lessons.length === 0 ? (
                    <div className="mt-4 rounded-xl border border-dashed px-4 py-8 text-sm text-muted-foreground">
                      No lessons in {bucket.label} yet.
                    </div>
                  ) : (
                    <div className="mt-4 space-y-3">
                      {bucket.lessons.map((lesson) => (
                        <LessonCard
                          key={lesson.id}
                          lesson={lesson}
                          onDelete={handleDelete}
                          onUnpublish={handleUnpublish}
                          isDeleting={isDeleting}
                          isUnpublishing={isUnpublishingLesson}
                        />
                      ))}
                    </div>
                  )}
                </section>
              ))}
            </div>
          )}
        </section>
      </div>

      <AlertDialog open={Boolean(pendingUnpublish)} onOpenChange={(open) => (!open ? setPendingUnpublish(null) : null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Unpublish Lesson?</AlertDialogTitle>
            <AlertDialogDescription>
              {pendingUnpublish
                ? `"${pendingUnpublish.lessonTitle}" will be removed from the learner lesson library until it is published again.`
                : 'Confirm unpublish.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isUnpublishingLesson}>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleUnpublishConfirm} disabled={isUnpublishingLesson}>
              {isUnpublishingLesson ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Unpublish
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </MainLayout>
  );
};

export default AdminLessonsPage;
