import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { DndContext, DragEndEvent, DragOverlay, DragStartEvent, PointerSensor, pointerWithin, useDroppable, useSensor, useSensors } from '@dnd-kit/core';
import { SortableContext, arrayMove, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { ArrowDown, ArrowLeft, ArrowUp, EyeOff, GripVertical, Loader2, Pencil, Save, Trash2 } from 'lucide-react';
import { MainLayout } from '@/components/layout/MainLayout';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { deleteLesson, fetchAdminLessons, fetchCategories, moveAdminLessonToCategory, reorderAdminLessonPath, updateLesson } from '@/lib/api';
import type { Category, Lesson } from '@/types';

const UNCATEGORIZED_KEY = '__uncategorized__';

type CategoryBucket = {
  key: string;
  label: string;
  color: string | null;
  isVirtual: boolean;
  lessons: Lesson[];
};

type PendingCategoryMove = {
  lessonId: string;
  lessonTitle: string;
  sourceKey: string;
  sourceLabel: string;
  targetKey: string;
  targetLabel: string;
  sourceLessonIds: string[];
  targetLessonIds: string[];
};

type PendingUnpublish = {
  lessonId: string;
  lessonTitle: string;
};

const sortLessons = (lessons: Lesson[]) =>
  [...lessons].sort((left, right) => {
    const leftOrder = left.path_order ?? Number.MAX_SAFE_INTEGER;
    const rightOrder = right.path_order ?? Number.MAX_SAFE_INTEGER;
    if (leftOrder !== rightOrder) {
      return leftOrder - rightOrder;
    }
    return new Date(left.created_at ?? 0).getTime() - new Date(right.created_at ?? 0).getTime();
  });

const categoryKeyForLesson = (lesson: Lesson) => lesson.category_id ?? UNCATEGORIZED_KEY;
const lessonDragId = (lessonId: string) => `lesson:${lessonId}`;
const categoryDropId = (categoryKey: string) => `category:${categoryKey}`;

const parseLessonDragId = (value: string | null | undefined) => {
  if (!value?.startsWith('lesson:')) return null;
  return value.slice('lesson:'.length);
};

const parseCategoryDropId = (value: string | null | undefined) => {
  if (!value?.startsWith('category:')) return null;
  return value.slice('category:'.length);
};

const buildOrderedLessonIds = (lessons: Lesson[]) => {
  const groupedOrder: Record<string, string[]> = {};
  lessons.forEach((lesson) => {
    const key = categoryKeyForLesson(lesson);
    if (!groupedOrder[key]) {
      groupedOrder[key] = [];
    }
    groupedOrder[key].push(lesson.id);
  });
  return groupedOrder;
};

const CategoryDropButton = ({
  bucket,
  isActive,
  isDragging,
  isDropTarget,
  onSelect,
}: {
  bucket: CategoryBucket;
  isActive: boolean;
  isDragging: boolean;
  isDropTarget: boolean;
  onSelect: () => void;
}) => {
  const { setNodeRef, isOver } = useDroppable({
    id: categoryDropId(bucket.key),
    data: { type: 'category', categoryKey: bucket.key },
  });

  return (
    <div ref={setNodeRef}>
      <Button
        type="button"
        variant={isActive ? 'default' : 'outline'}
        onClick={onSelect}
        className={`rounded-full transition-all ${
          isDropTarget || isOver
            ? 'border-primary ring-2 ring-primary/25'
            : ''
        }`}
        style={isActive && bucket.color ? { backgroundColor: bucket.color } : undefined}
      >
        {bucket.label}
        {isDragging && (isDropTarget || isOver) ? (
          <span className="ml-2 text-[10px] uppercase tracking-wide">Drop to move</span>
        ) : null}
      </Button>
    </div>
  );
};

const LessonCardDetails = ({ lesson, index }: { lesson: Lesson; index: number }) => (
  <div className="min-w-0">
    <div className="flex items-center gap-2">
      <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-mainAlt/40 text-xs font-semibold text-mainAccent dark:text-white">
        {index + 1}
      </span>
      <p className="font-semibold text-mainAccent dark:text-white truncate">{lesson.title}</p>
      <span
        className={`text-xs px-2 py-0.5 rounded-full ${
          lesson.is_published
            ? 'border border-primary/35 bg-primary/15 text-primary'
            : 'border border-statusSoft bg-statusSoft text-statusStrong'
        }`}
      >
        {lesson.is_published ? 'Published' : 'Draft'}
      </span>
    </div>
    <p className="mt-1 text-sm text-muted-foreground line-clamp-2">{lesson.summary || lesson.description}</p>
  </div>
);

const SortableLessonCard = ({
  lesson,
  index,
  isLast,
  categoryKey,
  onMove,
  onUnpublish,
  onDelete,
  isDeleting,
  isUnpublishing,
}: {
  lesson: Lesson;
  index: number;
  isLast: boolean;
  categoryKey: string;
  onMove: (direction: 'up' | 'down', lessonId: string) => void;
  onUnpublish: (lesson: Lesson) => void;
  onDelete: (lesson: Lesson) => void;
  isDeleting: boolean;
  isUnpublishing: boolean;
}) => {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: lessonDragId(lesson.id),
    data: { type: 'lesson', lessonId: lesson.id, categoryKey },
  });

  return (
    <div
      ref={setNodeRef}
      style={{
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.5 : 1,
      }}
      className="flex items-start justify-between gap-4 rounded-xl border bg-background/80 px-3 py-3 shadow-sm transition-shadow cursor-grab active:cursor-grabbing hover:shadow-md"
      {...attributes}
      {...listeners}
    >
      <div className="flex min-w-0 flex-1 items-start gap-3">
        <span className="mt-1 text-muted-foreground">
          <GripVertical className="h-4 w-4" />
        </span>
        <LessonCardDetails lesson={lesson} index={index} />
      </div>
      <div className="flex gap-2 shrink-0">
        <Button
          size="icon"
          variant="outline"
          onPointerDown={(event) => event.stopPropagation()}
          onClick={() => onMove('up', lesson.id)}
          disabled={index === 0}
          aria-label={`Move ${lesson.title} up`}
        >
          <ArrowUp className="h-4 w-4" />
        </Button>
        <Button
          size="icon"
          variant="outline"
          onPointerDown={(event) => event.stopPropagation()}
          onClick={() => onMove('down', lesson.id)}
          disabled={isLast}
          aria-label={`Move ${lesson.title} down`}
        >
          <ArrowDown className="h-4 w-4" />
        </Button>
        <Link to={`/admin/lessons/${lesson.id}/edit`}>
          <Button
            size="sm"
            onPointerDown={(event) => event.stopPropagation()}
            className="h-9 border border-mainAlt bg-transparent text-mainAccent dark:text-white hover:bg-mainAlt/35"
          >
            <Pencil className="h-4 w-4 mr-1" /> Edit
          </Button>
        </Link>
        {lesson.is_published ? (
          <Button
            size="sm"
            variant="outline"
            onPointerDown={(event) => event.stopPropagation()}
            onClick={() => onUnpublish(lesson)}
            disabled={isUnpublishing}
            className="h-9 border border-mainAlt text-mainAccent dark:text-white"
          >
            <EyeOff className="h-4 w-4 mr-1" /> Unpublish
          </Button>
        ) : null}
        <Button
          size="sm"
          onPointerDown={(event) => event.stopPropagation()}
          onClick={() => onDelete(lesson)}
          disabled={isDeleting}
          className="h-9 bg-primary hover:bg-primary/90 text-white"
        >
          <Trash2 className="h-4 w-4 mr-1" /> Delete
        </Button>
      </div>
    </div>
  );
};

const AdminLessonsPage = () => {
  const [lessons, setLessons] = useState<Lesson[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [orderedLessonIds, setOrderedLessonIds] = useState<Record<string, string[]>>({});
  const [dirtyCategoryKeys, setDirtyCategoryKeys] = useState<string[]>([]);
  const [selectedCategoryKey, setSelectedCategoryKey] = useState<string | null>(null);
  const [activeLessonId, setActiveLessonId] = useState<string | null>(null);
  const [hoveredCategoryKey, setHoveredCategoryKey] = useState<string | null>(null);
  const [pendingMove, setPendingMove] = useState<PendingCategoryMove | null>(null);
  const [pendingUnpublish, setPendingUnpublish] = useState<PendingUnpublish | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isMovingLesson, setIsMovingLesson] = useState(false);
  const [isUnpublishingLesson, setIsUnpublishingLesson] = useState(false);
  const [savingCategoryKey, setSavingCategoryKey] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 8 },
    })
  );

  useEffect(() => {
    Promise.all([fetchAdminLessons(), fetchCategories()])
      .then(([loadedLessons, loadedCategories]) => {
        const sortedLessons = sortLessons(loadedLessons);
        setLessons(sortedLessons);
        setCategories(loadedCategories);
        setOrderedLessonIds(buildOrderedLessonIds(sortedLessons));
      })
      .catch((error) => {
        console.warn('Failed to load admin lessons', error);
        setLoadError(error instanceof Error ? error.message : 'Failed to load lessons');
      })
      .finally(() => setIsLoading(false));
  }, []);

  const categoryBuckets = useMemo(() => {
    const buckets = categories.map((category) => ({
      key: category.id,
      label: category.name,
      color: category.color,
      isVirtual: false,
      lessons: [] as Lesson[],
    }));
    const bucketByKey = new Map(buckets.map((bucket) => [bucket.key, bucket]));
    const uncategorizedLessons: Lesson[] = [];

    lessons.forEach((lesson) => {
      if (!lesson.category_id) {
        uncategorizedLessons.push(lesson);
        return;
      }
      const existing = bucketByKey.get(lesson.category_id);
      if (existing) {
        existing.lessons.push(lesson);
      }
    });

    const orderedBuckets = buckets.map((bucket) => {
      const ids = orderedLessonIds[bucket.key] ?? bucket.lessons.map((lesson) => lesson.id);
      const byId = new Map(bucket.lessons.map((lesson) => [lesson.id, lesson]));
      return {
        ...bucket,
        lessons: ids.map((id) => byId.get(id)).filter((lesson): lesson is Lesson => Boolean(lesson)),
      };
    });

    if (uncategorizedLessons.length > 0) {
      const ids = orderedLessonIds[UNCATEGORIZED_KEY] ?? uncategorizedLessons.map((lesson) => lesson.id);
      const byId = new Map(uncategorizedLessons.map((lesson) => [lesson.id, lesson]));
      orderedBuckets.push({
        key: UNCATEGORIZED_KEY,
        label: 'Uncategorized',
        color: '#6b7280',
        isVirtual: true,
        lessons: ids.map((id) => byId.get(id)).filter((lesson): lesson is Lesson => Boolean(lesson)),
      });
    }

    return orderedBuckets;
  }, [categories, lessons, orderedLessonIds]);

  const bucketByKey = useMemo(
    () => new Map(categoryBuckets.map((bucket) => [bucket.key, bucket])),
    [categoryBuckets]
  );

  const lessonById = useMemo(
    () => new Map(lessons.map((lesson) => [lesson.id, lesson])),
    [lessons]
  );

  useEffect(() => {
    if (!categoryBuckets.length) {
      setSelectedCategoryKey(null);
      return;
    }
    const hasSelected = selectedCategoryKey && categoryBuckets.some((bucket) => bucket.key === selectedCategoryKey);
    if (hasSelected) {
      return;
    }
    const preferred = categoryBuckets.find((bucket) => bucket.lessons.length > 0) ?? categoryBuckets[0];
    setSelectedCategoryKey(preferred.key);
  }, [categoryBuckets, selectedCategoryKey]);

  const activeBucket = useMemo(
    () => categoryBuckets.find((bucket) => bucket.key === selectedCategoryKey) ?? categoryBuckets[0] ?? null,
    [categoryBuckets, selectedCategoryKey]
  );

  const activeLesson = activeLessonId ? lessonById.get(activeLessonId) ?? null : null;

  const handleDelete = async (lesson: Lesson) => {
    const ok = window.confirm(`Delete lesson "${lesson.title}"? This cannot be undone.`);
    if (!ok) {
      return;
    }
    setIsDeleting(true);
    try {
      await deleteLesson(lesson.id);
      setLessons((prev) => prev.filter((item) => item.id !== lesson.id));
      setOrderedLessonIds((prev) => {
        const next = { ...prev };
        Object.keys(next).forEach((key) => {
          next[key] = next[key].filter((id) => id !== lesson.id);
        });
        return next;
      });
    } catch (error) {
      console.warn('Delete lesson failed', error);
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

  const markCategoryDirty = (categoryKey: string) => {
    setDirtyCategoryKeys((prev) => (prev.includes(categoryKey) ? prev : [...prev, categoryKey]));
  };

  const moveLesson = (direction: 'up' | 'down', lessonId: string) => {
    if (!activeBucket) return;
    setOrderedLessonIds((prev) => {
      const current = [...(prev[activeBucket.key] ?? activeBucket.lessons.map((lesson) => lesson.id))];
      const index = current.indexOf(lessonId);
      if (index < 0) return prev;
      const targetIndex = direction === 'up' ? index - 1 : index + 1;
      if (targetIndex < 0 || targetIndex >= current.length) {
        return prev;
      }
      [current[index], current[targetIndex]] = [current[targetIndex], current[index]];
      return { ...prev, [activeBucket.key]: current };
    });
    markCategoryDirty(activeBucket.key);
  };

  const handleSaveOrder = async () => {
    if (!activeBucket) return;
    const lessonIds = orderedLessonIds[activeBucket.key] ?? activeBucket.lessons.map((lesson) => lesson.id);
    setSavingCategoryKey(activeBucket.key);
    setLoadError(null);
    try {
      const savedLessons = await reorderAdminLessonPath(
        activeBucket.key === UNCATEGORIZED_KEY ? null : activeBucket.key,
        lessonIds
      );
      const savedById = new Map(savedLessons.map((lesson) => [lesson.id, lesson]));
      setLessons((prev) => sortLessons(prev.map((lesson) => savedById.get(lesson.id) ?? lesson)));
      setOrderedLessonIds((prev) => ({
        ...prev,
        [activeBucket.key]: savedLessons.map((lesson) => lesson.id),
      }));
      setDirtyCategoryKeys((prev) => prev.filter((key) => key !== activeBucket.key));
    } catch (error) {
      console.warn('Failed to save lesson order', error);
      setLoadError(error instanceof Error ? error.message : 'Failed to save lesson order');
    } finally {
      setSavingCategoryKey(null);
    }
  };

  const handleDragStart = (event: DragStartEvent) => {
    setActiveLessonId(parseLessonDragId(String(event.active.id)));
    setHoveredCategoryKey(null);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const draggedLessonId = parseLessonDragId(String(event.active.id));
    const overId = event.over ? String(event.over.id) : null;
    setActiveLessonId(null);
    setHoveredCategoryKey(null);

    if (!draggedLessonId || !overId) {
      return;
    }

    const draggedLesson = lessonById.get(draggedLessonId);
    if (!draggedLesson) {
      return;
    }

    const sourceKey = categoryKeyForLesson(draggedLesson);
    const sourceBucket = bucketByKey.get(sourceKey);
    if (!sourceBucket) {
      return;
    }

    const targetCategoryKey = parseCategoryDropId(overId);
    if (targetCategoryKey && targetCategoryKey !== sourceKey) {
      const targetBucket = bucketByKey.get(targetCategoryKey);
      if (!targetBucket) {
        return;
      }
      const sourceLessonIds = (orderedLessonIds[sourceKey] ?? sourceBucket.lessons.map((lesson) => lesson.id)).filter(
        (id) => id !== draggedLessonId
      );
      const targetLessonIds = [
        ...(orderedLessonIds[targetCategoryKey] ?? targetBucket.lessons.map((lesson) => lesson.id)).filter(
          (id) => id !== draggedLessonId
        ),
        draggedLessonId,
      ];
      setPendingMove({
        lessonId: draggedLessonId,
        lessonTitle: draggedLesson.title,
        sourceKey,
        sourceLabel: sourceBucket.label,
        targetKey: targetCategoryKey,
        targetLabel: targetBucket.label,
        sourceLessonIds,
        targetLessonIds,
      });
      return;
    }

    const overLessonId = parseLessonDragId(overId);
    if (!overLessonId || !activeBucket || sourceKey !== activeBucket.key || overLessonId === draggedLessonId) {
      return;
    }

    const current = [...(orderedLessonIds[activeBucket.key] ?? activeBucket.lessons.map((lesson) => lesson.id))];
    const oldIndex = current.indexOf(draggedLessonId);
    const newIndex = current.indexOf(overLessonId);
    if (oldIndex < 0 || newIndex < 0 || oldIndex === newIndex) {
      return;
    }
    setOrderedLessonIds((prev) => ({
      ...prev,
      [activeBucket.key]: arrayMove(current, oldIndex, newIndex),
    }));
    markCategoryDirty(activeBucket.key);
  };

  const handleMoveConfirm = async () => {
    if (!pendingMove) {
      return;
    }

    setIsMovingLesson(true);
    setLoadError(null);
    try {
      const result = await moveAdminLessonToCategory(pendingMove.lessonId, {
        sourceCategoryId: pendingMove.sourceKey === UNCATEGORIZED_KEY ? null : pendingMove.sourceKey,
        targetCategoryId: pendingMove.targetKey === UNCATEGORIZED_KEY ? null : pendingMove.targetKey,
        sourceLessonIds: pendingMove.sourceLessonIds,
        targetLessonIds: pendingMove.targetLessonIds,
      });

      const updatedById = new Map<Lesson['id'], Lesson>();
      result.sourceLessons.forEach((lesson) => updatedById.set(lesson.id, lesson));
      result.targetLessons.forEach((lesson) => updatedById.set(lesson.id, lesson));
      updatedById.set(result.movedLesson.id, result.movedLesson);

      setLessons((prev) => sortLessons(prev.map((lesson) => updatedById.get(lesson.id) ?? lesson)));
      setOrderedLessonIds((prev) => ({
        ...prev,
        [pendingMove.sourceKey]: result.sourceLessons.map((lesson) => lesson.id),
        [pendingMove.targetKey]: result.targetLessons.map((lesson) => lesson.id),
      }));
      setDirtyCategoryKeys((prev) =>
        prev.filter((key) => key !== pendingMove.sourceKey && key !== pendingMove.targetKey)
      );
      setSelectedCategoryKey(pendingMove.targetKey);
      setPendingMove(null);
    } catch (error) {
      console.warn('Failed to move lesson to category', error);
      setLoadError(error instanceof Error ? error.message : 'Failed to move lesson');
    } finally {
      setIsMovingLesson(false);
    }
  };

  return (
    <MainLayout>
      <div className="container max-w-5xl mx-auto px-4 py-6 md:py-8 pb-safe space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link to="/admin" className="text-muted-foreground hover:text-foreground">
              <ArrowLeft className="h-5 w-5" />
            </Link>
            <div>
              <h1 className="text-2xl font-bold">Manage Lessons</h1>
              <p className="text-muted-foreground">Drag cards to reorder. Drop over a category pill to move lessons between paths.</p>
            </div>
          </div>
          <Link to="/admin/lessons/create">
            <Button>Create Lesson</Button>
          </Link>
        </div>

        {loadError && <p className="text-sm text-destructive">{loadError}</p>}

        <section className="space-y-4 rounded-2xl border bg-card/40 p-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-xl font-semibold text-mainAccent dark:text-white">Category Paths</h2>
              <p className="text-sm text-muted-foreground">Drag within the list to reorder, or drag over a category tab to move and confirm.</p>
            </div>
            {activeBucket ? (
              <Button
                onClick={handleSaveOrder}
                disabled={savingCategoryKey === activeBucket.key || !dirtyCategoryKeys.includes(activeBucket.key)}
              >
                {savingCategoryKey === activeBucket.key ? (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                ) : (
                  <Save className="mr-2 h-4 w-4" />
                )}
                Save Order
              </Button>
            ) : null}
          </div>

          <DndContext
            sensors={sensors}
            collisionDetection={pointerWithin}
            onDragStart={handleDragStart}
            onDragCancel={() => {
              setActiveLessonId(null);
              setHoveredCategoryKey(null);
            }}
            onDragMove={(event) => {
              setHoveredCategoryKey(parseCategoryDropId(event.over ? String(event.over.id) : null));
            }}
            onDragEnd={handleDragEnd}
          >
            <div className="flex flex-wrap gap-2">
              {categoryBuckets.map((bucket) => (
                <CategoryDropButton
                  key={bucket.key}
                  bucket={bucket}
                  isActive={bucket.key === activeBucket?.key}
                  isDragging={Boolean(activeLessonId)}
                  isDropTarget={hoveredCategoryKey === bucket.key}
                  onSelect={() => setSelectedCategoryKey(bucket.key)}
                />
              ))}
            </div>

            {isLoading ? (
              <div className="flex items-center gap-2 text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading lessons...
              </div>
            ) : !activeBucket ? (
              <p className="text-muted-foreground">No categories available yet.</p>
            ) : activeBucket.lessons.length === 0 ? (
              <div className="rounded-xl border border-dashed px-4 py-8 text-sm text-muted-foreground">
                No lessons in {activeBucket.label} yet.
              </div>
            ) : (
              <SortableContext
                items={activeBucket.lessons.map((lesson) => lessonDragId(lesson.id))}
                strategy={verticalListSortingStrategy}
              >
                <div className="space-y-2">
                  {activeBucket.lessons.map((lesson, index) => (
                    <SortableLessonCard
                      key={lesson.id}
                      lesson={lesson}
                      index={index}
                      isLast={index === activeBucket.lessons.length - 1}
                      categoryKey={activeBucket.key}
                      onMove={moveLesson}
                      onUnpublish={handleUnpublish}
                      onDelete={handleDelete}
                      isDeleting={isDeleting}
                      isUnpublishing={isUnpublishingLesson}
                    />
                  ))}
                </div>
              </SortableContext>
            )}

            <DragOverlay>
              {activeLesson ? (
                <div className="w-[min(100%,900px)] rounded-xl border bg-background/95 px-4 py-3 shadow-2xl">
                  <div className="flex items-start gap-3">
                    <GripVertical className="mt-1 h-4 w-4 text-muted-foreground" />
                    <LessonCardDetails lesson={activeLesson} index={0} />
                  </div>
                </div>
              ) : null}
            </DragOverlay>
          </DndContext>
        </section>
      </div>

      <AlertDialog open={Boolean(pendingMove)} onOpenChange={(open) => (!open ? setPendingMove(null) : null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Move Lesson To Another Category?</AlertDialogTitle>
            <AlertDialogDescription>
              {pendingMove
                ? `Move "${pendingMove.lessonTitle}" from ${pendingMove.sourceLabel} to ${pendingMove.targetLabel}? It will be placed at the end of the target path.`
                : 'Confirm the category change.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isMovingLesson}>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleMoveConfirm} disabled={isMovingLesson}>
              {isMovingLesson ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Confirm Move
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={Boolean(pendingUnpublish)} onOpenChange={(open) => (!open ? setPendingUnpublish(null) : null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Unpublish Lesson?</AlertDialogTitle>
            <AlertDialogDescription>
              {pendingUnpublish
                ? `"${pendingUnpublish.lessonTitle}" will be removed from the learner lesson hub until it is published again.`
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
