import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Plus, Trash2, Save, Loader2 } from 'lucide-react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import type { QuizQuestion } from '@/types';
import {
  fetchAdminLessonById,
  fetchAdminLessonQuizQuestions,
  replaceAdminLessonQuiz,
  updateLesson,
} from '@/lib/api';

const EditLessonPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    summary: '',
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
  const [quizQuestions, setQuizQuestions] = useState<Partial<QuizQuestion>[]>([]);

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
          // fall through
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

  const normalizeOptions = (options: Record<string, string> | null | undefined) => {
    const base = options ?? {};
    return {
      A: base.A ?? '',
      B: base.B ?? '',
      C: base.C ?? '',
      D: base.D ?? '',
    };
  };

  useEffect(() => {
    if (!id) {
      setLoadError('Missing lesson id.');
      setIsLoading(false);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    setLoadError(null);
    Promise.all([fetchAdminLessonById(id), fetchAdminLessonQuizQuestions(id)])
      .then(([lesson, questions]) => {
        if (cancelled) {
          return;
        }
        setFormData({
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
        const normalizedQuestions = (questions ?? []).map((q, idx) => ({
          question_text: q.question_text ?? '',
          question_type: q.question_type ?? 'multiple_choice',
          options: normalizeOptions(q.options ?? undefined),
          correct_answer: q.correct_answer ?? 'A',
          explanation: q.explanation ?? '',
          points: q.points ?? 10,
          order_index: q.order_index ?? idx,
        }));
        setQuizQuestions(normalizedQuestions);
      })
      .catch((error) => {
        if (cancelled) {
          return;
        }
        console.warn('Failed to load lesson details', error);
        setLoadError(error instanceof Error ? error.message : 'Failed to load lesson');
      })
      .finally(() => {
        if (cancelled) {
          return;
        }
        setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  const validateForm = (publish: boolean) => {
    if (!formData.title || !formData.title.trim()) {
      return 'Lesson title is required.';
    }

    const validateQuestions = () => {
      for (let i = 0; i < quizQuestions.length; i += 1) {
        const q = quizQuestions[i];
        if (!q.question_text || !q.question_text.trim()) {
          return `Question ${i + 1}: question text is required.`;
        }
        if (!q.explanation || !q.explanation.trim()) {
          return `Question ${i + 1}: explanation is required.`;
        }
        const options = (q.options ?? {}) as Record<string, string>;
        for (const key of ['A', 'B', 'C', 'D']) {
          if (!options[key] || !options[key].trim()) {
            return `Question ${i + 1}: option ${key} is required.`;
          }
        }
        if (!q.correct_answer || !['A', 'B', 'C', 'D'].includes(q.correct_answer)) {
          return `Question ${i + 1}: correct answer must be A, B, C, or D.`;
        }
        const points = q.points ?? 0;
        if (points < 1 || points > 100) {
          return `Question ${i + 1}: points must be between 1 and 100.`;
        }
      }
      return null;
    };

    if (!publish) {
      return null;
    }

    const requiredFields: Array<[string, string]> = [
      ['summary', formData.summary],
      ['description', formData.description],
      ['origin_content', formData.origin_content],
      ['definition_content', formData.definition_content],
      ['lore_content', formData.lore_content],
      ['evolution_content', formData.evolution_content],
      ['comparison_content', formData.comparison_content],
      ['badge_name', formData.badge_name],
    ];

    for (const [key, value] of requiredFields) {
      if (!value || !value.trim()) {
        return `Missing required field: ${key.replace('_', ' ')}`;
      }
    }

    const objectives = formData.learning_objectives.filter(o => o.trim());
    if (objectives.length === 0) {
      return 'At least one learning objective is required.';
    }

    const examples = formData.usage_examples.filter(e => e.trim());
    if (examples.length === 0) {
      return 'At least one usage example is required.';
    }

    if (!formData.estimated_minutes || formData.estimated_minutes <= 0) {
      return 'Estimated minutes must be greater than 0.';
    }

    if (!formData.xp_reward || formData.xp_reward <= 0) {
      return 'XP reward must be greater than 0.';
    }

    if (formData.difficulty_level < 1 || formData.difficulty_level > 3) {
      return 'Difficulty level must be between 1 and 3.';
    }

    if (quizQuestions.length === 0) {
      return 'At least one quiz question is required.';
    }

    return validateQuestions();
  };

  const buildQuestionsPayload = () =>
    quizQuestions.map((q, idx) => ({
      question_text: q.question_text?.trim(),
      question_type: q.question_type ?? 'multiple_choice',
      options: Object.fromEntries(
        ['A', 'B', 'C', 'D'].map((key) => [
          key,
          ((q.options ?? {}) as Record<string, string>)[key]?.trim() ?? '',
        ])
      ),
      correct_answer: q.correct_answer,
      explanation: q.explanation?.trim(),
      points: q.points ?? 10,
      order_index: q.order_index ?? idx,
    }));

  const handleAddObjective = () => {
    setFormData(prev => ({
      ...prev,
      learning_objectives: [...prev.learning_objectives, ''],
    }));
  };

  const handleRemoveObjective = (index: number) => {
    setFormData(prev => ({
      ...prev,
      learning_objectives: prev.learning_objectives.filter((_, i) => i !== index),
    }));
  };

  const handleObjectiveChange = (index: number, value: string) => {
    setFormData(prev => ({
      ...prev,
      learning_objectives: prev.learning_objectives.map((obj, i) => i === index ? value : obj),
    }));
  };

  const handleAddExample = () => {
    setFormData(prev => ({
      ...prev,
      usage_examples: [...prev.usage_examples, ''],
    }));
  };

  const handleRemoveExample = (index: number) => {
    setFormData(prev => ({
      ...prev,
      usage_examples: prev.usage_examples.filter((_, i) => i !== index),
    }));
  };

  const handleExampleChange = (index: number, value: string) => {
    setFormData(prev => ({
      ...prev,
      usage_examples: prev.usage_examples.map((ex, i) => i === index ? value : ex),
    }));
  };

  const handleAddQuestion = () => {
    setQuizQuestions(prev => [
      ...prev,
      {
        question_text: '',
        question_type: 'multiple_choice',
        options: { A: '', B: '', C: '', D: '' },
        correct_answer: 'A',
        explanation: '',
        points: 10,
        order_index: prev.length,
      },
    ]);
  };

  const handleRemoveQuestion = (index: number) => {
    setQuizQuestions(prev => prev.filter((_, i) => i !== index));
  };

  const handleQuestionChange = (index: number, field: string, value: any) => {
    setQuizQuestions(prev => prev.map((q, i) => i === index ? { ...q, [field]: value } : q));
  };

  const handleOptionChange = (questionIndex: number, optionKey: string, value: string) => {
    setQuizQuestions(prev => prev.map((q, i) => {
      if (i === questionIndex) {
        return {
          ...q,
          options: { ...(q.options as Record<string, string>), [optionKey]: value },
        };
      }
      return q;
    }));
  };

  const handleSubmit = async (publish: boolean) => {
    if (!id) {
      return;
    }
    const validationError = validateForm(publish);
    if (validationError) {
      setSubmitError(validationError);
      return;
    }
    setIsSubmitting(true);
    setSubmitError(null);

    try {
      await replaceAdminLessonQuiz(id, buildQuestionsPayload());
      await updateLesson(id, {
        ...formData,
        learning_objectives: formData.learning_objectives.filter(o => o.trim()),
        usage_examples: formData.usage_examples.filter(e => e.trim()),
        is_published: publish,
      });
      navigate('/admin/lessons');
    } catch (error) {
      console.warn('Update lesson failed', error);
      setSubmitError(error instanceof Error ? error.message : 'Failed to update lesson');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <MainLayout>
      <div className="container max-w-3xl mx-auto px-4 py-6 md:py-8 pb-safe">
        <div className="flex items-center gap-4 mb-6">
          <Link to="/admin/lessons" className="text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-5 w-5" />
          </Link>
          <h1 className="text-2xl font-bold">Edit Lesson</h1>
        </div>

        {isLoading ? (
          <div className="flex items-center gap-2 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading lesson...
          </div>
        ) : loadError ? (
          <p className="text-sm text-destructive">{loadError}</p>
        ) : (
          <form onSubmit={(e) => { e.preventDefault(); handleSubmit(true); }} className="space-y-6">
            {submitError && <p className="text-sm text-destructive">{submitError}</p>}
            <Card>
              <CardHeader>
                <CardTitle>Basic Information</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="title">Lesson Title *</Label>
                  <Input
                    id="title"
                    placeholder="e.g., Gen Alpha Slang 101"
                    value={formData.title}
                    onChange={(e) => setFormData(prev => ({ ...prev, title: e.target.value }))}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="summary">Short Summary *</Label>
                  <Input
                    id="summary"
                    placeholder="One sentence summary of the lesson"
                    value={formData.summary}
                    onChange={(e) => setFormData(prev => ({ ...prev, summary: e.target.value }))}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="description">Full Description *</Label>
                  <Textarea
                    id="description"
                    placeholder="Detailed description of what this lesson covers..."
                    value={formData.description}
                    onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
                    rows={4}
                    required
                  />
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="estimated_minutes">Duration (min)</Label>
                    <Input
                      id="estimated_minutes"
                      type="number"
                      min={5}
                      max={120}
                      value={formData.estimated_minutes}
                      onChange={(e) => setFormData(prev => ({ ...prev, estimated_minutes: parseInt(e.target.value) }))}
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="xp_reward">XP Reward</Label>
                    <Input
                      id="xp_reward"
                      type="number"
                      min={10}
                      max={1000}
                      value={formData.xp_reward}
                      onChange={(e) => setFormData(prev => ({ ...prev, xp_reward: parseInt(e.target.value) }))}
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="difficulty">Difficulty</Label>
                    <select
                      id="difficulty"
                      className="w-full h-10 px-3 rounded-md border border-input bg-background"
                      value={formData.difficulty_level}
                      onChange={(e) => setFormData(prev => ({ ...prev, difficulty_level: parseInt(e.target.value) }))}
                    >
                      <option value={1}>Beginner</option>
                      <option value={2}>Intermediate</option>
                      <option value={3}>Advanced</option>
                    </select>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="badge_name">Badge Name</Label>
                    <Input
                      id="badge_name"
                      placeholder="e.g., Slang Master"
                      value={formData.badge_name}
                      onChange={(e) => setFormData(prev => ({ ...prev, badge_name: e.target.value }))}
                    />
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>Learning Objectives</CardTitle>
                  <Button type="button" variant="outline" size="sm" onClick={handleAddObjective}>
                    <Plus className="h-4 w-4 mr-1" />
                    Add
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                {formData.learning_objectives.map((objective, index) => (
                  <div key={index} className="flex gap-2">
                    <Input
                      placeholder={`Objective ${index + 1}`}
                      value={objective}
                      onChange={(e) => handleObjectiveChange(index, e.target.value)}
                    />
                    {formData.learning_objectives.length > 1 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        onClick={() => handleRemoveObjective(index)}
                      >
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    )}
                  </div>
                ))}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Lesson Content</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="origin_content">Origin / History</Label>
                  <Textarea
                    id="origin_content"
                    placeholder="Where did this trend/term come from?"
                    value={formData.origin_content}
                    onChange={(e) => setFormData(prev => ({ ...prev, origin_content: e.target.value }))}
                    rows={3}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="definition_content">Definition / Meaning</Label>
                  <Textarea
                    id="definition_content"
                    placeholder="What does it mean?"
                    value={formData.definition_content}
                    onChange={(e) => setFormData(prev => ({ ...prev, definition_content: e.target.value }))}
                    rows={3}
                  />
                </div>

                <div className="space-y-2">
                  <Label>Usage Examples</Label>
                  {formData.usage_examples.map((example, index) => (
                    <div key={index} className="flex gap-2">
                      <Input
                        placeholder={`Example ${index + 1}`}
                        value={example}
                        onChange={(e) => handleExampleChange(index, e.target.value)}
                      />
                      {formData.usage_examples.length > 1 && (
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          onClick={() => handleRemoveExample(index)}
                        >
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      )}
                    </div>
                  ))}
                  <Button type="button" variant="outline" size="sm" onClick={handleAddExample}>
                    <Plus className="h-4 w-4 mr-1" />
                    Add Example
                  </Button>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="lore_content">Lore / Why It's Significant</Label>
                  <Textarea
                    id="lore_content"
                    placeholder="Why is this funny or important to Gen Alpha?"
                    value={formData.lore_content}
                    onChange={(e) => setFormData(prev => ({ ...prev, lore_content: e.target.value }))}
                    rows={3}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="evolution_content">Evolution</Label>
                  <Textarea
                    id="evolution_content"
                    placeholder="How has this changed over time?"
                    value={formData.evolution_content}
                    onChange={(e) => setFormData(prev => ({ ...prev, evolution_content: e.target.value }))}
                    rows={2}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="comparison_content">Boomer/Millennial Comparison</Label>
                  <Input
                    id="comparison_content"
                    placeholder="e.g., Rizz = Having 'game' (90s slang)"
                    value={formData.comparison_content}
                    onChange={(e) => setFormData(prev => ({ ...prev, comparison_content: e.target.value }))}
                  />
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>Quiz Questions</CardTitle>
                  <Button type="button" variant="outline" size="sm" onClick={handleAddQuestion}>
                    <Plus className="h-4 w-4 mr-1" />
                    Add Question
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="space-y-6">
                {quizQuestions.length === 0 ? (
                  <p className="text-center text-muted-foreground py-4">
                    No quiz questions yet. Add some to test learner understanding.
                  </p>
                ) : (
                  quizQuestions.map((question, qIndex) => (
                    <div key={qIndex} className="border rounded-lg p-4 space-y-4">
                      <div className="flex items-center justify-between">
                        <Badge>Question {qIndex + 1}</Badge>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          onClick={() => handleRemoveQuestion(qIndex)}
                        >
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </div>

                      <div className="space-y-2">
                        <Label>Question Text</Label>
                        <Input
                          placeholder="What does 'rizz' mean?"
                          value={question.question_text}
                          onChange={(e) => handleQuestionChange(qIndex, 'question_text', e.target.value)}
                        />
                      </div>

                      <div className="grid grid-cols-2 gap-3">
                        {['A', 'B', 'C', 'D'].map((key) => (
                          <div key={key} className="space-y-1">
                            <Label className="text-sm">Option {key}</Label>
                            <Input
                              placeholder={`Option ${key}`}
                              value={(question.options as Record<string, string>)?.[key] || ''}
                              onChange={(e) => handleOptionChange(qIndex, key, e.target.value)}
                            />
                          </div>
                        ))}
                      </div>

                      <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-2">
                          <Label>Correct Answer</Label>
                          <select
                            className="w-full h-10 px-3 rounded-md border border-input bg-background"
                            value={question.correct_answer}
                            onChange={(e) => handleQuestionChange(qIndex, 'correct_answer', e.target.value)}
                          >
                            <option value="A">A</option>
                            <option value="B">B</option>
                            <option value="C">C</option>
                            <option value="D">D</option>
                          </select>
                        </div>
                        <div className="space-y-2">
                          <Label>Points</Label>
                          <Input
                            type="number"
                            min={1}
                            max={100}
                            value={question.points}
                            onChange={(e) => handleQuestionChange(qIndex, 'points', parseInt(e.target.value))}
                          />
                        </div>
                      </div>

                      <div className="space-y-2">
                        <Label>Explanation (shown after answer)</Label>
                        <Textarea
                          placeholder="Why this is the correct answer..."
                          value={question.explanation || ''}
                          onChange={(e) => handleQuestionChange(qIndex, 'explanation', e.target.value)}
                          rows={2}
                        />
                      </div>
                    </div>
                  ))
                )}
              </CardContent>
            </Card>

            <div className="flex gap-3">
              <Button type="button" variant="outline" className="flex-1" onClick={() => navigate('/admin/lessons')}>
                Cancel
              </Button>
              <Button
                type="button"
                variant="outline"
                className="flex-1"
                onClick={() => handleSubmit(false)}
                disabled={isSubmitting}
              >
                Save Draft
              </Button>
              <Button type="submit" className="flex-1 gradient-primary border-0" disabled={isSubmitting}>
                <Save className="h-4 w-4 mr-2" />
                {isSubmitting ? 'Saving...' : 'Publish Lesson'}
              </Button>
            </div>
          </form>
        )}
      </div>
    </MainLayout>
  );
};

export default EditLessonPage;
