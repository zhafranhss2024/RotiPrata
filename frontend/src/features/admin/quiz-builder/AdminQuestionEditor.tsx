import React from "react";
import { Image as ImageIcon, Link2, Loader2, Plus, Trash2, Video } from "lucide-react";
import type { AdminQuizQuestionDraft } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { MatchPairsBoard } from "@/components/quiz/MatchPairsBoard";
import { LessonMediaDisplay, inferLessonMediaKind, type LessonMediaKind } from "@/components/lesson/LessonMediaDisplay";
import { cn } from "@/lib/utils";

type Props = {
  question: AdminQuizQuestionDraft;
  onChange: (next: AdminQuizQuestionDraft) => void;
  errorMessage?: string | null;
  mediaEnabled?: boolean;
  onUploadMedia: (kind: LessonMediaKind, file: File | null) => void;
  onAttachMediaLink: (kind: LessonMediaKind) => void;
  onRemoveMedia: () => void;
};

type ChoiceMap = Record<string, string>;
type MatchPairRow = {
  leftText: string;
  rightText: string;
};

const safeJsonParse = <T,>(value: string | null | undefined, fallback: T): T => {
  if (!value) return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
};

const normalizeChoiceMap = (value: unknown): ChoiceMap => {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return { A: "", B: "", C: "", D: "" };
  }
  const entries = Object.entries(value as Record<string, unknown>)
    .map(([key, text]) => [String(key), String(text ?? "")] as const)
    .filter(([key]) => key.trim().length > 0);
  if (entries.length === 0) {
    return { A: "", B: "", C: "", D: "" };
  }
  return Object.fromEntries(entries);
};

const normalizePairs = (value: unknown, prefix: "l" | "r") => {
  if (!Array.isArray(value)) {
    return [
      { id: `${prefix}1`, text: "" },
      { id: `${prefix}2`, text: "" },
    ];
  }
  const parsed = value
    .map((item, index) => {
      const typed = item as Record<string, unknown>;
      return {
        id: String(typed.id ?? `${prefix}${index + 1}`),
        text: String(typed.text ?? ""),
      };
    })
    .filter((item) => item.id.trim().length > 0);
  return parsed.length > 0 ? parsed : [{ id: `${prefix}1`, text: "" }, { id: `${prefix}2`, text: "" }];
};

const normalizeMatchPairRows = (question: AdminQuizQuestionDraft): MatchPairRow[] => {
  const left = normalizePairs((question.options as Record<string, unknown>)?.left, "l");
  const right = normalizePairs((question.options as Record<string, unknown>)?.right, "r");
  const answerMap = safeJsonParse<Record<string, string>>(question.correct_answer, {});
  const rowCount = Math.max(left.length, right.length, 2);

  const rows: MatchPairRow[] = [];
  for (let index = 0; index < rowCount; index += 1) {
    const leftItem = left[index] ?? { id: `l${index + 1}`, text: "" };
    const mappedRight = right.find((item) => item.id === answerMap[leftItem.id]);
    const rightItem = mappedRight ?? right[index] ?? { id: `r${index + 1}`, text: "" };
    rows.push({
      leftText: String(leftItem.text ?? ""),
      rightText: String(rightItem.text ?? ""),
    });
  }
  return rows;
};

const applyMatchPairRows = (question: AdminQuizQuestionDraft, rows: MatchPairRow[]): AdminQuizQuestionDraft => {
  const safeRows =
    rows.length >= 2
      ? rows
      : [
          ...rows,
          ...Array.from({ length: Math.max(0, 2 - rows.length) }, () => ({
            leftText: "",
            rightText: "",
          })),
        ];

  const left = safeRows.map((row, index) => ({ id: `l${index + 1}`, text: row.leftText }));
  const right = safeRows.map((row, index) => ({ id: `r${index + 1}`, text: row.rightText }));
  const correctAnswer = JSON.stringify(
    Object.fromEntries(left.map((item, index) => [item.id, right[index]?.id ?? `r${index + 1}`]))
  );

  return {
    ...question,
    options: {
      ...(question.options ?? {}),
      left,
      right,
    },
    correct_answer: correctAnswer,
  };
};

const normalizeAcceptedAnswers = (question: AdminQuizQuestionDraft) => {
  const trimmed = question.correct_answer.trim();
  if (!trimmed) {
    return [""];
  }
  if (trimmed.startsWith("{")) {
    const parsed = safeJsonParse<{ accepted?: unknown[] }>(trimmed, {});
    const accepted = Array.isArray(parsed.accepted) ? parsed.accepted : [];
    const values = accepted.map((item) => String(item ?? ""));
    return values.length > 0 ? values : [""];
  }
  if (trimmed.startsWith("[")) {
    const parsed = safeJsonParse<unknown[]>(trimmed, []);
    const values = parsed.map((item) => String(item ?? ""));
    return values.length > 0 ? values : [""];
  }
  return [trimmed];
};

const serializeAcceptedAnswers = (answers: string[]) =>
  JSON.stringify({
    accepted: answers.map((answer) => answer.trim()),
  });

const mediaAcceptForKind = (kind: LessonMediaKind) => {
  if (kind === "video") {
    return "video/*";
  }
  if (kind === "gif") {
    return "image/gif";
  }
  return "image/*";
};

export const AdminQuestionEditor = ({
  question,
  onChange,
  errorMessage,
  mediaEnabled = true,
  onUploadMedia,
  onAttachMediaLink,
  onRemoveMedia,
}: Props) => {
  const points = Number.isFinite(question.points) ? question.points : 10;
  const acceptedAnswers = normalizeAcceptedAnswers(question);
  const selectedMediaKind =
    question.media_kind ?? inferLessonMediaKind(question.media_url, null) ?? "image";
  const hasMedia = Boolean(question.media_url);
  const isProcessingMedia = question.media_status === "processing";
  const hasMediaDraftState = Boolean(
    question.media_asset_id || question.media_link_url?.trim() || question.media_error
  );

  const updateOptions = (patch: Record<string, unknown>) =>
    onChange({
      ...question,
      options: {
        ...(question.options ?? {}),
        ...patch,
      },
    });

  const setMediaKind = (kind: LessonMediaKind) =>
    onChange({
      ...question,
      media_kind: kind,
      media_error: null,
    });

  const renderTypeEditor = () => {
    if (question.question_type === "multiple_choice") {
      const rawChoices = (question.options?.choices as Record<string, unknown> | undefined) ?? question.options;
      const choices = normalizeChoiceMap(rawChoices);
      const entries = Object.entries(choices);

      return (
        <div className="space-y-3 rounded-xl border bg-card/30 p-4">
          {entries.map(([choiceId, text]) => (
            <div key={choiceId} className="grid grid-cols-[auto_1fr_auto] items-center gap-2">
              <button
                type="button"
                className={`h-5 w-5 rounded-full border-2 transition ${
                  question.correct_answer === choiceId ? "border-primary bg-primary" : "border-muted-foreground/40"
                }`}
                aria-label={`Set ${choiceId} as correct answer`}
                onClick={() => onChange({ ...question, correct_answer: choiceId })}
              />
              <Input
                value={text}
                onChange={(event) =>
                  updateOptions({
                    choices: {
                      ...choices,
                      [choiceId]: event.target.value,
                    },
                  })
                }
                placeholder={`Option ${choiceId}`}
              />
              <Button
                type="button"
                size="sm"
                variant={question.correct_answer === choiceId ? "default" : "outline"}
                onClick={() => onChange({ ...question, correct_answer: choiceId })}
              >
                {question.correct_answer === choiceId ? "Correct" : "Set"}
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => {
              const nextId = String.fromCharCode(65 + entries.length);
              updateOptions({
                choices: {
                  ...choices,
                  [nextId]: "",
                },
              });
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
                selected === value ? "border-primary bg-primary/10" : "border-border hover:border-primary/50"
              }`}
              onClick={() => onChange({ ...question, correct_answer: value })}
            >
              {value === "true" ? "True" : "False"}
            </button>
          ))}
        </div>
      );
    }

    if (question.question_type === "match_pairs") {
      const rows = normalizeMatchPairRows(question);
      return (
        <div className="space-y-3 rounded-xl border p-4">
          {rows.map((row, index) => (
            <div key={`pair-row-${index}`} className="grid gap-2 md:grid-cols-[1fr_1fr_auto]">
              <Input
                value={row.leftText}
                placeholder={`Left item ${index + 1}`}
                onChange={(event) => {
                  const next = [...rows];
                  next[index] = { ...next[index], leftText: event.target.value };
                  onChange(applyMatchPairRows(question, next));
                }}
              />
              <Input
                value={row.rightText}
                placeholder={`Right item ${index + 1}`}
                onChange={(event) => {
                  const next = [...rows];
                  next[index] = { ...next[index], rightText: event.target.value };
                  onChange(applyMatchPairRows(question, next));
                }}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                disabled={rows.length <= 2}
                onClick={() => {
                  if (rows.length <= 2) return;
                  const next = rows.filter((_, rowIndex) => rowIndex !== index);
                  onChange(applyMatchPairRows(question, next));
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
            onClick={() => onChange(applyMatchPairRows(question, [...rows, { leftText: "", rightText: "" }]))}
          >
            <Plus className="mr-2 h-4 w-4" />
            Add Pair
          </Button>
        </div>
      );
    }

    return (
      <div className="space-y-3 rounded-xl border p-4">
        {acceptedAnswers.map((answer, index) => (
          <div key={`accepted-answer-${index}`} className="grid gap-2 md:grid-cols-[1fr_auto]">
            <Input
              value={answer}
              placeholder={`Accepted answer ${index + 1}`}
              onChange={(event) => {
                const next = [...acceptedAnswers];
                next[index] = event.target.value;
                onChange({
                  ...question,
                  correct_answer: serializeAcceptedAnswers(next),
                });
              }}
            />
            <Button
              type="button"
              variant="ghost"
              size="icon"
              disabled={acceptedAnswers.length <= 1}
              onClick={() => {
                if (acceptedAnswers.length <= 1) return;
                const next = acceptedAnswers.filter((_, answerIndex) => answerIndex !== index);
                onChange({
                  ...question,
                  correct_answer: serializeAcceptedAnswers(next),
                });
              }}
            >
              <Trash2 className="h-4 w-4 text-destructive" />
            </Button>
          </div>
        ))}
        <div className="grid gap-3 md:grid-cols-3">
          <div className="space-y-2 md:col-span-2">
            <Label>Placeholder</Label>
            <Input
              value={String((question.options as Record<string, unknown>)?.placeholder ?? "")}
              onChange={(event) => updateOptions({ placeholder: event.target.value })}
              placeholder="Type your answer"
            />
          </div>
          <div className="space-y-2">
            <Label>Min Length</Label>
            <Input
              type="number"
              min={1}
              value={String((question.options as Record<string, unknown>)?.minLength ?? 1)}
              onChange={(event) => updateOptions({ minLength: Number(event.target.value || 1) })}
            />
          </div>
        </div>
        <div className="space-y-2 md:max-w-[220px]">
          <Label>Max Length</Label>
          <Input
            type="number"
            min={1}
            value={String((question.options as Record<string, unknown>)?.maxLength ?? 120)}
            onChange={(event) => updateOptions({ maxLength: Number(event.target.value || 120) })}
          />
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() =>
            onChange({
              ...question,
              correct_answer: serializeAcceptedAnswers([...acceptedAnswers, ""]),
            })
          }
        >
          <Plus className="mr-2 h-4 w-4" />
          Add Accepted Answer
        </Button>
      </div>
    );
  };

  const renderMediaEditor = () => (
    <div className="space-y-4 rounded-xl border bg-card/30 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <Label>Question Media</Label>
          <p className="text-sm text-muted-foreground">
            Optional image, GIF, or video shown under the question prompt.
          </p>
        </div>
        {hasMedia || hasMediaDraftState ? (
          <Button type="button" variant="ghost" size="sm" onClick={onRemoveMedia}>
            <Trash2 className="mr-2 h-4 w-4 text-destructive" />
            Remove Media
          </Button>
        ) : null}
      </div>

      <div className="flex flex-wrap gap-2">
        {[
          { kind: "image" as const, label: "Add Image", icon: ImageIcon },
          { kind: "gif" as const, label: "Add GIF", icon: ImageIcon },
          { kind: "video" as const, label: "Add Video", icon: Video },
        ].map(({ kind, label, icon: Icon }) => (
          <Button
            key={kind}
            type="button"
            variant={selectedMediaKind === kind ? "default" : "outline"}
            size="sm"
            className={cn(selectedMediaKind === kind ? "" : "bg-background")}
            onClick={() => setMediaKind(kind)}
          >
            <Icon className="mr-2 h-4 w-4" />
            {label}
          </Button>
        ))}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-2">
          <Label className="flex items-center gap-2">
            <ImageIcon className="h-4 w-4" />
            Upload File
          </Label>
          <Input
            type="file"
            accept={mediaAcceptForKind(selectedMediaKind)}
            disabled={!mediaEnabled || isProcessingMedia}
            onChange={(event) => {
              const file = event.target.files?.[0] ?? null;
              onUploadMedia(selectedMediaKind, file);
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
              value={question.media_link_url ?? ""}
              onChange={(event) =>
                onChange({
                  ...question,
                  media_link_url: event.target.value,
                  media_kind: selectedMediaKind,
                  media_error: null,
                })
              }
              placeholder="https://..."
              disabled={!mediaEnabled || isProcessingMedia}
            />
            <Button
              type="button"
              variant="outline"
              disabled={!mediaEnabled || isProcessingMedia}
              onClick={() => onAttachMediaLink(selectedMediaKind)}
            >
              Attach
            </Button>
          </div>
        </div>
      </div>

      {!mediaEnabled ? (
        <div className="rounded-lg border border-dashed bg-muted/20 px-3 py-2 text-sm text-muted-foreground">
          Save the lesson draft first before uploading quiz media.
        </div>
      ) : null}

      {isProcessingMedia ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Media is processing. Videos may take longer than images or GIFs.
        </div>
      ) : null}

      {question.media_error ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-sm text-destructive">
          {question.media_error}
        </div>
      ) : null}

      {question.media_url ? (
        <LessonMediaDisplay
          mediaUrl={question.media_url}
          mediaKind={selectedMediaKind}
          thumbnailUrl={question.media_thumbnail_url ?? null}
          alt={question.question_text || "Question media"}
          mediaClassName="max-h-80"
        />
      ) : (
        <div className="rounded-xl border border-dashed bg-muted/20 px-4 py-8 text-sm text-muted-foreground">
          {isProcessingMedia
            ? "Processing media..."
            : "Upload a file or attach a URL to add media to this question."}
        </div>
      )}
    </div>
  );

  const renderPreview = () => {
    if (question.question_type === "multiple_choice") {
      const rawChoices = (question.options?.choices as Record<string, unknown> | undefined) ?? question.options;
      return (
        <div className="space-y-2">
          {Object.entries(normalizeChoiceMap(rawChoices)).map(([choiceId, text]) => (
            <div key={`preview-${choiceId}`} className="rounded-xl border px-3 py-2 text-sm">
              <span className="mr-2 font-semibold">{choiceId}.</span>
              <span>{text || "Option text"}</span>
            </div>
          ))}
        </div>
      );
    }

    if (question.question_type === "true_false") {
      return (
        <div className="grid grid-cols-2 gap-2">
          <div className="rounded-xl border px-3 py-3 text-center text-sm">True</div>
          <div className="rounded-xl border px-3 py-3 text-center text-sm">False</div>
        </div>
      );
    }

    if (question.question_type === "match_pairs") {
      const left = normalizePairs((question.options as Record<string, unknown>)?.left, "l");
      const right = normalizePairs((question.options as Record<string, unknown>)?.right, "r");
      const pairs = safeJsonParse<Record<string, string>>(question.correct_answer, {});
      return (
        <MatchPairsBoard
          leftItems={left}
          rightItems={right}
          pairs={pairs}
          onChange={() => undefined}
          disabled
          compact
          seed={question.clientId}
        />
      );
    }

    return (
      <div className="rounded-xl border px-4 py-3 text-sm text-muted-foreground">
        {String((question.options as Record<string, unknown>)?.placeholder ?? "Type your answer")}
      </div>
    );
  };

  return (
    <div className="space-y-4">
      <div className="grid gap-4 md:grid-cols-3">
        <div className="space-y-2 md:col-span-2">
          <Label>{question.question_type === "match_pairs" ? "Question Prompt" : "Question Prompt"}</Label>
          <Textarea
            value={question.question_text}
            onChange={(event) => onChange({ ...question, question_text: event.target.value })}
            rows={4}
            placeholder="Write the question prompt."
          />
        </div>
        <div className="space-y-2">
          <Label>Points</Label>
          <Input
            type="number"
            min={1}
            max={100}
            value={points}
            onChange={(event) => onChange({ ...question, points: Number(event.target.value || 10) })}
          />
        </div>
      </div>

      {renderMediaEditor()}

      <div className="space-y-2">
        <Label>Answer Builder</Label>
        {renderTypeEditor()}
      </div>

      <div className="space-y-2">
        <Label>Explanation</Label>
        <Textarea
          value={question.explanation}
          onChange={(event) => onChange({ ...question, explanation: event.target.value })}
          rows={3}
          placeholder="Explain why the answer is correct."
        />
      </div>

      {errorMessage ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-sm text-destructive">
          {errorMessage}
        </div>
      ) : null}

      <div className="space-y-2 rounded-xl border bg-card/30 p-4">
        <Label>Preview</Label>
        <div className="space-y-3">
          <p className="text-base font-semibold">{question.question_text || "Your question prompt will appear here"}</p>
          {question.media_url ? (
            <LessonMediaDisplay
              mediaUrl={question.media_url}
              mediaKind={selectedMediaKind}
              thumbnailUrl={question.media_thumbnail_url ?? null}
              alt={question.question_text || "Question media"}
              mediaClassName="max-h-80"
            />
          ) : null}
          {renderPreview()}
        </div>
      </div>
    </div>
  );
};
