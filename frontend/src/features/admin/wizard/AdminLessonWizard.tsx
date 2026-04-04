import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  AlertTriangle,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Image as ImageIcon,
  Link2,
  Loader2,
  Plus,
  Save,
  Star,
  Trash2,
  Type,
  Video,
} from "lucide-react";
import type {
  AdminQuizQuestionDraft,
  AdminValidationError,
  Category,
  Lesson,
  LessonMediaAsset,
  LessonSectionBlockType,
  QuizQuestion,
  WizardStepKey,
} from "@/types";
import {
  createAdminLessonDraft,
  fetchLessonMediaStatus,
  fetchAdminLessonById,
  fetchAdminLessonQuizQuestions,
  fetchCategories,
  publishAdminLesson,
  saveAdminLessonDraftStep,
  startLessonMediaLink,
  startLessonMediaUpload,
} from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { AdminQuestionEditor } from "@/features/admin/quiz-builder/AdminQuestionEditor";
import { FeedVideoPlayer } from "@/components/feed/FeedVideoPlayer";
import { inferLessonMediaKind, type LessonMediaKind } from "@/components/lesson/LessonMediaDisplay";
import { cn } from "@/lib/utils";

type Props = {
  mode: "create" | "edit";
  lessonId?: string;
};

type LessonDraftForm = {
  id?: string;
  title: string;
  description: string;
  summary: string;
  category_id: string;
  learning_objectives: string[];
  estimated_minutes: number;
  xp_reward: number;
  badge_name: string;
  difficulty_level: number;
  content_sections: LessonContentSectionForm[];
  is_published?: boolean;
};

type LessonContentBlockForm = {
  id: string;
  blockType: LessonSectionBlockType;
  textContent: string;
  mediaAssetId: string;
  caption: string;
  altText: string;
  media: LessonMediaAsset | null;
  linkUrl: string;
  isUploading: boolean;
  error: string | null;
};

type LessonContentSectionForm = {
  sectionKey: string;
  title: string;
  blocks: LessonContentBlockForm[];
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

  if (question.media_status === "processing") {
    errors.push("Wait for question media to finish processing.");
  }
  if (question.media_status === "failed") {
    errors.push(question.media_error?.trim() || "Question media failed to process. Remove it or attach another file or URL.");
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

const LESSON_SECTION_CONFIG: Array<{ sectionKey: string; title: string }> = [
  { sectionKey: "intro", title: "Origin" },
  { sectionKey: "definition", title: "Definition" },
  { sectionKey: "usage", title: "Usage Examples" },
  { sectionKey: "lore", title: "Lore" },
  { sectionKey: "evolution", title: "Evolution" },
  { sectionKey: "comparison", title: "Comparison" },
];

const PREVIEW_STEP_GAP = 88;
const PREVIEW_MAX_VISIBLE_DISTANCE = 3;
const PREVIEW_STEP_HORIZONTAL_OFFSET = 18;

const createBlockId = () => createClientId();

const createEmptyBlock = (blockType: LessonSectionBlockType): LessonContentBlockForm => ({
  id: createBlockId(),
  blockType,
  textContent: "",
  mediaAssetId: "",
  caption: "",
  altText: "",
  media: null,
  linkUrl: "",
  isUploading: false,
  error: null,
});

const createDefaultSections = (): LessonContentSectionForm[] =>
  LESSON_SECTION_CONFIG.map((section) => ({
    sectionKey: section.sectionKey,
    title: section.title,
    blocks: [],
  }));

const normalizeMediaAsset = (value: unknown): LessonMediaAsset | null => {
  if (!value || typeof value !== "object") {
    return null;
  }
  const media = value as Record<string, unknown>;
  const id = toString(media.id);
  if (!id) {
    return null;
  }
  return {
    id,
    lesson_id: toString(media.lesson_id) || null,
    source_type: (toString(media.source_type) || "upload") as "upload" | "link",
    media_kind: (toString(media.media_kind) || "image") as "image" | "gif" | "video",
    source_url: toString(media.source_url) || null,
    status: (toString(media.status) || "processing") as "processing" | "ready" | "failed",
    playback_url: toString(media.playback_url) || null,
    thumbnail_url: toString(media.thumbnail_url) || null,
    storage_path: toString(media.storage_path) || null,
    mime_type: toString(media.mime_type) || null,
    duration_ms: Number(media.duration_ms ?? 0) || null,
    width: Number(media.width ?? 0) || null,
    height: Number(media.height ?? 0) || null,
    size_bytes: Number(media.size_bytes ?? 0) || null,
    error_message: toString(media.error_message) || null,
    created_at: toString(media.created_at) || null,
    updated_at: toString(media.updated_at) || null,
  };
};

const normalizeContentSections = (lesson: Partial<Lesson> | Record<string, unknown> | null | undefined) => {
  const rawSections = Array.isArray((lesson as Record<string, unknown> | null | undefined)?.content_sections)
    ? (((lesson as Record<string, unknown>).content_sections as unknown[]) ?? [])
    : [];

  if (rawSections.length > 0) {
    return LESSON_SECTION_CONFIG.map((config) => {
      const sourceSection =
        rawSections.find((section) => {
          if (!section || typeof section !== "object") return false;
          const typed = section as Record<string, unknown>;
          const key = toString(typed.sectionKey ?? typed.section_key ?? typed.id);
          return key === config.sectionKey;
        }) ?? {};
      const typedSection = sourceSection as Record<string, unknown>;
      const rawBlocks = Array.isArray(typedSection.blocks) ? (typedSection.blocks as unknown[]) : [];
      return {
        sectionKey: config.sectionKey,
        title: config.title,
        blocks: rawBlocks
          .map((block) => {
            if (!block || typeof block !== "object") return null;
            const typedBlock = block as Record<string, unknown>;
            const blockType = toString(typedBlock.blockType ?? typedBlock.block_type) as LessonSectionBlockType;
            if (!["text", "image", "gif", "video"].includes(blockType)) return null;
            return {
              id: toString(typedBlock.id) || createBlockId(),
              blockType,
              textContent: toString(typedBlock.textContent ?? typedBlock.text_content),
              mediaAssetId: toString(typedBlock.mediaAssetId ?? typedBlock.media_asset_id),
              caption: toString(typedBlock.caption),
              altText: toString(typedBlock.altText ?? typedBlock.alt_text),
              media: normalizeMediaAsset(typedBlock.media),
              linkUrl: "",
              isUploading: false,
              error: null,
            } satisfies LessonContentBlockForm;
          })
          .filter(Boolean) as LessonContentBlockForm[],
      } satisfies LessonContentSectionForm;
    });
  }

  return [
    {
      sectionKey: "intro",
      title: "Origin",
      blocks: toString(lesson?.origin_content)
        ? [{ ...createEmptyBlock("text"), textContent: toString(lesson?.origin_content) }]
        : [],
    },
    {
      sectionKey: "definition",
      title: "Definition",
      blocks: toString(lesson?.definition_content)
        ? [{ ...createEmptyBlock("text"), textContent: toString(lesson?.definition_content) }]
        : [],
    },
    {
      sectionKey: "usage",
      title: "Usage Examples",
      blocks: toStringList(lesson?.usage_examples)
        .filter((value) => value.trim().length > 0)
        .map((value) => ({ ...createEmptyBlock("text"), textContent: value })),
    },
    {
      sectionKey: "lore",
      title: "Lore",
      blocks: toString(lesson?.lore_content)
        ? [{ ...createEmptyBlock("text"), textContent: toString(lesson?.lore_content) }]
        : [],
    },
    {
      sectionKey: "evolution",
      title: "Evolution",
      blocks: toString(lesson?.evolution_content)
        ? [{ ...createEmptyBlock("text"), textContent: toString(lesson?.evolution_content) }]
        : [],
    },
    {
      sectionKey: "comparison",
      title: "Comparison",
      blocks: toString(lesson?.comparison_content)
        ? [{ ...createEmptyBlock("text"), textContent: toString(lesson?.comparison_content) }]
        : [],
    },
  ];
};

const normalizeLessonDraft = (lesson: Partial<Lesson> | Record<string, unknown> | null | undefined): LessonDraftForm => ({
  id: lesson?.id ? String(lesson.id) : undefined,
  title: toString(lesson?.title),
  description: toString(lesson?.description),
  summary: toString(lesson?.summary),
  category_id: toString(lesson?.category_id),
  learning_objectives: toStringList(lesson?.learning_objectives),
  estimated_minutes: Number(lesson?.estimated_minutes ?? 15),
  xp_reward: Number(lesson?.xp_reward ?? 100),
  badge_name: toString(lesson?.badge_name),
  difficulty_level: Number(lesson?.difficulty_level ?? 1),
  content_sections: normalizeContentSections(lesson),
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
    media_url: null,
    media_kind: null,
    media_asset_id: null,
    media_thumbnail_url: null,
    media_status: "idle" as const,
    media_link_url: "",
    media_error: null,
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
    media_url: toString(question.media_url) || null,
    media_kind: inferLessonMediaKind(toString(question.media_url) || null, null),
    media_asset_id: null,
    media_thumbnail_url: null,
    media_status: toString(question.media_url) ? "ready" : "idle",
    media_link_url: "",
    media_error: null,
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
  category_id: form.category_id || null,
  learning_objectives: form.learning_objectives.map((item) => item.trim()).filter(Boolean),
  estimated_minutes: Number(form.estimated_minutes || 0),
  xp_reward: Number(form.xp_reward || 0),
  badge_name: form.badge_name,
  difficulty_level: Number(form.difficulty_level || 1),
  content_sections: form.content_sections.map((section) => ({
    sectionKey: section.sectionKey,
    title: section.title,
    blocks: section.blocks.map((block) => ({
      id: block.id,
      blockType: block.blockType,
      textContent: block.textContent,
      mediaAssetId: block.mediaAssetId || null,
      caption: block.caption || null,
      altText: block.altText || null,
    })),
  })),
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
  const [categories, setCategories] = useState<Category[]>([]);
  const [lessonId, setLessonId] = useState<string | null>(lessonIdProp ?? null);
  const [openQuestionId, setOpenQuestionId] = useState<string | null>(null);
  const [quizBuilderStage, setQuizBuilderStage] = useState<QuizBuilderStage>("choose_type");
  const [draftQuestion, setDraftQuestion] = useState<AdminQuizQuestionDraft | null>(null);
  const [editingQuestionId, setEditingQuestionId] = useState<string | null>(null);
  const [highlightedQuestionId, setHighlightedQuestionId] = useState<string | null>(null);
  const [previewSectionKey, setPreviewSectionKey] = useState<string>(LESSON_SECTION_CONFIG[0]?.sectionKey ?? "intro");
  const [previewAnimatingIndex, setPreviewAnimatingIndex] = useState<number | null>(null);
  const [previewNextPulseIndex, setPreviewNextPulseIndex] = useState<number | null>(null);
  const [lessonForm, setLessonForm] = useState<LessonDraftForm>(() => normalizeLessonDraft(null));
  const [questions, setQuestions] = useState<AdminQuizQuestionDraft[]>([]);
  const [skipEmbedding, setSkipEmbedding] = useState(false);

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
    if (lessonForm.content_sections.some((section) => section.sectionKey === previewSectionKey)) {
      return;
    }
    setPreviewSectionKey(lessonForm.content_sections[0]?.sectionKey ?? LESSON_SECTION_CONFIG[0]?.sectionKey ?? "intro");
  }, [lessonForm.content_sections, previewSectionKey]);

  useEffect(() => {
    let active = true;
    fetchCategories()
      .then((loaded) => {
        if (!active) return;
        setCategories(loaded);
      })
      .catch((error) => {
        if (!active) return;
        console.warn("Failed to load lesson categories", error);
      });

    return () => {
      active = false;
    };
  }, []);

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

  const updateSectionBlocks = (
    sectionKey: string,
    updater: (blocks: LessonContentBlockForm[]) => LessonContentBlockForm[]
  ) => {
    setPreviewSectionKey(sectionKey);
    setLessonForm((prev) => ({
      ...prev,
      content_sections: prev.content_sections.map((section) =>
        section.sectionKey === sectionKey ? { ...section, blocks: updater(section.blocks) } : section
      ),
    }));
    markStepUnsaved("content");
  };

  const updateBlock = (
    sectionKey: string,
    blockId: string,
    updater: (block: LessonContentBlockForm) => LessonContentBlockForm
  ) => {
    updateSectionBlocks(sectionKey, (blocks) =>
      blocks.map((block) => (block.id === blockId ? updater(block) : block))
    );
  };

  const moveBlock = (sectionKey: string, blockId: string, direction: -1 | 1) => {
    updateSectionBlocks(sectionKey, (blocks) => {
      const index = blocks.findIndex((block) => block.id === blockId);
      const nextIndex = index + direction;
      if (index < 0 || nextIndex < 0 || nextIndex >= blocks.length) {
        return blocks;
      }
      const next = [...blocks];
      const [block] = next.splice(index, 1);
      next.splice(nextIndex, 0, block);
      return next;
    });
  };

  const addContentBlock = (sectionKey: string, blockType: LessonSectionBlockType) => {
    updateSectionBlocks(sectionKey, (blocks) => [...blocks, createEmptyBlock(blockType)]);
  };

  const removeContentBlock = (sectionKey: string, blockId: string) => {
    updateSectionBlocks(sectionKey, (blocks) => blocks.filter((block) => block.id !== blockId));
  };

  const pollLessonMedia = async (sectionKey: string, blockId: string, assetId: string) => {
    if (!lessonId) return;
    for (let attempt = 0; attempt < 45; attempt += 1) {
      try {
        const status = await fetchLessonMediaStatus(lessonId, assetId);
        updateBlock(sectionKey, blockId, (block) => ({
          ...block,
          media: {
            ...(block.media ?? {
              id: status.assetId,
              source_type: "upload",
              media_kind: status.mediaKind,
            }),
            id: status.assetId,
            media_kind: status.mediaKind,
            status: status.status,
            playback_url: status.playbackUrl ?? null,
            thumbnail_url: status.thumbnailUrl ?? null,
            error_message: status.errorMessage ?? null,
          },
          mediaAssetId: assetId,
          isUploading: status.status === "processing",
          error: status.status === "failed" ? status.errorMessage ?? "Media processing failed." : null,
        }));
        if (status.status !== "processing") {
          return;
        }
      } catch (error) {
        updateBlock(sectionKey, blockId, (block) => ({
          ...block,
          isUploading: false,
          error: error instanceof Error ? error.message : "Failed to fetch media status.",
        }));
        return;
      }
      await new Promise((resolve) => window.setTimeout(resolve, 2000));
    }
  };

  const handleUploadBlockMedia = async (sectionKey: string, blockId: string, file: File | null) => {
    if (!lessonId || !file) return;
    updateBlock(sectionKey, blockId, (block) => ({ ...block, isUploading: true, error: null }));
    try {
      const formData = new FormData();
      formData.append("file", file);
      const response = await startLessonMediaUpload(lessonId, formData);
      updateBlock(sectionKey, blockId, (block) => ({
        ...block,
        mediaAssetId: response.assetId,
        media: {
          id: response.assetId,
          source_type: "upload",
          media_kind: block.blockType === "text" ? "image" : block.blockType,
          status: "processing",
        },
        isUploading: true,
        error: null,
      }));
      void pollLessonMedia(sectionKey, blockId, response.assetId);
    } catch (error) {
      updateBlock(sectionKey, blockId, (block) => ({
        ...block,
        isUploading: false,
        error: error instanceof Error ? error.message : "Upload failed.",
      }));
    }
  };

  const handleAttachBlockLink = async (sectionKey: string, blockId: string) => {
    if (!lessonId) return;
    const section = lessonForm.content_sections.find((item) => item.sectionKey === sectionKey);
    const block = section?.blocks.find((item) => item.id === blockId);
    if (!block || !block.linkUrl.trim()) {
      updateBlock(sectionKey, blockId, (current) => ({ ...current, error: "Enter a media URL first." }));
      return;
    }

    updateBlock(sectionKey, blockId, (current) => ({ ...current, isUploading: true, error: null }));
    try {
      const response = await startLessonMediaLink(lessonId, {
        sourceUrl: block.linkUrl.trim(),
        mediaKind: block.blockType === "text" ? "image" : block.blockType,
      });
      updateBlock(sectionKey, blockId, (current) => ({
        ...current,
        mediaAssetId: response.assetId,
        media: {
          id: response.assetId,
          source_type: "link",
          media_kind: current.blockType === "text" ? "image" : current.blockType,
          status: "processing",
          source_url: current.linkUrl.trim(),
        },
        isUploading: true,
        error: null,
      }));
      void pollLessonMedia(sectionKey, blockId, response.assetId);
    } catch (error) {
      updateBlock(sectionKey, blockId, (current) => ({
        ...current,
        isUploading: false,
        error: error instanceof Error ? error.message : "Failed to attach media URL.",
      }));
    }
  };

  const resolveBlockMediaUrl = (block: LessonContentBlockForm) =>
    block.media?.playback_url ?? block.media?.source_url ?? "";

  const mediaAcceptForBlock = (blockType: LessonSectionBlockType) => {
    if (blockType === "text") {
      return "image/*,image/gif,video/*";
    }
    return "image/*,image/gif,video/*";
  };

  const previewSections = lessonForm.content_sections;
  const previewCurrentIndex = Math.max(
    0,
    previewSections.findIndex((section) => section.sectionKey === previewSectionKey)
  );
  const previewCurrentSection = previewSections[previewCurrentIndex] ?? previewSections[0] ?? null;
  const previewCompletedSections = previewCurrentIndex;
  const previewHasPrevious = previewCurrentIndex > 0;
  const previewHasNext = previewCurrentIndex >= 0 && previewCurrentIndex < previewSections.length - 1;

  const previewRailOffsetX = (index: number) =>
    index % 2 === 0 ? -PREVIEW_STEP_HORIZONTAL_OFFSET : PREVIEW_STEP_HORIZONTAL_OFFSET;

  const setPreviewSectionByIndex = (index: number) => {
    const target = previewSections[index];
    if (!target) {
      return;
    }
    setPreviewSectionKey(target.sectionKey);
  };

  const animatePreviewAdvance = (direction: -1 | 1) => {
    const nextIndex = previewCurrentIndex + direction;
    if (nextIndex < 0 || nextIndex >= previewSections.length) {
      return;
    }
    if (direction > 0) {
      setPreviewAnimatingIndex(previewCurrentIndex);
      setPreviewNextPulseIndex(nextIndex);
      window.setTimeout(() => {
        setPreviewAnimatingIndex(null);
        setPreviewNextPulseIndex(null);
      }, 650);
    }
    setPreviewSectionByIndex(nextIndex);
  };

  const renderContentBlockPreview = (block: LessonContentBlockForm) => {
    const mediaUrl = resolveBlockMediaUrl(block);
    const isReady = block.media?.status === "ready" && mediaUrl;

    if (block.blockType === "text") {
      const paragraphs = block.textContent
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean);

      if (paragraphs.length === 0) {
        return <p className="text-sm text-muted-foreground">Add text to preview this block.</p>;
      }

      return (
        <div className="space-y-3 text-base leading-7 text-mainAccent/95 dark:text-white/95">
          {paragraphs.map((paragraph, index) => (
            <p key={`${block.id}-preview-text-${index}`}>{paragraph}</p>
          ))}
        </div>
      );
    }

    if (!isReady) {
      return (
        <div className="rounded-xl border border-dashed bg-muted/30 px-4 py-8 text-sm text-muted-foreground">
          {block.isUploading
            ? "Processing media..."
            : "Upload a file or attach a URL to preview this media block."}
        </div>
      );
    }

    if (block.blockType === "video") {
      return (
        <div className="space-y-2">
          <FeedVideoPlayer
            sourceUrl={mediaUrl}
            poster={block.media?.thumbnail_url ?? null}
            showPoster
            className="w-full overflow-hidden rounded-xl bg-black"
            controls
            loop={false}
            isActive
            isPaused={false}
            shouldAutoplay={false}
          />
          {block.caption.trim() ? <p className="text-sm text-muted-foreground">{block.caption.trim()}</p> : null}
        </div>
      );
    }

    return (
      <div className="space-y-2">
        <img
          src={mediaUrl}
          alt={block.altText.trim() || block.caption.trim() || "Lesson media preview"}
          className="max-h-80 w-full rounded-xl border object-cover"
        />
        {block.caption.trim() ? <p className="text-sm text-muted-foreground">{block.caption.trim()}</p> : null}
      </div>
    );
  };

  const renderFullLessonPreview = () => {
    const previewTitle = lessonForm.title.trim() || "Untitled Lesson";
    const previewSummary = lessonForm.summary.trim() || "Your lesson summary will appear here.";

    return (
      <div className="h-full xl:sticky xl:top-6">
        <div className="overflow-hidden rounded-[2rem] border bg-card shadow-xl xl:flex xl:h-full xl:flex-col">
          <div className="border-b bg-gradient-to-br from-mainAccent/10 via-background to-background px-6 py-5">
            <p className="text-xs uppercase tracking-[0.22em] text-muted-foreground">Full Lesson Preview</p>
            <div className="mt-3 flex items-start justify-between gap-4">
              <div className="min-w-0">
                <div className="inline-flex items-center text-sm text-mainAccent dark:text-white">
                  <ArrowLeft className="mr-2 h-4 w-4" />
                  Back to Lesson Details
                </div>
                <h3 className="mt-3 text-2xl font-bold text-mainAccent dark:text-white">{previewTitle}</h3>
                <p className="mt-2 text-sm leading-6 text-muted-foreground">{previewSummary}</p>
              </div>
              <Badge variant="outline" className="shrink-0">
                {previewCurrentIndex + 1}/{Math.max(previewSections.length, 1)}
              </Badge>
            </div>
          </div>

          <div className="grid min-h-[720px] grid-cols-[132px_minmax(0,1fr)] gap-0 bg-background xl:min-h-0 xl:flex-1">
            <aside className="relative border-r bg-muted/20 px-3 py-6">
              <button
                type="button"
                aria-label="Previous preview section"
                onClick={() => animatePreviewAdvance(-1)}
                disabled={!previewHasPrevious}
                className="mx-auto mb-4 flex h-9 w-9 items-center justify-center rounded-full border border-mainAlt bg-main text-mainAccent disabled:opacity-40"
              >
                <ArrowUp className="h-4 w-4" />
              </button>

              <div className="relative mx-auto h-[460px] w-[108px] overflow-hidden">
                {previewSections.map((section, index) => {
                  const relative = index - previewCurrentIndex;
                  const distance = Math.abs(relative);
                  const isVisible = distance <= PREVIEW_MAX_VISIBLE_DISTANCE;
                  const opacity = distance === 0 ? 1 : distance === 1 ? 0.72 : distance === 2 ? 0.42 : 0.2;
                  const scale = distance === 0 ? 1 : distance === 1 ? 0.86 : distance === 2 ? 0.72 : 0.62;
                  const y = relative * PREVIEW_STEP_GAP;
                  const x = previewRailOffsetX(index);
                  const isCompleted = index < previewCompletedSections;
                  const isCurrent = index === previewCurrentIndex;
                  const baseClasses = isCompleted
                    ? "bg-duoGreen border-[#b51f3d] text-white shadow-mainCircleShadow"
                    : isCurrent
                      ? "bg-mainAccent border-mainAccent text-main shadow-mainCircleShadow"
                      : "bg-main border-mainAlt text-mainAccent/85 dark:text-white/85 shadow-mainCircleShadow";

                  return (
                    <div
                      key={`preview-stop-${section.sectionKey}`}
                      className={cn("absolute left-1/2 top-1/2 transition-all duration-500", !isVisible && "pointer-events-none")}
                      style={{
                        transform: `translate(-50%, -50%) translate(${x}px, ${y}px) scale(${scale})`,
                        opacity: isVisible ? opacity : 0,
                      }}
                    >
                      <button
                        type="button"
                        onClick={() => setPreviewSectionKey(section.sectionKey)}
                        className={cn(
                          "relative flex h-16 w-[68px] items-center justify-center rounded-full border-2 transition-transform duration-200",
                          baseClasses,
                          isCurrent && "animate-stop-current ring-4 ring-mainAccent/25",
                          previewAnimatingIndex === index && "animate-stop-fill",
                          previewNextPulseIndex === index && "animate-stop-next"
                        )}
                      >
                        {isCompleted ? (
                          <Check className="h-6 w-6" />
                        ) : isCurrent ? (
                          <Star className="h-6 w-6 fill-current" />
                        ) : (
                          <span className="text-lg">{index + 1}</span>
                        )}
                      </button>
                      <p
                        className="pointer-events-none absolute left-1/2 top-[72px] h-8 w-[120px] -translate-x-1/2 overflow-hidden text-center text-[11px] leading-4 text-mainAccent/90 dark:text-white/90 break-words"
                        title={section.title}
                      >
                        {section.title}
                      </p>
                    </div>
                  );
                })}
              </div>

              <button
                type="button"
                aria-label="Next preview section"
                onClick={() => animatePreviewAdvance(1)}
                disabled={!previewHasNext}
                className="mx-auto mt-4 flex h-9 w-9 items-center justify-center rounded-full border border-mainAlt bg-main text-mainAccent disabled:opacity-40"
              >
                <ArrowDown className="h-4 w-4" />
              </button>
            </aside>

            <div className="flex min-h-[720px] flex-col">
              <div className="border-b px-6 py-5">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">Section Page</p>
                    <h4 className="mt-2 text-4xl font-semibold text-mainAccent dark:text-white">
                      {previewCurrentSection?.title ?? "Section Preview"}
                    </h4>
                  </div>
                  <Badge variant="outline">
                    {previewCurrentSection?.blocks.length ?? 0} block
                    {(previewCurrentSection?.blocks.length ?? 0) === 1 ? "" : "s"}
                  </Badge>
                </div>
              </div>

              <div className="flex-1 overflow-y-auto px-6 py-6">
                {previewCurrentSection && previewCurrentSection.blocks.length > 0 ? (
                  <div className="space-y-5 text-lg leading-9 text-mainAccent/95 dark:text-white/95">
                    {previewCurrentSection.blocks.map((block) => (
                      <div key={`${previewCurrentSection.sectionKey}-${block.id}`} className="rounded-2xl bg-card p-4 shadow-sm">
                        {renderContentBlockPreview(block)}
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="rounded-2xl border border-dashed bg-muted/20 px-5 py-12 text-base text-muted-foreground">
                    This section preview is empty. Add text or media blocks on the left and they will render here in the learner layout.
                  </div>
                )}
              </div>

              <div className="border-t bg-background/95 px-6 py-4">
                <div className="flex items-center justify-between gap-3">
                  <Button
                    type="button"
                    variant="outline"
                    disabled={!previewHasPrevious}
                    onClick={() => animatePreviewAdvance(-1)}
                  >
                    <ArrowLeft className="mr-2 h-4 w-4" />
                    Back
                  </Button>
                  {previewHasNext ? (
                    <Button type="button" className="duo-button-primary" onClick={() => animatePreviewAdvance(1)}>
                      Next
                      <ArrowRight className="ml-2 h-4 w-4" />
                    </Button>
                  ) : (
                    <Button type="button" className="duo-button-primary">
                      Take Quiz
                    </Button>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
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

  const pollQuestionMedia = async (assetId: string) => {
    if (!lessonId) return;
    for (let attempt = 0; attempt < 45; attempt += 1) {
      try {
        const status = await fetchLessonMediaStatus(lessonId, assetId);
        setDraftQuestion((current) => {
          if (!current || current.media_asset_id !== assetId) {
            return current;
          }
          return {
            ...current,
            media_asset_id: assetId,
            media_kind: status.mediaKind,
            media_status: status.status,
            media_thumbnail_url: status.thumbnailUrl ?? null,
            media_url: status.status === "ready" ? status.playbackUrl ?? current.media_url : null,
            media_error: status.status === "failed" ? status.errorMessage ?? "Media processing failed." : null,
          };
        });
        if (status.status !== "processing") {
          return;
        }
      } catch (error) {
        setDraftQuestion((current) =>
          current && current.media_asset_id === assetId
            ? {
                ...current,
                media_status: "failed",
                media_error: error instanceof Error ? error.message : "Failed to fetch media status.",
              }
            : current
        );
        return;
      }
      await new Promise((resolve) => window.setTimeout(resolve, 2000));
    }
  };

  const handleUploadQuestionMedia = async (kind: LessonMediaKind, file: File | null) => {
    if (!lessonId || !file || !draftQuestion) return;
    setDraftQuestion((current) =>
      current
        ? {
            ...current,
            media_kind: kind,
            media_url: null,
            media_thumbnail_url: null,
            media_status: "processing",
            media_error: null,
          }
        : current
    );
    try {
      const formData = new FormData();
      formData.append("file", file);
      const response = await startLessonMediaUpload(lessonId, formData);
      setDraftQuestion((current) =>
        current
          ? {
              ...current,
              media_asset_id: response.assetId,
              media_kind: kind,
              media_status: "processing",
              media_url: null,
              media_thumbnail_url: null,
              media_error: null,
            }
          : current
      );
      void pollQuestionMedia(response.assetId);
    } catch (error) {
      setDraftQuestion((current) =>
        current
          ? {
              ...current,
              media_status: "failed",
              media_error: error instanceof Error ? error.message : "Upload failed.",
            }
          : current
      );
    }
  };

  const handleAttachQuestionMediaLink = async (kind: LessonMediaKind) => {
    if (!lessonId || !draftQuestion) return;
    const sourceUrl = draftQuestion.media_link_url?.trim();
    if (!sourceUrl) {
      setDraftQuestion((current) =>
        current
          ? {
              ...current,
              media_error: "Enter a media URL first.",
            }
          : current
      );
      return;
    }

    setDraftQuestion((current) =>
      current
        ? {
            ...current,
            media_kind: kind,
            media_url: null,
            media_thumbnail_url: null,
            media_status: "processing",
            media_error: null,
          }
        : current
    );
    try {
      const response = await startLessonMediaLink(lessonId, {
        sourceUrl,
        mediaKind: kind,
      });
      setDraftQuestion((current) =>
        current
          ? {
              ...current,
              media_asset_id: response.assetId,
              media_kind: kind,
              media_status: "processing",
              media_url: null,
              media_thumbnail_url: null,
              media_error: null,
            }
          : current
      );
      void pollQuestionMedia(response.assetId);
    } catch (error) {
      setDraftQuestion((current) =>
        current
          ? {
              ...current,
              media_status: "failed",
              media_error: error instanceof Error ? error.message : "Failed to attach media URL.",
            }
          : current
      );
    }
  };

  const handleRemoveQuestionMedia = () => {
    setDraftQuestion((current) =>
      current
        ? {
            ...current,
            media_url: null,
            media_kind: null,
            media_asset_id: null,
            media_thumbnail_url: null,
            media_status: "idle",
            media_link_url: "",
            media_error: null,
          }
        : current
    );
  };

  const handlePublish = async (options?: { skip_embedding?: boolean }) => {
    if (!lessonId) return;
    setIsPublishing(true);
    setGlobalError(null);
    try {
      const lessonPayload = {
        ...toLessonPayload(lessonForm),
        ...(options?.skip_embedding !== undefined && { skip_embedding: options.skip_embedding }),
      };

      const result = await publishAdminLesson(lessonId, {
        lesson: lessonPayload,
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
            <Label>Category</Label>
            <Select
              value={lessonForm.category_id || "__none__"}
              onValueChange={(value) => {
                setLessonForm((prev) => ({ ...prev, category_id: value === "__none__" ? "" : value }));
                markStepUnsaved("basics");
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select a category" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__none__">No category</SelectItem>
                {categories.map((category) => (
                  <SelectItem key={category.id} value={category.id}>
                    {category.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
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
    <Card className="overflow-hidden">
      <CardHeader>
        <CardTitle>Lesson Content</CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <div className="grid gap-0 xl:h-[calc(100vh-14rem)] xl:grid-cols-2">
          <div className="space-y-6 border-b p-5 xl:h-full xl:overflow-y-auto xl:border-b-0 xl:border-r xl:p-6">
            <div className="rounded-2xl border bg-muted/10 p-4 md:p-5">
              <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                <div>
                  <h3 className="text-lg font-semibold">Lesson Content Editor</h3>
                  <p className="text-sm text-muted-foreground">
                    Build the six fixed learner sections on the left. The full learner page preview stays live on the right.
                  </p>
                </div>
                <Badge variant="outline">{lessonForm.content_sections.length} sections</Badge>
              </div>

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
            </div>

            <div className="space-y-5">
              {lessonForm.content_sections.map((section) => (
                <div
                  key={section.sectionKey}
                  className={cn(
                    "rounded-2xl border p-4 transition-colors md:p-5",
                    previewSectionKey === section.sectionKey
                      ? "border-mainAccent/50 bg-mainAccent/5"
                      : "bg-card"
                  )}
                  onClick={() => setPreviewSectionKey(section.sectionKey)}
                >
                  <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <div>
                      <div className="flex items-center gap-2">
                        <h3 className="text-lg font-semibold">{section.title}</h3>
                        {previewSectionKey === section.sectionKey ? <Badge variant="secondary">Previewing</Badge> : null}
                      </div>
                      <p className="text-sm text-muted-foreground">
                        Build this section with ordered text, image, GIF, and video blocks.
                      </p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => addContentBlock(section.sectionKey, "text")}
                      >
                        <Type className="mr-2 h-4 w-4" />
                        Add Text
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => addContentBlock(section.sectionKey, "image")}
                      >
                        <ImageIcon className="mr-2 h-4 w-4" />
                        Add Image
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => addContentBlock(section.sectionKey, "gif")}
                      >
                        <ImageIcon className="mr-2 h-4 w-4" />
                        Add GIF
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => addContentBlock(section.sectionKey, "video")}
                      >
                        <Video className="mr-2 h-4 w-4" />
                        Add Video
                      </Button>
                    </div>
                  </div>

                  {section.blocks.length === 0 ? (
                    <div className="rounded-xl border border-dashed bg-muted/30 px-4 py-8 text-sm text-muted-foreground">
                      This section is empty. Add at least one block before publishing.
                    </div>
                  ) : null}

                  <div className="space-y-3">
                    {section.blocks.map((block, index) => (
                      <div key={block.id} className="rounded-xl border bg-card p-4">
                        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <Badge variant="secondary">#{index + 1}</Badge>
                            <Badge variant="outline" className="capitalize">
                              {block.blockType}
                            </Badge>
                            {block.media?.status ? (
                              <Badge variant={block.media.status === "failed" ? "destructive" : "outline"}>
                                {block.media.status}
                              </Badge>
                            ) : null}
                          </div>
                          <div className="flex items-center gap-2">
                            <Button
                              type="button"
                              size="icon"
                              variant="ghost"
                              disabled={index === 0}
                              onClick={() => moveBlock(section.sectionKey, block.id, -1)}
                            >
                              <ChevronUp className="h-4 w-4" />
                            </Button>
                            <Button
                              type="button"
                              size="icon"
                              variant="ghost"
                              disabled={index === section.blocks.length - 1}
                              onClick={() => moveBlock(section.sectionKey, block.id, 1)}
                            >
                              <ChevronDown className="h-4 w-4" />
                            </Button>
                            <Button
                              type="button"
                              size="icon"
                              variant="ghost"
                              onClick={() => removeContentBlock(section.sectionKey, block.id)}
                            >
                              <Trash2 className="h-4 w-4 text-destructive" />
                            </Button>
                          </div>
                        </div>

                        {block.blockType === "text" ? (
                          <div className="space-y-2">
                            <Label>Text</Label>
                            <Textarea
                              value={block.textContent}
                              onChange={(event) => {
                                updateBlock(section.sectionKey, block.id, (current) => ({
                                  ...current,
                                  textContent: event.target.value,
                                }));
                              }}
                              rows={5}
                              placeholder="Write the lesson text for this section block."
                            />
                          </div>
                        ) : (
                          <div className="space-y-4">
                            <div className="grid gap-4 md:grid-cols-2">
                              <div className="space-y-2">
                                <Label className="flex items-center gap-2">
                                  <ImageIcon className="h-4 w-4" />
                                  Upload File
                                </Label>
                                <Input
                                  type="file"
                                  accept={mediaAcceptForBlock(block.blockType)}
                                  onChange={(event) => {
                                    const file = event.target.files?.[0] ?? null;
                                    void handleUploadBlockMedia(section.sectionKey, block.id, file);
                                    event.currentTarget.value = "";
                                  }}
                                />
                              </div>
                              <div className="space-y-2">
                                <Label className="flex items-center gap-2">
                                  <Link2 className="h-4 w-4" />
                                  Media URL
                                </Label>
                                <div className="flex gap-2">
                                  <Input
                                    value={block.linkUrl}
                                    onChange={(event) => {
                                      updateBlock(section.sectionKey, block.id, (current) => ({
                                        ...current,
                                        linkUrl: event.target.value,
                                      }));
                                    }}
                                    placeholder="https://..."
                                  />
                                  <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => {
                                      void handleAttachBlockLink(section.sectionKey, block.id);
                                    }}
                                    disabled={block.isUploading}
                                  >
                                    Attach
                                  </Button>
                                </div>
                              </div>
                            </div>

                            <div className="grid gap-4 md:grid-cols-2">
                              <div className="space-y-2">
                                <Label>Caption</Label>
                                <Input
                                  value={block.caption}
                                  onChange={(event) => {
                                    updateBlock(section.sectionKey, block.id, (current) => ({
                                      ...current,
                                      caption: event.target.value,
                                    }));
                                  }}
                                  placeholder="Optional caption"
                                />
                              </div>
                              <div className="space-y-2">
                                <Label>Alt Text</Label>
                                <Input
                                  value={block.altText}
                                  onChange={(event) => {
                                    updateBlock(section.sectionKey, block.id, (current) => ({
                                      ...current,
                                      altText: event.target.value,
                                    }));
                                  }}
                                  placeholder="Describe the media"
                                />
                              </div>
                            </div>

                            {block.isUploading ? (
                              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                                <Loader2 className="h-4 w-4 animate-spin" />
                                Media is processing. Video may take longer than images or GIFs.
                              </div>
                            ) : null}

                            {block.error ? (
                              <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                                {block.error}
                              </div>
                            ) : null}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="bg-muted/10 p-4 xl:h-full xl:overflow-hidden xl:p-6">
            {renderFullLessonPreview()}
          </div>
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
                mediaEnabled={Boolean(lessonId)}
                onUploadMedia={handleUploadQuestionMedia}
                onAttachMediaLink={handleAttachQuestionMediaLink}
                onRemoveMedia={handleRemoveQuestionMedia}
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
            <div className="rounded-lg border p-3">
              <p className="text-sm text-muted-foreground">Category</p>
              <p className="font-medium">
                {categories.find((category) => category.id === lessonForm.category_id)?.name || "Not selected"}
              </p>
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
          {/* Skip Embedding Checkbox */}
          <div className="flex items-center space-x-2">
            <input
              type="checkbox"
              id="skip-embedding"
              checked={skipEmbedding}
              onChange={(e) => setSkipEmbedding(e.target.checked)}
              className="h-4 w-4 rounded border"
            />
            <label htmlFor="skip-embedding" className="text-sm">
              Skip embedding for this lesson
            </label>
          </div>
          <Button
            onClick={() => handlePublish({ skip_embedding: skipEmbedding })}
            disabled={isPublishing}
            className="w-full"
          >
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
