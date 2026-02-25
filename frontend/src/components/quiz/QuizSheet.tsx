import React, { useEffect, useMemo, useState } from 'react';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import { fetchLatestContentQuizResult, submitContentQuizResult } from '@/lib/api';
import type { Quiz, QuizQuestion, Content } from '@/types';

interface QuizSheetProps {
  quiz: Quiz | null;
  content: Content | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onComplete?: (score: number, maxScore: number) => void;
}

type StoredQuickQuizResult = {
  score: number;
  maxScore: number;
  completedAt: string;
};

const QUICK_QUIZ_RESULTS_KEY = 'rotiprata.quickQuiz.latestResults.v1';

const readStoredQuickQuizResults = (): Record<string, StoredQuickQuizResult> => {
  if (typeof window === 'undefined' || typeof localStorage === 'undefined') return {};
  try {
    const raw = localStorage.getItem(QUICK_QUIZ_RESULTS_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed as Record<string, StoredQuickQuizResult> : {};
  } catch {
    return {};
  }
};

const writeStoredQuickQuizResult = (quizKey: string, result: StoredQuickQuizResult) => {
  if (typeof window === 'undefined' || typeof localStorage === 'undefined') return;
  const existing = readStoredQuickQuizResults();
  existing[quizKey] = result;
  localStorage.setItem(QUICK_QUIZ_RESULTS_KEY, JSON.stringify(existing));
};

// TODO: Replace with Java backend API calls
// POST /api/quizzes/{id}/submit - Submit quiz answers
// GET /api/quizzes/{id}/results - Get quiz results

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
  const [completedResult, setCompletedResult] = useState<{ score: number; maxScore: number } | null>(null);

  // DUMMY QUESTIONS: used when no quiz is returned from the backend.
  const mockQuestions: QuizQuestion[] = [
    {
      id: '1',
      quiz_id: 'mock',
      question_text: `What does "${content?.title || 'this term'}" mean?`,
      question_type: 'multiple_choice',
      media_url: null,
      options: {
        A: 'A formal greeting',
        B: content?.definition_used || 'The correct answer',
        C: 'A type of food',
        D: 'None of the above',
      },
      correct_answer: 'B',
      explanation: content?.definition_used || 'This is the correct usage!',
      points: 10,
      order_index: 0,
      created_at: new Date().toISOString(),
    },
  ];

  const questions = quiz?.questions || mockQuestions;
  const currentQ = questions[currentQuestion];
  const maxScore = useMemo(
    () => questions.reduce((total, question) => total + (question.points ?? 10), 0),
    [questions]
  );
  const quickQuizKey = useMemo(
    () => `quiz:${quiz?.id ?? `content:${content?.id ?? 'unknown'}`}`,
    [quiz?.id, content?.id]
  );

  const resetQuizState = () => {
    setCurrentQuestion(0);
    setSelectedAnswer(null);
    setShowResult(false);
    setAnswers({});
    setQuizComplete(false);
    setScore(0);
    setCompletedResult(null);
  };

  useEffect(() => {
    if (!open || !content) return;
    resetQuizState();
    let cancelled = false;
    const applyStoredLatest = () => {
      const stored = readStoredQuickQuizResults()[quickQuizKey];
      if (!stored || cancelled) return;
      setCompletedResult({ score: stored.score, maxScore: stored.maxScore });
      setScore(stored.score);
      setQuizComplete(true);
      setCurrentQuestion(Math.max(0, questions.length - 1));
    };
    fetchLatestContentQuizResult(content.id)
      .then((latest) => {
        if (cancelled) return;
        if (!latest) {
          applyStoredLatest();
          return;
        }
        setCompletedResult({ score: latest.score, maxScore: latest.maxScore });
        setScore(latest.score);
        setQuizComplete(true);
        setCurrentQuestion(Math.max(0, questions.length - 1));
      })
      .catch(() => {
        applyStoredLatest();
      });
    return () => {
      cancelled = true;
    };
  }, [open, content, quickQuizKey, questions.length]);

  const handleSelectAnswer = (answer: string) => {
    if (showResult) return;
    setSelectedAnswer(answer);
  };

  const handleSubmitAnswer = () => {
    if (!selectedAnswer || !currentQ) return;

    setAnswers(prev => ({ ...prev, [currentQ.id]: selectedAnswer }));
    setShowResult(true);

    if (selectedAnswer === currentQ.correct_answer) {
      setScore(prev => prev + currentQ.points);
    }
  };

  const handleNext = async () => {
    if (currentQuestion < questions.length - 1) {
      setCurrentQuestion(prev => prev + 1);
      setSelectedAnswer(null);
      setShowResult(false);
    } else {
      // Quiz complete
      setQuizComplete(true);
      setCompletedResult({ score, maxScore });
      writeStoredQuickQuizResult(quickQuizKey, {
        score,
        maxScore,
        completedAt: new Date().toISOString(),
      });
      if (content) {
        submitContentQuizResult(content.id, {
          score,
          maxScore,
          answers,
        }).catch((error) => {
          console.warn('Failed to persist quick quiz result', error);
        });
      }
      
      onComplete?.(score, maxScore);
    }
  };

  const handleClose = () => {
    resetQuizState();
    onOpenChange(false);
  };

  if (!content) return null;

  return (
    <Sheet open={open} onOpenChange={handleClose}>
      <SheetContent side="bottom" className="h-[85vh] rounded-t-3xl">
        <SheetHeader>
          <div className="flex items-center justify-between">
            <SheetTitle className="text-lg">Quick Quiz</SheetTitle>
            <Badge variant="outline">
              {currentQuestion + 1} / {questions.length}
            </Badge>
          </div>
        </SheetHeader>

        <div className="mt-6 flex-1 overflow-y-auto">
          {quizComplete ? (
            /* Results screen */
            <div className="text-center py-8">
              <div className="text-6xl mb-4">
                {(completedResult?.score ?? score) >= Math.round((completedResult?.maxScore ?? maxScore) * 0.5) ? '🎉' : '💪'}
              </div>
              <h2 className="text-2xl font-bold mb-2">Quiz Complete!</h2>
              <p className="text-muted-foreground mb-6">
                You scored {completedResult?.score ?? score} out of {completedResult?.maxScore ?? maxScore} points
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
                    strokeDasharray={`${((completedResult?.score ?? score) / Math.max(1, completedResult?.maxScore ?? maxScore)) * 352} 352`}
                    className="text-primary"
                  />
                </svg>
                <div className="absolute inset-0 flex items-center justify-center">
                  <span className="text-2xl font-bold">
                    {Math.round(((completedResult?.score ?? score) / Math.max(1, completedResult?.maxScore ?? maxScore)) * 100)}%
                  </span>
                </div>
              </div>

              <div className="flex gap-3">
                <Button variant="outline" onClick={resetQuizState} className="flex-1">
                  Try Again
                </Button>
                <Button onClick={handleClose} className="flex-1">
                  Continue
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
