import React, { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { FeedCard } from './FeedCard';
import { CommentSheet, type FeedComment } from './CommentSheet';
import { QuizSheet } from '../quiz/QuizSheet';
import type { Content, Quiz } from '@/types';
import { fetchContentQuiz, flagContent, rejectContent, saveContent, trackContentView, voteContent } from '@/lib/api';
import { toast } from '@/components/ui/sonner';
import { useAuthContext } from '@/contexts/AuthContext';
import { useLocation, useNavigate } from 'react-router-dom';

interface FeedContainerProps {
  contents: Content[];
  onLoadMore?: () => void;
  hasMore?: boolean;
  isLoading?: boolean;
  initialIndex?: number;
  containerClassName?: string;
}

// Backend: /api/content/{id} actions.
// Dummy behavior is used when mocks are enabled.

const defaultComments: Omit<FeedComment, 'id'>[] = [
  { author: 'brainrot_learner', text: 'This made the slang explanation click for me.' },
  { author: 'memeprof', text: 'Usage example is accurate.' },
];

export function FeedContainer({
  contents,
  onLoadMore,
  hasMore = false,
  isLoading = false,
  initialIndex = 0,
  containerClassName,
}: FeedContainerProps) {
  const { isAdmin } = useAuthContext();
  const navigate = useNavigate();
  const location = useLocation();
  const containerHeightClass =
    'h-[calc(100vh-var(--bottom-nav-height)-var(--safe-area-bottom))] md:h-[calc(100vh-4rem)]';
  const [hiddenContentIds, setHiddenContentIds] = useState<Set<string>>(() => new Set());
  const [takingDownContentId, setTakingDownContentId] = useState<string | null>(null);
  const [activeIndex, setActiveIndex] = useState(initialIndex);
  const [selectedContent, setSelectedContent] = useState<Content | null>(null);
  const [showComments, setShowComments] = useState(false);
  const [showQuiz, setShowQuiz] = useState(false);
  const [activeQuiz, setActiveQuiz] = useState<Quiz | null>(null);
  const [commentsByContent, setCommentsByContent] = useState<Record<string, FeedComment[]>>({});
  const [savedByContent, setSavedByContent] = useState<Record<string, boolean>>({});
  const containerRef = useRef<HTMLDivElement>(null);
  const isAdminUser = isAdmin();
  const visibleContents = useMemo(
    () => contents.filter((content) => !hiddenContentIds.has(content.id)),
    [contents, hiddenContentIds]
  );

  const seededCommentsByContent = useMemo<Record<string, FeedComment[]>>(() => {
    const seeded: Record<string, FeedComment[]> = {};
    contents.forEach((content) => {
      seeded[content.id] = defaultComments.map((comment, index) => ({
        id: `seed-${content.id}-${index}`,
        ...comment,
      }));
    });
    return seeded;
  }, [contents]);

  const getCommentsForContent = useCallback(
    (contentId: string) => commentsByContent[contentId] ?? seededCommentsByContent[contentId] ?? [],
    [commentsByContent, seededCommentsByContent]
  );

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

  const handleCommentClick = (content: Content) => {
    setSelectedContent(content);
    setShowComments(true);
  };

  const handlePostComment = (contentId: string, commentText: string) => {
    setCommentsByContent((prev) => {
      const baseComments = prev[contentId] ?? seededCommentsByContent[contentId] ?? [];
      return {
        ...prev,
        [contentId]: [
          ...baseComments,
          { id: `comment-${Date.now()}`, author: 'you', text: commentText },
        ],
      };
    });
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

  const handleVote = async (contentId: string, type: string) => {
    try {
      await voteContent(contentId, type);
    } catch (error) {
      console.warn('Vote failed', error);
    }
  };

  const handleSave = (contentId: string) => {
    const nextSaved = !savedByContent[contentId];
    setSavedByContent((prev) => ({
      ...prev,
      [contentId]: nextSaved,
    }));

    toast(nextSaved ? 'Saved' : 'Unsaved', {
      position: 'bottom-center',
    });

    if (nextSaved) {
      saveContent(contentId).catch((error) => {
        console.warn('Save failed', error);
      });
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
      } catch (_err) {
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
    const confirmed = window.confirm('Confirm take down video? This video will then be unpublished.');
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
          <span className="text-6xl mb-4 block">Brain</span>
          <h2 className="text-xl font-bold mb-2">No content yet</h2>
          <p className="text-muted-foreground">Check back soon for brain rot education!</p>
        </div>
      </div>
    );
  }

  return (
    <>
      <div
        ref={containerRef}
        className={`${containerHeightClass} ${containerClassName ?? ''} overflow-y-auto snap-y snap-mandatory overscroll-y-contain scrollbar-hide`}
      >
        {visibleContents.map((content, index) => (
          <div key={content.id} className="h-full w-full snap-start snap-always">
            <FeedCard
              content={content}
              isActive={index === activeIndex}
              isSaved={Boolean(savedByContent[content.id])}
              commentCount={getCommentsForContent(content.id).length}
              onCommentClick={() => handleCommentClick(content)}
              onVote={(type) => handleVote(content.id, type)}
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
        ))}

        {/* Loading indicator */}
        {isLoading && (
          <div className="h-full w-full flex items-center justify-center">
            <div className="animate-pulse-soft">
              <span className="text-2xl">Loading...</span>
            </div>
          </div>
        )}
      </div>

      {/* Comment sheet */}
      <CommentSheet
        content={selectedContent}
        open={showComments}
        comments={selectedContent ? getCommentsForContent(selectedContent.id) : []}
        onOpenChange={setShowComments}
        onPostComment={handlePostComment}
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
