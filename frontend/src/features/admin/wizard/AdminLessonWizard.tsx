import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Loader2,
  Plus,
  Save,
  Trash2,
} from "lucide-react";
import type {
  AdminQuizQuestionDraft,
  AdminValidationError,
  Lesson,
  QuizQuestion,
  WizardStepKey,
} from "@/types";
import {
  createAdminLessonDraft,
  fetchAdminLessonById,
  fetchAdminLessonQuizQuestions,
  publishAdminLesson,
  saveAdminLessonDraftStep,
} from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { AdminQuestionEditor } from "@/features/admin/quiz-builder/AdminQuestionEditor";

type Props = {
  mode: "create" | "edit";
  lessonId?: string;
};

type LessonDraftForm = {
  id?: string;
  title: string;
  description: string;
  summary: string;
  learning_objectives: string[];
  estimated_minutes: number;
  xp_reward: number;
  badge_name: string;
  difficulty_level: number;
  origin_content: string;
  definition_content: string;
  usage_examples: string[];
  lore_content: string;
  evolution_content: string;
  comparison_content: string;
  is_published?: boolean;
};

type StepState = "saved" | "unsaved" | "invalid" | "saving";

const STEPS: Array<{ key: WizardStepKey; label: string }> = [
  { key: "basics", label: "Lesson Basics" },
  { key: "content", label: "Lesson Content" },
  { key: "quiz_setup", label: "Quiz Builder" },
  { key: "review_publish", label: "Review & Publish" },
];

const QUESTION_TYPE_CHOICES: Array<{ value: AdminQuizQuestionDraft["question_type"]; label: string }> = [
  { value: "multiple_choice", label: "Multiple Choice" },
  { value: "true_false", label: "True / False" },
  { value: "cloze", label: "Cloze" },
  { value: "word_bank", label: "Word Bank" },
  { value: "conversation", label: "Conversation" },
  { value: "match_pairs", label: "Match Pairs" },
  { value: "short_text", label: "Short Text" },
];

const MAX_QUESTIONS = 10;
type QuizBuilderStage = "choose_type" | "edit_question" | "ask_more";

const asTrimmed = (value: unknown) => String(value ?? "").trim();

const parseJsonObject = (value: string): Record<string, unknown> | null => {
  try {
    const parsed = JSON.parse(value);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
  } catch {
    return null;
  }
  return null;
};

const parseJsonArray = (value: string): unknown[] | null => {
  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) {
      return parsed;
    }
  } catch {
    return null;
  }
  return null;
};

const questionDraftErrors = (question: AdminQuizQuestionDraft): string[] => {
  const errors: string[] = [];
  const prompt = asTrimmed(question.question_text);
  const explanation = asTrimmed(question.explanation);
  if (!prompt) errors.push("Question prompt is required.");
  if (!explanation) errors.push("Explanation is required.");
  if (!Number.isFinite(question.points) || question.points < 1 || question.points > 100) {
    errors.push("Points must be between 1 and 100.");
  }

  const options = (question.options ?? {}) as Record<string, unknown>;
  const correctAnswer = asTrimmed(question.correct_answer);

  if (question.question_type === "multiple_choice") {
    const rawChoices = (options.choices ?? options) as Record<string, unknown>;
    const choices = Object.entries(rawChoices).filter(([, text]) => asTrimmed(text).length > 0);
    if (choices.length < 2) errors.push("Add at least 2 non-empty options.");
    if (!correctAnswer || !choices.some(([choiceId]) => choiceId === correctAnswer)) {
      errors.push("Pick a valid correct option.");
    }
  } else if (question.question_type === "true_false") {
    if (!["true", "false"].includes(correctAnswer.toLowerCase())) {
      errors.push("Pick true or false as the correct answer.");
    }
  } else if (question.question_type === "cloze") {
    const blanks = (options.blankOptions ?? {}) as Record<string, unknown>;
    const blankEntries = Object.entries(blanks);
    if (blankEntries.length === 0) errors.push("Add at least one blank.");
    const answerMap = parseJsonObject(correctAnswer);
    if (!answerMap) errors.push("Correct answer mapping is invalid.");
    blankEntries.forEach(([blankId, rawChoices]) => {
      const choiceEntries = Object.entries(rawChoices as Record<string, unknown>).filter(
        ([, text]) => asTrimmed(text).length > 0
      );
      if (choiceEntries.length < 2) errors.push(`Blank ${blankId} needs at least 2 options.`);
      const mapped = answerMap?.[blankId];
      if (!mapped || !choiceEntries.some(([choiceId]) => choiceId === mapped)) {
        errors.push(`Blank ${blankId} needs a valid correct option.`);
      }
    });
  } else if (question.question_type === "word_bank") {
    const tokens = Array.isArray(options.tokens) ? options.tokens : [];
    const validTokens = tokens
      .map((token) => token as Record<string, unknown>)
      .filter((token) => asTrimmed(token.id) && asTrimmed(token.text));
    if (validTokens.length < 2) errors.push("Add at least 2 tokens with text.");
    const tokenOrder = parseJsonArray(correctAnswer);
    if (!tokenOrder || tokenOrder.length === 0) {
      errors.push("Set a valid correct token order.");
    }
  } else if (question.question_type === "conversation") {
    const turns = Array.isArray(options.turns) ? options.turns : [];
    if (turns.length === 0) errors.push("Add at least one conversation turn.");
    const answerMap = parseJsonObject(correctAnswer);
    if (!answerMap) errors.push("Correct answer mapping is invalid.");
    turns.forEach((rawTurn, index) => {
      const turn = rawTurn as Record<string, unknown>;
      const turnId = asTrimmed(turn.id) || `turn_${index + 1}`;
      const promptText = asTrimmed(turn.prompt);
      const replies = Array.isArray(turn.replies) ? turn.replies : [];
      const validReplies = replies
        .map((reply) => reply as Record<string, unknown>)
        .filter((reply) => asTrimmed(reply.id) && asTrimmed(reply.text));
      if (!promptText) errors.push(`Turn ${index + 1} prompt is required.`);
      if (validReplies.length < 2) errors.push(`Turn ${index + 1} needs at least 2 replies.`);
      const mapped = answerMap?.[turnId];
      if (!mapped || !validReplies.some((reply) => asTrimmed(reply.id) === String(mapped))) {
        errors.push(`Turn ${index + 1} needs a valid correct reply.`);
      }
    });
  } else if (question.question_type === "match_pairs") {
    const left = Array.isArray(options.left) ? options.left : [];
    const right = Array.isArray(options.right) ? options.right : [];
    const validLeft = left
      .map((item) => item as Record<string, unknown>)
      .filter((item) => asTrimmed(item.id) && asTrimmed(item.text));
    const validRight = right
      .map((item) => item as Record<string, unknown>)
      .filter((item) => asTrimmed(item.id) && asTrimmed(item.text));
    if (validLeft.length === 0 || validRight.length === 0) {
      errors.push("Add left and right pair items.");
    }
    const answerMap = parseJsonObject(correctAnswer);
    if (!answerMap) errors.push("Correct pair mapping is invalid.");
    validLeft.forEach((leftItem) => {
      const leftId = asTrimmed(leftItem.id);
      const mapped = answerMap?.[leftId];
      if (!mapped || !validRight.some((rightItem) => asTrimmed(rightItem.id) === String(mapped))) {
        errors.push(`Pair for ${leftId} is missing or invalid.`);
      }
    });
  } else if (question.question_type === "short_text") {
    if (!correctAnswer) {
      errors.push("Provide at least one accepted answer.");
    } else if (correctAnswer.startsWith("{")) {
      const answerObj = parseJsonObject(correctAnswer);
      const accepted = Array.isArray(answerObj?.accepted) ? (answerObj?.accepted as unknown[]) : [];
      if (!accepted.some((value) => asTrimmed(value).length > 0)) {
        errors.push("Accepted answers must include at least one value.");
      }
    }
  }

  return Array.from(new Set(errors));
};

const STEP_INDEX: Record<WizardStepKey, number> = {
  basics: 0,
  content: 1,
  quiz_setup: 2,
  quiz_builder: 2,
  review_publish: 3,
};

const DEFAULT_STEP_STATUS: Record<WizardStepKey, StepState> = {
  basics: "unsaved",
  content: "unsaved",
  quiz_setup: "unsaved",
  quiz_builder: "unsaved",
  review_publish: "unsaved",
};

const createClientId = () =>
  typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

const toString = (value: unknown) => String(value ?? "");

const toStringList = (value: unknown) => {
  if (Array.isArray(value)) {
    const next = value.map((item) => String(item ?? "").trim()).filter(Boolean);
    return next.length > 0 ? next : [""];
  }
  if (typeof value === "string" && value.trim().startsWith("[")) {
    try {
      const parsed = JSON.parse(value) as unknown[];
      const next = parsed.map((item) => String(item ?? "").trim()).filter(Boolean);
      return next.length > 0 ? next : [""];
    } catch {
      return [""];
    }
  }
  if (typeof value === "string" && value.trim().length > 0) {
    return value
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return [""];
};

const normalizeLessonDraft = (lesson: Partial<Lesson> | Record<string, unknown> | null | undefined): LessonDraftForm => ({
  id: lesson?.id ? String(lesson.id) : undefined,
  title: toString(lesson?.title),
  description: toString(lesson?.description),
  summary: toString(lesson?.summary),
  learning_objectives: toStringList(lesson?.learning_objectives),
  estimated_minutes: Number(lesson?.estimated_minutes ?? 15),
  xp_reward: Number(lesson?.xp_reward ?? 100),
  badge_name: toString(lesson?.badge_name),
  difficulty_level: Number(lesson?.difficulty_level ?? 1),
  origin_content: toString(lesson?.origin_content),
  definition_content: toString(lesson?.definition_content),
  usage_examples: toStringList(lesson?.usage_examples),
  lore_content: toString(lesson?.lore_content),
  evolution_content: toString(lesson?.evolution_content),
  comparison_content: toString(lesson?.comparison_content),
  is_published: Boolean(lesson?.is_published),
});

const newQuestionByType = (
  type: AdminQuizQuestionDraft["question_type"],
  orderIndex: number
): AdminQuizQuestionDraft => {
  const base = {
    clientId: createClientId(),
    question_type: type,
    question_text: "",
    explanation: "",
    points: 10,
    order_index: orderIndex,
  } as const;

  if (type === "multiple_choice") {
    return {
      ...base,
      question_type: "multiple_choice",
      options: { choices: { A: "", B: "", C: "", D: "" } },
      correct_answer: "A",
    };
  }
  if (type === "true_false") {
    return {
      ...base,
      question_type: "true_false",
      options: {},
      correct_answer: "true",
    };
  }
  if (type === "cloze") {
    return {
      ...base,
      question_type: "cloze",
      options: { blankOptions: { blank1: { A: "", B: "" } } },
      correct_answer: '{"blank1":"A"}',
    };
  }
  if (type === "word_bank") {
    return {
      ...base,
      question_type: "word_bank",
      options: { tokens: [{ id: "t1", text: "" }, { id: "t2", text: "" }] },
      correct_answer: '["t1"]',
    };
  }
  if (type === "conversation") {
    return {
      ...base,
      question_type: "conversation",
      options: {
        turns: [
          {
            id: "turn_1",
            prompt: "",
            replies: [
              { id: "r1", text: "" },
              { id: "r2", text: "" },
            ],
          },
        ],
      },
      correct_answer: '{"turn_1":"r1"}',
    };
  }
  if (type === "match_pairs") {
    return {
      ...base,
      question_type: "match_pairs",
      options: {
        left: [
          { id: "l1", text: "" },
          { id: "l2", text: "" },
        ],
        right: [
          { id: "r1", text: "" },
          { id: "r2", text: "" },
        ],
      },
      correct_answer: '{"l1":"r1","l2":"r2"}',
    };
  }
  return {
    ...base,
    question_type: "short_text",
    options: { placeholder: "Type your answer", minLength: 1, maxLength: 120 },
    correct_answer: '{"accepted":[""]}',
  };
};

const normalizeAdminQuestion = (question: Partial<QuizQuestion>, orderIndex: number): AdminQuizQuestionDraft => {
  const normalizedType = QUESTION_TYPE_CHOICES.some((choice) => choice.value === question.question_type)
    ? (question.question_type as AdminQuizQuestionDraft["question_type"])
    : "multiple_choice";
  const base = newQuestionByType(normalizedType, orderIndex);
  return {
    ...base,
    clientId: createClientId(),
    question_text: toString(question.question_text),
    explanation: toString(question.explanation),
    points: Number(question.points ?? 10),
    order_index: Number(question.order_index ?? orderIndex),
    options:
      question.options && typeof question.options === "object" && !Array.isArray(question.options)
        ? (question.options as Record<string, unknown>)
        : base.options,
    correct_answer: toString(question.correct_answer || base.correct_answer),
  };
};

const toLessonPayload = (form: LessonDraftForm) => ({
  title: form.title,
  description: form.description,
  summary: form.summary,
  learning_objectives: form.learning_objectives.map((item) => item.trim()).filter(Boolean),
  estimated_minutes: Number(form.estimated_minutes || 0),
  xp_reward: Number(form.xp_reward || 0),
  badge_name: form.badge_name,
  difficulty_level: Number(form.difficulty_level || 1),
  origin_content: form.origin_content,
  definition_content: form.definition_content,
  usage_examples: form.usage_examples.map((item) => item.trim()).filter(Boolean),
  lore_content: form.lore_content,
  evolution_content: form.evolution_content,
  comparison_content: form.comparison_content,
});

const mapCompletenessToStatus = (
  current: Record<WizardStepKey, StepState>,
  completeness?: Record<string, boolean> | null
) => {
  if (!completeness) return current;
  return {
    basics: completeness.basics ? "saved" : current.basics === "invalid" ? "invalid" : "unsaved",
    content: completeness.content ? "saved" : current.content === "invalid" ? "invalid" : "unsaved",
    quiz_setup: completeness.quiz_setup ? "saved" : current.quiz_setup === "invalid" ? "invalid" : "unsaved",
    quiz_builder:
      completeness.quiz_builder ? "saved" : current.quiz_builder === "invalid" ? "invalid" : "unsaved",
    review_publish:
      completeness.review_publish ? "saved" : current.review_publish === "invalid" ? "invalid" : "unsaved",
  };
};

const normalizeUiStep = (step: WizardStepKey | string | null | undefined): WizardStepKey => {
  if (step === "quiz_builder") {
    return "quiz_setup";
  }
  if (step === "basics" || step === "content" || step === "quiz_setup" || step === "review_publish") {
    return step;
  }
  return "review_publish";
};

export const AdminLessonWizard = ({ mode, lessonId: lessonIdProp }: Props) => {
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(true);
  const [isPublishing, setIsPublishing] = useState(false);
  const [activeStep, setActiveStep] = useState<WizardStepKey>("basics");
  const [stepStatus, setStepStatus] = useState<Record<WizardStepKey, StepState>>(DEFAULT_STEP_STATUS);
  const [stepErrors, setStepErrors] = useState<Record<WizardStepKey, AdminValidationError[]>>({
    basics: [],
    content: [],
    quiz_setup: [],
    quiz_builder: [],
    review_publish: [],
  });

  const [globalError, setGlobalError] = useState<string | null>(null);
  const [isDirty, setIsDirty] = useState(false);
  const [lessonId, setLessonId] = useState<string | null>(lessonIdProp ?? null);
  const [openQuestionId, setOpenQuestionId] = useState<string | null>(null);
  const [quizBuilderStage, setQuizBuilderStage] = useState<QuizBuilderStage>("choose_type");
  const [draftQuestion, setDraftQuestion] = useState<AdminQuizQuestionDraft | null>(null);
  const [editingQuestionId, setEditingQuestionId] = useState<string | null>(null);
  const [highlightedQuestionId, setHighlightedQuestionId] = useState<string | null>(null);
  const [lessonForm, setLessonForm] = useState<LessonDraftForm>(() => normalizeLessonDraft(null));
  const [questions, setQuestions] = useState<AdminQuizQuestionDraft[]>([]);

  const questionErrorsByIndex = useMemo(() => {
    const map = new Map<number, string>();
    stepErrors.quiz_builder.forEach((error) => {
      if (typeof error.questionIndex === "number" && !map.has(error.questionIndex)) {
        map.set(error.questionIndex, error.message);
      }
    });
    return map;
  }, [stepErrors.quiz_builder]);

  useEffect(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!isDirty) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [isDirty]);

  useEffect(() => {
    if (!highlightedQuestionId) {
      return;
    }
    const timeout = window.setTimeout(() => setHighlightedQuestionId(null), 600);
    return () => window.clearTimeout(timeout);
  }, [highlightedQuestionId]);

  useEffect(() => {
    let active = true;
    const load = async () => {
      setIsLoading(true);
      setGlobalError(null);
      try {
        if (mode === "create") {
          const draft = await createAdminLessonDraft();
          if (!active) return;
          setLessonId(draft.lessonId);
          setLessonForm(normalizeLessonDraft(draft.lessonSnapshot));
          setQuestions([]);
          setOpenQuestionId(null);
          setDraftQuestion(null);
          setEditingQuestionId(null);
          setQuizBuilderStage("choose_type");
          setStepStatus((prev) => mapCompletenessToStatus(prev, draft.completeness));
          return;
        }
        if (!lessonIdProp) {
          throw new Error("Missing lesson id");
        }
        const [lesson, lessonQuestions] = await Promise.all([
          fetchAdminLessonById(lessonIdProp),
          fetchAdminLessonQuizQuestions(lessonIdProp),
        ]);
        if (!active) return;
        setLessonId(lessonIdProp);
        setLessonForm(normalizeLessonDraft(lesson));
        const normalized = (lessonQuestions ?? []).map((question, index) =>
          normalizeAdminQuestion(question, index)
        );
        setQuestions(normalized);
        setOpenQuestionId(normalized[0]?.clientId ?? null);
        setDraftQuestion(null);
        setEditingQuestionId(null);
        setQuizBuilderStage(normalized.length > 0 ? "ask_more" : "choose_type");
        setStepStatus((prev) => ({
          ...prev,
          basics: "saved",
          content: "saved",
          quiz_setup: normalized.length > 0 ? "saved" : "unsaved",
          quiz_builder: normalized.length > 0 ? "saved" : "unsaved",
        }));
      } catch (error) {
        if (!active) return;
        setGlobalError(error instanceof Error ? error.message : "Failed to load lesson wizard.");
      } finally {
        if (active) setIsLoading(false);
      }
    };

    void load();
    return () => {
      active = false;
    };
  }, [lessonIdProp, mode]);

  const markStepUnsaved = (step: WizardStepKey) => {
    setStepStatus((prev) => ({ ...prev, [step]: "unsaved" }));
    setIsDirty(true);
  };

  const saveStep = async (
    step: WizardStepKey,
    overrides?: { questions?: AdminQuizQuestionDraft[] }
  ) => {
    if (!lessonId) return false;
    setStepStatus((prev) => ({ ...prev, [step]: "saving" }));
    setGlobalError(null);
    try {
      const payloadQuestions = overrides?.questions ?? questions;
      const result = await saveAdminLessonDraftStep(lessonId, step, {
        lesson: toLessonPayload(lessonForm),
        questions:
          step === "quiz_setup" || step === "quiz_builder" || step === "review_publish"
            ? payloadQuestions
            : undefined,
      });
      const nextErrors: Record<WizardStepKey, AdminValidationError[]> = {
        basics: [],
        content: [],
        quiz_setup: [],
        quiz_builder: [],
        review_publish: [],
      };
      result.errors.forEach((error) => {
        nextErrors[error.step].push(error);
      });
      setStepErrors((prev) => ({
        ...prev,
        ...nextErrors,
      }));
      if (!result.stepValid) {
        setStepStatus((prev) => ({ ...prev, [step]: "invalid" }));
        setIsDirty(true);
        return false;
      }

      setLessonForm((prev) => normalizeLessonDraft({ ...prev, ...(result.lessonSnapshot ?? {}) }));
      setStepStatus((prev) => mapCompletenessToStatus({ ...prev, [step]: "saved" }, result.completeness));
      setIsDirty(false);
      return true;
    } catch (error) {
      setStepStatus((prev) => ({ ...prev, [step]: "invalid" }));
      setGlobalError(error instanceof Error ? error.message : "Failed to save this step.");
      return false;
    }
  };

  const commitDraftQuestion = (): { ok: boolean; nextQuestions: AdminQuizQuestionDraft[] } => {
    if (!draftQuestion) {
      return { ok: true, nextQuestions: questions };
    }
    const errors = questionDraftErrors(draftQuestion);
    if (errors.length > 0) {
      setGlobalError(errors[0]);
      return { ok: false, nextQuestions: questions };
    }

    if (editingQuestionId) {
      const next = questions.map((question) =>
        question.clientId === editingQuestionId ? draftQuestion : question
      );
      setQuestions(next);
      setOpenQuestionId(editingQuestionId);
      setHighlightedQuestionId(editingQuestionId);
      setDraftQuestion(null);
      setEditingQuestionId(null);
      setQuizBuilderStage("ask_more");
      setGlobalError(null);
      markStepUnsaved("quiz_setup");
      markStepUnsaved("quiz_builder");
      return { ok: true, nextQuestions: next };
    }

    const inserted = {
      ...draftQuestion,
      order_index: questions.length,
    };
    const next = [...questions, inserted];
    setQuestions(next);
    setOpenQuestionId(inserted.clientId);
    setHighlightedQuestionId(inserted.clientId);
    setDraftQuestion(null);
    setEditingQuestionId(null);
    setQuizBuilderStage("ask_more");
    setGlobalError(null);
    markStepUnsaved("quiz_setup");
    markStepUnsaved("quiz_builder");
    return { ok: true, nextQuestions: next };
  };

  const saveAndMoveStep = async (nextStep: WizardStepKey) => {
    let overrideQuestions: AdminQuizQuestionDraft[] | undefined;
    if (activeStep === "quiz_setup" && quizBuilderStage === "edit_question" && draftQuestion) {
      const committed = commitDraftQuestion();
      if (!committed.ok) return;
      overrideQuestions = committed.nextQuestions;
    }

    const saved = await saveStep(
      activeStep,
      overrideQuestions ? { questions: overrideQuestions } : undefined
    );
    if (!saved) return;
    setActiveStep(nextStep);
  };

  const beginCreateQuestion = (type: AdminQuizQuestionDraft["question_type"]) => {
    if (questions.length >= MAX_QUESTIONS) {
      setGlobalError(`You can add up to ${MAX_QUESTIONS} questions.`);
      return;
    }
    const question = newQuestionByType(type, questions.length);
    setDraftQuestion(question);
    setEditingQuestionId(null);
    setOpenQuestionId(question.clientId);
    setQuizBuilderStage("edit_question");
    setGlobalError(null);
  };

  const beginEditQuestion = (clientId: string) => {
    const target = questions.find((question) => question.clientId === clientId);
    if (!target) {
      return;
    }
    setDraftQuestion({
      ...target,
      options: { ...(target.options ?? {}) },
    });
    setEditingQuestionId(clientId);
    setOpenQuestionId(clientId);
    setQuizBuilderStage("edit_question");
    setGlobalError(null);
  };

  const saveCurrentDraftQuestion = () => {
    void commitDraftQuestion();
  };

  const handleAskMoreAddQuestion = async () => {
    const saved = await saveStep("quiz_setup");
    if (!saved) return;
    setQuizBuilderStage("choose_type");
    setGlobalError(null);
  };

  const handleAskMoreContinueToReview = async () => {
    const saved = await saveStep("quiz_setup");
    if (!saved) return;
    setActiveStep("review_publish");
  };

  const handleDeleteQuestion = (clientId: string) => {
    const next = questions
      .filter((question) => question.clientId !== clientId)
      .map((question, index) => ({ ...question, order_index: index }));
    setQuestions(next);
    if (openQuestionId === clientId) {
      setOpenQuestionId(next[0]?.clientId ?? null);
    }
    if (editingQuestionId === clientId) {
      setDraftQuestion(null);
      setEditingQuestionId(null);
      setQuizBuilderStage(next.length > 0 ? "ask_more" : "choose_type");
    }
    if (next.length === 0 && editingQuestionId !== clientId) {
      setQuizBuilderStage("choose_type");
    }
    markStepUnsaved("quiz_setup");
    markStepUnsaved("quiz_builder");
  };

  const handleQuestionDraftUpdate = (nextQuestion: AdminQuizQuestionDraft) => {
    setDraftQuestion(nextQuestion);
  };

  const handlePublish = async () => {
    if (!lessonId) return;
    setIsPublishing(true);
    setGlobalError(null);
    try {
      const result = await publishAdminLesson(lessonId, {
        lesson: toLessonPayload(lessonForm),
        questions,
      });
      if (!result.success) {
        const nextErrors: Record<WizardStepKey, AdminValidationError[]> = {
          basics: [],
          content: [],
          quiz_setup: [],
          quiz_builder: [],
          review_publish: [],
        };
        result.errors.forEach((error) => {
          const mappedStep = normalizeUiStep(error.step);
          if (mappedStep !== error.step) {
            nextErrors[mappedStep].push({ ...error, step: mappedStep });
            return;
          }
          nextErrors[error.step].push(error);
        });
        setStepErrors(nextErrors);
        setStepStatus((prev) => ({
          ...prev,
          basics: nextErrors.basics.length ? "invalid" : prev.basics,
          content: nextErrors.content.length ? "invalid" : prev.content,
          quiz_setup: nextErrors.quiz_setup.length ? "invalid" : prev.quiz_setup,
          quiz_builder: nextErrors.quiz_builder.length ? "invalid" : prev.quiz_builder,
          review_publish: "invalid",
        }));
        setActiveStep(normalizeUiStep(result.firstInvalidStep));
        setGlobalError("Publish failed. Fix the highlighted step and retry.");
        return;
      }
      setStepStatus({
        basics: "saved",
        content: "saved",
        quiz_setup: "saved",
        quiz_builder: "saved",
        review_publish: "saved",
      });
      setIsDirty(false);
      navigate("/admin/lessons");
    } catch (error) {
      setGlobalError(error instanceof Error ? error.message : "Failed to publish lesson.");
    } finally {
      setIsPublishing(false);
    }
  };

  const currentStepIndex = STEP_INDEX[activeStep];

  if (isLoading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const renderBasicsStep = () => (
    <Card>
      <CardHeader>
        <CardTitle>Lesson Basics</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="lesson-title">Title</Label>
          <Input
            id="lesson-title"
            value={lessonForm.title}
            onChange={(event) => {
              setLessonForm((prev) => ({ ...prev, title: event.target.value }));
              markStepUnsaved("basics");
            }}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="lesson-summary">Summary</Label>
          <Textarea
            id="lesson-summary"
            value={lessonForm.summary}
            onChange={(event) => {
              setLessonForm((prev) => ({ ...prev, summary: event.target.value }));
              markStepUnsaved("basics");
            }}
            rows={3}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="lesson-description">Description</Label>
          <Textarea
            id="lesson-description"
            value={lessonForm.description}
            onChange={(event) => {
              setLessonForm((prev) => ({ ...prev, description: event.target.value }));
              markStepUnsaved("basics");
            }}
            rows={4}
          />
        </div>
        <div className="grid gap-3 md:grid-cols-3">
          <div className="space-y-2">
            <Label>Estimated Minutes</Label>
            <Input
              type="number"
              min={1}
              value={lessonForm.estimated_minutes}
              onChange={(event) => {
                setLessonForm((prev) => ({ ...prev, estimated_minutes: Number(event.target.value || 0) }));
                markStepUnsaved("basics");
              }}
            />
          </div>
          <div className="space-y-2">
            <Label>XP Reward</Label>
            <Input
              type="number"
              min={1}
              value={lessonForm.xp_reward}
              onChange={(event) => {
                setLessonForm((prev) => ({ ...prev, xp_reward: Number(event.target.value || 0) }));
                markStepUnsaved("basics");
              }}
            />
          </div>
          <div className="space-y-2">
            <Label>Difficulty (1-3)</Label>
            <Input
              type="number"
              min={1}
              max={3}
              value={lessonForm.difficulty_level}
              onChange={(event) => {
                setLessonForm((prev) => ({ ...prev, difficulty_level: Number(event.target.value || 1) }));
                markStepUnsaved("basics");
              }}
            />
          </div>
        </div>
        <div className="space-y-2">
          <Label htmlFor="lesson-badge-name">Badge Name</Label>
          <Input
            id="lesson-badge-name"
            value={lessonForm.badge_name}
            onChange={(event) => {
              setLessonForm((prev) => ({ ...prev, badge_name: event.target.value }));
              markStepUnsaved("basics");
            }}
            placeholder="e.g. W Rizz"
          />
        </div>
      </CardContent>
    </Card>
  );

  const renderContentStep = () => (
    <Card>
      <CardHeader>
        <CardTitle>Lesson Content</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <Label>Learning Objectives</Label>
          {lessonForm.learning_objectives.map((objective, index) => (
            <div key={`objective-${index}`} className="grid grid-cols-[1fr_auto] gap-2">
              <Input
                value={objective}
                onChange={(event) => {
                  const next = [...lessonForm.learning_objectives];
                  next[index] = event.target.value;
                  setLessonForm((prev) => ({ ...prev, learning_objectives: next }));
                  markStepUnsaved("content");
                }}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={() => {
                  const next = lessonForm.learning_objectives.filter((_, objectiveIndex) => objectiveIndex !== index);
                  setLessonForm((prev) => ({ ...prev, learning_objectives: next.length ? next : [""] }));
                  markStepUnsaved("content");
                }}
              >
                <Trash2 className="h-4 w-4 text-destructive" />
              </Button>
            </div>
          ))}
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => {
              setLessonForm((prev) => ({
                ...prev,
                learning_objectives: [...prev.learning_objectives, ""],
              }));
              markStepUnsaved("content");
            }}
          >
            <Plus className="mr-2 h-4 w-4" />
            Add Objective
          </Button>
        </div>
        <div className="space-y-2">
          <Label>Origin</Label>
          <Textarea
            value={lessonForm.origin_content}
            onChange={(event) => {
              setLessonForm((prev) => ({ ...prev, origin_content: event.target.value }));
              markStepUnsaved("content");
            }}
            rows={3}
          />
        </div>
        <div className="space-y-2">
          <Label>Definition</Label>
          <Textarea
            value={lessonForm.definition_content}
            onChange={(event) => {
              setLessonForm((prev) => ({ ...prev, definition_content: event.target.value }));
              markStepUnsaved("content");
            }}
            rows={3}
          />
        </div>
        <div className="space-y-2">
          <Label>Usage Examples</Label>
          {lessonForm.usage_examples.map((example, index) => (
            <div key={`example-${index}`} className="grid grid-cols-[1fr_auto] gap-2">
              <Input
                value={example}
                onChange={(event) => {
                  const next = [...lessonForm.usage_examples];
                  next[index] = event.target.value;
                  setLessonForm((prev) => ({ ...prev, usage_examples: next }));
                  markStepUnsaved("content");
                }}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={() => {
                  const next = lessonForm.usage_examples.filter((_, exampleIndex) => exampleIndex !== index);
                  setLessonForm((prev) => ({ ...prev, usage_examples: next.length ? next : [""] }));
                  markStepUnsaved("content");
                }}
              >
                <Trash2 className="h-4 w-4 text-destructive" />
              </Button>
            </div>
          ))}
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => {
              setLessonForm((prev) => ({ ...prev, usage_examples: [...prev.usage_examples, ""] }));
              markStepUnsaved("content");
            }}
          >
            <Plus className="mr-2 h-4 w-4" />
            Add Example
          </Button>
        </div>
        <div className="space-y-2">
          <Label>Lore</Label>
          <Textarea
            value={lessonForm.lore_content}
            onChange={(event) => {
              setLessonForm((prev) => ({ ...prev, lore_content: event.target.value }));
              markStepUnsaved("content");
            }}
            rows={3}
          />
        </div>
        <div className="space-y-2">
          <Label>Evolution</Label>
          <Textarea
            value={lessonForm.evolution_content}
            onChange={(event) => {
              setLessonForm((prev) => ({ ...prev, evolution_content: event.target.value }));
              markStepUnsaved("content");
            }}
            rows={3}
          />
        </div>
        <div className="space-y-2">
          <Label>Comparison</Label>
          <Textarea
            value={lessonForm.comparison_content}
            onChange={(event) => {
              setLessonForm((prev) => ({ ...prev, comparison_content: event.target.value }));
              markStepUnsaved("content");
            }}
            rows={2}
          />
        </div>
      </CardContent>
    </Card>
  );

  const renderQuizSetupStep = () => {
    const nextQuestionNumber = questions.length + (editingQuestionId ? 0 : 1);
    const draftErrors = draftQuestion ? questionDraftErrors(draftQuestion) : [];
    const activeCircleId =
      quizBuilderStage === "edit_question" && draftQuestion
        ? draftQuestion.clientId
        : openQuestionId;
    const circleItems = [
      ...questions.map((question, index) => ({
        clientId: question.clientId,
        number: index + 1,
        isDraft: false,
      })),
      ...(
        quizBuilderStage === "edit_question" && draftQuestion && !editingQuestionId
          ? [
              {
                clientId: draftQuestion.clientId,
                number: questions.length + 1,
                isDraft: true,
              },
            ]
          : []
      ),
    ];

    return (
      <Card>
        <CardHeader>
          <CardTitle>Quiz Builder</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="rounded-xl border p-3 space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <Label>Questions</Label>
              <Badge variant="outline">
                {questions.length}/{MAX_QUESTIONS}
              </Badge>
            </div>
            <p className="text-xs text-muted-foreground">
              Build one question at a time. After saving, choose whether to add another.
            </p>
          </div>

          {circleItems.length > 0 ? (
            <>
              <div className="rounded-xl border p-3">
                <div className="flex gap-2 overflow-x-auto pb-1">
                  {circleItems.map((circle) => {
                    const isActive = circle.clientId === activeCircleId;
                    const isHighlighted = circle.clientId === highlightedQuestionId;
                    return (
                      <button
                        key={`question-circle-${circle.clientId}`}
                        type="button"
                        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full border text-sm font-semibold transition-all duration-300 ${
                          isActive
                            ? "border-primary bg-primary text-white"
                            : circle.isDraft
                              ? "border-primary/40 bg-primary/10 text-primary dark:border-blue-700 dark:bg-blue-950/30 dark:text-blue-200"
                              : "border-muted-foreground/30 bg-background text-muted-foreground hover:border-primary/40"
                        } ${isHighlighted ? "scale-110 shadow-lg" : ""}`}
                        onClick={() => {
                          if (!circle.isDraft) {
                            beginEditQuestion(circle.clientId);
                          }
                        }}
                        title={circle.isDraft ? `Question ${circle.number} (draft)` : `Question ${circle.number}`}
                      >
                        {circle.number}
                      </button>
                    );
                  })}
                </div>
              </div>
            </>
          ) : null}

          {quizBuilderStage === "choose_type" ? (
            <div className="rounded-xl border p-4 space-y-4">
              <p className="text-sm font-semibold">
                What type should question {nextQuestionNumber} be?
              </p>
              <div className="grid gap-2 sm:grid-cols-2">
                {QUESTION_TYPE_CHOICES.map((choice) => (
                  <Button
                    key={`choice-${choice.value}`}
                    type="button"
                    variant="outline"
                    className="justify-start"
                    disabled={questions.length >= MAX_QUESTIONS}
                    onClick={() => beginCreateQuestion(choice.value)}
                  >
                    {choice.label}
                  </Button>
                ))}
              </div>
              {questions.length > 0 ? (
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => {
                    setQuizBuilderStage("ask_more");
                    setGlobalError(null);
                  }}
                >
                  Back to my questions
                </Button>
              ) : null}
            </div>
          ) : null}

          {quizBuilderStage === "edit_question" && draftQuestion ? (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border p-3">
                <div>
                  <p className="text-sm font-semibold">
                    {editingQuestionId ? "Editing" : "Building"} Question{" "}
                    {editingQuestionId
                      ? questions.findIndex((question) => question.clientId === editingQuestionId) + 1
                      : nextQuestionNumber}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {
                      QUESTION_TYPE_CHOICES.find(
                        (choice) => choice.value === draftQuestion.question_type
                      )?.label
                    }
                  </p>
                </div>
                <div className="flex gap-2">
                  {editingQuestionId ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => handleDeleteQuestion(editingQuestionId)}
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  ) : null}
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => {
                      setDraftQuestion(null);
                      setEditingQuestionId(null);
                      setQuizBuilderStage(questions.length > 0 ? "ask_more" : "choose_type");
                    }}
                  >
                    Cancel
                  </Button>
                </div>
              </div>

              <AdminQuestionEditor
                question={draftQuestion}
                onChange={handleQuestionDraftUpdate}
                errorMessage={draftErrors[0] ?? null}
              />

              {draftErrors.length > 0 ? (
                <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-sm text-destructive">
                  <p className="font-medium">Complete these before saving:</p>
                  <ul className="mt-1 space-y-1">
                    {draftErrors.slice(0, 4).map((error) => (
                      <li key={`draft-error-${error}`}>{error}</li>
                    ))}
                  </ul>
                </div>
              ) : null}

              <Button
                type="button"
                className="w-full"
                onClick={saveCurrentDraftQuestion}
                disabled={draftErrors.length > 0}
              >
                Save Question
              </Button>
            </div>
          ) : null}

          {quizBuilderStage === "ask_more" ? (
            <div className="rounded-xl border p-4 space-y-3">
              <p className="text-sm font-semibold">
                {questions.length >= MAX_QUESTIONS
                  ? `You reached the max of ${MAX_QUESTIONS} questions.`
                  : "Do you want to add another question?"}
              </p>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Button
                  type="button"
                  onClick={() => {
                    void handleAskMoreAddQuestion();
                  }}
                  disabled={questions.length >= MAX_QUESTIONS || stepStatus.quiz_setup === "saving"}
                >
                  Yes, add question
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => {
                    void handleAskMoreContinueToReview();
                  }}
                  disabled={stepStatus.quiz_setup === "saving"}
                >
                  No, continue to review
                </Button>
              </div>
            </div>
          ) : null}

          {questions.length === 0 && quizBuilderStage !== "edit_question" && quizBuilderStage !== "choose_type" ? (
            <p className="rounded-xl border border-dashed p-4 text-sm text-muted-foreground">
              No questions yet. Start by choosing a question type.
            </p>
          ) : null}
        </CardContent>
      </Card>
    );
  };

  const renderReviewStep = () => {
    const allErrors = STEPS.flatMap((step) => stepErrors[step.key]);
    return (
      <Card>
        <CardHeader>
          <CardTitle>Review & Publish</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-2 md:grid-cols-2">
            <div className="rounded-lg border p-3">
              <p className="text-sm text-muted-foreground">Lesson</p>
              <p className="font-medium">{lessonForm.title || "Untitled Lesson"}</p>
            </div>
            <div className="rounded-lg border p-3">
              <p className="text-sm text-muted-foreground">Questions</p>
              <p className="font-medium">{questions.length}</p>
            </div>
          </div>
          <div className="rounded-lg border p-3 space-y-2">
            {STEPS.map((step) => (
              <div key={step.key} className="flex items-center justify-between text-sm">
                <span>{step.label}</span>
                <Badge variant={stepStatus[step.key] === "invalid" ? "destructive" : "secondary"}>
                  {stepStatus[step.key]}
                </Badge>
              </div>
            ))}
          </div>
          {allErrors.length > 0 ? (
            <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-3">
              <p className="mb-2 text-sm font-medium text-destructive">Publish errors</p>
              <ul className="space-y-1 text-sm">
                {allErrors.map((error, index) => (
                  <li key={`${error.step}-${error.fieldPath}-${index}`}>
                    {error.step}: {error.fieldPath} - {error.message}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
          <Button onClick={handlePublish} disabled={isPublishing} className="w-full">
            {isPublishing ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <CheckCircle2 className="mr-2 h-4 w-4" />}
            Publish Lesson
          </Button>
        </CardContent>
      </Card>
    );
  };

  const currentStepErrors = stepErrors[activeStep];
  const hasPrevious = currentStepIndex > 0;
  const hasNext = currentStepIndex < STEPS.length - 1;
  const shortStepLabel: Record<WizardStepKey, string> = {
    basics: "Basics",
    content: "Content",
    quiz_setup: "Build",
    quiz_builder: "Order",
    review_publish: "Review",
  };

  const getStepVisual = (step: WizardStepKey, index: number) => {
    const state = stepStatus[step];
    const isActive = index === currentStepIndex;
    const isComplete = index < currentStepIndex && state !== "invalid";
    const isInvalid = state === "invalid";
    const isSaving = state === "saving";
    return { state, isActive, isComplete, isInvalid, isSaving };
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-start gap-3">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="mt-0.5 shrink-0"
            onClick={() => {
              if (isDirty && !window.confirm("You have unsaved changes. Leave this page?")) {
                return;
              }
              navigate("/admin/lessons");
            }}
          >
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="text-xl font-bold sm:text-2xl">{mode === "create" ? "Create Lesson" : "Edit Lesson"}</h1>
            <p className="text-sm text-muted-foreground">
              Wizard saves and verifies each step before moving forward.
            </p>
          </div>
        </div>
        <div className="self-start sm:self-auto">
          {isDirty ? <Badge variant="outline">Unsaved</Badge> : <Badge variant="secondary">Saved</Badge>}
        </div>
      </div>

      {globalError ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-4 w-4" />
            <span>{globalError}</span>
          </div>
        </div>
      ) : null}

      <div className="space-y-3 rounded-xl border bg-card/50 p-3 md:p-4">
        <div className="overflow-x-auto pb-1">
          <div className="flex min-w-max items-start gap-2 md:w-full md:min-w-0 md:justify-between">
            {STEPS.map((step, index) => {
              const visual = getStepVisual(step.key, index);
              const connectorComplete = index < currentStepIndex;
              return (
                <React.Fragment key={step.key}>
                  <button
                    type="button"
                    onClick={() => {
                      if (step.key === activeStep) return;
                      void saveAndMoveStep(step.key);
                    }}
                    className="group flex min-w-[68px] flex-col items-center gap-1.5 px-1 text-center md:min-w-0 md:flex-1"
                  >
                    <span
                      className={`flex h-7 w-7 items-center justify-center rounded-full border text-xs font-semibold transition md:h-9 md:w-9 md:text-sm ${
                        visual.isInvalid
                          ? "border-destructive bg-destructive text-destructive-foreground"
                          : visual.isComplete
                            ? "border-primary bg-primary text-primary-foreground"
                            : visual.isActive
                              ? "border-primary bg-primary/10 text-primary ring-2 ring-primary/20"
                              : "border-muted-foreground/30 bg-background text-muted-foreground"
                      }`}
                    >
                      {visual.isSaving ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin md:h-4 md:w-4" />
                      ) : visual.isComplete ? (
                        <CheckCircle2 className="h-3.5 w-3.5 md:h-4 md:w-4" />
                      ) : (
                        index + 1
                      )}
                    </span>
                    <span
                      className={`text-[10px] font-medium leading-tight md:text-xs ${
                        visual.isActive ? "text-foreground" : "text-muted-foreground"
                      }`}
                    >
                      <span className="md:hidden">{shortStepLabel[step.key]}</span>
                      <span className="hidden md:inline">{step.label}</span>
                    </span>
                    <span
                      className={`hidden text-[10px] capitalize md:inline ${
                        visual.isInvalid ? "text-destructive" : "text-muted-foreground"
                      }`}
                    >
                      {visual.state}
                    </span>
                  </button>
                  {index < STEPS.length - 1 ? (
                    <div
                      className={`mt-3 h-0.5 w-8 shrink-0 rounded-full transition md:mt-4 md:h-1 md:flex-1 md:min-w-[28px] ${
                        connectorComplete ? "bg-primary" : "bg-muted"
                      }`}
                    />
                  ) : null}
                </React.Fragment>
              );
            })}
          </div>
        </div>
      </div>

      {currentStepErrors.length > 0 ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-sm">
          <p className="mb-1 font-medium text-destructive">Fix these fields before leaving this step:</p>
          <ul className="space-y-1 text-destructive">
            {currentStepErrors.map((error, index) => (
              <li key={`${error.fieldPath}-${index}`}>
                {error.fieldPath}: {error.message}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {activeStep === "basics" ? renderBasicsStep() : null}
      {activeStep === "content" ? renderContentStep() : null}
      {activeStep === "quiz_setup" ? renderQuizSetupStep() : null}
      {activeStep === "review_publish" ? renderReviewStep() : null}

      <div className="sticky bottom-0 z-20 rounded-lg border bg-background/95 p-3 pb-[calc(env(safe-area-inset-bottom)+0.75rem)] backdrop-blur">
        <div className="space-y-2 sm:hidden">
          {hasNext ? (
            <Button
              type="button"
              className="w-full"
              onClick={() => {
                const nextStep = STEPS[currentStepIndex + 1].key;
                void saveAndMoveStep(nextStep);
              }}
            >
              Next
            </Button>
          ) : null}
          <Button
            type="button"
            variant="secondary"
            className="w-full"
            onClick={() => {
              void saveStep(activeStep);
            }}
          >
            <Save className="mr-2 h-4 w-4" />
            Save Step
          </Button>
          <Button
            type="button"
            variant="outline"
            className="w-full"
            disabled={!hasPrevious}
            onClick={() => {
              if (!hasPrevious) return;
              const previousStep = STEPS[currentStepIndex - 1].key;
              void saveAndMoveStep(previousStep);
            }}
          >
            Previous
          </Button>
        </div>

        <div className="hidden sm:flex sm:flex-row sm:justify-between">
          <Button
            type="button"
            variant="outline"
            disabled={!hasPrevious}
            onClick={() => {
              if (!hasPrevious) return;
              const previousStep = STEPS[currentStepIndex - 1].key;
              void saveAndMoveStep(previousStep);
            }}
          >
            Previous
          </Button>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                void saveStep(activeStep);
              }}
            >
              <Save className="mr-2 h-4 w-4" />
              Save Step
            </Button>
            {hasNext ? (
              <Button
                type="button"
                onClick={() => {
                  const nextStep = STEPS[currentStepIndex + 1].key;
                  void saveAndMoveStep(nextStep);
                }}
              >
                Next
              </Button>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
};
