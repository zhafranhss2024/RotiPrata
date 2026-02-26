import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Loader2, Pencil, Trash2 } from 'lucide-react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Button } from '@/components/ui/button';
import { deleteLesson, fetchAdminLessons } from '@/lib/api';
import type { Lesson } from '@/types';

const AdminLessonsPage = () => {
  const [lessons, setLessons] = useState<Lesson[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isDeleting, setIsDeleting] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    fetchAdminLessons()
      .then(setLessons)
      .catch((error) => {
        console.warn('Failed to load admin lessons', error);
        setLoadError(error instanceof Error ? error.message : 'Failed to load lessons');
      })
      .finally(() => setIsLoading(false));
  }, []);

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
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <MainLayout>
      <div className="container max-w-4xl mx-auto px-4 py-6 md:py-8 pb-safe space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link to="/admin" className="text-muted-foreground hover:text-foreground">
              <ArrowLeft className="h-5 w-5" />
            </Link>
            <div>
              <h1 className="text-2xl font-bold">Manage Lessons</h1>
              <p className="text-muted-foreground">Admins can edit or delete any lesson, including pre-existing ones.</p>
            </div>
          </div>
          <Link to="/admin/lessons/create">
            <Button>Create Lesson</Button>
          </Link>
        </div>

        {loadError && <p className="text-sm text-destructive">{loadError}</p>}

        <section className="space-y-3">
          <h2 className="text-xl font-semibold text-white">All Lessons ({lessons.length})</h2>

          {isLoading ? (
            <div className="flex items-center gap-2 text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading lessons...
            </div>
          ) : lessons.length === 0 ? (
            <p className="text-muted-foreground">No lessons available yet.</p>
          ) : (
            <div className="space-y-1">
              {lessons.map((lesson) => (
                <div key={lesson.id} className="px-1">
                  <div className="flex items-start justify-between gap-4 rounded-xl px-3 py-3 hover:bg-mainAlt/25 transition-colors">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="font-semibold text-white truncate">{lesson.title}</p>
                        <span
                          className={`text-xs px-2 py-0.5 rounded-full ${
                            lesson.is_published
                              ? 'bg-emerald-900/40 text-emerald-300'
                              : 'bg-amber-900/35 text-amber-300'
                          }`}
                        >
                          {lesson.is_published ? 'Published' : 'Draft'}
                        </span>
                      </div>
                      <p className="text-sm text-muted-foreground line-clamp-2">{lesson.summary || lesson.description}</p>
                    </div>
                    <div className="flex gap-2 shrink-0">
                      <Link to={`/admin/lessons/${lesson.id}/edit`}>
                        <Button
                          size="sm"
                          className="h-9 border border-mainAlt bg-transparent text-white hover:bg-mainAlt/35"
                        >
                          <Pencil className="h-4 w-4 mr-1" /> Edit
                        </Button>
                      </Link>
                      <Button
                        size="sm"
                        onClick={() => handleDelete(lesson)}
                        disabled={isDeleting}
                        className="h-9 bg-[#fe2c55] hover:bg-[#ff3f69] text-white"
                      >
                        <Trash2 className="h-4 w-4 mr-1" /> Delete
                      </Button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </MainLayout>
  );
};

export default AdminLessonsPage;
