import React, { useState } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { 
  ArrowLeft, 
  Plus, 
  X, 
  GripVertical,
  Trash2,
  Save,
} from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import type { Lesson, QuizQuestion } from '@/types';
import { createLesson, createLessonQuiz } from '@/lib/api';

// Backend: /api/admin/lessons and /api/admin/lessons/{id}/quiz
// Dummy data is returned when mocks are enabled.

const CreateLessonPage = () => {
  const navigate = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);
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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);

    try {
      const lesson = await createLesson({
        ...formData,
        learning_objectives: formData.learning_objectives.filter(o => o.trim()),
        usage_examples: formData.usage_examples.filter(e => e.trim()),
      });

      if (quizQuestions.length > 0) {
        await createLessonQuiz(lesson.id, quizQuestions);
      }

      navigate('/admin/lessons');
    } catch (error) {
      console.warn('Create lesson failed', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <MainLayout>
      <div className="container max-w-3xl mx-auto px-4 py-6 md:py-8 pb-safe">
        {/* Header */}
        <div className="flex items-center gap-4 mb-6">
          <Link to="/admin" className="text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-5 w-5" />
          </Link>
          <h1 className="text-2xl font-bold">Create Lesson</h1>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Basic Info */}
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

          {/* Learning Objectives */}
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
                      <X className="h-4 w-4" />
                    </Button>
                  )}
                </div>
              ))}
            </CardContent>
          </Card>

          {/* Lesson Content */}
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
                        <X className="h-4 w-4" />
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

          {/* Quiz Questions */}
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

          {/* Submit */}
          <div className="flex gap-3">
            <Button type="button" variant="outline" className="flex-1" onClick={() => navigate('/admin')}>
              Cancel
            </Button>
            <Button type="submit" className="flex-1 gradient-primary border-0" disabled={isSubmitting}>
              <Save className="h-4 w-4 mr-2" />
              {isSubmitting ? 'Creating...' : 'Create Lesson'}
            </Button>
          </div>
        </form>
      </div>
    </MainLayout>
  );
};

export default CreateLessonPage;
