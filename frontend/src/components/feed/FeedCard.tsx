import React, { useEffect, useRef, useState } from 'react';
import {
  Heart,
  MessageCircle,
  Flag,
  ChevronLeft,
  BookOpen,
  ShieldOff,
  FilePenLine,
  Play,
  MoreHorizontal,
  X,
  Bookmark,
  Share2,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import type { Content } from '@/types';
import { FeedVideoPlayer, type FeedVideoPlaybackMetrics, type FeedVideoPreload } from './FeedVideoPlayer';

interface FeedCardProps {
  content: Content;
  isActive?: boolean;
  shouldMountMedia?: boolean;
  preload?: FeedVideoPreload;
  shouldAutoplay?: boolean;
  isPaused?: boolean;
  isPlaybackBlocked?: boolean;
  isSaved?: boolean;
  isLikePending?: boolean;
  isSavePending?: boolean;
  isLiked?: boolean;
  likeCount?: number;
  commentCount: number;
  onLearnMoreClick?: () => void;
  onCommentClick?: () => void;
  onLikeToggle?: (nextLiked: boolean) => void;
  onSave?: () => void;
  onShare?: () => void;
  onFlag?: () => void;
  onQuizClick?: () => void;
  onTogglePlayback?: () => void;
  onPlaybackBlocked?: () => void;
  onPlaybackStarted?: () => void;
  onPlaybackMetrics?: (metrics: FeedVideoPlaybackMetrics) => void;
  onMediaInteraction?: () => void;
  showEdit?: boolean;
  onEdit?: () => void;
  showTakeDown?: boolean;
  onTakeDown?: () => void;
  isTakingDown?: boolean;
}

type FloatingHeart = {
  id: number;
  x: number;
  y: number;
  scale: number;
  rotate: number;
  driftX: number;
};

const TAP_MOVEMENT_SLOP = 12;
const LEARN_MORE_SWIPE_THRESHOLD = 72;
const LEARN_MORE_MAX_VERTICAL_DRIFT = 48;

const isInteractiveGestureTarget = (target: EventTarget | null) =>
  target instanceof Element &&
  Boolean(target.closest('button, a, input, textarea, select, [role="button"], [data-feed-gesture-exempt="true"]'));

export function FeedCard({
  content,
  isActive = false,
  shouldMountMedia = true,
  preload = 'metadata',
  shouldAutoplay = true,
  isPaused = false,
  isPlaybackBlocked = false,
  isSaved = false,
  isLikePending = false,
  isSavePending = false,
  isLiked = false,
  likeCount = 0,
  commentCount,
  onLearnMoreClick,
  onCommentClick,
  onLikeToggle,
  onSave,
  onShare,
  onFlag,
  onQuizClick,
  onTogglePlayback,
  onPlaybackBlocked,
  onPlaybackStarted,
  onPlaybackMetrics,
  onMediaInteraction,
  showEdit = false,
  onEdit,
  showTakeDown = false,
  onTakeDown,
  isTakingDown = false,
}: FeedCardProps) {
  const [floatingHearts, setFloatingHearts] = useState<FloatingHeart[]>([]);
  const [isBuffering, setIsBuffering] = useState(false);
  const [isMoreMenuOpen, setIsMoreMenuOpen] = useState(false);
  const lastTapRef = useRef(0);
  const singleTapTimerRef = useRef<number | null>(null);
  const touchStartRef = useRef<{ x: number; y: number } | null>(null);

  const clearSingleTapTimer = () => {
    if (singleTapTimerRef.current) {
      window.clearTimeout(singleTapTimerRef.current);
      singleTapTimerRef.current = null;
    }
  };

  useEffect(() => {
    return () => { clearSingleTapTimer(); };
  }, []);

  useEffect(() => {
    if (!isActive || content.content_type !== 'video') setIsBuffering(false);
  }, [content.content_type, isActive]);

  useEffect(() => {
    if (!isActive) setIsMoreMenuOpen(false);
  }, [isActive]);
  
  const addFloatingHearts = (x: number, y: number) => {
    const hearts = Array.from({ length: 6 }, (_, index) => ({
      id: Date.now() + index,
      x,
      y,
      scale: 0.85 + Math.random() * 0.7,
      rotate: -25 + Math.random() * 50,
      driftX: -42 + Math.random() * 84,
    }));
    setFloatingHearts((prev) => [...prev, ...hearts]);
    window.setTimeout(() => {
      setFloatingHearts((prev) =>
        prev.filter((heart) => !hearts.some((created) => created.id === heart.id))
      );
    }, 850);
  };

  const handleLike = () => {
    if (isLikePending) return;
    onLikeToggle?.(!isLiked);
  };

  const handleDoubleTapLike = (x: number, y: number) => {
    addFloatingHearts(x, y);
    if (isLiked || isLikePending) return;
    onLikeToggle?.(true);
  };

  const scheduleSingleTapPlaybackToggle = () => {
    if (content.content_type !== 'video') return;
    clearSingleTapTimer();
    singleTapTimerRef.current = window.setTimeout(() => {
      onTogglePlayback?.();
    }, 220);
  };

  const handleMediaClick = (event: React.MouseEvent<HTMLDivElement>) => {
    if (event.detail !== 1) return;
    if (isMoreMenuOpen) { setIsMoreMenuOpen(false); return; }
    onMediaInteraction?.();
    scheduleSingleTapPlaybackToggle();
  };

  const handleMediaDoubleClick = (event: React.MouseEvent<HTMLDivElement>) => {
    clearSingleTapTimer();
    onMediaInteraction?.();
    handleDoubleTapLike(event.clientX, event.clientY);
  };

  const handleCardTouchStart = (event: React.TouchEvent<HTMLDivElement>) => {
    if (isInteractiveGestureTarget(event.target)) { touchStartRef.current = null; return; }
    const touch = event.touches[0];
    if (!touch) { touchStartRef.current = null; return; }
    touchStartRef.current = { x: touch.clientX, y: touch.clientY };
  };

  const handleCardTouchEnd = (event: React.TouchEvent<HTMLDivElement>) => {
    const touch = event.changedTouches[0];
    if (!touch) return;

    const touchStart = touchStartRef.current;
    touchStartRef.current = null;

    if (touchStart) {
      const deltaX = touch.clientX - touchStart.x;
      const deltaY = touch.clientY - touchStart.y;
      const absDeltaX = Math.abs(deltaX);
      const absDeltaY = Math.abs(deltaY);

      if (
        deltaX <= -LEARN_MORE_SWIPE_THRESHOLD &&
        absDeltaY <= LEARN_MORE_MAX_VERTICAL_DRIFT &&
        absDeltaX > absDeltaY
      ) {
        clearSingleTapTimer();
        onMediaInteraction?.();
        onLearnMoreClick?.();
        return;
      }

      if (absDeltaX > TAP_MOVEMENT_SLOP || absDeltaY > TAP_MOVEMENT_SLOP) {
        clearSingleTapTimer();
        return;
      }
    }

    if (isMoreMenuOpen) { setIsMoreMenuOpen(false); return; }

    onMediaInteraction?.();
    const now = Date.now();
    if (now - lastTapRef.current < 300) {
      clearSingleTapTimer();
      handleDoubleTapLike(touch.clientX, touch.clientY);
    } else {
      scheduleSingleTapPlaybackToggle();
    }
    lastTapRef.current = now;
  };

  const showPausedOverlay =
    content.content_type === 'video' && shouldMountMedia && isActive && (isPaused || isPlaybackBlocked);
  const showBufferingOverlay =
    content.content_type === 'video' && shouldMountMedia && isActive && isBuffering && !isPaused && !isPlaybackBlocked;

  const renderBackgroundMedia = () => {
    const streamUrl = content.stream_url ?? content.media_url;
    if (content.content_type === 'video' && streamUrl) {
      if (shouldMountMedia) {
        return (
          <FeedVideoPlayer
            sourceUrl={streamUrl}
            showPoster={false}
            className="h-full w-full object-cover object-[center_28%] md:object-contain md:object-top"
            preload={preload}
            isActive={isActive}
            isPaused={isPaused}
            shouldAutoplay={shouldAutoplay}
            onPlaybackBlocked={onPlaybackBlocked}
            onPlaybackStarted={onPlaybackStarted}
            onBufferingChange={(nextIsBuffering) => setIsBuffering(nextIsBuffering)}
            onPlaybackMetrics={onPlaybackMetrics}
          />
        );
      }
      return (
        <div className="w-full h-full bg-black relative overflow-hidden">
          <div className="absolute inset-0 animate-pulse bg-gradient-to-b from-white/5 via-transparent to-white/5" />
        </div>
      );
    }
    if (content.content_type === 'image' && content.media_url) {
      return (
        <img
          src={content.media_url}
          alt={content.title}
          className="w-full h-full object-cover object-[center_28%] md:object-contain"
        />
      );
    }
    return (
      <div className="w-full h-full flex items-center justify-center gradient-primary">
        <span className="text-6xl">Brain</span>
      </div>
    );
  };

  const hasMoreItems =
    onLearnMoreClick ||
    (showEdit && onEdit) ||
    (showTakeDown && onTakeDown) ||
    onQuizClick ||
    onSave ||
    onShare;

  return (
    <div
      className="relative w-full h-full snap-start"
      onTouchStart={handleCardTouchStart}
      onTouchEnd={handleCardTouchEnd}
      onTouchCancel={() => { touchStartRef.current = null; }}
    >
      <style>{`
        @keyframes feed-heart-float {
          0%   { opacity: 0; transform: translate(-50%, -50%) scale(0.45); }
          20%  { opacity: 1; }
          100% { opacity: 0; transform: translate(calc(-50% + var(--heart-drift-x, 0px)), -180%) scale(1.1); }
        }
        @keyframes sheet-slide-up {
          from { transform: translateY(100%); }
          to   { transform: translateY(0); }
        }
        @keyframes sheet-backdrop-in {
          from { opacity: 0; }
          to   { opacity: 1; }
        }
        .bottom-sheet {
          animation: sheet-slide-up 260ms cubic-bezier(0.32, 0.72, 0, 1) forwards;
        }
        .bottom-sheet-backdrop {
          animation: sheet-backdrop-in 220ms ease forwards;
        }
      `}</style>

      {/* Background media */}
      <div
        className="absolute inset-0 bg-black flex items-center justify-center overflow-hidden"
        onClick={handleMediaClick}
        onDoubleClick={handleMediaDoubleClick}
      >
        {renderBackgroundMedia()}
      </div>

      {/* Floating hearts */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden">
        {floatingHearts.map((heart) => (
          <Heart
            key={heart.id}
            className="absolute h-8 w-8 text-red-500 fill-red-500"
            style={{
              left: heart.x,
              top: heart.y,
              opacity: 0,
              animation: 'feed-heart-float 850ms ease-out forwards',
              transform: `translate(-50%, -50%) scale(${heart.scale}) rotate(${heart.rotate}deg)`,
              ['--heart-drift-x' as string]: `${heart.driftX}px`,
            }}
          />
        ))}
      </div>

      {/* Paused overlay */}
      {showPausedOverlay && (
        <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
          <div className="h-16 w-16 rounded-full bg-black/45 backdrop-blur-sm shadow-xl flex items-center justify-center">
            <Play className="h-8 w-8 text-white fill-white translate-x-[1px]" />
          </div>
        </div>
      )}

      {/* Buffering overlay */}
      {showBufferingOverlay && (
        <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
          <div className="h-8 w-8 rounded-full border-2 border-white/25 border-t-white/90 animate-spin" />
        </div>
      )}

      {/* Bottom-left content info */}
      <div className="absolute bottom-0 left-0 right-16 p-4 pb-8 pointer-events-none">
        {content.category && (
          <Badge className="mb-3 bg-black/35 text-white/80">{content.category.name}</Badge>
        )}
        <h2 className="text-xl font-bold text-white/80 mb-2 line-clamp-2">{content.title}</h2>
        {content.description && (
          <p className="text-white/80 text-sm line-clamp-2 mb-3">{content.description}</p>
        )}
        <div className="flex items-center">
          <span className="text-white/80 text-sm font-medium">
            @{content.creator?.display_name || 'anonymous'}
          </span>
        </div>
      </div>

      {/* Right-side action buttons — always just 4 items max, never overflows */}
      <div className="absolute right-3 bottom-6 flex flex-col items-center gap-3">

        {/* More */}
        {hasMoreItems && (
          <button
            onClick={() => setIsMoreMenuOpen(true)}
            data-feed-gesture-exempt="true"
            aria-label="More options"
            className="flex flex-col items-center gap-0.5 text-white/80 hover:text-white touch-target"
          >
            <div className="w-11 h-11 rounded-full bg-black/35 backdrop-blur-sm flex items-center justify-center transition-colors">
              <MoreHorizontal className="h-5 w-5" />
            </div>
            <span className="text-[10px] font-medium">More</span>
          </button>
        )}

        {/* Like */}
        <button
          onClick={handleLike}
          disabled={isLikePending}
          className="flex flex-col items-center gap-0.5 text-white/80 hover:text-white touch-target disabled:opacity-60 disabled:cursor-not-allowed"
        >
          <div className={cn(
            'w-11 h-11 rounded-full backdrop-blur-sm flex items-center justify-center transition-colors',
            isLiked ? 'bg-red-500/20' : 'bg-black/35'
          )}>
            <Heart className={cn('h-6 w-6 transition-colors', isLiked ? 'fill-red-500 text-red-500' : '')} />
          </div>
          <span className="text-[10px] font-medium">{likeCount}</span>
        </button>

        {/* Comment */}
        <button
          onClick={onCommentClick}
          className="flex flex-col items-center gap-0.5 text-white/80 hover:text-white touch-target"
        >
          <div className="w-11 h-11 rounded-full bg-black/35 backdrop-blur-sm flex items-center justify-center transition-colors">
            <MessageCircle className="h-6 w-6" />
          </div>
          <span className="text-[10px] font-medium">{commentCount}</span>
        </button>

        {/* Flag */}
        <button
          onClick={onFlag}
          className="flex flex-col items-center gap-0.5 text-white/80 hover:text-white touch-target"
        >
          <div className="w-10 h-10 rounded-full bg-black/35 backdrop-blur-sm flex items-center justify-center transition-colors">
            <Flag className="h-4 w-4" />
          </div>
        </button>
      </div>

      {/* ── Bottom sheet More menu ── */}
      {isMoreMenuOpen && (
        <div
          className="absolute inset-0 z-50 flex flex-col justify-end"
          data-feed-gesture-exempt="true"
        >
          {/* Backdrop */}
          <div
            className="bottom-sheet-backdrop absolute inset-0 bg-black/50"
            onClick={() => setIsMoreMenuOpen(false)}
          />

          {/* Sheet */}
          <div className="bottom-sheet relative bg-[#1c1c1e] rounded-t-2xl overflow-hidden">
            {/* Drag handle */}
            <div className="flex justify-center pt-3 pb-1">
              <div className="w-9 h-1 rounded-full bg-white/25" />
            </div>

            {/* Header */}
            <div className="flex items-center justify-between px-5 py-3">
              <span className="text-xs font-semibold tracking-widest text-white/40 uppercase">Actions</span>
              <button
                onClick={() => setIsMoreMenuOpen(false)}
                className="w-7 h-7 rounded-full bg-white/10 flex items-center justify-center text-white/60 hover:text-white transition-colors"
                aria-label="Close"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {/* Menu items */}
            <div className="flex flex-col pb-2">

              {onLearnMoreClick && (
                <button
                  onClick={() => { setIsMoreMenuOpen(false); onLearnMoreClick(); }}
                  className="flex items-center gap-4 px-5 py-3.5 text-white hover:bg-white/5 transition-colors active:bg-white/10"
                >
                  <ChevronLeft className="h-5 w-5 rotate-180 text-white/70 shrink-0" />
                  <span className="text-[15px] font-medium">Learn more</span>
                </button>
              )}

              {onQuizClick && (
                <button
                  onClick={() => { setIsMoreMenuOpen(false); onQuizClick(); }}
                  className="flex items-center gap-4 px-5 py-3.5 text-white hover:bg-white/5 transition-colors active:bg-white/10"
                >
                  <BookOpen className="h-5 w-5 text-white/70 shrink-0" />
                  <span className="text-[15px] font-medium">Quiz</span>
                </button>
              )}

              {onSave && (
                <button
                  onClick={() => { setIsMoreMenuOpen(false); onSave(); }}
                  disabled={isSavePending}
                  className="flex items-center gap-4 px-5 py-3.5 text-white hover:bg-white/5 transition-colors active:bg-white/10 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  <Bookmark className={cn('h-5 w-5 shrink-0 transition-colors', isSaved ? 'fill-amber-400 text-amber-400' : 'text-white/70')} />
                  <span className={cn('text-[15px] font-medium', isSaved ? 'text-amber-400' : '')}>
                    {isSaved ? 'Saved' : 'Save'}
                  </span>
                </button>
              )}

              {onShare && (
                <button
                  onClick={() => { setIsMoreMenuOpen(false); onShare(); }}
                  className="flex items-center gap-4 px-5 py-3.5 text-white hover:bg-white/5 transition-colors active:bg-white/10"
                >
                  <Share2 className="h-5 w-5 text-white/70 shrink-0" />
                  <span className="text-[15px] font-medium">Share</span>
                </button>
              )}

              {showEdit && onEdit && (
                <button
                  onClick={() => { setIsMoreMenuOpen(false); onEdit(); }}
                  className="flex items-center gap-4 px-5 py-3.5 text-white hover:bg-white/5 transition-colors active:bg-white/10"
                >
                  <FilePenLine className="h-5 w-5 text-white/70 shrink-0" />
                  <span className="text-[15px] font-medium">Edit</span>
                </button>
              )}

              {showTakeDown && onTakeDown && (
                <>
                  {/* Separator before destructive action */}
                  <div className="mx-5 my-1 h-px bg-white/10" />
                  <button
                    onClick={() => { if (!isTakingDown) { setIsMoreMenuOpen(false); onTakeDown(); } }}
                    disabled={isTakingDown}
                    className="flex items-center gap-4 px-5 py-3.5 hover:bg-white/5 transition-colors active:bg-white/10 disabled:opacity-60 disabled:cursor-not-allowed"
                  >
                    <ShieldOff className="h-5 w-5 text-red-400 shrink-0" />
                    <span className="text-[15px] font-medium text-red-400">
                      {isTakingDown ? 'Taking down…' : 'Take down'}
                    </span>
                  </button>
                </>
              )}
            </div>

            {/* Safe area spacer */}
            <div className="h-safe-bottom pb-6" />
          </div>
        </div>
      )}
    </div>
  );
}