import React from "react";
import { cn } from "@/lib/utils";

type ConversationReply = {
  id: string;
  text: string;
};

type ConversationTurn = {
  id: string;
  prompt: string;
  replies: ConversationReply[];
};

type ConversationBoardProps = {
  turns: ConversationTurn[];
  answers: Record<string, string>;
  onChange?: (nextAnswers: Record<string, string>) => void;
  disabled?: boolean;
  compact?: boolean;
  className?: string;
};

export function ConversationBoard({
  turns,
  answers,
  onChange,
  disabled = false,
  compact = false,
  className,
}: ConversationBoardProps) {
  return (
    <div className={cn("space-y-4", className)}>
      {turns.map((turn, turnIndex) => (
        <div key={turn.id} className={cn("space-y-3", compact ? "space-y-2" : "space-y-3")}>
          <p className={cn("uppercase tracking-wide text-mainAccent", compact ? "text-[10px]" : "text-xs")}>
            Turn {turnIndex + 1}
          </p>
          <div
            className={cn(
              "rounded-2xl border border-mainAlt text-mainAccent dark:text-white shadow-duoGrayBorderShadow",
              compact ? "px-3 py-2 text-sm" : "px-4 py-3 text-base"
            )}
          >
            {turn.prompt.trim() || `Turn ${turnIndex + 1} prompt`}
          </div>
          <div className={cn("grid gap-2", compact ? "gap-2" : "gap-3")}>
            {turn.replies.map((reply, replyIndex) => {
              const selected = answers[turn.id] === reply.id;
              return (
                <button
                  key={`${turn.id}-${reply.id}`}
                  type="button"
                  onClick={() => {
                    if (disabled || !onChange) return;
                    onChange({
                      ...answers,
                      [turn.id]: reply.id,
                    });
                  }}
                  disabled={disabled || !onChange}
                  className={cn(
                    "w-full rounded-2xl border text-left text-mainAccent dark:text-white transition active:translate-y-[5px] active:shadow-none",
                    compact ? "px-3 py-2 text-sm" : "px-4 py-3",
                    selected
                      ? "border-mainAccent bg-mainAccent/15 shadow-mainCircleShadow"
                      : "border-mainAlt bg-main hover:bg-mainAlt shadow-duoGrayBorderShadow",
                    (disabled || !onChange) && "cursor-default"
                  )}
                >
                  {reply.text.trim() || `Reply ${replyIndex + 1}`}
                </button>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}
