import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Loader2, Pencil, Save, Trash2, X } from 'lucide-react';
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
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [form, setForm] = useState({
    title: '',
    summary: '',
    description: '',
    learning_objectives: [''],
    estimated_minutes: 15,
    xp_reward: 100,
    badge_name: '',
    difficulty_level: 1,
    origin_content: '',
    definition_content: '',
    usage_examples: [''],
    lore_content: '',
    evolution_content: '',
    comparison_content: '',
  });

  const normalizeStringArray = (value: unknown) => {
    if (Array.isArray(value)) {
      const cleaned = value.map((item) => (item == null ? '' : String(item))).map((item) => item.trim());
      return cleaned.length ? cleaned : [''];
    }
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (!trimmed) {
        return [''];
      }
      if (trimmed.startsWith('[')) {
        try {
          const parsed = JSON.parse(trimmed);
          if (Array.isArray(parsed)) {
            return parsed.map((item) => (item == null ? '' : String(item))).map((item) => item.trim());
          }
        } catch {
          // fall through to string handling
        }
      }
      if (trimmed.includes(',')) {
        const parts = trimmed.split(',').map((part) => part.trim()).filter(Boolean);
        return parts.length ? parts : [''];
      }
      return [trimmed];
    }
    return [''];
  };

  useEffect(() => {
    fetchAdminLessons()
      .then(setLessons)
      .catch((error) => {
        console.warn('Failed to load admin lessons', error);
        setLoadError(error instanceof Error ? error.message : 'Failed to load lessons');
      })
      .finally(() => setIsLoading(false));
  }, []);

  const openEdit = (lesson: Lesson) => {
    setSelectedLesson(lesson);
    setSubmitError(null);
    setForm({
      title: lesson.title ?? '',
      summary: lesson.summary ?? '',
      description: lesson.description ?? '',
      learning_objectives: normalizeStringArray(lesson.learning_objectives),
      estimated_minutes: lesson.estimated_minutes ?? 15,
      xp_reward: lesson.xp_reward ?? 100,
      badge_name: lesson.badge_name ?? '',
      difficulty_level: lesson.difficulty_level ?? 1,
      origin_content: lesson.origin_content ?? '',
      definition_content: lesson.definition_content ?? '',
      usage_examples: normalizeStringArray(lesson.usage_examples),
      lore_content: lesson.lore_content ?? '',
      evolution_content: lesson.evolution_content ?? '',
      comparison_content: lesson.comparison_content ?? '',
    });
    setIsEditOpen(true);
  };

  const validateForm = (publish: boolean) => {
    if (!form.title || !form.title.trim()) {
      return 'Lesson title is required.';
    }

    if (!publish) {
      return null;
    }

    const requiredFields: Array<[string, string]> = [
      ['summary', form.summary],
      ['description', form.description],
      ['origin_content', form.origin_content],
      ['definition_content', form.definition_content],
      ['lore_content', form.lore_content],
      ['evolution_content', form.evolution_content],
      ['comparison_content', form.comparison_content],
      ['badge_name', form.badge_name],
    ];

    for (const [key, value] of requiredFields) {
      if (!value || !value.trim()) {
        return `Missing required field: ${key.replace('_', ' ')}`;
      }
    }

    const objectives = normalizeStringArray(form.learning_objectives).filter(o => o.trim());
    if (objectives.length === 0) {
      return 'At least one learning objective is required.';
    }

    const examples = normalizeStringArray(form.usage_examples).filter(e => e.trim());
    if (examples.length === 0) {
      return 'At least one usage example is required.';
    }

    if (!form.estimated_minutes || form.estimated_minutes <= 0) {
      return 'Estimated minutes must be greater than 0.';
    }

    if (!form.xp_reward || form.xp_reward <= 0) {
      return 'XP reward must be greater than 0.';
    }

    if (form.difficulty_level < 1 || form.difficulty_level > 3) {
      return 'Difficulty level must be between 1 and 3.';
    }

    return null;
  };

  const handleSave = async (publish: boolean) => {
    if (!selectedLesson) {
      return;
    }
    const validationError = validateForm(publish);
    if (validationError) {
      setSubmitError(validationError);
      return;
    }
    setIsSaving(true);
    try {
      const updated = await updateLesson(selectedLesson.id, {
        ...form,
        learning_objectives: normalizeStringArray(form.learning_objectives).filter(o => o.trim()),
        usage_examples: normalizeStringArray(form.usage_examples).filter(e => e.trim()),
        is_published: publish,
      });
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

        {loadError && <p className="text-sm text-destructive">{loadError}</p>}

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
                    <div className="flex items-center gap-2">
                      <p className="font-semibold">{lesson.title}</p>
                      <span className={`text-xs px-2 py-0.5 rounded-full ${lesson.is_published ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'}`}>
                        {lesson.is_published ? 'Published' : 'Draft'}
                      </span>
                    </div>
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
            {submitError && <p className="text-sm text-destructive">{submitError}</p>}
            {selectedLesson && (
              <p className="text-sm text-muted-foreground">
                Status: <span className="font-medium">{selectedLesson.is_published ? 'Published' : 'Draft'}</span>
              </p>
            )}
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
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-2">
                  <Label htmlFor="lesson-minutes">Estimated Minutes</Label>
                  <Input
                    id="lesson-minutes"
                    type="number"
                    min={5}
                    max={120}
                    value={form.estimated_minutes}
                    onChange={(e) => setForm((prev) => ({ ...prev, estimated_minutes: parseInt(e.target.value) }))}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="lesson-xp">XP Reward</Label>
                  <Input
                    id="lesson-xp"
                    type="number"
                    min={10}
                    max={1000}
                    value={form.xp_reward}
                    onChange={(e) => setForm((prev) => ({ ...prev, xp_reward: parseInt(e.target.value) }))}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="lesson-difficulty">Difficulty</Label>
                  <select
                    id="lesson-difficulty"
                    className="w-full h-10 px-3 rounded-md border border-input bg-background"
                    value={form.difficulty_level}
                    onChange={(e) => setForm((prev) => ({ ...prev, difficulty_level: parseInt(e.target.value) }))}
                  >
                    <option value={1}>Beginner</option>
                    <option value={2}>Intermediate</option>
                    <option value={3}>Advanced</option>
                  </select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="lesson-badge">Badge Name</Label>
                  <Input id="lesson-badge" value={form.badge_name} onChange={(e) => setForm((prev) => ({ ...prev, badge_name: e.target.value }))} />
                </div>
              </div>
              <div className="space-y-2">
                <Label>Learning Objectives</Label>
                {normalizeStringArray(form.learning_objectives).map((objective, index) => (
                  <div key={index} className="flex gap-2">
                    <Input
                      placeholder={`Objective ${index + 1}`}
                      value={objective}
                      onChange={(e) =>
                        setForm((prev) => ({
                          ...prev,
                          learning_objectives: normalizeStringArray(prev.learning_objectives).map((o, i) =>
                            i === index ? e.target.value : o
                          ),
                        }))
                      }
                    />
                    {normalizeStringArray(form.learning_objectives).length > 1 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        onClick={() =>
                          setForm((prev) => ({
                            ...prev,
                            learning_objectives: normalizeStringArray(prev.learning_objectives).filter((_, i) => i !== index),
                          }))
                        }
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    )}
                  </div>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setForm((prev) => ({
                      ...prev,
                      learning_objectives: [...normalizeStringArray(prev.learning_objectives), ''],
                    }))
                  }
                >
                  Add Objective
                </Button>
              </div>
              <div className="space-y-2">
                <Label htmlFor="lesson-origin">Origin Content</Label>
                <Textarea id="lesson-origin" rows={3} value={form.origin_content} onChange={(e) => setForm((prev) => ({ ...prev, origin_content: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lesson-definition">Definition Content</Label>
                <Textarea id="lesson-definition" rows={3} value={form.definition_content} onChange={(e) => setForm((prev) => ({ ...prev, definition_content: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label>Usage Examples</Label>
                {normalizeStringArray(form.usage_examples).map((example, index) => (
                  <div key={index} className="flex gap-2">
                    <Input
                      placeholder={`Example ${index + 1}`}
                      value={example}
                      onChange={(e) =>
                        setForm((prev) => ({
                          ...prev,
                          usage_examples: normalizeStringArray(prev.usage_examples).map((ex, i) =>
                            i === index ? e.target.value : ex
                          ),
                        }))
                      }
                    />
                    {normalizeStringArray(form.usage_examples).length > 1 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        onClick={() =>
                          setForm((prev) => ({
                            ...prev,
                            usage_examples: normalizeStringArray(prev.usage_examples).filter((_, i) => i !== index),
                          }))
                        }
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    )}
                  </div>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setForm((prev) => ({
                      ...prev,
                      usage_examples: [...normalizeStringArray(prev.usage_examples), ''],
                    }))
                  }
                >
                  Add Example
                </Button>
              </div>
              <div className="space-y-2">
                <Label htmlFor="lesson-lore">Lore Content</Label>
                <Textarea id="lesson-lore" rows={3} value={form.lore_content} onChange={(e) => setForm((prev) => ({ ...prev, lore_content: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lesson-evolution">Evolution Content</Label>
                <Textarea id="lesson-evolution" rows={3} value={form.evolution_content} onChange={(e) => setForm((prev) => ({ ...prev, evolution_content: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lesson-comparison">Comparison Content</Label>
                <Input id="lesson-comparison" value={form.comparison_content} onChange={(e) => setForm((prev) => ({ ...prev, comparison_content: e.target.value }))} />
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setIsEditOpen(false)}>Cancel</Button>
              {selectedLesson?.is_published ? (
                <Button variant="outline" onClick={() => handleSave(false)} disabled={isSaving}>
                  <Save className="h-4 w-4 mr-1" />
                  Unpublish
                </Button>
              ) : (
                <>
                  <Button variant="outline" onClick={() => handleSave(false)} disabled={isSaving}>
                    <Save className="h-4 w-4 mr-1" />
                    Save Draft
                  </Button>
                  <Button onClick={() => handleSave(true)} disabled={isSaving}>
                    {isSaving ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : null}
                    Publish
                  </Button>
                </>
              )}
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </MainLayout>
  );
};

export default AdminLessonsPage;
