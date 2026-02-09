import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Card, CardContent } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { 
  ArrowLeft, 
  Clock, 
  Star, 
  Users, 
  Play, 
  Bookmark,
  CheckCircle,
  ChevronRight,
} from 'lucide-react';
import type { Lesson } from '@/types';
import {
  enrollLesson,
  fetchLessonById,
  fetchLessonProgress,
  fetchLessonSections,
  saveLesson,
} from '@/lib/api';

// Backend: /api/lessons/{id}, /api/lessons/{id}/enroll, /api/lessons/{id}/progress
// Dummy data is returned when mocks are enabled.

const LessonDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const [lesson, setLesson] = useState<Lesson | null>(null);
  const [isEnrolled, setIsEnrolled] = useState(true);
  const [progress, setProgress] = useState(0);
  const [lessonSections, setLessonSections] = useState<{ id: string; title: string; completed: boolean }[]>([]);

  useEffect(() => {
    if (!id) return;
    fetchLessonById(id)
      .then(setLesson)
      .catch((error) => console.warn('Failed to load lesson', error));

    fetchLessonProgress()
      .then((progressMap) => setProgress(progressMap[id] ?? 0))
      .catch((error) => console.warn('Failed to load progress', error));

    fetchLessonSections(id)
      .then(setLessonSections)
      .catch((error) => console.warn('Failed to load lesson sections', error));
  }, [id]);

  const handleEnroll = async () => {
    if (!id) return;
    try {
      await enrollLesson(id);
      setIsEnrolled(true);
    } catch (error) {
      console.warn('Enroll failed', error);
    }
  };

  const handleSave = async () => {
    if (!id) return;
    try {
      await saveLesson(id);
    } catch (error) {
      console.warn('Save lesson failed', error);
    }
  };

  const getDifficultyLabel = (level: number) => {
    switch (level) {
      case 1: return { label: 'Beginner', color: 'bg-success' };
      case 2: return { label: 'Intermediate', color: 'bg-warning' };
      case 3: return { label: 'Advanced', color: 'bg-destructive' };
      default: return { label: 'Beginner', color: 'bg-success' };
    }
  };

  if (!lesson) {
    return (
      <MainLayout>
        <div className="container max-w-2xl mx-auto px-4 py-16 text-center text-muted-foreground">
          Loading lesson...
        </div>
      </MainLayout>
    );
  }

  const difficulty = getDifficultyLabel(lesson.difficulty_level);

  return (
    <MainLayout>
      <div className="container max-w-2xl mx-auto px-4 py-6 md:py-8 pb-safe">
        {/* Back button */}
        <Link to="/lessons" className="inline-flex items-center text-muted-foreground hover:text-foreground mb-4">
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Lessons
        </Link>

        {/* Header */}
        <div className="rounded-2xl gradient-primary p-6 mb-6 text-white">
          <div className="flex items-start justify-between mb-4">
            <Badge className={`${difficulty.color} text-white`}>
              {difficulty.label}
            </Badge>
            <Button variant="ghost" size="icon" className="text-white hover:bg-white/20" onClick={handleSave}>
              <Bookmark className="h-5 w-5" />
            </Button>
          </div>
          
          <h1 className="text-2xl font-bold mb-2">{lesson.title}</h1>
          <p className="text-white/80 mb-4">{lesson.summary}</p>
          
          <div className="flex flex-wrap items-center gap-4 text-sm text-white/80">
            <span className="flex items-center gap-1">
              <Clock className="h-4 w-4" />
              {lesson.estimated_minutes} min
            </span>
            <span className="flex items-center gap-1">
              <Star className="h-4 w-4" />
              {lesson.xp_reward} XP
            </span>
            <span className="flex items-center gap-1">
              <Users className="h-4 w-4" />
              {lesson.completion_count} completed
            </span>
          </div>
          
          {lesson.badge_name && (
            <div className="mt-4 inline-flex items-center gap-2 bg-white/20 rounded-full px-3 py-1">
              <span>🏆</span>
              <span className="text-sm">Earn: {lesson.badge_name}</span>
            </div>
          )}
        </div>

        {/* Progress (if enrolled) */}
        {isEnrolled && (
          <Card className="mb-6">
            <CardContent className="p-4">
              <div className="flex items-center justify-between mb-2">
                <span className="font-medium">Your Progress</span>
                <span className="text-sm text-muted-foreground">{progress}%</span>
              </div>
              <Progress value={progress} className="h-2" />
            </CardContent>
          </Card>
        )}

        {/* Learning Objectives */}
        <div className="mb-6">
          <h2 className="font-semibold mb-3">What You'll Learn</h2>
          <Card>
            <CardContent className="p-4">
              <ul className="space-y-2">
                {lesson.learning_objectives?.map((objective, index) => (
                  <li key={index} className="flex items-start gap-2">
                    <CheckCircle className="h-5 w-5 text-success flex-shrink-0 mt-0.5" />
                    <span>{objective}</span>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </div>

        {/* Lesson Sections */}
        {isEnrolled && (
          <div className="mb-6">
            <h2 className="font-semibold mb-3">Lesson Content</h2>
            <Card>
              <CardContent className="p-0">
                {lessonSections.map((section, index) => (
                  <React.Fragment key={section.id}>
                    <Link 
                      to={`/lessons/${id}/${section.id}`}
                      className="flex items-center gap-3 p-4 hover:bg-muted/50 transition-colors"
                    >
                      <div className={`w-8 h-8 rounded-full flex items-center justify-center ${
                        section.completed 
                          ? 'bg-success text-white' 
                          : 'bg-muted text-muted-foreground'
                      }`}>
                        {section.completed ? (
                          <CheckCircle className="h-5 w-5" />
                        ) : (
                          <span className="text-sm font-medium">{index + 1}</span>
                        )}
                      </div>
                      <span className="flex-1 font-medium">{section.title}</span>
                      <ChevronRight className="h-5 w-5 text-muted-foreground" />
                    </Link>
                    {index < lessonSections.length - 1 && <Separator />}
                  </React.Fragment>
                ))}
              </CardContent>
            </Card>
          </div>
        )}

        {/* Description */}
        <div className="mb-6">
          <h2 className="font-semibold mb-3">About This Lesson</h2>
          <p className="text-muted-foreground">{lesson.description}</p>
        </div>

        {/* Comparison section */}
        {lesson.comparison_content && (
          <div className="mb-6">
            <h2 className="font-semibold mb-3">Boomer Translation Guide</h2>
            <Card className="bg-muted/50">
              <CardContent className="p-4">
                <p className="text-sm">{lesson.comparison_content}</p>
              </CardContent>
            </Card>
          </div>
        )}

        {/* CTA */}
        <div className="sticky bottom-nav-height md:bottom-4 left-0 right-0 p-4 bg-background/80 backdrop-blur-lg border-t md:border md:rounded-xl">
          {isEnrolled ? (
            <Button asChild className="w-full gradient-primary border-0" size="lg">
              <Link to={`/lessons/${id}/intro`}>
                <Play className="h-5 w-5 mr-2" />
                {progress > 0 ? 'Continue Lesson' : 'Start Lesson'}
              </Link>
            </Button>
          ) : (
            <Button onClick={handleEnroll} className="w-full" size="lg">
              Enroll Now - Free
            </Button>
          )}
        </div>
      </div>
    </MainLayout>
  );
};

export default LessonDetailPage;
