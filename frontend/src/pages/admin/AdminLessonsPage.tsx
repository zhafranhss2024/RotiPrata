import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Loader2, Pencil, Trash2 } from 'lucide-react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { deleteLesson, fetchAdminLessons, updateLesson } from '@/lib/api';
import type { Lesson } from '@/types';

const AdminLessonsPage = () => {
  const [lessons, setLessons] = useState<Lesson[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [selectedLesson, setSelectedLesson] = useState<Lesson | null>(null);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [form, setForm] = useState({
    title: '',
    summary: '',
    description: '',
    origin_content: '',
    definition_content: '',
  });

  useEffect(() => {
    fetchAdminLessons()
      .then(setLessons)
      .catch((error) => console.warn('Failed to load admin lessons', error))
      .finally(() => setIsLoading(false));
  }, []);

  const openEdit = (lesson: Lesson) => {
    setSelectedLesson(lesson);
    setForm({
      title: lesson.title ?? '',
      summary: lesson.summary ?? '',
      description: lesson.description ?? '',
      origin_content: lesson.origin_content ?? '',
      definition_content: lesson.definition_content ?? '',
    });
    setIsEditOpen(true);
  };

  const handleSave = async () => {
    if (!selectedLesson) {
      return;
    }
    setIsSaving(true);
    try {
      const updated = await updateLesson(selectedLesson.id, form);
      setLessons((prev) => prev.map((item) => (item.id === updated.id ? { ...item, ...updated } : item)));
      setIsEditOpen(false);
      setSelectedLesson(null);
    } catch (error) {
      console.warn('Update lesson failed', error);
    } finally {
      setIsSaving(false);
    }
  };

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

        <Card>
          <CardHeader>
            <CardTitle>All Lessons ({lessons.length})</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {isLoading ? (
              <div className="flex items-center gap-2 text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading lessons...
              </div>
            ) : lessons.length === 0 ? (
              <p className="text-muted-foreground">No lessons available yet.</p>
            ) : (
              lessons.map((lesson) => (
                <div key={lesson.id} className="border rounded-lg p-4 flex items-start justify-between gap-4">
                  <div>
                    <p className="font-semibold">{lesson.title}</p>
                    <p className="text-sm text-muted-foreground line-clamp-2">{lesson.summary || lesson.description}</p>
                  </div>
                  <div className="flex gap-2 shrink-0">
                    <Button variant="outline" size="sm" onClick={() => openEdit(lesson)}>
                      <Pencil className="h-4 w-4 mr-1" /> Edit
                    </Button>
                    <Button variant="destructive" size="sm" onClick={() => handleDelete(lesson)} disabled={isDeleting}>
                      <Trash2 className="h-4 w-4 mr-1" /> Delete
                    </Button>
                  </div>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Dialog open={isEditOpen} onOpenChange={setIsEditOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Edit Lesson</DialogTitle>
              <DialogDescription>Update your lesson content and save changes.</DialogDescription>
            </DialogHeader>
            <div className="space-y-3">
              <div className="space-y-2">
                <Label htmlFor="lesson-title">Title</Label>
                <Input id="lesson-title" value={form.title} onChange={(e) => setForm((prev) => ({ ...prev, title: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lesson-summary">Summary</Label>
                <Input id="lesson-summary" value={form.summary} onChange={(e) => setForm((prev) => ({ ...prev, summary: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lesson-description">Description</Label>
                <Textarea id="lesson-description" rows={3} value={form.description} onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lesson-origin">Origin Content</Label>
                <Textarea id="lesson-origin" rows={3} value={form.origin_content} onChange={(e) => setForm((prev) => ({ ...prev, origin_content: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lesson-definition">Definition Content</Label>
                <Textarea id="lesson-definition" rows={3} value={form.definition_content} onChange={(e) => setForm((prev) => ({ ...prev, definition_content: e.target.value }))} />
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setIsEditOpen(false)}>Cancel</Button>
              <Button onClick={handleSave} disabled={isSaving}>
                {isSaving ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
                Save Changes
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </MainLayout>
  );
};

export default AdminLessonsPage;
