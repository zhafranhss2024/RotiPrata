import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Separator } from '@/components/ui/separator';
import { ArrowLeft, CheckCircle, ChevronLeft, ChevronRight, Clock } from 'lucide-react';
import type { Lesson, LessonSection } from '@/types';
import {
  enrollLesson,
  fetchLessonById,
  fetchLessonProgress,
  fetchLessonSections,
  updateLessonProgress,
} from '@/lib/api';

const LessonSectionPage = () => {
  const { id, sectionId } = useParams<{ id: string; sectionId: string }>();
  const navigate = useNavigate();
  const [lesson, setLesson] = useState<Lesson | null>(null);
  const [sections, setSections] = useState<LessonSection[]>([]);
  const [progress, setProgress] = useState(0);
  const [isEnrolled, setIsEnrolled] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    let active = true;
    setIsLoading(true);
    setError(null);

    Promise.all([fetchLessonById(id), fetchLessonSections(id), fetchLessonProgress()])
      .then(([lessonData, sectionData, progressMap]) => {
        if (!active) return;
        const sortedSections = [...sectionData].sort((a, b) => a.order_index - b.order_index);
        setLesson(lessonData);
        setSections(sortedSections);
        setProgress(progressMap[id] ?? 0);
        setIsEnrolled(Object.prototype.hasOwnProperty.call(progressMap, id));
      })
      .catch((loadError) => {
        if (!active) return;
        console.warn('Failed to load lesson section', loadError);
        setError('Failed to load lesson content. Please try again.');
      })
      .finally(() => {
        if (active) {
          setIsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [id]);

  const currentIndex = useMemo(
    () => sections.findIndex((section) => section.id === sectionId),
    [sections, sectionId]
  );

  useEffect(() => {
    if (!id || !sectionId || sections.length === 0 || currentIndex !== -1) return;
    navigate(`/lessons/${id}/${sections[0].id}`, { replace: true });
  }, [id, sectionId, sections, currentIndex, navigate]);

  const totalSections = sections.length;
  const hasValidCurrentSection = currentIndex >= 0 && currentIndex < totalSections;
  const currentSection = hasValidCurrentSection ? sections[currentIndex] : null;
  const completedCount =
    totalSections > 0 ? Math.min(totalSections, Math.floor((Math.max(progress, 0) / 100) * totalSections)) : 0;
  const viewedCount = hasValidCurrentSection ? Math.max(completedCount, currentIndex + 1) : completedCount;
  const displayProgress = totalSections > 0 ? Math.round((viewedCount / totalSections) * 100) : progress;
  const hasPrevious = currentIndex > 0;
  const hasNext = hasValidCurrentSection && currentIndex < totalSections - 1;

  const persistProgress = async (targetProgress: number) => {
    if (!id) return;
    if (!isEnrolled) {
      await enrollLesson(id);
      setIsEnrolled(true);
    }
    await updateLessonProgress(id, targetProgress);
    setProgress(targetProgress);
  };

  const handleCompleteSection = async () => {
    if (!id || !hasValidCurrentSection) return;
    setIsSaving(true);
    setSaveError(null);
    try {
      const targetProgress = totalSections > 0 ? Math.round((viewedCount / totalSections) * 100) : progress;
      await persistProgress(Math.max(progress, targetProgress));

      if (hasNext) {
        navigate(`/lessons/${id}/${sections[currentIndex + 1].id}`);
      } else {
        navigate(`/lessons/${id}`);
      }
    } catch (saveProgressError) {
      console.warn('Failed to save lesson progress', saveProgressError);
      setSaveError('Unable to save progress right now. Please try again.');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <MainLayout>
        <div className="container max-w-3xl mx-auto px-4 py-16 text-center text-muted-foreground">
          Loading lesson...
        </div>
      </MainLayout>
    );
  }

  if (error || !lesson) {
    return (
      <MainLayout>
        <div className="container max-w-3xl mx-auto px-4 py-12">
          <Card className="border-destructive/30 bg-destructive/10">
            <CardContent className="p-6 text-center space-y-4">
              <p className="text-destructive">{error ?? 'Unable to load this lesson.'}</p>
              <Button asChild variant="outline">
                <Link to="/lessons">Back to Lesson Hub</Link>
              </Button>
            </CardContent>
          </Card>
        </div>
      </MainLayout>
    );
  }

  if (!currentSection) {
    return (
      <MainLayout>
        <div className="container max-w-3xl mx-auto px-4 py-12">
          <Card>
            <CardContent className="p-6 text-center space-y-4">
              <p className="text-muted-foreground">This lesson does not have any readable sections yet.</p>
              <Button asChild variant="outline">
                <Link to={`/lessons/${id}`}>Back to Lesson</Link>
              </Button>
            </CardContent>
          </Card>
        </div>
      </MainLayout>
    );
  }

  return (
    <MainLayout>
      <div className="container max-w-3xl mx-auto px-4 py-6 md:py-8 pb-safe">
        <Link to={`/lessons/${id}`} className="inline-flex items-center text-muted-foreground hover:text-foreground mb-4">
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Lesson Details
        </Link>

        <Card className="mb-6">
          <CardContent className="p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h1 className="text-xl font-bold">{lesson.title}</h1>
              <Badge variant="secondary">
                Section {currentIndex + 1} of {totalSections}
              </Badge>
            </div>

            <div className="flex items-center gap-3 text-sm text-muted-foreground">
              <span className="flex items-center gap-1">
                <Clock className="h-4 w-4" />
                {currentSection.duration_minutes} min
              </span>
              <span>{displayProgress}% complete</span>
            </div>

            <Progress value={displayProgress} className="h-2" />
          </CardContent>
        </Card>

        <Card className="mb-6">
          <CardContent className="p-6">
            <h2 className="text-lg font-semibold mb-3">{currentSection.title}</h2>
            <div className="space-y-3 text-sm leading-6">
              {currentSection.content
                .split('\n')
                .filter((line) => line.trim().length > 0)
                .map((line, index) => (
                  <p key={`${currentSection.id}-${index}`}>{line}</p>
                ))}
            </div>
          </CardContent>
        </Card>

        <Card className="mb-6">
          <CardContent className="p-0">
            {sections.map((section, index) => {
              const isSectionCompleted = index < viewedCount;
              return (
                <React.Fragment key={section.id}>
                  <Link
                    to={`/lessons/${id}/${section.id}`}
                    className="flex items-center gap-3 px-4 py-3 hover:bg-muted/50 transition-colors"
                  >
                    <div
                      className={`w-7 h-7 rounded-full flex items-center justify-center ${
                        isSectionCompleted ? 'bg-success text-white' : 'bg-muted text-muted-foreground'
                      }`}
                    >
                      {isSectionCompleted ? <CheckCircle className="h-4 w-4" /> : <span>{index + 1}</span>}
                    </div>
                    <span className="flex-1 text-sm font-medium">{section.title}</span>
                    {section.id === currentSection.id && <Badge variant="outline">Current</Badge>}
                  </Link>
                  {index < sections.length - 1 && <Separator />}
                </React.Fragment>
              );
            })}
          </CardContent>
        </Card>

        {saveError && (
          <Card className="mb-4 border-destructive/30 bg-destructive/10">
            <CardContent className="p-3 text-sm text-destructive">{saveError}</CardContent>
          </Card>
        )}

        <div className="sticky bottom-nav-height md:bottom-4 left-0 right-0 p-4 bg-background/80 backdrop-blur-lg border-t md:border md:rounded-xl flex gap-3">
          <Button
            type="button"
            variant="outline"
            className="flex-1"
            disabled={!hasPrevious || isSaving}
            onClick={() => {
              if (!hasPrevious) return;
              navigate(`/lessons/${id}/${sections[currentIndex - 1].id}`);
            }}
          >
            <ChevronLeft className="h-4 w-4 mr-1" />
            Previous
          </Button>

          <Button type="button" className="flex-1 gradient-primary border-0" onClick={handleCompleteSection} disabled={isSaving}>
            {hasNext ? (
              <>
                Mark Complete & Next
                <ChevronRight className="h-4 w-4 ml-1" />
              </>
            ) : (
              'Finish Lesson'
            )}
          </Button>
        </div>
      </div>
    </MainLayout>
  );
};

export default LessonSectionPage;
