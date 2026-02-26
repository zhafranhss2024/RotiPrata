import React, { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { MainLayout } from "@/components/layout/MainLayout";
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Check,
  CheckCircle2,
  Heart,
  RotateCcw,
  Star,
  XCircle,
} from "lucide-react";
import type {
  Lesson,
  LessonHeartsStatus,
  LessonProgressDetail,
  LessonQuizAnswerResult,
  LessonQuizQuestion,
  LessonQuizState,
} from "@/types";
import {
  fetchLessonById,
  fetchLessonProgressDetail,
  fetchLessonQuizState,
  restartLessonQuiz,
  submitLessonQuizAnswer,
} from "@/lib/api";
import { emitHeartsUpdated } from "@/lib/heartsEvents";
import { cn } from "@/lib/utils";
import { MatchPairsBoard } from "@/components/quiz/MatchPairsBoard";
import { ConversationBoard } from "@/components/quiz/ConversationBoard";

type ClozeTemplatePart = { kind: "text"; value: string } | { kind: "blank"; blankId: string };
type ClozeBankOption = {
  optionKey: string;
  text: string;
  matches: Array<{
    blankId: string;
    choiceId: string;
  }>;
};

const CLOZE_TOKEN_REGEX = /\{\{([a-zA-Z0-9_-]+)\}\}/g;
const STEP_GAP = 70;
const MAX_VISIBLE_DISTANCE = 3;
const STEP_HORIZONTAL_OFFSET = 26;

const formatRefill = (value?: string | null) => {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleString();
};

const questionTypeLabel = (question: LessonQuizQuestion) => {
  switch (question.questionType) {
    case "multiple_choice":
      return "Choose one answer";
    case "true_false":
      return "Choose true or false";
    case "cloze":
      return "Fill in the blanks";
    case "word_bank":
      return "Build the sentence";
    case "conversation":
      return "Complete the dialogue";
    case "match_pairs":
      return "Match the pairs";
    case "short_text":
      return "Type your answer";
    default:
      return "Answer the question";
  }
};

const parseClozeTemplate = (template: string | null | undefined): ClozeTemplatePart[] => {
  const source = template ?? "";
  const parts: ClozeTemplatePart[] = [];
  let cursor = 0;
  const regex = new RegExp(CLOZE_TOKEN_REGEX.source, "g");
  let match = regex.exec(source);
  while (match) {
    const tokenStart = match.index;
    const tokenEnd = regex.lastIndex;
    if (tokenStart > cursor) {
      parts.push({ kind: "text", value: source.slice(cursor, tokenStart) });
    }
    parts.push({ kind: "blank", blankId: match[1] });
    cursor = tokenEnd;
    match = regex.exec(source);
  }
  if (cursor < source.length) {
    parts.push({ kind: "text", value: source.slice(cursor) });
  }
  return parts;
};

const normalizeQuestionResponse = (
  question: LessonQuizQuestion,
  response: Record<string, unknown> | null
) => {
  if (!response) return null;

  switch (question.questionType) {
    case "multiple_choice":
      return typeof response.choiceId === "string" ? response : null;
    case "true_false":
      return typeof response.value === "boolean" ? response : null;
    case "cloze": {
      const answers = response.answers as Record<string, unknown> | undefined;
      if (!answers || typeof answers !== "object") return null;
      const blanks = Object.keys(question.payload.blankOptions);
      return blanks.every((blankId) => typeof answers[blankId] === "string") ? response : null;
    }
    case "conversation": {
      const answers = response.answers as Record<string, unknown> | undefined;
      if (!answers || typeof answers !== "object") return null;
      return question.payload.turns.every((turn) => typeof answers[turn.id] === "string") ? response : null;
    }
    case "match_pairs": {
      const pairs = response.pairs as Record<string, unknown> | undefined;
      if (!pairs || typeof pairs !== "object") return null;
      return question.payload.left.every((item) => typeof pairs[item.id] === "string") ? response : null;
    }
    case "word_bank": {
      const tokenOrder = response.tokenOrder;
      if (!Array.isArray(tokenOrder) || tokenOrder.length === 0) return null;
      if (!tokenOrder.every((tokenId) => typeof tokenId === "string")) return null;
      return response;
    }
    case "short_text": {
      const text = typeof response.text === "string" ? response.text.trim() : "";
      return text.length > 0 ? { text } : null;
    }
    default:
      return null;
  }
};

const railOffsetX = (index: number) =>
  index % 2 === 0 ? -STEP_HORIZONTAL_OFFSET : STEP_HORIZONTAL_OFFSET;

const resolveHeartsAfterSubmit = (
  previousHearts: LessonHeartsStatus,
  submitResult: LessonQuizAnswerResult
): LessonHeartsStatus => {
  const serverHearts = submitResult.hearts;
  if (submitResult.correct) {
    return serverHearts;
  }
  if (serverHearts.heartsRemaining < previousHearts.heartsRemaining) {
    return serverHearts;
  }
  return {
    ...serverHearts,
    heartsRemaining: Math.max(0, previousHearts.heartsRemaining - 1),
  };
};

const DuoChoiceButton = ({
  selected,
  children,
  onClick,
  disabled,
}: {
  selected: boolean;
  children: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
}) => (
  <button
    type="button"
    className={cn(
      "w-full rounded-2xl border px-4 py-4 text-left transition active:translate-y-[5px] active:shadow-none",
      selected
        ? "border-[#b51f3d] bg-duoGreen text-white shadow-mainShadow"
        : "border-mainAlt bg-main text-white hover:bg-mainAlt shadow-duoGrayBorderShadow"
    )}
    onClick={onClick}
    disabled={disabled}
  >
    {children}
  </button>
);

const LessonQuizQuestionRenderer = ({
  question,
  response,
  onChange,
  disabled,
}: {
  question: LessonQuizQuestion;
  response: Record<string, unknown> | null;
  onChange: (next: Record<string, unknown>) => void;
  disabled: boolean;
}) => {
  const [selectedClozeOptionKey, setSelectedClozeOptionKey] = useState<string | null>(null);
  const [clozeUiError, setClozeUiError] = useState<string | null>(null);
  const [draggingClozeOptionKey, setDraggingClozeOptionKey] = useState<string | null>(null);
  const [hoveredBlankId, setHoveredBlankId] = useState<string | null>(null);

  if (question.questionType === "multiple_choice" || question.questionType === "true_false") {
    const choices = question.payload.choices;
    const selectedId =
      typeof response?.choiceId === "string"
        ? response.choiceId
        : typeof response?.value === "boolean"
          ? String(response.value)
          : null;

    return (
      <div className="grid gap-3 lg:grid-cols-2">
        {choices.map((choice) => {
          const selected = selectedId === choice.id;
          return (
            <DuoChoiceButton
              key={choice.id}
              selected={selected}
              onClick={() => {
                if (question.questionType === "true_false") {
                  onChange({ value: choice.id === "true" });
                } else {
                  onChange({ choiceId: choice.id });
                }
              }}
              disabled={disabled}
            >
              {question.questionType === "multiple_choice" ? (
                <span className="font-bold mr-2">{choice.id}.</span>
              ) : null}
              <span>{choice.text}</span>
            </DuoChoiceButton>
          );
        })}
      </div>
    );
  }

  if (question.questionType === "cloze") {
    const blankOptions = question.payload.blankOptions;
    const answers = (response?.answers as Record<string, unknown>) ?? {};
    const templateParts = parseClozeTemplate(question.questionText);
    const hasTemplateBlanks = templateParts.some((part) => part.kind === "blank");
    const optionBank = (() => {
      const map = new Map<string, ClozeBankOption>();
      Object.entries(blankOptions).forEach(([blankId, choices]) => {
        choices.forEach((choice) => {
          const normalizedText = choice.text.trim().toLowerCase();
          const existing = map.get(normalizedText);
          if (existing) {
            existing.matches.push({ blankId, choiceId: choice.id });
            return;
          }
          map.set(normalizedText, {
            optionKey: normalizedText,
            text: choice.text,
            matches: [{ blankId, choiceId: choice.id }],
          });
        });
      });
      return Array.from(map.values());
    })();

    const assignOptionToBlank = (targetBlankId: string, optionKey: string) => {
      const option = optionBank.find((item) => item.optionKey === optionKey);
      if (!option) return;
      const match = option.matches.find((item) => item.blankId === targetBlankId);
      if (!match) {
        setClozeUiError("This option does not fit this blank.");
        return;
      }
      setClozeUiError(null);
      onChange({
        answers: {
          ...answers,
          [targetBlankId]: match.choiceId,
        },
      });
      setSelectedClozeOptionKey(null);
      setDraggingClozeOptionKey(null);
      setHoveredBlankId(null);
    };

    const clearBlankAnswer = (blankId: string) => {
      const nextAnswers = { ...answers };
      delete nextAnswers[blankId];
      onChange({ answers: nextAnswers });
    };

    return (
      <div className="space-y-4">
        <p className="text-sm text-mainAccent">Drag an option into a blank, or tap an option then tap a blank.</p>
        {hasTemplateBlanks ? (
          <div className="p-1 text-lg leading-9 text-white">
            {templateParts.map((part, index) => {
              if (part.kind === "text") return <span key={`cloze-text-${index}`}>{part.value}</span>;
              const blankId = part.blankId;
              const selectedChoiceId =
                typeof answers[blankId] === "string" ? String(answers[blankId]) : null;
              const label =
                selectedChoiceId == null
                  ? "Drop here"
                  : blankOptions[blankId]?.find((choice) => choice.id === selectedChoiceId)?.text ??
                    "Filled";
              return (
                <button
                  key={`cloze-blank-${blankId}-${index}`}
                  type="button"
                  className={cn(
                    "mx-1 inline-flex min-w-24 items-center justify-center rounded-xl border px-3 py-2 align-middle text-sm transition",
                    selectedChoiceId
                      ? "border-[#b51f3d] bg-duoGreen text-white"
                      : "border-mainAlt bg-main text-mainAccent border-dashed",
                    hoveredBlankId === blankId && "border-mainAccent bg-mainAlt text-white"
                  )}
                  onClick={() => {
                    if (disabled) return;
                    if (selectedClozeOptionKey) {
                      assignOptionToBlank(blankId, selectedClozeOptionKey);
                    } else if (selectedChoiceId) {
                      clearBlankAnswer(blankId);
                    }
                  }}
                  onDragOver={(event) => {
                    if (disabled) return;
                    event.preventDefault();
                    event.dataTransfer.dropEffect = "move";
                    setHoveredBlankId(blankId);
                  }}
                  onDragEnter={(event) => {
                    if (disabled) return;
                    event.preventDefault();
                    setHoveredBlankId(blankId);
                  }}
                  onDragLeave={() => {
                    setHoveredBlankId((current) => (current === blankId ? null : current));
                  }}
                  onDrop={(event) => {
                    if (disabled) return;
                    event.preventDefault();
                    const droppedOptionKey = event.dataTransfer.getData("text/plain");
                    if (droppedOptionKey) {
                      assignOptionToBlank(blankId, droppedOptionKey);
                    }
                  }}
                  disabled={disabled}
                >
                  {label}
                </button>
              );
            })}
          </div>
        ) : null}
        <div className="flex flex-wrap gap-2">
          {optionBank.map((option) => {
            const selected = option.matches.some((item) => answers[item.blankId] === item.choiceId);
            const tapSelected = selectedClozeOptionKey === option.optionKey;
            return (
              <button
                key={`cloze-opt-${option.optionKey}`}
                type="button"
                draggable={!disabled}
                onDragStart={(event) => {
                  event.dataTransfer.setData("text/plain", option.optionKey);
                  event.dataTransfer.effectAllowed = "move";
                  setDraggingClozeOptionKey(option.optionKey);
                  setClozeUiError(null);
                }}
                onDragEnd={() => {
                  setDraggingClozeOptionKey(null);
                  setHoveredBlankId(null);
                }}
                onClick={() => {
                  if (disabled) return;
                  setClozeUiError(null);
                  setSelectedClozeOptionKey((prev) => (prev === option.optionKey ? null : option.optionKey));
                }}
                className={cn(
                  "rounded-2xl border px-3 py-2 text-sm transition",
                  selected
                    ? "border-[#b51f3d] bg-duoGreen text-white"
                    : tapSelected
                      ? "border-mainAccent bg-mainAccent text-main"
                      : "border-mainAlt bg-main text-white hover:bg-mainAlt",
                  draggingClozeOptionKey === option.optionKey && "opacity-60 scale-[0.98]"
                )}
                disabled={disabled}
              >
                {option.text}
              </button>
            );
          })}
        </div>
        {clozeUiError ? <p className="text-sm text-red-200">{clozeUiError}</p> : null}
      </div>
    );
  }

  if (question.questionType === "conversation") {
    const answers = (response?.answers as Record<string, unknown>) ?? {};
    return (
      <ConversationBoard
        turns={question.payload.turns}
        answers={Object.entries(answers).reduce((acc, [turnId, replyId]) => {
          if (typeof replyId === "string") {
            acc[turnId] = replyId;
          }
          return acc;
        }, {} as Record<string, string>)}
        onChange={(nextAnswers) => onChange({ answers: nextAnswers })}
        disabled={disabled}
      />
    );
  }

  if (question.questionType === "match_pairs") {
    const pairs = Object.entries((response?.pairs as Record<string, unknown>) ?? {}).reduce(
      (acc, [leftId, rightId]) => {
        if (typeof rightId === "string") {
          acc[leftId] = rightId;
        }
        return acc;
      },
      {} as Record<string, string>
    );
    return (
      <MatchPairsBoard
        leftItems={question.payload.left}
        rightItems={question.payload.right}
        pairs={pairs}
        onChange={(nextPairs) => onChange({ pairs: nextPairs })}
        disabled={disabled}
        seed={question.questionId}
      />
    );
  }

  if (question.questionType === "word_bank") {
    const tokenOrder = (response?.tokenOrder as string[] | undefined) ?? [];
    const tokenById = new Map(question.payload.tokens.map((token) => [token.id, token]));
    const selected = tokenOrder.map((tokenId) => tokenById.get(tokenId)).filter(Boolean);
    const selectedSet = new Set(tokenOrder);

    return (
      <div className="space-y-4">
        <div className="min-h-20 rounded-2xl p-4 flex flex-wrap gap-2">
          {selected.length === 0 ? (
            <p className="text-sm text-mainAccent">Tap words to build your answer.</p>
          ) : (
            selected.map((token, idx) => (
              <button
                key={`selected-${token!.id}-${idx}`}
                type="button"
                className="rounded-2xl border border-[#b51f3d] bg-duoGreen px-3 py-1 text-sm text-white"
                onClick={() => {
                  const next = tokenOrder.filter((_, itemIdx) => itemIdx !== idx);
                  onChange({ tokenOrder: next });
                }}
                disabled={disabled}
              >
                {token!.text}
              </button>
            ))
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          {question.payload.tokens.map((token) => {
            const isSelected = selectedSet.has(token.id);
            return (
              <button
                key={token.id}
                type="button"
                className={cn(
                  "rounded-2xl border px-3 py-2 text-sm transition",
                  isSelected
                    ? "border-mainAlt bg-mainAlt text-white/50"
                    : "border-mainAlt bg-main text-white hover:bg-mainAlt"
                )}
                onClick={() => onChange({ tokenOrder: [...tokenOrder, token.id] })}
                disabled={disabled || isSelected}
              >
                {token.text}
              </button>
            );
          })}
        </div>
      </div>
    );
  }

  const shortText = typeof response?.text === "string" ? response.text : "";
  return (
    <textarea
      className="w-full min-h-40 rounded-2xl border border-mainAlt bg-main p-4 text-white placeholder:text-mainAccent/70"
      placeholder={question.payload.placeholder ?? "Type your answer"}
      minLength={question.payload.minLength ?? 1}
      maxLength={question.payload.maxLength ?? 280}
      value={shortText}
      onChange={(event) => onChange({ text: event.target.value })}
      disabled={disabled}
    />
  );
};

const LessonQuizPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [lesson, setLesson] = useState<Lesson | null>(null);
  const [progressDetail, setProgressDetail] = useState<LessonProgressDetail | null>(null);
  const [quizState, setQuizState] = useState<LessonQuizState | null>(null);
  const [responseDraft, setResponseDraft] = useState<Record<string, unknown> | null>(null);
  const [feedback, setFeedback] = useState<LessonQuizAnswerResult | null>(null);
  const [quizSummary, setQuizSummary] = useState<{
    totalQuestions: number;
    correctCount: number;
    earnedScore: number;
    maxScore: number;
    passed: boolean;
    wrongQuestionIds: string[];
  } | null>(null);
  const [pendingState, setPendingState] = useState<LessonQuizState | null>(null);
  const [railCompleteIndex, setRailCompleteIndex] = useState<number | null>(null);
  const [railNextIndex, setRailNextIndex] = useState<number | null>(null);

  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshState = async (lessonId: string) => {
    const [lessonData, progress, quiz] = await Promise.all([
      fetchLessonById(lessonId),
      fetchLessonProgressDetail(lessonId),
      fetchLessonQuizState(lessonId),
    ]);
    setLesson(lessonData);
    setProgressDetail(progress);
    setQuizState(quiz);
    setQuizSummary(null);
  };

  useEffect(() => {
    if (!id) return;
    let active = true;
    setIsLoading(true);
    refreshState(id)
      .catch((loadError) => {
        if (!active) return;
        console.warn("Failed to load lesson quiz", loadError);
        setError("Unable to load quiz right now.");
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [id]);

  useEffect(() => {
    setResponseDraft(null);
    setFeedback(null);
    setPendingState(null);
    setRailCompleteIndex(null);
    setRailNextIndex(null);
  }, [quizState?.currentQuestion?.questionId, quizState?.currentQuestion?.questionType]);

  useEffect(() => {
    if (!feedback) return;
    const clip = feedback.correct ? "/audio/correct.mp3" : "/audio/incorrect.mp3";
    const audio = new Audio(clip);
    void audio.play().catch(() => undefined);
  }, [feedback]);

  const quizHearts = quizState?.hearts;
  useEffect(() => {
    if (!quizHearts) return;
    emitHeartsUpdated(quizHearts);
  }, [quizHearts]);

  const currentQuestion = quizState?.currentQuestion ?? null;
  const normalizedResponse = currentQuestion
    ? normalizeQuestionResponse(currentQuestion, responseDraft)
    : null;

  const currentQuestionNumber = useMemo(() => {
    if (!quizState?.questionIndex) return 1;
    return quizState.questionIndex + 1;
  }, [quizState?.questionIndex]);

  const totalQuestions = quizState?.totalQuestions ?? 1;
  const currentIndex = Math.max(0, currentQuestionNumber - 1);
  const completedCount = Math.max(0, currentQuestionNumber - 1);

  const handleSubmit = async () => {
    if (!id || !quizState?.attemptId || !currentQuestion || !normalizedResponse) return;
    setIsSubmitting(true);
    setError(null);
    try {
      const submitResult = await submitLessonQuizAnswer(id, {
        attemptId: quizState.attemptId,
        questionId: currentQuestion.questionId,
        response: normalizedResponse,
      });
      const resolvedHearts = resolveHeartsAfterSubmit(quizState.hearts, submitResult);
      emitHeartsUpdated(resolvedHearts);
      if (submitResult.quizCompleted) {
        setFeedback(null);
        setPendingState(null);
        setResponseDraft(null);
        setQuizState((previous) =>
          previous
            ? {
                ...previous,
                status: submitResult.passed ? "passed" : "failed",
                questionIndex: submitResult.questionIndex,
                totalQuestions: submitResult.totalQuestions,
                correctCount: submitResult.correctCount,
                earnedScore: submitResult.earnedScore,
                maxScore: submitResult.maxScore,
                hearts: resolvedHearts,
                canAnswer: false,
                canRestart: !submitResult.passed,
                currentQuestion: null,
                wrongQuestionIds: submitResult.wrongQuestionIds ?? [],
              }
            : previous
        );
        setQuizSummary({
          totalQuestions: submitResult.totalQuestions ?? totalQuestions,
          correctCount: submitResult.correctCount ?? 0,
          earnedScore: submitResult.earnedScore ?? 0,
          maxScore: submitResult.maxScore ?? 0,
          passed: submitResult.passed,
          wrongQuestionIds: submitResult.wrongQuestionIds ?? [],
        });
        if (submitResult.passed) {
          const refreshedProgress = await fetchLessonProgressDetail(id);
          setProgressDetail(refreshedProgress);
        }
        return;
      }

      setFeedback(submitResult);

      // Update hearts and aggregate counters immediately from submit response.
      setQuizState((previous) =>
        previous
          ? {
              ...previous,
              status: submitResult.status,
              correctCount: submitResult.correctCount,
              earnedScore: submitResult.earnedScore,
              maxScore: submitResult.maxScore,
              hearts: resolvedHearts,
              canAnswer: false,
              wrongQuestionIds: submitResult.wrongQuestionIds ?? previous.wrongQuestionIds ?? [],
            }
          : previous
      );

      if (submitResult.correct) {
        setRailCompleteIndex(currentIndex);
        setRailNextIndex(currentIndex + 1 < totalQuestions ? currentIndex + 1 : null);
      }

      setPendingState({
        attemptId: submitResult.attemptId,
        status: submitResult.status,
        questionIndex: submitResult.questionIndex,
        totalQuestions: submitResult.totalQuestions,
        correctCount: submitResult.correctCount,
        earnedScore: submitResult.earnedScore,
        maxScore: submitResult.maxScore,
        currentQuestion: submitResult.nextQuestion,
        hearts: resolvedHearts,
        canAnswer: submitResult.status === "in_progress" && Boolean(submitResult.nextQuestion),
        canRestart: submitResult.status === "failed",
        wrongQuestionIds: submitResult.wrongQuestionIds ?? [],
      });
    } catch (submitError) {
      console.warn("Quiz answer failed", submitError);
      setError("Unable to submit answer right now.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleContinue = async () => {
    if (pendingState) {
      setQuizState(pendingState);
      setPendingState(null);
      setFeedback(null);
      setResponseDraft(null);
      return;
    }
    setFeedback(null);
    setResponseDraft(null);
  };

  const handleRestart = async () => {
    if (!id) return;
    setIsSubmitting(true);
    setError(null);
    try {
      const restarted = await restartLessonQuiz(id, "full");
      setQuizState(restarted);
      emitHeartsUpdated(restarted.hearts);
      setFeedback(null);
      setQuizSummary(null);
      setPendingState(null);
      setResponseDraft(null);
    } catch (restartError) {
      console.warn("Failed to restart quiz", restartError);
      setError("Unable to restart quiz right now.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRedoWrong = async () => {
    if (!id) return;
    setIsSubmitting(true);
    setError(null);
    try {
      const restarted = await restartLessonQuiz(id, "wrong_only");
      setQuizState(restarted);
      emitHeartsUpdated(restarted.hearts);
      setFeedback(null);
      setQuizSummary(null);
      setPendingState(null);
      setResponseDraft(null);
    } catch (restartError) {
      console.warn("Failed to restart wrong-only quiz", restartError);
      setError("Unable to restart wrong questions right now.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDownAction = async () => {
    if (quizSummary) return;
    if (isSubmitting) return;
    if (feedback) {
      await handleContinue();
      return;
    }
    await handleSubmit();
  };

  const canDownAction =
    !quizSummary && (!!feedback || (!!normalizedResponse && quizState?.canAnswer && quizState.status !== "passed"));

  if (isLoading) {
    return (
      <MainLayout className="overflow-hidden">
        <div className="w-full px-4 lg:px-8 py-16 text-center text-mainAccent">Loading quiz...</div>
      </MainLayout>
    );
  }

  if (error || !lesson || !quizState) {
    return (
      <MainLayout className="overflow-hidden">
        <div className="w-full px-4 lg:px-8 py-10">
          <div className="rounded-2xl p-6 text-center space-y-4">
            <p className="text-red-200">{error ?? "Unable to load quiz."}</p>
            <Link to={`/lessons/${id}`} className="inline-flex h-11 items-center justify-center px-5 duo-button-primary">
              Back to Lesson
            </Link>
          </div>
        </div>
      </MainLayout>
    );
  }

  const refillText = formatRefill(quizState.hearts.heartsRefillAt);

  return (
    <MainLayout className="overflow-hidden">
      <div className="w-full min-h-[calc(100dvh-4rem)] px-4 lg:px-8 py-6 flex flex-col">
        <div className="flex items-center justify-between">
          <Link to={`/lessons/${id}`} className="inline-flex items-center text-mainAccent hover:text-white">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Exit
          </Link>
          <div className="flex items-center gap-2">
            <span className="rounded-full bg-mainAlt px-2 py-1 text-xs text-mainAccent">
              {currentQuestionNumber}/{totalQuestions}
            </span>
            <div className="inline-flex items-center gap-1 rounded-full border border-mainAlt bg-main px-3 py-1 text-sm text-white">
              <Heart className="h-4 w-4 text-rose-400" />
              {quizState.hearts.heartsRemaining}
            </div>
          </div>
        </div>

        {quizState.status === "blocked_hearts" ? (
          <p className="mt-2 text-sm text-red-200">Hearts are empty{refillText ? ` until ${refillText}` : ""}.</p>
        ) : null}

        {quizSummary ? (
          <div className="flex-1 flex items-start lg:items-center justify-center">
            <section className="w-full max-w-2xl rounded-3xl p-6 lg:p-8 space-y-6">
              <div className="text-center space-y-2">
                <img
                  src={quizSummary.passed ? "/icon-images/STAR_COMPLETE.svg" : "/icon-images/STAR_INCOMPLETE.svg"}
                  alt="Quiz summary"
                  className="h-20 w-20 mx-auto"
                />
                <h2 className="text-3xl text-white">
                  {quizSummary.passed ? "Perfect quiz run" : "Quiz finished"}
                </h2>
                <p className="text-mainAccent">
                  {quizSummary.passed
                    ? "Lesson is complete."
                    : "Lesson stays incomplete until you get full marks."}
                </p>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="rounded-2xl px-4 py-3 text-center">
                  <p className="text-xs uppercase text-mainAccent">Correct</p>
                  <p className="text-2xl text-white">
                    {quizSummary.correctCount}/{quizSummary.totalQuestions}
                  </p>
                </div>
                <div className="rounded-2xl px-4 py-3 text-center">
                  <p className="text-xs uppercase text-mainAccent">Score</p>
                  <p className="text-2xl text-white">
                    {quizSummary.earnedScore}/{quizSummary.maxScore}
                  </p>
                </div>
                <div className="rounded-2xl px-4 py-3 text-center">
                  <p className="text-xs uppercase text-mainAccent">Accuracy</p>
                  <p className="text-2xl text-white">
                    {Math.round(
                      (quizSummary.correctCount / Math.max(1, quizSummary.totalQuestions)) * 100
                    )}
                    %
                  </p>
                </div>
              </div>
              <div
                className={cn(
                  "flex flex-wrap gap-3",
                  quizSummary.passed ? "justify-center" : "justify-center lg:justify-start"
                )}
              >
                {quizSummary.passed ? (
                  <button
                    type="button"
                    onClick={() => navigate(`/lessons/${id}`)}
                    className="h-12 px-7 duo-button-primary"
                  >
                    Complete Lesson
                  </button>
                ) : (
                  <>
                    <button
                      type="button"
                      onClick={() => {
                        void handleRedoWrong();
                      }}
                      className="h-12 px-7 duo-button-primary disabled:opacity-60"
                      disabled={isSubmitting}
                    >
                      Redo Wrong Questions
                    </button>
                    <button
                      type="button"
                      onClick={() => navigate(`/lessons/${id}`)}
                      className="h-12 px-7 rounded-xl border border-mainAlt bg-main text-white"
                      disabled={isSubmitting}
                    >
                      Continue (Incomplete)
                    </button>
                    <button
                      type="button"
                      onClick={handleRestart}
                      className="h-12 px-7 rounded-xl border border-mainAlt bg-main text-white"
                      disabled={isSubmitting}
                    >
                      Retry Full Quiz
                    </button>
                  </>
                )}
              </div>
            </section>
          </div>
        ) : quizState.status === "passed" ? (
          <div className="flex-1 flex items-start lg:items-center justify-center">
            <section className="w-full max-w-2xl text-center space-y-4">
              <img src="/icon-images/STAR_COMPLETE.svg" alt="Passed" className="h-20 w-20 mx-auto" />
              <h2 className="text-3xl text-white">Quiz Passed</h2>
              <p className="text-base text-mainAccent">Lesson completion and rewards are now applied.</p>
              <button
                type="button"
                onClick={() => navigate(`/lessons/${id}`)}
                className="mx-auto h-12 w-full max-w-80 duo-button-primary"
              >
                Back to Lesson
              </button>
            </section>
          </div>
        ) : (
          <div className="mt-4 mx-auto max-w-[1420px] grid lg:grid-cols-[170px_minmax(0,1fr)] gap-10 items-start">
            <aside className="hidden lg:flex flex-col items-center pt-4 sticky top-24">
              <button
                type="button"
                aria-label="Previous question"
                disabled
                className="h-9 w-9 rounded-full border border-mainAlt bg-main text-mainAccent opacity-35 mb-3"
              >
                <ArrowUp className="h-4 w-4 mx-auto" />
              </button>

              <div className="relative h-[460px] w-[120px] overflow-hidden">
                {Array.from({ length: totalQuestions }).map((_, index) => {
                  const relative = index - currentIndex;
                  const distance = Math.abs(relative);
                  const isVisible = distance <= MAX_VISIBLE_DISTANCE;
                  const opacity =
                    distance === 0 ? 1 : distance === 1 ? 0.72 : distance === 2 ? 0.42 : 0.2;
                  const scale =
                    distance === 0 ? 1 : distance === 1 ? 0.86 : distance === 2 ? 0.72 : 0.6;
                  const y = relative * STEP_GAP;
                  const x = railOffsetX(index);
                  const isCompleted = index < completedCount;
                  const isCurrent = relative === 0;
                  const isAnimatingComplete = railCompleteIndex === index;
                  const isNextPulse = railNextIndex === index;

                  const baseClasses = isCompleted
                    ? "bg-duoGreen border-[#b51f3d] text-white shadow-mainCircleShadow"
                    : isCurrent
                      ? "bg-mainAccent border-mainAccent text-main shadow-mainCircleShadow"
                      : "bg-main border-mainAlt text-white/85 shadow-mainCircleShadow";

                  return (
                    <div
                      key={`quiz-path-${index}`}
                      className={cn("absolute left-1/2 top-1/2 transition-all duration-500", !isVisible && "pointer-events-none")}
                      style={{
                        transform: `translate(-50%, -50%) translate(${x}px, ${y}px) scale(${scale})`,
                        opacity: isVisible ? opacity : 0,
                      }}
                    >
                      <div
                        className={cn(
                          "relative h-16 w-[68px] rounded-full border-2 flex items-center justify-center",
                          baseClasses,
                          isCurrent && "animate-stop-current ring-4 ring-mainAccent/25",
                          isAnimatingComplete && "animate-stop-fill",
                          isNextPulse && "animate-stop-next"
                        )}
                      >
                        {isCompleted ? <Check className="h-6 w-6" /> : isCurrent ? <Star className="h-6 w-6 fill-current" /> : <span className="text-lg">{index + 1}</span>}
                      </div>
                    </div>
                  );
                })}
              </div>

              <button
                type="button"
                aria-label={feedback ? "Continue" : "Check answer"}
                onClick={() => {
                  void handleDownAction();
                }}
                disabled={!canDownAction || isSubmitting}
                className="h-9 w-9 rounded-full border border-mainAlt bg-main text-mainAccent disabled:opacity-35 mt-3"
              >
                <ArrowDown className="h-4 w-4 mx-auto" />
              </button>
            </aside>

            <section className="min-w-0 max-w-5xl w-full justify-self-center pt-6">
              {currentQuestion ? (
                <>
                  <p className="text-xs uppercase tracking-wide text-mainAccent">
                    {questionTypeLabel(currentQuestion)}
                  </p>
                  {currentQuestion.questionType !== "cloze" ? (
                    <h2 className="text-4xl text-white mt-2 mb-7">
                      {currentQuestion.prompt ?? currentQuestion.questionText}
                    </h2>
                  ) : null}
                  <LessonQuizQuestionRenderer
                    key={currentQuestion.questionId}
                    question={currentQuestion}
                    response={responseDraft}
                    onChange={setResponseDraft}
                    disabled={isSubmitting || !!feedback || !quizState.canAnswer}
                  />

                  {feedback ? (
                    <div className="mt-6 space-y-3">
                      <p className="font-bold inline-flex items-center gap-2">
                        {feedback.correct ? <CheckCircle2 className="h-5 w-5" /> : <XCircle className="h-5 w-5" />}
                        <span className={cn(feedback.correct ? "text-[#70f5b2]" : "text-[#ff8c98]")}>
                          {feedback.correct ? "Correct" : "Not quite"}
                        </span>
                      </p>
                      {feedback.explanation ? <p className="text-sm text-white/85">{feedback.explanation}</p> : null}
                      <button
                        type="button"
                        onClick={() => {
                          void handleContinue();
                        }}
                        className="h-12 px-6 duo-button-primary"
                        disabled={isSubmitting}
                      >
                        Continue
                      </button>
                    </div>
                  ) : quizState.status !== "passed" ? (
                    <div className="mt-6">
                      <button
                        type="button"
                        onClick={() => {
                          void handleSubmit();
                        }}
                        disabled={!normalizedResponse || isSubmitting || !quizState.canAnswer}
                        className="h-12 px-8 duo-button-primary disabled:opacity-60"
                      >
                        Check
                      </button>
                    </div>
                  ) : null}
                </>
              ) : null}

              {(quizState.status === "failed" || quizState.canRestart) && (
                <div className="mt-6">
                  <button
                    type="button"
                    onClick={handleRestart}
                    disabled={isSubmitting || quizState.hearts.heartsRemaining <= 0}
                    className="h-11 border border-mainAlt bg-main text-white rounded-xl disabled:opacity-60 px-4"
                  >
                    <RotateCcw className="h-4 w-4 inline mr-2" />
                    Restart Quiz
                  </button>
                </div>
              )}
            </section>
          </div>
        )}

      </div>
    </MainLayout>
  );
};

export default LessonQuizPage;
