import React, { useEffect, useRef, useState } from 'react';
import {
  Heart,
  MessageCircle,
  Share2,
  Bookmark,
  Flag,
  ChevronLeft,
  BookOpen,
  ShieldOff,
  FilePenLine,
  Play,
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
  const lastTapRef = useRef(0);
  const singleTapTimerRef = useRef<number | null>(null);

  const clearSingleTapTimer = () => {
    if (singleTapTimerRef.current) {
      window.clearTimeout(singleTapTimerRef.current);
      singleTapTimerRef.current = null;
    }
  };

  useEffect(() => {
    return () => {
      clearSingleTapTimer();
    };
  }, []);

  useEffect(() => {
    if (!isActive || content.content_type !== 'video') {
      setIsBuffering(false);
    }
  }, [content.content_type, isActive]);

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
    if (isLikePending) {
      return;
    }
    if (isLiked) {
      onLikeToggle?.(false);
      return;
    }
    onLikeToggle?.(true);
  };

  const handleDoubleTapLike = (x: number, y: number) => {
    addFloatingHearts(x, y);

    if (isLiked || isLikePending) {
      return;
    }
    onLikeToggle?.(true);
  };

  const scheduleSingleTapPlaybackToggle = () => {
    if (content.content_type !== 'video') {
      return;
    }
    clearSingleTapTimer();
    singleTapTimerRef.current = window.setTimeout(() => {
      onTogglePlayback?.();
    }, 220);
  };

  const handleMediaClick = (event: React.MouseEvent<HTMLDivElement>) => {
    if (event.detail !== 1) {
      return;
    }
    onMediaInteraction?.();
    scheduleSingleTapPlaybackToggle();
  };

  const handleMediaDoubleClick = (event: React.MouseEvent<HTMLDivElement>) => {
    clearSingleTapTimer();
    onMediaInteraction?.();
    handleDoubleTapLike(event.clientX, event.clientY);
  };

  const handleMediaTouchEnd = (event: React.TouchEvent<HTMLDivElement>) => {
    const touch = event.changedTouches[0];
    if (!touch) {
      return;
    }

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
    content.content_type === 'video' &&
    shouldMountMedia &&
    isActive &&
    (isPaused || isPlaybackBlocked);
  const showBufferingOverlay =
    content.content_type === 'video' &&
    shouldMountMedia &&
    isActive &&
    isBuffering &&
    !isPaused &&
    !isPlaybackBlocked;

  const renderBackgroundMedia = () => {
    const streamUrl = content.stream_url ?? content.media_url;
    if (content.content_type === 'video' && streamUrl) {
      if (shouldMountMedia) {
        return (
          <FeedVideoPlayer
            sourceUrl={streamUrl}
            showPoster={false}
            className="w-full h-full object-contain object-center"
            preload={preload}
            isActive={isActive}
            isPaused={isPaused}
            shouldAutoplay={shouldAutoplay}
            onPlaybackBlocked={onPlaybackBlocked}
            onPlaybackStarted={onPlaybackStarted}
            onBufferingChange={setIsBuffering}
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
      return <img src={content.media_url} alt={content.title} className="w-full h-full object-contain" />;
    }

    return (
      <div className="w-full h-full flex items-center justify-center gradient-primary">
        <span className="text-6xl">Brain</span>
      </div>
    );
  };

  return (
    <div className="relative w-full h-full snap-start">
      <div
        className="absolute inset-0 bg-black flex items-center justify-center overflow-hidden"
        onClick={handleMediaClick}
        onDoubleClick={handleMediaDoubleClick}
        onTouchEnd={handleMediaTouchEnd}
      >
        {renderBackgroundMedia()}
      </div>

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

      {showPausedOverlay && (
        <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
          <div className="h-16 w-16 rounded-full bg-black/45 backdrop-blur-sm shadow-xl flex items-center justify-center">
            <Play className="h-8 w-8 text-white fill-white translate-x-[1px]" />
          </div>
        </div>
      )}

      {showBufferingOverlay && (
        <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
          <div className="h-8 w-8 rounded-full border-2 border-white/25 border-t-white/90 animate-spin" />
        </div>
      )}

      <style>{`
        @keyframes feed-heart-float {
          0% {
            opacity: 0;
            transform: translate(-50%, -50%) scale(0.45);
          }
          20% {
            opacity: 1;
          }
          100% {
            opacity: 0;
            transform: translate(calc(-50% + var(--heart-drift-x, 0px)), -180%) scale(1.1);
          }
        }
      `}</style>

      <button
        onClick={onLearnMoreClick}
        className="absolute right-4 top-1/2 -translate-y-1/2 flex items-center gap-2 text-white/80 hover:text-white/80 transition-colors touch-target"
      >
        <span className="text-sm font-medium">Learn more</span>
        <ChevronLeft className="h-5 w-5 rotate-180" />
      </button>

      <div className="absolute bottom-0 left-0 right-16 p-4 pb-8 pointer-events-none">
        {content.category && (
          <Badge className="mb-3 bg-black/35 text-white/80">
            {content.category.name}
          </Badge>
        )}

        <h2 className="text-xl font-bold text-white/80 mb-2 line-clamp-2">{content.title}</h2>

        {content.learning_objective && (
          <div className="flex items-center gap-2 mb-3">
            <Badge
              variant="secondary"
              className="bg-black/35 text-white/80"
            >
              Learn: {content.learning_objective}
            </Badge>
          </div>
        )}

        {content.description && (
          <p className="text-white/80 text-sm line-clamp-2 mb-3">{content.description}</p>
        )}

        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-full bg-black/35 flex items-center justify-center overflow-hidden">
            {content.creator?.avatar_url ? (
              <img
                src={content.creator.avatar_url}
                alt={content.creator.display_name ?? 'Creator avatar'}
                className="w-full h-full object-cover"
              />
            ) : (
              <span className="text-sm text-white/80">User</span>
            )}
          </div>
          <span className="text-white/80 text-sm font-medium">
            @{content.creator?.display_name || 'anonymous'}
          </span>
        </div>
      </div>

      <div className="absolute right-4 bottom-24 flex flex-col items-center gap-4">
        {showEdit && onEdit && (
          <button onClick={onEdit} className="flex flex-col items-center gap-1 text-white/80 hover:text-white/80 touch-target">
            <div className="w-12 h-12 rounded-full bg-black/35 backdrop-blur-sm flex items-center justify-center transition-colors">
              <FilePenLine className="h-6 w-6" />
            </div>
            <span className="text-xs font-medium">Edit</span>
          </button>
        )}

        {showTakeDown && onTakeDown && (
          <button
            onClick={onTakeDown}
            disabled={isTakingDown}
            className="flex flex-col items-center gap-1 text-white/80 hover:text-white/80 touch-target disabled:opacity-60 disabled:cursor-not-allowed"
          >
            <div className="w-12 h-12 rounded-full bg-black/35 backdrop-blur-sm flex items-center justify-center transition-colors">
              <ShieldOff className="h-6 w-6" />
            </div>
            <span className="text-xs font-medium">{isTakingDown ? '...' : 'Take down'}</span>
          </button>
        )}

        {onQuizClick && (
          <button
            onClick={onQuizClick}
            className="flex flex-col items-center gap-1 text-white/80 hover:text-white/80 touch-target"
          >
            <div className="w-12 h-12 rounded-full bg-black/35 backdrop-blur-sm flex items-center justify-center transition-colors">
              <BookOpen className="h-6 w-6" />
            </div>
            <span className="text-xs font-medium">Quiz</span>
          </button>
        )}

        <button
          onClick={handleLike}
          disabled={isLikePending}
          className="flex flex-col items-center gap-1 text-white/80 hover:text-white/80 touch-target disabled:opacity-60 disabled:cursor-not-allowed"
        >
          <div
            className={cn(
              'w-12 h-12 rounded-full backdrop-blur-sm flex items-center justify-center transition-colors',
              isLiked ? 'bg-red-500/20' : 'bg-black/35'
            )}
          >
            <Heart
              className={cn(
                'h-6 w-6 transition-colors',
                isLiked ? 'fill-red-500 text-red-500' : 'text-white/80'
              )}
            />
          </div>
          <span className="text-xs font-medium">{likeCount}</span>
        </button>

        <button
          onClick={onCommentClick}
          className="flex flex-col items-center gap-1 text-white/80 hover:text-white/80 touch-target"
        >
          <div className="w-12 h-12 rounded-full bg-black/35 backdrop-blur-sm flex items-center justify-center transition-colors">
            <MessageCircle className="h-6 w-6" />
          </div>
          <span className="text-xs font-medium">{commentCount}</span>
        </button>

        <button
          onClick={onSave}
          disabled={isSavePending}
          className="flex flex-col items-center gap-1 text-white/80 hover:text-white/80 touch-target disabled:opacity-60 disabled:cursor-not-allowed"
        >
          <div
            className={cn(
              'w-12 h-12 rounded-full backdrop-blur-sm flex items-center justify-center transition-colors',
              isSaved ? 'bg-amber-400/20' : 'bg-black/35'
            )}
          >
            <Bookmark
              className={cn(
                'h-6 w-6 transition-colors',
                isSaved ? 'fill-amber-400 text-amber-400' : 'text-white/80'
              )}
            />
          </div>
          <span className="text-xs font-medium">{isSaved ? 'Saved' : 'Save'}</span>
        </button>

        <button onClick={onShare} className="flex flex-col items-center gap-1 text-white/80 hover:text-white/80 touch-target">
          <div className="w-12 h-12 rounded-full bg-black/35 backdrop-blur-sm flex items-center justify-center transition-colors">
            <Share2 className="h-6 w-6" />
          </div>
          <span className="text-xs font-medium">Share</span>
        </button>

        <button
          onClick={onFlag}
          className="flex flex-col items-center gap-1 text-white/80 hover:text-white/80 touch-target"
        >
          <div className="w-10 h-10 rounded-full bg-black/35 backdrop-blur-sm flex items-center justify-center transition-colors">
            <Flag className="h-4 w-4" />
          </div>
        </button>
      </div>
    </div>
  );
}
