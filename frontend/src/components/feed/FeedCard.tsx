import React, { useRef, useState } from 'react';
import { Heart, MessageCircle, Share2, Bookmark, Flag, ChevronLeft } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import type { Content } from '@/types';

interface FeedCardProps {
  content: Content;
  isActive?: boolean;
  onSwipeLeft?: () => void;
  onVote?: (type: 'educational') => void;
  onSave?: () => void;
  onShare?: () => void;
  onFlag?: () => void;
  onQuizClick?: () => void;
}

type FloatingHeart = {
  id: number;
  x: number;
  y: number;
  scale: number;
  rotate: number;
  driftX: number;
};

// TODO: Replace with Java backend API calls
// POST /api/content/{id}/vote - Vote on content
// POST /api/content/{id}/save - Save/bookmark content
// POST /api/content/{id}/view - Track view
// POST /api/content/{id}/flag - Flag content

export function FeedCard({
  content,
  isActive = false,
  onSwipeLeft,
  onVote,
  onSave,
  onShare,
  onFlag,
  onQuizClick,
}: FeedCardProps) {
  const [isLiked, setIsLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(content.educational_value_votes);
  const [floatingHearts, setFloatingHearts] = useState<FloatingHeart[]>([]);
  const lastTapRef = useRef(0);

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
      setFloatingHearts((prev) => prev.filter((heart) => !hearts.some((created) => created.id === heart.id)));
    }, 850);
  };

  const likeContent = () => {
    setIsLiked(true);
    setLikeCount((prev) => prev + 1);
    onVote?.('educational');
  };

  const handleLike = () => {
    if (isLiked) {
      setIsLiked(false);
      setLikeCount((prev) => Math.max(0, prev - 1));
      return;
    }

    likeContent();
  };

  const handleDoubleTapLike = (x: number, y: number) => {
    addFloatingHearts(x, y);

    if (isLiked) {
      return;
    }

    likeContent();
  };

  const handleCardDoubleClick = (event: React.MouseEvent<HTMLDivElement>) => {
    handleDoubleTapLike(event.clientX, event.clientY);
  };

  const handleCardTouchEnd = (event: React.TouchEvent<HTMLDivElement>) => {
    const now = Date.now();
    if (now - lastTapRef.current < 300) {
      const touch = event.changedTouches[0];
      if (!touch) return;
      handleDoubleTapLike(touch.clientX, touch.clientY);
    }
    lastTapRef.current = now;
  };

  const getCategoryBadgeClass = (type?: string) => {
    switch (type) {
      case 'slang': return 'badge-slang';
      case 'meme': return 'badge-meme';
      case 'dance_trend': return 'badge-dance';
      case 'social_practice': return 'badge-social';
      case 'cultural_reference': return 'badge-cultural';
      default: return 'bg-muted text-muted-foreground';
    }
  };

  return (
    <div
    className="relative w-full h-full snap-start"
    onDoubleClick={handleCardDoubleClick}
    onTouchEnd={handleCardTouchEnd}
  >
      {/* Background media */}
      <div className="absolute inset-0 bg-muted">
        {content.content_type === 'video' && content.media_url ? (
          <video
            src={content.media_url}
            className="w-full h-full object-cover"
            loop
            muted={!isActive}
            autoPlay={isActive}
            playsInline
          />
        ) : content.content_type === 'image' && content.media_url ? (
          <img
            src={content.media_url}
            alt={content.title}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center gradient-primary">
            <span className="text-6xl">🧠</span>
          </div>
        )}
      </div>

      {/* Gradient overlay */}
      <div className="absolute inset-0 gradient-feed" />

            {/* Floating hearts on double tap */}
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

      {/* Swipe left indicator */}
      <button
        onClick={onSwipeLeft}
        className="absolute right-4 top-1/2 -translate-y-1/2 flex items-center gap-2 text-white/70 hover:text-white transition-colors touch-target"
      >
        <span className="text-sm font-medium">Learn more</span>
        <ChevronLeft className="h-5 w-5 rotate-180" />
      </button>

      {/* Content info - bottom section */}
      <div className="absolute bottom-0 left-0 right-16 p-4 pb-8">
        {/* Category badge */}
        {content.category && (
          <Badge className={cn('mb-3', getCategoryBadgeClass(content.category.type))}>
            {content.category.name}
          </Badge>
        )}

        {/* Title */}
        <h2 className="text-xl font-bold text-white mb-2 line-clamp-2">
          {content.title}
        </h2>

        {/* Learning objective */}
        {content.learning_objective && (
          <div className="flex items-center gap-2 mb-3">
            <Badge variant="secondary" className="bg-white/20 text-white border-0">
              🎯 Learn: {content.learning_objective}
            </Badge>
          </div>
        )}

        {/* Description */}
        {content.description && (
          <p className="text-white/80 text-sm line-clamp-2 mb-3">
            {content.description}
          </p>
        )}

        {/* Creator info */}
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center">
            <span className="text-sm">👤</span>
          </div>
          <span className="text-white/80 text-sm font-medium">
            @{content.creator?.display_name || 'anonymous'}
          </span>
        </div>

        {/* Quick quiz button */}
        {onQuizClick && (
          <Button
            onClick={onQuizClick}
            className="mt-4 gradient-secondary border-0 text-white"
            size="sm"
          >
            🧩 Take Quick Quiz
          </Button>
        )}
      </div>

      {/* Right side actions */}
      <div className="absolute right-4 bottom-24 flex flex-col items-center gap-4">
        {/* Educational value vote */}
        <button
          onClick={handleLike}
          className="flex flex-col items-center gap-1 text-white touch-target"
        >
          <div className="w-12 h-12 rounded-full bg-white/20 backdrop-blur-sm flex items-center justify-center hover:bg-white/30 transition-colors">
          <Heart className={cn('h-6 w-6 transition-colors', isLiked && 'fill-red-500 text-red-500')} />
          </div>
          <span className="text-xs font-medium">{likeCount}</span>
        </button>

        {/* Comments - link to detail */}
        <button
          onClick={onSwipeLeft}
          className="flex flex-col items-center gap-1 text-white touch-target"
        >
          <div className="w-12 h-12 rounded-full bg-white/20 backdrop-blur-sm flex items-center justify-center hover:bg-white/30 transition-colors">
            <MessageCircle className="h-6 w-6" />
          </div>
          <span className="text-xs font-medium">Details</span>
        </button>

        {/* Save/Bookmark */}
        <button
          onClick={onSave}
          className="flex flex-col items-center gap-1 text-white touch-target"
        >
          <div className="w-12 h-12 rounded-full bg-white/20 backdrop-blur-sm flex items-center justify-center hover:bg-white/30 transition-colors">
            <Bookmark className="h-6 w-6" />
          </div>
          <span className="text-xs font-medium">Save</span>
        </button>

        {/* Share */}
        <button
          onClick={onShare}
          className="flex flex-col items-center gap-1 text-white touch-target"
        >
          <div className="w-12 h-12 rounded-full bg-white/20 backdrop-blur-sm flex items-center justify-center hover:bg-white/30 transition-colors">
            <Share2 className="h-6 w-6" />
          </div>
          <span className="text-xs font-medium">Share</span>
        </button>

        {/* Flag */}
        <button
          onClick={onFlag}
          className="flex flex-col items-center gap-1 text-white/60 hover:text-white touch-target"
        >
          <div className="w-10 h-10 rounded-full bg-white/10 backdrop-blur-sm flex items-center justify-center hover:bg-white/20 transition-colors">
            <Flag className="h-4 w-4" />
          </div>
        </button>
      </div>
    </div>
  );
}
