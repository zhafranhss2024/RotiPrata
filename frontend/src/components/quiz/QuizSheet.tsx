import React, { useMemo, useState } from 'react';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { Quiz, Content } from '@/types';
import { submitContentQuiz } from '@/lib/api';
import { toast } from '@/components/ui/sonner';

interface QuizSheetProps {
  quiz: Quiz | null;
  content: Content | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onComplete?: (score: number, maxScore: number) => void;
}

export function QuizSheet({
  quiz,
  content,
  open,
  onOpenChange,
  onComplete,
}: QuizSheetProps) {
  const [currentQuestion, setCurrentQuestion] = useState(0);
  const [selectedAnswer, setSelectedAnswer] = useState<string | null>(null);
  const [showResult, setShowResult] = useState(false);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [quizComplete, setQuizComplete] = useState(false);
  const [score, setScore] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const questions = quiz?.questions && quiz.questions.length > 0 ? quiz.questions : null;
  const currentQ = questions ? questions[currentQuestion] : null;
  const maxScore = useMemo(() => {
    if (!questions) return 0;
    return questions.reduce((total, question) => total + (question.points ?? 10), 0);
  }, [questions]);

  const handleSelectAnswer = (answer: string) => {
    if (showResult) return;
    setSelectedAnswer(answer);
  };

  const handleSubmitAnswer = () => {
    if (!selectedAnswer || !currentQ) return;

    setAnswers(prev => ({ ...prev, [currentQ.id]: selectedAnswer }));
    setShowResult(true);

    if (selectedAnswer === currentQ.correct_answer) {
      setScore(prev => prev + (currentQ.points ?? 10));
    }
  };

  const handleNext = async () => {
    if (!questions) {
      return;
    }
    if (currentQuestion < questions.length - 1) {
      setCurrentQuestion(prev => prev + 1);
      setSelectedAnswer(null);
      setShowResult(false);
      return;
    }

    setQuizComplete(true);
    onComplete?.(score, maxScore);

    if (!content?.id) {
      return;
    }

    setIsSubmitting(true);
    try {
      await submitContentQuiz(content.id, { answers, timeTakenSeconds: null });
      toast('Quiz results saved', { position: 'bottom-center' });
    } catch (error) {
      console.warn('Failed to save quiz results', error);
      toast('Failed to save quiz results', { position: 'bottom-center' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReset = () => {
    setCurrentQuestion(0);
    setSelectedAnswer(null);
    setShowResult(false);
    setAnswers({});
    setQuizComplete(false);
    setScore(0);
    setIsSubmitting(false);
  };

  const handleClose = () => {
    handleReset();
    onOpenChange(false);
  };

  if (!content) return null;

  return (
    <Sheet open={open} onOpenChange={handleClose}>
      <SheetContent side="bottom" className="h-[85vh] rounded-t-3xl">
        <SheetHeader>
          <div className="flex items-center justify-between">
            <SheetTitle className="text-lg">Quick Quiz</SheetTitle>
            {questions ? (
              <Badge variant="outline">
                {currentQuestion + 1} / {questions.length}
              </Badge>
            ) : null}
          </div>
        </SheetHeader>

        <div className="mt-6 flex-1 overflow-y-auto">
          {!questions ? (
            <div className="text-center py-12">
              <div className="text-5xl mb-4">No quiz yet</div>
              <p className="text-muted-foreground mb-6">
                This video does not have a quick quiz.
              </p>
              <Button onClick={handleClose}>Close</Button>
            </div>
          ) : quizComplete ? (
            /* Results screen */
            <div className="text-center py-8">
              <div className="text-6xl mb-4">
                {score >= maxScore * 0.5 ? '🎉' : '💪'}
              </div>
              <h2 className="text-2xl font-bold mb-2">Quiz Complete!</h2>
              <p className="text-muted-foreground mb-6">
                You scored {score} out of {maxScore} points
              </p>
              
              <div className="w-32 h-32 mx-auto mb-6 relative">
                <svg className="w-full h-full -rotate-90">
                  <circle
                    cx="64"
                    cy="64"
                    r="56"
                    stroke="currentColor"
                    strokeWidth="8"
                    fill="none"
                    className="text-muted"
                  />
                  <circle
                    cx="64"
                    cy="64"
                    r="56"
                    stroke="currentColor"
                    strokeWidth="8"
                    fill="none"
                    strokeDasharray={`${maxScore ? (score / maxScore) * 352 : 0} 352`}
                    className="text-primary"
                  />
                </svg>
                <div className="absolute inset-0 flex items-center justify-center">
                  <span className="text-2xl font-bold">
                    {maxScore ? Math.round((score / maxScore) * 100) : 0}%
                  </span>
                </div>
              </div>

              <div className="flex gap-3">
                <Button variant="outline" onClick={handleReset} className="flex-1" disabled={isSubmitting}>
                  Try Again
                </Button>
                <Button onClick={handleClose} className="flex-1" disabled={isSubmitting}>
                  {isSubmitting ? 'Saving...' : 'Continue'}
                </Button>
              </div>
            </div>
          ) : (
            /* Question screen */
            <div className="space-y-6">
              {/* Question */}
              <div>
                <h3 className="text-xl font-semibold mb-2">
                  {currentQ?.question_text}
                </h3>
              </div>

              {/* Options */}
              <div className="space-y-3">
                {currentQ?.options && Object.entries(currentQ.options).map(([key, value]) => {
                  const isSelected = selectedAnswer === key;
                  const isCorrect = key === currentQ.correct_answer;
                  const showCorrect = showResult && isCorrect;
                  const showIncorrect = showResult && isSelected && !isCorrect;

                  return (
                    <button
                      key={key}
                      onClick={() => handleSelectAnswer(key)}
                      disabled={showResult}
                      className={cn(
                        "w-full p-4 rounded-xl border-2 text-left transition-all",
                        !showResult && isSelected && "border-sky-400 bg-sky-500/10",
                        !showResult && !isSelected && "border-border hover:border-sky-400/60",
                        showCorrect && "border-success bg-success/10",
                        showIncorrect && "border-destructive bg-destructive/10",
                      )}
                    >
                      <span>{value}</span>
                    </button>
                  );
                })}
              </div>

              {/* Explanation */}
              {showResult && currentQ?.explanation && (
                <div className="space-y-1">
                  <p
                    className={cn(
                      "font-medium",
                      selectedAnswer === currentQ.correct_answer ? "text-emerald-400" : "text-rose-400"
                    )}
                  >
                    {selectedAnswer === currentQ.correct_answer ? '✅ Correct!' : '❌ Not quite!'}
                  </p>
                  <p className="text-sm text-muted-foreground leading-relaxed">{currentQ.explanation}</p>
                </div>
              )}

              {/* Actions */}
              <div className="pt-4">
                {!showResult ? (
                  <Button
                    onClick={handleSubmitAnswer}
                    disabled={!selectedAnswer}
                    className="w-full gradient-primary border-0"
                    size="lg"
                  >
                    Check Answer
                  </Button>
                ) : (
                  <Button
                    onClick={handleNext}
                    className="w-full"
                    size="lg"
                  >
                    {currentQuestion < questions.length - 1 ? (
                      <>
                        Next Question
                        <ChevronRight className="h-5 w-5 ml-2" />
                      </>
                    ) : (
                      'See Results'
                    )}
                  </Button>
                )}
              </div>
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
