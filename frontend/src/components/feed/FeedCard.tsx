import React from 'react';
import { Heart, MessageCircle, Share2, Bookmark, Flag, ChevronLeft, BookOpen, ShieldOff, FilePenLine } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import type { Content, Category } from '@/types';

interface FeedCardProps {
  content: Content;
  isActive?: boolean;
  onSwipeLeft?: () => void;
  onLike?: () => void;
  likeCount?: number;
  likedByMe?: boolean;
  isLiking?: boolean;
  onSave?: () => void;
  onShare?: () => void;
  onFlag?: () => void;
  onQuizClick?: () => void;
  showEdit?: boolean;
  onEdit?: () => void;
  showTakeDown?: boolean;
  onTakeDown?: () => void;
  isTakingDown?: boolean;
}

// TODO: Replace with Java backend API calls
// POST /api/content/{id}/like - Like content
// DELETE /api/content/{id}/like - Unlike content
// POST /api/content/{id}/save - Save/bookmark content
// POST /api/content/{id}/view - Track view
// POST /api/content/{id}/flag - Flag content

export function FeedCard({
  content,
  isActive = false,
  onSwipeLeft,
  onLike,
  likeCount,
  likedByMe = false,
  isLiking = false,
  onSave,
  onShare,
  onFlag,
  onQuizClick,
  showEdit = false,
  onEdit,
  showTakeDown = false,
  onTakeDown,
  isTakingDown = false,
}: FeedCardProps) {
  const resolvedLikeCount = Number(likeCount ?? content.likes_count ?? content.educational_value_votes ?? 0);

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
    <div className="relative w-full h-full snap-start">
      {/* Background media */}
      <div className="absolute inset-0 bg-black flex items-center justify-center overflow-hidden">
        {content.content_type === 'video' && content.media_url ? (
          <video
            src={content.media_url}
            className="w-full h-full object-contain"
            loop
            muted={!isActive}
            autoPlay={isActive}
            playsInline
          />
        ) : content.content_type === 'image' && content.media_url ? (
          <img
            src={content.media_url}
            alt={content.title}
            className="w-full h-full object-contain"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center gradient-primary">
            <span className="text-6xl">🧠</span>
          </div>
        )}
      </div>

      {/* Gradient overlay */}
      <div className="absolute inset-0 gradient-feed" />

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
          <Badge className={cn("mb-3", getCategoryBadgeClass(content.category.type))}>
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
      </div>

      {/* Right side actions */}
      <div className="absolute right-4 bottom-24 flex flex-col items-center gap-4">
        {/* Admin edit */}
        {showEdit && onEdit && (
          <button
            onClick={onEdit}
            className="flex flex-col items-center gap-1 text-white touch-target"
          >
            <div className="w-12 h-12 rounded-full bg-sky-500/30 backdrop-blur-sm flex items-center justify-center hover:bg-sky-500/45 transition-colors">
              <FilePenLine className="h-6 w-6" />
            </div>
            <span className="text-xs font-medium">Edit</span>
          </button>
        )}

        {/* Admin take down */}
        {showTakeDown && onTakeDown && (
          <button
            onClick={onTakeDown}
            disabled={isTakingDown}
            className="flex flex-col items-center gap-1 text-white touch-target disabled:opacity-60 disabled:cursor-not-allowed"
          >
            <div className="w-12 h-12 rounded-full bg-rose-500/30 backdrop-blur-sm flex items-center justify-center hover:bg-rose-500/45 transition-colors">
              <ShieldOff className="h-6 w-6" />
            </div>
            <span className="text-xs font-medium">{isTakingDown ? '...' : 'Take down'}</span>
          </button>
        )}

        {/* Quick quiz */}
        {onQuizClick && (
          <button
            onClick={onQuizClick}
            className="flex flex-col items-center gap-1 text-white touch-target"
          >
            <div className="w-12 h-12 rounded-full bg-white/20 backdrop-blur-sm flex items-center justify-center hover:bg-white/30 transition-colors">
              <BookOpen className="h-6 w-6" />
            </div>
            <span className="text-xs font-medium">Quiz</span>
          </button>
        )}
        {/* Educational value vote */}
        <button
          onClick={onLike}
          disabled={isLiking}
          className="flex flex-col items-center gap-1 text-white touch-target disabled:opacity-90 disabled:cursor-default"
        >
          <div className={cn(
            "w-12 h-12 rounded-full backdrop-blur-sm flex items-center justify-center transition-colors",
            likedByMe ? "bg-red-500/35" : "bg-white/20 hover:bg-white/30"
          )}>
            <Heart className={cn("h-6 w-6", likedByMe ? "fill-red-500 text-red-500" : "")} />
          </div>
          <span className="text-xs font-medium">{resolvedLikeCount}</span>
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
