import React, { useMemo, useState } from "react";
import { cn } from "@/lib/utils";
import type { LessonQuizMatchPairsItem } from "@/types";

type MatchPairsBoardProps = {
  leftItems: LessonQuizMatchPairsItem[];
  rightItems: LessonQuizMatchPairsItem[];
  pairs: Record<string, string>;
  onChange: (nextPairs: Record<string, string>) => void;
  disabled?: boolean;
  compact?: boolean;
  seed?: string;
  className?: string;
};

const hashString = (value: string) => {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
};

const deterministicShuffle = <T,>(items: T[], seed: string) => {
  const next = [...items];
  let state = hashString(seed) || 1;
  const random = () => {
    state ^= state << 13;
    state ^= state >> 17;
    state ^= state << 5;
    return ((state >>> 0) % 100000) / 100000;
  };

  for (let i = next.length - 1; i > 0; i -= 1) {
    const j = Math.floor(random() * (i + 1));
    [next[i], next[j]] = [next[j], next[i]];
  }
  return next;
};

const normalizePairs = (
  rawPairs: Record<string, string>,
  leftItems: LessonQuizMatchPairsItem[],
  rightItems: LessonQuizMatchPairsItem[]
) => {
  const validLeftIds = new Set(leftItems.map((item) => item.id));
  const validRightIds = new Set(rightItems.map((item) => item.id));
  const takenRights = new Set<string>();
  const normalized: Record<string, string> = {};

  Object.entries(rawPairs).forEach(([leftId, rightId]) => {
    if (!validLeftIds.has(leftId)) return;
    if (!validRightIds.has(rightId)) return;
    if (takenRights.has(rightId)) return;
    normalized[leftId] = rightId;
    takenRights.add(rightId);
  });

  return normalized;
};

export function MatchPairsBoard({
  leftItems,
  rightItems,
  pairs,
  onChange,
  disabled = false,
  compact = false,
  seed = "",
  className,
}: MatchPairsBoardProps) {
  const [selectedLeftId, setSelectedLeftId] = useState<string | null>(null);
  const [selectedRightId, setSelectedRightId] = useState<string | null>(null);
  const stablePairs = useMemo(
    () => normalizePairs(pairs, leftItems, rightItems),
    [pairs, leftItems, rightItems]
  );
  const leftByRight = useMemo(() => {
    const index: Record<string, string> = {};
    Object.entries(stablePairs).forEach(([leftId, rightId]) => {
      index[rightId] = leftId;
    });
    return index;
  }, [stablePairs]);

  const shuffledRightItems = useMemo(
    () =>
      deterministicShuffle(
        rightItems,
        `${seed}|${rightItems.map((item) => `${item.id}:${item.text}`).join("|")}`
      ),
    [rightItems, seed]
  );

  const unmatchedLeft = useMemo(
    () => leftItems.filter((item) => !stablePairs[item.id]),
    [leftItems, stablePairs]
  );
  const unmatchedRight = useMemo(
    () => shuffledRightItems.filter((item) => !leftByRight[item.id]),
    [shuffledRightItems, leftByRight]
  );

  const applyPair = (leftId: string, rightId: string) => {
    const nextPairs = { ...stablePairs };
    Object.entries(nextPairs).forEach(([existingLeft, existingRight]) => {
      if (existingLeft === leftId || existingRight === rightId) {
        delete nextPairs[existingLeft];
      }
    });
    nextPairs[leftId] = rightId;
    onChange(nextPairs);
    setSelectedLeftId(null);
    setSelectedRightId(null);
  };

  const handleSelectLeft = (leftId: string) => {
    if (disabled) return;
    if (selectedRightId) {
      applyPair(leftId, selectedRightId);
      return;
    }
    setSelectedLeftId((current) => (current === leftId ? null : leftId));
  };

  const handleSelectRight = (rightId: string) => {
    if (disabled) return;
    if (selectedLeftId) {
      applyPair(selectedLeftId, rightId);
      return;
    }
    setSelectedRightId((current) => (current === rightId ? null : rightId));
  };

  const clearPair = (leftId: string) => {
    if (disabled) return;
    const nextPairs = { ...stablePairs };
    delete nextPairs[leftId];
    onChange(nextPairs);
    setSelectedLeftId(null);
    setSelectedRightId(null);
  };

  return (
    <div className={cn("space-y-4", className)}>
      <p className={cn("text-mainAccent", compact ? "text-xs" : "text-sm")}>
        Tap one card from each side to match. Right side is randomized.
      </p>
      <div className="grid gap-3 md:grid-cols-2">
        <div className="space-y-2">
          {unmatchedLeft.length === 0 ? (
            <div
              className={cn(
                "rounded-xl border border-mainAlt border-dashed text-mainAccent",
                compact ? "px-3 py-2 text-xs" : "px-4 py-3 text-sm"
              )}
            >
              Left side complete
            </div>
          ) : (
            unmatchedLeft.map((item) => {
              const isSelected = selectedLeftId === item.id;
              return (
                <button
                  key={`left-${item.id}`}
                  type="button"
                  onClick={() => handleSelectLeft(item.id)}
                  disabled={disabled}
                  className={cn(
                    "w-full rounded-2xl border text-left text-white transition active:translate-y-[5px] active:shadow-none",
                    compact ? "px-3 py-2 text-sm" : "px-4 py-3",
                    isSelected
                      ? "border-mainAccent bg-mainAccent/20 shadow-mainCircleShadow"
                      : "border-mainAlt bg-main hover:bg-mainAlt shadow-duoGrayBorderShadow",
                    disabled && "opacity-70 cursor-not-allowed"
                  )}
                >
                  {item.text}
                </button>
              );
            })
          )}
        </div>

        <div className="space-y-2">
          {unmatchedRight.length === 0 ? (
            <div
              className={cn(
                "rounded-xl border border-mainAlt border-dashed text-mainAccent",
                compact ? "px-3 py-2 text-xs" : "px-4 py-3 text-sm"
              )}
            >
              Right side complete
            </div>
          ) : (
            unmatchedRight.map((item) => {
              const isSelected = selectedRightId === item.id;
              return (
                <button
                  key={`right-${item.id}`}
                  type="button"
                  onClick={() => handleSelectRight(item.id)}
                  disabled={disabled}
                  className={cn(
                    "w-full rounded-2xl border text-left text-white transition active:translate-y-[5px] active:shadow-none",
                    compact ? "px-3 py-2 text-sm" : "px-4 py-3",
                    isSelected
                      ? "border-mainAccent bg-mainAccent/20 shadow-mainCircleShadow"
                      : "border-mainAlt bg-main hover:bg-mainAlt shadow-duoGrayBorderShadow",
                    disabled && "opacity-70 cursor-not-allowed"
                  )}
                >
                  {item.text}
                </button>
              );
            })
          )}
        </div>
      </div>

      <div className="space-y-2">
        {Object.entries(stablePairs).length === 0 ? (
          <div
            className={cn(
              "rounded-xl border border-mainAlt border-dashed text-mainAccent",
              compact ? "px-3 py-2 text-xs" : "px-4 py-3 text-sm"
            )}
          >
            Matched pairs will appear here.
          </div>
        ) : (
          Object.entries(stablePairs).map(([leftId, rightId]) => {
            const leftText = leftItems.find((item) => item.id === leftId)?.text ?? leftId;
            const rightText = rightItems.find((item) => item.id === rightId)?.text ?? rightId;
            return (
              <button
                key={`pair-${leftId}-${rightId}`}
                type="button"
                onClick={() => clearPair(leftId)}
                disabled={disabled}
                className={cn(
                  "w-full rounded-2xl border border-[#b51f3d] bg-duoGreen text-white text-left transition",
                  compact ? "px-3 py-2 text-xs" : "px-4 py-3 text-sm",
                  disabled ? "cursor-default" : "hover:opacity-90"
                )}
                title={disabled ? undefined : "Click to unmatch"}
              >
                <span className="font-semibold">{leftText}</span>
                <span className="mx-2 text-white/80">-&gt;</span>
                <span>{rightText}</span>
              </button>
            );
          })
        )}
      </div>
    </div>
  );
}
