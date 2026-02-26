import React, { useEffect, useMemo, useRef, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import type { AdminQuizQuestionDraft } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { MatchPairsBoard } from "@/components/quiz/MatchPairsBoard";
import { ConversationBoard } from "@/components/quiz/ConversationBoard";

type Props = {
  question: AdminQuizQuestionDraft;
  onChange: (next: AdminQuizQuestionDraft) => void;
  errorMessage?: string | null;
};

type ChoiceMap = Record<string, string>;
type BlankOptions = Record<string, ChoiceMap>;
type StringMap = Record<string, string>;
type ClozeTemplatePart = { kind: "text"; value: string } | { kind: "blank"; blankId: string };

const CLOZE_TOKEN_REGEX = /\{\{([a-zA-Z0-9_-]+)\}\}/g;
const LEGACY_CLOZE_TOKEN_REGEX = /\{\{blank_(\d+)\}\}/gi;

const safeJsonParse = <T,>(value: string | null | undefined, fallback: T): T => {
  if (!value) return fallback;
  try {
    const parsed = JSON.parse(value);
    return parsed as T;
  } catch {
    return fallback;
  }
};

const normalizeChoiceMap = (value: unknown): ChoiceMap => {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return { A: "", B: "", C: "", D: "" };
  }
  const source = value as Record<string, unknown>;
  const entries = Object.entries(source)
    .map(([key, text]) => [key, String(text ?? "")] as const)
    .filter(([key]) => key.trim().length > 0);
  if (entries.length === 0) {
    return { A: "", B: "", C: "", D: "" };
  }
  return Object.fromEntries(entries);
};

const normalizeClozeChoiceMap = (value: unknown): ChoiceMap => {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return { A: "", B: "" };
  }
  const source = value as Record<string, unknown>;
  const entries = Object.entries(source)
    .map(([key, text]) => [key, String(text ?? "")] as const)
    .filter(([key]) => key.trim().length > 0);
  if (entries.length === 0) {
    return { A: "", B: "" };
  }
  return Object.fromEntries(entries);
};

const normalizeBlankId = (rawId: string) => {
  const value = String(rawId ?? "").trim();
  const match = /^blank[_\s-]?(\d+)$/i.exec(value);
  if (match) {
    return `blank${Number(match[1])}`;
  }
  return value;
};

const normalizeBlankOptions = (value: unknown): BlankOptions => {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  const source = value as Record<string, unknown>;
  const normalized: BlankOptions = {};
  Object.entries(source).forEach(([rawBlankId, choices]) => {
    const blankId = normalizeBlankId(rawBlankId);
    if (!blankId) {
      return;
    }
    const parsedChoices = normalizeClozeChoiceMap(choices);
    normalized[blankId] = normalized[blankId]
      ? { ...normalized[blankId], ...parsedChoices }
      : parsedChoices;
  });
  return Object.keys(normalized).length > 0 ? normalized : {};
};

const clozeTemplateParts = (template: string): ClozeTemplatePart[] => {
  const parts: ClozeTemplatePart[] = [];
  let cursor = 0;
  const regex = new RegExp(CLOZE_TOKEN_REGEX.source, "g");
  let match = regex.exec(template);
  while (match) {
    const tokenStart = match.index;
    const tokenEnd = regex.lastIndex;
    if (tokenStart > cursor) {
      parts.push({ kind: "text", value: template.slice(cursor, tokenStart) });
    }
    parts.push({ kind: "blank", blankId: match[1] });
    cursor = tokenEnd;
    match = regex.exec(template);
  }
  if (cursor < template.length) {
    parts.push({ kind: "text", value: template.slice(cursor) });
  }
  return parts.length > 0 ? parts : [{ kind: "text", value: template }];
};

const extractBlankIdsFromTemplate = (template: string): string[] => {
  const ids: string[] = [];
  const regex = new RegExp(CLOZE_TOKEN_REGEX.source, "g");
  let match = regex.exec(template);
  while (match) {
    ids.push(match[1]);
    match = regex.exec(template);
  }
  return ids;
};

const nextBlankId = (blankOptions: BlankOptions) => {
  const max = Object.keys(blankOptions).reduce((acc, blankId) => {
    const match = /^blank[_\s-]?(\d+)$/i.exec(blankId);
    if (!match) return acc;
    return Math.max(acc, Number(match[1]));
  }, 0);
  return `blank${max + 1}`;
};

const nextChoiceId = (choices: ChoiceMap) => {
  const existing = Object.keys(choices);
  for (let i = 0; i < 52; i += 1) {
    const candidate = i < 26 ? String.fromCharCode(65 + i) : `C${i - 25}`;
    if (!existing.includes(candidate)) {
      return candidate;
    }
  }
  return `C${existing.length + 1}`;
};

const formatBlankLabel = (blankId: string) => {
  const match = /^blank[_\s-]?(\d+)$/i.exec(blankId);
  if (match) {
    return `Blank ${match[1]}`;
  }
  return blankId.replace(/_/g, " ");
};

const normalizeLegacyBlankTokensInText = (text: string) =>
  text.replace(LEGACY_CLOZE_TOKEN_REGEX, (_match, n: string) => `{{blank${n}}}`);

const canonicalizeClozeTemplateIds = (text: string) => {
  const oldToNew = new Map<string, string>();
  const orderedBlankIds: string[] = [];
  const newToOld: Record<string, string> = {};
  let counter = 1;
  const canonicalText = text.replace(CLOZE_TOKEN_REGEX, (_full, capturedId: string) => {
    const sourceId = normalizeBlankId(capturedId);
    let mapped = oldToNew.get(sourceId);
    if (!mapped) {
      mapped = `blank${counter}`;
      counter += 1;
      oldToNew.set(sourceId, mapped);
      orderedBlankIds.push(mapped);
      newToOld[mapped] = sourceId;
    }
    return `{{${mapped}}}`;
  });
  return {
    canonicalText,
    orderedBlankIds,
    newToOld,
  };
};

const normalizeClozeAnswerMap = (value: unknown): StringMap => {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  const source = value as Record<string, unknown>;
  const normalized: StringMap = {};
  Object.entries(source).forEach(([rawBlankId, answer]) => {
    const blankId = normalizeBlankId(rawBlankId);
    if (!blankId) {
      return;
    }
    normalized[blankId] = String(answer ?? "");
  });
  return normalized;
};

const syncClozeQuestionToTemplate = (
  question: AdminQuizQuestionDraft,
  nextQuestionText: string
): AdminQuizQuestionDraft => {
  if (question.question_type !== "cloze") {
    return {
      ...question,
      question_text: nextQuestionText,
    };
  }

  const canonicalQuestionText = normalizeLegacyBlankTokensInText(nextQuestionText);
  const canonicalized = canonicalizeClozeTemplateIds(canonicalQuestionText);
  const existingBlankOptions = normalizeBlankOptions(
    (question.options as Record<string, unknown>)?.blankOptions
  );
  const answerMap = normalizeClozeAnswerMap(safeJsonParse<StringMap>(question.correct_answer, {}));
  const templateBlankIds = canonicalized.orderedBlankIds;

  const nextBlankOptions: BlankOptions = {};
  const nextAnswers: StringMap = {};

  templateBlankIds.forEach((blankId) => {
    const sourceBlankId = canonicalized.newToOld[blankId] ?? blankId;
    const baseChoices = normalizeClozeChoiceMap(
      existingBlankOptions[sourceBlankId] ?? existingBlankOptions[blankId]
    );
    const keys = Object.keys(baseChoices);
    const orderedKeys = ["A", ...keys.filter((key) => key !== "A")];
    const uniqueOrderedKeys = orderedKeys.filter((key, index) => orderedKeys.indexOf(key) === index);
    const limitedKeys = uniqueOrderedKeys.slice(0, 5);

    const limitedChoices: ChoiceMap = {};
    limitedKeys.forEach((key) => {
      limitedChoices[key] = String(baseChoices[key] ?? "");
    });
    if (!Object.prototype.hasOwnProperty.call(limitedChoices, "A")) {
      limitedChoices.A = "";
    }
    while (Object.keys(limitedChoices).length < 2) {
      const id = nextChoiceId(limitedChoices);
      limitedChoices[id] = "";
    }

    nextBlankOptions[blankId] = limitedChoices;
    const mapped = answerMap[sourceBlankId] ?? answerMap[blankId];
    nextAnswers[blankId] =
      mapped && Object.prototype.hasOwnProperty.call(limitedChoices, mapped) ? mapped : "A";
  });

  return {
    ...question,
    question_text: canonicalized.canonicalText,
    correct_answer: JSON.stringify(nextAnswers),
    options: {
      ...(question.options ?? {}),
      blankOptions: nextBlankOptions,
    },
  };
};

const autoConvertUnderscoreRunsToClozeBlanks = (
  question: AdminQuizQuestionDraft,
  nextQuestionText: string
): AdminQuizQuestionDraft => {
  if (question.question_type !== "cloze") {
    return {
      ...question,
      question_text: nextQuestionText,
    };
  }

  const normalizedInput = normalizeLegacyBlankTokensInText(nextQuestionText);
  const existingBlankOptions = normalizeBlankOptions(
    (question.options as Record<string, unknown>)?.blankOptions
  );
  const nextBlankOptions: BlankOptions = { ...existingBlankOptions };
  const converted = normalizedInput.replace(/_{2,}/g, () => {
    const blankId = nextBlankId(nextBlankOptions);
    nextBlankOptions[blankId] = { A: "", B: "" };
    return `{{${blankId}}}`;
  });

  return syncClozeQuestionToTemplate(
    {
      ...question,
      options: {
        ...(question.options ?? {}),
        blankOptions: nextBlankOptions,
      },
    },
    converted
  );
};

const normalizeTokens = (value: unknown) => {
  if (!Array.isArray(value)) {
    return [
      { id: "t1", text: "" },
      { id: "t2", text: "" },
    ];
  }
  const parsed = value
    .map((item, index) => {
      const next = item as Record<string, unknown>;
      const id = String(next?.id ?? `t${index + 1}`);
      const text = String(next?.text ?? "");
      return { id, text };
    })
    .filter((item) => item.id.trim().length > 0);
  return parsed.length > 0 ? parsed : [{ id: "t1", text: "" }];
};

const normalizeConversationTurns = (value: unknown) => {
  if (!Array.isArray(value)) {
    return [
      {
        id: "turn_1",
        prompt: "",
        replies: [
          { id: "r1", text: "" },
          { id: "r2", text: "" },
        ],
      },
    ];
  }
  const turns = value
    .map((item, idx) => {
      const next = item as Record<string, unknown>;
      const repliesRaw = Array.isArray(next?.replies) ? (next.replies as Array<Record<string, unknown>>) : [];
      const replies = repliesRaw.map((reply, replyIdx) => ({
        id: String(reply.id ?? `r${replyIdx + 1}`),
        text: String(reply.text ?? ""),
      }));
      return {
        id: String(next?.id ?? `turn_${idx + 1}`),
        prompt: String(next?.prompt ?? ""),
        replies: replies.length > 0 ? replies : [{ id: "r1", text: "" }],
      };
    })
    .filter((turn) => turn.id.trim().length > 0);
  return turns.length > 0 ? turns : [{ id: "turn_1", prompt: "", replies: [{ id: "r1", text: "" }] }];
};

const normalizePairs = (value: unknown, prefix: "l" | "r") => {
  if (!Array.isArray(value)) {
    return [
      { id: `${prefix}1`, text: "" },
      { id: `${prefix}2`, text: "" },
    ];
  }
  const parsed = value
    .map((item, idx) => {
      const next = item as Record<string, unknown>;
      return {
        id: String(next?.id ?? `${prefix}${idx + 1}`),
        text: String(next?.text ?? ""),
      };
    })
    .filter((item) => item.id.trim().length > 0);
  return parsed.length > 0 ? parsed : [{ id: `${prefix}1`, text: "" }];
};

type MatchPairRow = {
  leftText: string;
  rightText: string;
};

const normalizeMatchPairRows = (question: AdminQuizQuestionDraft): MatchPairRow[] => {
  const left = normalizePairs((question.options as Record<string, unknown>)?.left, "l");
  const right = normalizePairs((question.options as Record<string, unknown>)?.right, "r");
  const answerMap = safeJsonParse<StringMap>(question.correct_answer, {});
  const rowCount = Math.max(left.length, right.length, 2);

  const rows: MatchPairRow[] = [];
  for (let index = 0; index < rowCount; index += 1) {
    const leftItem = left[index] ?? { id: `l${index + 1}`, text: "" };
    const mappedRightId = answerMap[leftItem.id];
    const mappedRight = mappedRightId ? right.find((item) => item.id === mappedRightId) : undefined;
    const rightItem = mappedRight ?? right[index] ?? { id: `r${index + 1}`, text: "" };
    rows.push({
      leftText: String(leftItem.text ?? ""),
      rightText: String(rightItem.text ?? ""),
    });
  }
  return rows;
};

const applyMatchPairRows = (
  question: AdminQuizQuestionDraft,
  rows: MatchPairRow[]
): AdminQuizQuestionDraft => {
  const normalizedRows = rows.map((row) => ({
    leftText: String(row.leftText ?? ""),
    rightText: String(row.rightText ?? ""),
  }));

  const safeRows =
    normalizedRows.length >= 2
      ? normalizedRows
      : [
          ...normalizedRows,
          ...Array.from({ length: Math.max(0, 2 - normalizedRows.length) }, () => ({
            leftText: "",
            rightText: "",
          })),
        ];

  const left = safeRows.map((row, index) => ({ id: `l${index + 1}`, text: row.leftText }));
  const right = safeRows.map((row, index) => ({ id: `r${index + 1}`, text: row.rightText }));
  const answerMap = Object.fromEntries(left.map((item, index) => [item.id, right[index]?.id ?? `r${index + 1}`]));

  return {
    ...question,
    options: {
      ...(question.options ?? {}),
      left,
      right,
    },
    correct_answer: JSON.stringify(answerMap),
  };
};

const updateOptions = (
  question: AdminQuizQuestionDraft,
  patch: Record<string, unknown>
): AdminQuizQuestionDraft => ({
  ...question,
  options: {
    ...(question.options ?? {}),
    ...patch,
  },
});

export const AdminQuestionEditor = ({ question, onChange, errorMessage }: Props) => {
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [matchPreviewPairs, setMatchPreviewPairs] = useState<Record<string, string>>({});
  const promptInputRef = useRef<HTMLTextAreaElement | null>(null);

  const optionError = errorMessage ?? null;
  const points = Number.isFinite(question.points) ? question.points : 10;
  const livePrompt = question.question_text.trim() || "Your question prompt will appear here";
  const previewQuestionNumber =
    Number.isFinite(question.order_index) && question.order_index >= 0
      ? question.order_index + 1
      : 1;

  useEffect(() => {
    if (question.question_type !== "cloze") {
      return;
    }
    const normalized = autoConvertUnderscoreRunsToClozeBlanks(question, question.question_text ?? "");
    const changed =
      normalized.question_text !== question.question_text ||
      normalized.correct_answer !== question.correct_answer ||
      JSON.stringify(normalized.options ?? {}) !== JSON.stringify(question.options ?? {});
    if (changed) {
      onChange(normalized);
    }
  }, [question, onChange]);

  useEffect(() => {
    if (question.question_type !== "match_pairs") {
      return;
    }
    setMatchPreviewPairs({});
  }, [question.clientId, question.question_type, question.options]);

  const renderTypeEditor = () => {
    if (question.question_type === "multiple_choice") {
      const rawChoices = (question.options?.choices as Record<string, unknown> | undefined) ?? question.options;
      const choices = normalizeChoiceMap(rawChoices);
      const choiceEntries = Object.entries(choices);

      return (
        <div className="space-y-3 rounded-xl border bg-card/30 p-3">
          {choiceEntries.map(([choiceId, text]) => (
            <div
              key={choiceId}
              className={`grid grid-cols-[auto_1fr_auto] items-center gap-2 rounded-xl border-2 px-3 py-2 transition ${
                question.correct_answer === choiceId
                  ? "border-primary bg-primary/10 dark:bg-emerald-950/30"
                  : "border-border"
              }`}
            >
              <button
                type="button"
                className={`h-5 w-5 rounded-full border-2 transition ${
                  question.correct_answer === choiceId
                    ? "border-primary bg-primary"
                    : "border-muted-foreground/40"
                }`}
                aria-label={`Set ${choiceId} as correct answer`}
                onClick={() => onChange({ ...question, correct_answer: choiceId })}
              />
              <Input
                value={text}
                onChange={(event) =>
                  onChange(
                    updateOptions(question, {
                      choices: {
                        ...choices,
                        [choiceId]: event.target.value,
                      },
                    })
                  )
                }
                placeholder={`Option ${choiceId}`}
              />
              <Button
                type="button"
                size="sm"
                variant={question.correct_answer === choiceId ? "default" : "outline"}
                className={question.correct_answer === choiceId ? "bg-primary hover:bg-primary/90" : ""}
                onClick={() => onChange({ ...question, correct_answer: choiceId })}
              >
                {question.correct_answer === choiceId ? "Correct" : "Set Correct"}
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => {
              const nextId = String.fromCharCode(65 + choiceEntries.length);
              onChange(
                updateOptions(question, {
                  choices: {
                    ...choices,
                    [nextId]: "",
                  },
                })
              );
            }}
          >
            <Plus className="mr-2 h-4 w-4" />
            Add Option
          </Button>
        </div>
      );
    }

    if (question.question_type === "true_false") {
      const selected = question.correct_answer.toLowerCase();
      return (
        <div className="grid grid-cols-2 gap-3">
          {["true", "false"].map((value) => (
            <button
              key={value}
              type="button"
              className={`rounded-2xl border-2 px-4 py-4 text-left font-semibold transition ${
                selected === value
                  ? "border-primary bg-primary/10 dark:bg-emerald-950/30"
                  : "border-border hover:border-primary/50"
              }`}
              onClick={() => onChange({ ...question, correct_answer: value })}
            >
              {value === "true" ? "True" : "False"}
            </button>
          ))}
        </div>
      );
    }

    if (question.question_type === "cloze") {
      const normalizedBlankOptions = normalizeBlankOptions(
        (question.options as Record<string, unknown>)?.blankOptions
      );
      const answerMap = normalizeClozeAnswerMap(safeJsonParse<StringMap>(question.correct_answer, {}));
      const canonicalTemplate = canonicalizeClozeTemplateIds(
        normalizeLegacyBlankTokensInText(question.question_text ?? "")
      );
      const blankIds =
        canonicalTemplate.orderedBlankIds.length > 0
          ? canonicalTemplate.orderedBlankIds
          : Object.keys(normalizedBlankOptions);
      const orderedBlankIds = blankIds;

      const correctByBlank: Record<string, string> = {};
      orderedBlankIds.forEach((blankId) => {
        const choices = normalizeClozeChoiceMap(normalizedBlankOptions[blankId]);
        const keys = Object.keys(choices);
        const mapped = answerMap[blankId];
        const correctKey =
          mapped && Object.prototype.hasOwnProperty.call(choices, mapped)
            ? mapped
            : (Object.prototype.hasOwnProperty.call(choices, "A") ? "A" : (keys[0] ?? "A"));
        correctByBlank[blankId] = String(choices[correctKey] ?? "");
      });

      const firstBlankId = orderedBlankIds[0];
      const firstChoices = normalizeClozeChoiceMap(normalizedBlankOptions[firstBlankId]);
      const firstMapped = (() => {
        const mapped = answerMap[firstBlankId];
        if (mapped && Object.prototype.hasOwnProperty.call(firstChoices, mapped)) {
          return mapped;
        }
        if (Object.prototype.hasOwnProperty.call(firstChoices, "A")) {
          return "A";
        }
        return Object.keys(firstChoices)[0] ?? "A";
      })();
      const sharedOtherOptionsBase = Object.entries(firstChoices)
        .filter(([choiceId]) => choiceId !== firstMapped)
        .map(([, text]) => String(text ?? ""))
        .slice(0, 4);
      const sharedOtherOptions =
        orderedBlankIds.length === 0
          ? []
          : (sharedOtherOptionsBase.length > 0 ? sharedOtherOptionsBase : [""]);

      const applyClozeModel = (
        nextCorrectByBlank: Record<string, string>,
        nextSharedOthers: string[],
        nextQuestionText: string = question.question_text
      ) => {
        const effectiveBlankIds = Array.from(
          new Set(extractBlankIdsFromTemplate(normalizeLegacyBlankTokensInText(nextQuestionText)))
        );
        const blankIdsForModel =
          effectiveBlankIds.length > 0 ? effectiveBlankIds : Object.keys(nextCorrectByBlank);
        const limitedShared = nextSharedOthers.slice(0, 4);
        const nextBlankOptions: BlankOptions = {};
        const nextAnswerMap: StringMap = {};
        blankIdsForModel.forEach((blankId) => {
          const choices: ChoiceMap = { A: nextCorrectByBlank[blankId] ?? "" };
          limitedShared.forEach((text, idx) => {
            const choiceId = String.fromCharCode(66 + idx); // B, C, D, E
            choices[choiceId] = text;
          });
          if (Object.keys(choices).length < 2) {
            choices.B = "";
          }
          nextBlankOptions[blankId] = choices;
          nextAnswerMap[blankId] = "A";
        });
        onChange(
          syncClozeQuestionToTemplate(
            {
              ...question,
              correct_answer: JSON.stringify(nextAnswerMap),
              options: {
                ...(question.options ?? {}),
                blankOptions: nextBlankOptions,
              },
            },
            nextQuestionText
          )
        );
      };

      return (
        <div className="space-y-4">
          <div className="rounded-xl border bg-card/30 p-3 space-y-3">
            <p className="text-sm font-medium">Sentence blank placement</p>
            <p className="text-xs text-muted-foreground">
              Select text in the sentence field above, then click "Turn Selection Into Blank".
            </p>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => {
                  const sentence = question.question_text ?? "";
                  const input = promptInputRef.current;
                  const selectionStart = input?.selectionStart ?? sentence.length;
                  const selectionEnd = input?.selectionEnd ?? sentence.length;
                  const selectedText = sentence.slice(selectionStart, selectionEnd).trim();
                  const id = nextBlankId(normalizedBlankOptions);
                  const token = `{{${id}}}`;
                  const nextSentence =
                    selectionStart !== selectionEnd
                      ? `${sentence.slice(0, selectionStart)}${token}${sentence.slice(selectionEnd)}`
                      : `${sentence}${sentence.endsWith(" ") || sentence.length === 0 ? "" : " "}${token}`;
                  applyClozeModel(
                    {
                      ...correctByBlank,
                      [id]: selectedText,
                    },
                    sharedOtherOptions,
                    nextSentence
                  );
                }}
              >
                <Plus className="mr-2 h-4 w-4" />
                Turn Selection Into Blank
              </Button>
            </div>
          </div>

          {orderedBlankIds.map((blankId) => (
            <div key={blankId} className="rounded-xl border p-3 space-y-2">
              <div className="flex items-center justify-between">
                <p className="font-medium">{formatBlankLabel(blankId)}</p>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  onClick={() => {
                    const nextCorrectByBlank = { ...correctByBlank };
                    delete nextCorrectByBlank[blankId];
                    const nextText = (question.question_text ?? "").split(`{{${blankId}}}`).join("_____");
                    applyClozeModel(nextCorrectByBlank, sharedOtherOptions, nextText);
                  }}
                >
                  <Trash2 className="h-4 w-4 text-destructive" />
                </Button>
              </div>
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground">Correct Answer</Label>
                <Input
                  value={correctByBlank[blankId] ?? ""}
                  onChange={(event) =>
                    applyClozeModel(
                      {
                        ...correctByBlank,
                        [blankId]: event.target.value,
                      },
                      sharedOtherOptions
                    )
                  }
                  placeholder={`${formatBlankLabel(blankId)} correct answer`}
                />
              </div>
            </div>
          ))}
          <div className="rounded-xl border p-3 space-y-2">
            <Label className="text-xs text-muted-foreground">Other Options (Shared Across Blanks)</Label>
            {sharedOtherOptions.map((choiceText, idx) => (
              <div key={`shared-option-${idx}`} className="grid grid-cols-[1fr_auto] gap-2 items-center">
                <Input
                  value={choiceText}
                  onChange={(event) => {
                    const next = [...sharedOtherOptions];
                    next[idx] = event.target.value;
                    applyClozeModel(correctByBlank, next);
                  }}
                  placeholder={`Other option ${idx + 1}`}
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  disabled={sharedOtherOptions.length <= 1}
                  onClick={() => {
                    if (sharedOtherOptions.length <= 1) {
                      return;
                    }
                    const next = sharedOtherOptions.filter((_, optionIdx) => optionIdx !== idx);
                    applyClozeModel(correctByBlank, next);
                  }}
                >
                  <Trash2 className="h-4 w-4 text-destructive" />
                </Button>
              </div>
            ))}
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={sharedOtherOptions.length >= 4}
              onClick={() => applyClozeModel(correctByBlank, [...sharedOtherOptions, ""])}
            >
              <Plus className="mr-2 h-4 w-4" />
              Add Other Option
            </Button>
            <p className="text-xs text-muted-foreground">
              Each blank has 1 correct answer + shared other options (max 5 total options per blank).
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => {
              const id = nextBlankId(normalizedBlankOptions);
              const nextText = `${question.question_text}${question.question_text.endsWith(" ") || question.question_text.length === 0 ? "" : " "}{{${id}}}`.trim();
              applyClozeModel(
                {
                  ...correctByBlank,
                  [id]: "",
                },
                sharedOtherOptions,
                nextText
              );
            }}
          >
            <Plus className="mr-2 h-4 w-4" />
            Add Blank
          </Button>
        </div>
      );
    }

    if (question.question_type === "word_bank") {
      const tokens = normalizeTokens((question.options as Record<string, unknown>)?.tokens);
      const correctOrder = safeJsonParse<string[]>(question.correct_answer, []);
      const selected = new Set(correctOrder);

      return (
        <div className="space-y-3 rounded-xl border p-3">
          <p className="text-sm text-muted-foreground">Tap tokens to include them in the correct answer sequence.</p>
          <div className="flex flex-wrap gap-2">
            {tokens.map((token) => (
              <button
                key={token.id}
                type="button"
                className={`rounded-full border px-3 py-1 text-sm ${
                  selected.has(token.id) ? "border-primary bg-primary/10 dark:bg-rose-950/30" : "hover:border-primary/50"
                }`}
                onClick={() => {
                  const next = selected.has(token.id)
                    ? correctOrder.filter((id) => id !== token.id)
                    : [...correctOrder, token.id];
                  onChange({ ...question, correct_answer: JSON.stringify(next) });
                }}
              >
                {token.text || token.id}
              </button>
            ))}
          </div>
          <div className="space-y-2">
            {tokens.map((token, index) => (
              <Input
                key={token.id}
                value={token.text}
                placeholder={`Token ${index + 1}`}
                onChange={(event) => {
                  const next = [...tokens];
                  next[index] = { ...next[index], text: event.target.value };
                  onChange(updateOptions(question, { tokens: next }));
                }}
              />
            ))}
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => {
              onChange(
                updateOptions(question, {
                  tokens: [...tokens, { id: `t${tokens.length + 1}`, text: "" }],
                })
              );
            }}
          >
            <Plus className="mr-2 h-4 w-4" />
            Add Token
          </Button>
        </div>
      );
    }

    if (question.question_type === "conversation") {
      const turns = normalizeConversationTurns((question.options as Record<string, unknown>)?.turns);
      const answerMap = safeJsonParse<StringMap>(question.correct_answer, {});

      return (
        <div className="space-y-4">
          {turns.map((turn, turnIndex) => (
            <div key={turn.id} className="rounded-xl border p-3 space-y-3">
              <Input
                value={turn.prompt}
                placeholder={`Turn ${turnIndex + 1} prompt`}
                onChange={(event) => {
                  const next = [...turns];
                  next[turnIndex] = { ...next[turnIndex], prompt: event.target.value };
                  onChange(updateOptions(question, { turns: next }));
                }}
              />
              {turn.replies.map((reply, replyIndex) => (
                <div key={reply.id} className="grid grid-cols-[auto_1fr] gap-2 items-center">
                  <input
                    type="radio"
                    name={`conversation-${question.clientId}-${turn.id}`}
                    checked={answerMap[turn.id] === reply.id}
                    onChange={() =>
                      onChange({
                        ...question,
                        correct_answer: JSON.stringify({
                          ...answerMap,
                          [turn.id]: reply.id,
                        }),
                      })
                    }
                  />
                  <Input
                    value={reply.text}
                    placeholder={`Reply ${replyIndex + 1}`}
                    onChange={(event) => {
                      const next = [...turns];
                      const replies = [...next[turnIndex].replies];
                      replies[replyIndex] = { ...replies[replyIndex], text: event.target.value };
                      next[turnIndex] = { ...next[turnIndex], replies };
                      onChange(updateOptions(question, { turns: next }));
                    }}
                  />
                </div>
              ))}
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => {
                  const next = [...turns];
                  const replies = [...next[turnIndex].replies, { id: `r${next[turnIndex].replies.length + 1}`, text: "" }];
                  next[turnIndex] = { ...next[turnIndex], replies };
                  onChange(updateOptions(question, { turns: next }));
                }}
              >
                <Plus className="mr-2 h-4 w-4" />
                Add Reply
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => {
              onChange(
                updateOptions(question, {
                  turns: [
                    ...turns,
                    { id: `turn_${turns.length + 1}`, prompt: "", replies: [{ id: "r1", text: "" }] },
                  ],
                })
              );
            }}
          >
            <Plus className="mr-2 h-4 w-4" />
            Add Turn
          </Button>
        </div>
      );
    }

    if (question.question_type === "match_pairs") {
      const rows = normalizeMatchPairRows(question);

      return (
        <div className="space-y-3 rounded-xl border p-3">
          <p className="text-sm text-muted-foreground">
            Enter exact left-right matches. Learner view will randomize the right column.
          </p>
          {rows.map((row, index) => (
            <div key={`pair-row-${index}`} className="grid grid-cols-[1fr_1fr_auto] gap-2 items-center">
              <Input
                value={row.leftText}
                placeholder={`Left ${index + 1}`}
                onChange={(event) => {
                  const nextRows = [...rows];
                  nextRows[index] = { ...nextRows[index], leftText: event.target.value };
                  onChange(applyMatchPairRows(question, nextRows));
                }}
              />
              <Input
                value={row.rightText}
                placeholder={`Right ${index + 1}`}
                onChange={(event) => {
                  const nextRows = [...rows];
                  nextRows[index] = { ...nextRows[index], rightText: event.target.value };
                  onChange(applyMatchPairRows(question, nextRows));
                }}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                disabled={rows.length <= 2}
                onClick={() => {
                  const nextRows = rows.filter((_, rowIndex) => rowIndex !== index);
                  onChange(applyMatchPairRows(question, nextRows));
                }}
              >
                <Trash2 className="h-4 w-4 text-destructive" />
              </Button>
            </div>
          ))}
          <div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                onChange(applyMatchPairRows(question, [...rows, { leftText: "", rightText: "" }]));
              }}
            >
              <Plus className="mr-2 h-4 w-4" />
              Add Pair
            </Button>
          </div>
        </div>
      );
    }

    const shortTextOptions = question.options ?? {};
    const accepted = safeJsonParse<{ accepted?: string[] }>(question.correct_answer, {
      accepted: [],
    }).accepted ?? [];

    return (
      <div className="space-y-3 rounded-xl border p-3">
        <Textarea
          className="min-h-20"
          placeholder={String(shortTextOptions.placeholder ?? "Type your answer")}
          value=""
          disabled
        />
        <Input
          value={String(shortTextOptions.placeholder ?? "")}
          placeholder="Placeholder text"
          onChange={(event) =>
            onChange(
              updateOptions(question, {
                placeholder: event.target.value,
              })
            )
          }
        />
        <div className="grid grid-cols-2 gap-2">
          <Input
            type="number"
            value={Number(shortTextOptions.minLength ?? 1)}
            min={1}
            onChange={(event) =>
              onChange(
                updateOptions(question, {
                  minLength: Number(event.target.value || 1),
                })
              )
            }
          />
          <Input
            type="number"
            value={Number(shortTextOptions.maxLength ?? 120)}
            min={1}
            onChange={(event) =>
              onChange(
                updateOptions(question, {
                  maxLength: Number(event.target.value || 120),
                })
              )
            }
          />
        </div>
        <div className="space-y-2">
          {accepted.map((value, index) => (
            <div key={`${value}-${index}`} className="grid grid-cols-[1fr_auto] gap-2">
              <Input
                value={value}
                onChange={(event) => {
                  const next = [...accepted];
                  next[index] = event.target.value;
                  onChange({
                    ...question,
                    correct_answer: JSON.stringify({ accepted: next }),
                  });
                }}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={() => {
                  const next = accepted.filter((_, acceptedIndex) => acceptedIndex !== index);
                  onChange({
                    ...question,
                    correct_answer: JSON.stringify({ accepted: next }),
                  });
                }}
              >
                <Trash2 className="h-4 w-4 text-destructive" />
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() =>
              onChange({
                ...question,
                correct_answer: JSON.stringify({ accepted: [...accepted, ""] }),
              })
            }
          >
            <Plus className="mr-2 h-4 w-4" />
            Add Accepted Answer
          </Button>
        </div>
      </div>
    );
  };

  const renderLearnerPreview = () => {
    if (question.question_type === "multiple_choice" || question.question_type === "true_false") {
      const previewChoices: Array<[string, string]> =
        question.question_type === "true_false"
          ? [
              ["true", "True"],
              ["false", "False"],
            ]
          : Object.entries(
              normalizeChoiceMap(
                (question.options?.choices as Record<string, unknown> | undefined) ?? question.options
              )
            );
      return (
        <div className="space-y-2">
          {previewChoices.map(([choiceId, text]) => {
            const isCorrect =
              question.question_type === "true_false"
                ? question.correct_answer.toLowerCase() === String(choiceId).toLowerCase()
                : question.correct_answer === choiceId;
            return (
              <div
                key={`preview-${choiceId}`}
                className={`w-full rounded-2xl border-2 px-4 py-3 text-left ${
                  isCorrect
                    ? "border-primary bg-primary/10 dark:bg-emerald-950/30"
                    : ""
                }`}
              >
                {question.question_type === "multiple_choice" ? (
                  <span className="mr-2 font-semibold">{choiceId}.</span>
                ) : null}
                <span>{String(text).trim() || `Option ${choiceId}`}</span>
              </div>
            );
          })}
        </div>
      );
    }

    if (question.question_type === "cloze") {
      const blanks = normalizeBlankOptions((question.options as Record<string, unknown>)?.blankOptions);
      const previewOptionBank = (() => {
        const byText = new Map<string, { key: string; text: string }>();
        Object.entries(blanks).forEach(([blankId, choices]) => {
          Object.entries(choices).forEach(([choiceId, choiceText]) => {
            const text = String(choiceText ?? "").trim();
            if (!text) return;
            const normalized = text.toLowerCase();
            if (!byText.has(normalized)) {
              byText.set(normalized, {
                key: `${blankId}-${choiceId}`,
                text,
              });
            }
          });
        });
        return Array.from(byText.values());
      })();
      const canonicalPreviewText = canonicalizeClozeTemplateIds(
        normalizeLegacyBlankTokensInText(question.question_text || "Complete this sentence: {{blank1}}")
      ).canonicalText;
      const parts = clozeTemplateParts(canonicalPreviewText);
      const hasTemplateBlank = parts.some((part) => part.kind === "blank");
      return (
        <div className="space-y-3">
          <div className="rounded-xl border p-3 text-base leading-8">
            {hasTemplateBlank ? (
              parts.map((part, index) =>
                part.kind === "text" ? (
                  <span key={`preview-text-${index}`}>{part.value}</span>
                ) : (
                  <span
                    key={`preview-blank-${part.blankId}-${index}`}
                    className="mx-1 inline-flex min-w-24 items-center justify-center rounded-lg border-2 border-dashed px-3 py-1 align-middle text-sm font-semibold"
                  >
                    {formatBlankLabel(part.blankId)}
                  </span>
                )
              )
            ) : (
              <span>{question.question_text.trim() || "Type a sentence and add blank tokens."}</span>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            {previewOptionBank.map((option) => (
              <div key={`preview-${option.key}`} className="rounded-full border px-3 py-1 text-sm">
                {option.text}
              </div>
            ))}
          </div>
        </div>
      );
    }

    if (question.question_type === "conversation") {
      const turns = normalizeConversationTurns((question.options as Record<string, unknown>)?.turns);
      const answerMap = safeJsonParse<StringMap>(question.correct_answer, {});
      return (
        <ConversationBoard turns={turns} answers={answerMap} compact />
      );
    }

    if (question.question_type === "match_pairs") {
      const left = normalizePairs((question.options as Record<string, unknown>)?.left, "l");
      const right = normalizePairs((question.options as Record<string, unknown>)?.right, "r");
      return (
        <MatchPairsBoard
          leftItems={left}
          rightItems={right}
          pairs={matchPreviewPairs}
          onChange={setMatchPreviewPairs}
          compact
          seed={`preview-${question.clientId}`}
        />
      );
    }

    if (question.question_type === "word_bank") {
      const tokens = normalizeTokens((question.options as Record<string, unknown>)?.tokens);
      return (
        <div className="space-y-3">
          <div className="min-h-14 rounded-xl border border-dashed p-3 text-sm text-muted-foreground">
            Selected words preview
          </div>
          <div className="flex flex-wrap gap-2">
            {tokens.map((token, index) => (
              <div key={`preview-${token.id}`} className="rounded-full border px-3 py-1 text-sm">
                {token.text.trim() || `Token ${index + 1}`}
              </div>
            ))}
          </div>
        </div>
      );
    }

    const shortTextOptions = question.options ?? {};
    return (
      <Textarea
        className="min-h-20"
        placeholder={String(shortTextOptions.placeholder ?? "Type your answer")}
        disabled
        value=""
      />
    );
  };

  const prettyOptions = useMemo(
    () => JSON.stringify(question.options ?? {}, null, 2),
    [question.options]
  );

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label>Live Learner Preview</Label>
        <div className="rounded-2xl border bg-muted/20 p-4 space-y-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Q{previewQuestionNumber}
          </p>
          {question.question_type !== "cloze" ? (
            <p className="text-base font-semibold">{livePrompt}</p>
          ) : null}
          {renderLearnerPreview()}
        </div>
      </div>
      <div className="space-y-2">
        <Label>{question.question_type === "cloze" ? "Sentence Template" : "Question Prompt"}</Label>
        <Textarea
          ref={promptInputRef}
          value={question.question_text}
          onChange={(event) => {
            const nextValue = event.target.value;
            if (question.question_type === "cloze") {
              onChange(autoConvertUnderscoreRunsToClozeBlanks(question, nextValue));
              return;
            }
            onChange({ ...question, question_text: nextValue });
          }}
          placeholder={
            question.question_type === "cloze"
              ? "Type a sentence, then use {{blank1}} style tokens for blanks"
              : "Type the question the learner will see"
          }
          rows={3}
        />
      </div>
      <div className="space-y-2">
        <Label>Learner Preview Editor</Label>
        {renderTypeEditor()}
      </div>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <div className="space-y-2">
          <Label>Points</Label>
          <Input
            type="number"
            value={points}
            min={1}
            max={100}
            onChange={(event) =>
              onChange({
                ...question,
                points: Number(event.target.value || 10),
              })
            }
          />
        </div>
        <div className="space-y-2">
          <Label>Explanation</Label>
          <Input
            value={question.explanation}
            onChange={(event) =>
              onChange({
                ...question,
                explanation: event.target.value,
              })
            }
            placeholder="Shown to learners after they answer"
          />
        </div>
      </div>
      {optionError ? <p className="text-sm text-destructive">{optionError}</p> : null}
      <div className="rounded-xl border p-3">
        <Button
          type="button"
          variant="ghost"
          className="h-8 px-2 text-sm"
          onClick={() => setShowAdvanced((prev) => !prev)}
        >
          {showAdvanced ? "Hide Advanced JSON" : "Advanced JSON"}
        </Button>
        {showAdvanced ? (
          <div className="mt-3 space-y-3">
            <div className="space-y-2">
              <Label>Options JSON</Label>
              <Textarea
                value={prettyOptions}
                rows={8}
                onChange={(event) => {
                  try {
                    const parsed = JSON.parse(event.target.value);
                    onChange({ ...question, options: parsed });
                  } catch {
                    // Keep current value until valid JSON.
                  }
                }}
              />
            </div>
            <div className="space-y-2">
              <Label>Correct Answer Raw</Label>
              <Textarea
                value={question.correct_answer}
                rows={3}
                onChange={(event) => onChange({ ...question, correct_answer: event.target.value })}
              />
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
};
