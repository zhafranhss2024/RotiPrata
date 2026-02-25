import React, { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { FeedCard } from './FeedCard';
import { ContentDetailSheet } from './ContentDetailSheet';
import { QuizSheet } from '../quiz/QuizSheet';
import type { Content, Quiz } from '@/types';
import { fetchContentQuiz, flagContent, likeContent, rejectContent, saveContent, trackContentView, unlikeContent, unsaveContent } from '@/lib/api';
import { useAuthContext } from '@/contexts/AuthContext';
import { useLocation, useNavigate } from 'react-router-dom';
import { getSaveHistory, recordLikeActivity, recordSaveActivity, recordWatchActivity, removeSaveActivity } from '@/lib/activityHistory';
import { useToast } from '@/hooks/use-toast';

interface FeedContainerProps {
  contents: Content[];
  onLoadMore?: () => void;
  hasMore?: boolean;
  isLoading?: boolean;
  initialIndex?: number;
  containerClassName?: string;
}

type ContentLikeState = {
  likesCount: number;
  likedByMe: boolean;
  isLiking: boolean;
};

// Backend: /api/content/{id} actions.
// Dummy behavior is used when mocks are enabled.

export function FeedContainer({
  contents,
  onLoadMore,
  hasMore = false,
  isLoading = false,
  initialIndex = 0,
  containerClassName,
}: FeedContainerProps) {
  const { isAdmin } = useAuthContext();
  const { toast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const containerHeightClass =
    "h-[calc(100vh-var(--bottom-nav-height)-var(--safe-area-bottom))] md:h-[calc(100vh-4rem)]";
  const [hiddenContentIds, setHiddenContentIds] = useState<Set<string>>(() => new Set());
  const [takingDownContentId, setTakingDownContentId] = useState<string | null>(null);
  const [activeIndex, setActiveIndex] = useState(initialIndex);
  const [selectedContent, setSelectedContent] = useState<Content | null>(null);
  const [showDetail, setShowDetail] = useState(false);
  const [showQuiz, setShowQuiz] = useState(false);
  const [activeQuiz, setActiveQuiz] = useState<Quiz | null>(null);
  const [contentLikeState, setContentLikeState] = useState<Record<string, ContentLikeState>>({});
  const [savedContentIds, setSavedContentIds] = useState<Set<string>>(() => new Set());
  const containerRef = useRef<HTMLDivElement>(null);
  const isAdminUser = isAdmin();

  const resolveLikesCount = useCallback(
    (content: Content) => Number(content.likes_count ?? content.educational_value_votes ?? 0),
    []
  );

  const resolveLikedByMe = useCallback((content: Content) => {
    return content.liked_by_me === true || content.liked_by_me === 'true';
  }, []);

  const getLikeState = useCallback(
    (content: Content): ContentLikeState => {
      const existing = contentLikeState[content.id];
      if (existing) return existing;
      return {
        likesCount: resolveLikesCount(content),
        likedByMe: resolveLikedByMe(content),
        isLiking: false,
      };
    },
    [contentLikeState, resolveLikedByMe, resolveLikesCount]
  );

  const visibleContents = useMemo(
    () => contents.filter((content) => !hiddenContentIds.has(content.id)),
    [contents, hiddenContentIds]
  );

  useEffect(() => {
    const historySavedIds = getSaveHistory().map((item) => item.itemId);
    const next = new Set(historySavedIds);
    contents.forEach((content) => {
      if ((content as Content & { saved_by_me?: boolean }).saved_by_me === true) {
        next.add(content.id);
      }
    });
    setSavedContentIds(next);
  }, [contents]);

  useEffect(() => {
    const clamped = Math.max(0, Math.min(initialIndex, Math.max(visibleContents.length - 1, 0)));
    setActiveIndex(clamped);
    const container = containerRef.current;
    if (!container) return;
    requestAnimationFrame(() => {
      container.scrollTop = clamped * container.clientHeight;
    });
  }, [initialIndex, visibleContents.length]);

  // Track active content on scroll
  const handleScroll = useCallback(() => {
    if (!containerRef.current) return;
    
    const container = containerRef.current;
    const scrollTop = container.scrollTop;
    const itemHeight = container.clientHeight;
    const newIndex = Math.round(scrollTop / itemHeight);
    
    if (newIndex !== activeIndex) {
      setActiveIndex(newIndex);
      
      // Fire-and-forget view tracking. Safe to ignore errors.
      const contentId = visibleContents[newIndex]?.id;
      if (contentId) {
        trackContentView(contentId).catch((error) => {
          console.warn('Failed to track view', error);
        });
        recordWatchActivity(contentId, visibleContents[newIndex]?.title || 'Untitled');
      }
    }

    // Load more when near bottom
    if (hasMore && !isLoading && scrollTop + itemHeight * 2 >= container.scrollHeight) {
      onLoadMore?.();
    }
  }, [activeIndex, hasMore, isLoading, onLoadMore, visibleContents]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    
    container.addEventListener('scroll', handleScroll);
    return () => container.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  const handleSwipeLeft = (content: Content) => {
    setSelectedContent(content);
    setShowDetail(true);
  };

  const handleQuizClick = (content: Content) => {
    fetchContentQuiz(content.id)
      .then((quiz) => {
        setActiveQuiz(quiz);
      })
      .catch((error) => {
        console.warn('Failed to load quiz', error);
      })
      .finally(() => {
        setSelectedContent(content);
        setShowQuiz(true);
      });
  };

  const handleLike = async (content: Content) => {
    const likeState = getLikeState(content);
    if (likeState.isLiking) {
      return;
    }

    const optimisticState: ContentLikeState = {
      likesCount: likeState.likedByMe
        ? Math.max(0, likeState.likesCount - 1)
        : likeState.likesCount + 1,
      likedByMe: !likeState.likedByMe,
      isLiking: true,
    };
    setContentLikeState((prev) => ({ ...prev, [content.id]: optimisticState }));
    if (selectedContent?.id === content.id) {
      setSelectedContent((prev) =>
        prev
          ? {
              ...prev,
              likes_count: optimisticState.likesCount,
              liked_by_me: optimisticState.likedByMe,
            }
          : prev
      );
    }

    try {
      const response = likeState.likedByMe
        ? await unlikeContent(content.id)
        : await likeContent(content.id);
      const finalState: ContentLikeState = {
        likesCount: response.likesCount,
        likedByMe: response.liked,
        isLiking: false,
      };
      setContentLikeState((prev) => ({ ...prev, [content.id]: finalState }));
      if (selectedContent?.id === content.id) {
        setSelectedContent((prev) =>
          prev
            ? {
                ...prev,
                likes_count: finalState.likesCount,
                liked_by_me: finalState.likedByMe,
              }
            : prev
        );
      }
      if (finalState.likedByMe) {
        recordLikeActivity(content.id, content.title || 'Untitled');
      }
    } catch (error) {
      setContentLikeState((prev) => ({ ...prev, [content.id]: { ...likeState, isLiking: false } }));
      if (selectedContent?.id === content.id) {
        setSelectedContent((prev) =>
          prev
            ? {
                ...prev,
                likes_count: likeState.likesCount,
                liked_by_me: likeState.likedByMe,
              }
            : prev
        );
      }
      console.warn('Like failed', error);
    }
  };

  const handleDoubleTapLike = async (content: Content) => {
    const likeState = getLikeState(content);
    if (likeState.likedByMe || likeState.isLiking) {
      return;
    }
    await handleLike(content);
  };

  const handleSave = async (contentId: string) => {
    const isCurrentlySaved = savedContentIds.has(contentId);
    setSavedContentIds((prev) => {
      const next = new Set(prev);
      if (isCurrentlySaved) {
        next.delete(contentId);
      } else {
        next.add(contentId);
      }
      return next;
    });
    const savedContent = contents.find((content) => content.id === contentId);
    if (isCurrentlySaved) {
      removeSaveActivity(contentId);
      toast({ title: 'Video removed from saved' });
    } else {
      recordSaveActivity(contentId, savedContent?.title || 'Untitled');
      toast({ title: 'Video saved' });
    }

    try {
      if (isCurrentlySaved) {
        await unsaveContent(contentId);
      } else {
        await saveContent(contentId);
      }
    } catch (error) {
      // Roll back optimistic saved state on error.
      setSavedContentIds((prev) => {
        const next = new Set(prev);
        if (isCurrentlySaved) {
          next.add(contentId);
        } else {
          next.delete(contentId);
        }
        return next;
      });
      if (isCurrentlySaved) {
        recordSaveActivity(contentId, savedContent?.title || 'Untitled');
      } else {
        removeSaveActivity(contentId);
      }
      console.warn('Save failed', error);
    }
  };

  const handleShare = async (content: Content) => {
    // Native share if available
    if (navigator.share) {
      try {
        await navigator.share({
          title: content.title,
          text: content.description || '',
          url: window.location.href,
        });
      } catch (err) {
        console.log('Share cancelled');
      }
    } else {
      // Fallback: copy to clipboard
      navigator.clipboard.writeText(window.location.href);
    }
  };

  const handleFlag = async (contentId: string) => {
    try {
      await flagContent(contentId, 'inappropriate');
    } catch (error) {
      console.warn('Flag failed', error);
    }
  };

  const handleTakeDown = async (contentId: string) => {
    if (!isAdminUser) return;
    const confirmed = window.confirm(
      'Confirm take down video? This video will then be unpublished.'
    );
    if (!confirmed) return;

    setTakingDownContentId(contentId);
    try {
      await rejectContent(contentId, 'Taken down by admin from feed/explore.');
      setHiddenContentIds((prev) => {
        const next = new Set(prev);
        next.add(contentId);
        return next;
      });
    } catch (error) {
      console.warn('Take down failed', error);
    } finally {
      setTakingDownContentId(null);
    }
  };

  const handleEdit = (content: Content) => {
    if (!isAdminUser) return;
    navigate('/create', {
      state: {
        editContent: content,
        returnTo: `${location.pathname}${location.search}`,
      },
    });
  };

  if (visibleContents.length === 0 && !isLoading) {
    return (
      <div className={`${containerHeightClass} flex items-center justify-center`}>
        <div className="text-center p-8">
          <span className="text-6xl mb-4 block">🧠</span>
          <h2 className="text-xl font-bold mb-2">No content yet</h2>
          <p className="text-muted-foreground">
            Check back soon for brain rot education!
          </p>
        </div>
      </div>
    );
  }

  return (
    <>
      <div
        ref={containerRef}
        className={`${containerHeightClass} ${containerClassName ?? ""} overflow-y-auto snap-y snap-mandatory overscroll-y-contain scrollbar-hide`}
      >
        {visibleContents.map((content, index) => {
          const likeState = getLikeState(content);
          return (
            <div key={content.id} className="h-full w-full snap-start snap-always">
              <FeedCard
                content={content}
                isActive={index === activeIndex}
                onSwipeLeft={() => handleSwipeLeft(content)}
                onLike={() => handleLike(content)}
                onDoubleTapLike={() => handleDoubleTapLike(content)}
                likeCount={likeState.likesCount}
                likedByMe={likeState.likedByMe}
                isLiking={likeState.isLiking}
                isSaved={savedContentIds.has(content.id)}
                onSave={() => handleSave(content.id)}
                onShare={() => handleShare(content)}
                onFlag={() => handleFlag(content.id)}
                onQuizClick={() => handleQuizClick(content)}
                showEdit={isAdminUser && content.content_type === 'video'}
                onEdit={() => handleEdit(content)}
                showTakeDown={isAdminUser && content.content_type === 'video'}
                onTakeDown={() => handleTakeDown(content.id)}
                isTakingDown={takingDownContentId === content.id}
              />
            </div>
          );
        })}
        
        {/* Loading indicator */}
        {isLoading && (
          <div className="h-full w-full flex items-center justify-center">
            <div className="animate-pulse-soft">
              <span className="text-4xl">🥞</span>
            </div>
          </div>
        )}
      </div>

      {/* Content detail sheet */}
      <ContentDetailSheet
        content={selectedContent}
        open={showDetail}
        onOpenChange={setShowDetail}
      />

      {/* Quiz sheet */}
      <QuizSheet
        quiz={activeQuiz}
        content={selectedContent}
        open={showQuiz}
        onOpenChange={setShowQuiz}
      />
    </>
  );
}
