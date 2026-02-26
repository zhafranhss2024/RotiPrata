import React, { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { FeedCard } from './FeedCard';
import { ContentDetailSheet } from './ContentDetailSheet';
import { CommentSheet, type FeedComment } from './CommentSheet';
import { QuizSheet } from '../quiz/QuizSheet';
import type { Content, Quiz } from '@/types';
import {
  deleteContentComment,
  fetchContentComments,
  fetchContentQuiz,
  flagContent,
  likeContent,
  postContentComment,
  rejectContent,
  saveContent,
  shareContent,
  trackContentView,
  unlikeContent,
  unsaveContent,
  type ContentComment,
} from '@/lib/api';
import { toast } from '@/components/ui/sonner';
import { Button } from '@/components/ui/button';
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

const mapApiComment = (comment: ContentComment): FeedComment => ({
  id: comment.id,
  userId: comment.userId ?? (comment as unknown as { user_id?: string }).user_id ?? '',
  author: comment.author || 'anonymous',
  text: comment.body,
});

export function FeedContainer({
  contents,
  onLoadMore,
  hasMore = false,
  isLoading = false,
  initialIndex = 0,
  containerClassName,
}: FeedContainerProps) {
  const { isAdmin, user } = useAuthContext();
  const navigate = useNavigate();
  const location = useLocation();
  const containerHeightClass =
    'h-[calc(100vh-var(--bottom-nav-height)-var(--safe-area-bottom))] md:h-[calc(100vh-4rem)]';
  const [hiddenContentIds, setHiddenContentIds] = useState<Set<string>>(() => new Set());
  const [takingDownContentId, setTakingDownContentId] = useState<string | null>(null);
  const [activeIndex, setActiveIndex] = useState(initialIndex);
  const [selectedContent, setSelectedContent] = useState<Content | null>(null);
  const [showDetail, setShowDetail] = useState(false);
  const [showComments, setShowComments] = useState(false);
  const [showQuiz, setShowQuiz] = useState(false);
  const [activeQuiz, setActiveQuiz] = useState<Quiz | null>(null);
  const [commentsByContent, setCommentsByContent] = useState<Record<string, FeedComment[]>>({});
  const [commentCountsByContent, setCommentCountsByContent] = useState<Record<string, number>>({});
  const [commentsLoadingByContent, setCommentsLoadingByContent] = useState<Record<string, boolean>>({});
  const [postingCommentByContent, setPostingCommentByContent] = useState<Record<string, boolean>>({});
  const [deletingCommentByContent, setDeletingCommentByContent] = useState<Record<string, string | null>>({});
  const [savedByContent, setSavedByContent] = useState<Record<string, boolean>>({});
  const [savePendingByContent, setSavePendingByContent] = useState<Record<string, boolean>>({});
  const [likedByContent, setLikedByContent] = useState<Record<string, boolean>>({});
  const [likeCountsByContent, setLikeCountsByContent] = useState<Record<string, number>>({});
  const [likePendingByContent, setLikePendingByContent] = useState<Record<string, boolean>>({});
  const [pausedByContent, setPausedByContent] = useState<Record<string, boolean>>({});
  const containerRef = useRef<HTMLDivElement>(null);
  const cardRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const visibilityRatiosRef = useRef<Record<string, number>>({});
  const trackedViewsRef = useRef<Set<string>>(new Set());
  const viewTimerRef = useRef<number | null>(null);
  const prefetchLengthRef = useRef<number | null>(null);
  const activeIndexRef = useRef(activeIndex);
  const previousActiveContentIdRef = useRef<string | null>(null);
  const isAdminUser = isAdmin();
  const currentUserId =
    (user as unknown as { user_id?: string; userId?: string } | null)?.user_id ??
    (user as unknown as { user_id?: string; userId?: string } | null)?.userId ??
    null;
  const visibleContents = useMemo(
    () => contents.filter((content) => !hiddenContentIds.has(content.id)),
    [contents, hiddenContentIds]
  );

  useEffect(() => {
    activeIndexRef.current = activeIndex;
  }, [activeIndex]);

  useEffect(() => {
    const currentActiveId = visibleContents[activeIndex]?.id ?? null;
    const previousActiveId = previousActiveContentIdRef.current;
    if (previousActiveId && previousActiveId !== currentActiveId) {
      setPausedByContent((prev) => {
        if (prev[previousActiveId] === undefined) {
          return prev;
        }
        const next = { ...prev };
        delete next[previousActiveId];
        return next;
      });
    }
    previousActiveContentIdRef.current = currentActiveId;
  }, [activeIndex, visibleContents]);

  useEffect(() => {
    if (!contents.length) {
      return;
    }
    setLikedByContent((prev) => {
      const next = { ...prev };
      contents.forEach((content) => {
        if (next[content.id] === undefined && content.is_liked !== undefined) {
          next[content.id] = Boolean(content.is_liked);
        }
      });
      return next;
    });
    setSavedByContent((prev) => {
      const next = { ...prev };
      contents.forEach((content) => {
        if (next[content.id] === undefined && content.is_saved !== undefined) {
          next[content.id] = Boolean(content.is_saved);
        }
      });
      return next;
    });
    setCommentCountsByContent((prev) => {
      const next = { ...prev };
      contents.forEach((content) => {
        const parsedCount =
          typeof content.comments_count === 'number'
            ? content.comments_count
            : Number(content.comments_count);
        const initialCount = Number.isFinite(parsedCount) ? Math.max(0, parsedCount) : 0;
        next[content.id] = Math.max(next[content.id] ?? 0, initialCount);
      });
      return next;
    });
  }, [contents]);

  useEffect(() => {
    if (activeIndex >= visibleContents.length && visibleContents.length > 0) {
      setActiveIndex(visibleContents.length - 1);
    }
  }, [activeIndex, visibleContents.length]);

  const getCommentsForContent = useCallback(
    (contentId: string) => commentsByContent[contentId] ?? [],
    [commentsByContent]
  );

  const getCommentCountForContent = useCallback(
    (content: Content) => commentCountsByContent[content.id] ?? getCommentsForContent(content.id).length,
    [commentCountsByContent, getCommentsForContent]
  );

  const getLikeCountForContent = useCallback(
    (content: Content) => likeCountsByContent[content.id] ?? content.educational_value_votes,
    [likeCountsByContent]
  );

  const setCardRef = useCallback(
    (contentId: string) => (node: HTMLDivElement | null) => {
      cardRefs.current[contentId] = node;
    },
    []
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

  useEffect(() => {
    const container = containerRef.current;
    if (!container || visibleContents.length === 0) {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          const contentId = entry.target.getAttribute('data-content-id');
          if (!contentId) {
            return;
          }
          visibilityRatiosRef.current[contentId] = entry.isIntersecting ? entry.intersectionRatio : 0;
        });

        let bestIndex = -1;
        let bestRatio = -1;

        visibleContents.forEach((content, index) => {
          const ratio = visibilityRatiosRef.current[content.id] ?? 0;
          if (ratio >= 0.6 && ratio > bestRatio) {
            bestRatio = ratio;
            bestIndex = index;
          }
        });

        if (bestIndex >= 0) {
          if (bestIndex !== activeIndexRef.current) {
            setActiveIndex(bestIndex);
          }
        } else if (activeIndexRef.current !== -1) {
          // No feed card is >= 60% visible (e.g. end-of-feed card), so pause all media.
          setActiveIndex(-1);
        }
      },
      {
        root: container,
        threshold: [0, 0.25, 0.5, 0.6, 0.75, 1],
      }
    );

    visibleContents.forEach((content) => {
      const node = cardRefs.current[content.id];
      if (node) {
        observer.observe(node);
      }
    });

    return () => observer.disconnect();
  }, [visibleContents]);

  useEffect(() => {
    if (viewTimerRef.current) {
      window.clearTimeout(viewTimerRef.current);
      viewTimerRef.current = null;
    }

    const activeContent = visibleContents[activeIndex];
    if (!activeContent) {
      return;
    }

    const contentId = activeContent.id;
    viewTimerRef.current = window.setTimeout(() => {
      const ratio = visibilityRatiosRef.current[contentId] ?? 0;
      if (ratio < 0.6 || trackedViewsRef.current.has(contentId)) {
        return;
      }
      trackedViewsRef.current.add(contentId);
      trackContentView(contentId).catch((error) => {
        console.warn('Failed to track view', error);
      });
    }, 800);

    return () => {
      if (viewTimerRef.current) {
        window.clearTimeout(viewTimerRef.current);
        viewTimerRef.current = null;
      }
    };
  }, [activeIndex, visibleContents]);

  useEffect(() => {
    if (!onLoadMore || !hasMore || isLoading || visibleContents.length === 0) {
      return;
    }
    const triggerIndex = Math.max(0, visibleContents.length - 3);
    if (activeIndex < triggerIndex) {
      prefetchLengthRef.current = null;
      return;
    }
    if (prefetchLengthRef.current === visibleContents.length) {
      return;
    }
    prefetchLengthRef.current = visibleContents.length;
    onLoadMore();
  }, [activeIndex, hasMore, isLoading, onLoadMore, visibleContents.length]);

  const loadComments = useCallback(async (contentId: string) => {
    setCommentsLoadingByContent((prev) => ({ ...prev, [contentId]: true }));
    try {
      const comments = await fetchContentComments(contentId);
      const mappedComments = comments.map(mapApiComment);
      setCommentsByContent((prev) => ({
        ...prev,
        [contentId]: mappedComments,
      }));
      setCommentCountsByContent((prev) => ({
        ...prev,
        [contentId]: Math.max(prev[contentId] ?? 0, mappedComments.length),
      }));
    } catch (error) {
      console.warn('Failed to fetch comments', error);
      toast('Failed to load comments', { position: 'bottom-center' });
    } finally {
      setCommentsLoadingByContent((prev) => ({ ...prev, [contentId]: false }));
    }
  }, []);

  const handleCommentClick = (content: Content) => {
    setSelectedContent(content);
    setShowComments(true);
    void loadComments(content.id);
  };

  const handleLearnMoreClick = (content: Content) => {
    setSelectedContent(content);
    setShowDetail(true);
  };

  const handlePostComment = useCallback(async (contentId: string, commentText: string) => {
    setPostingCommentByContent((prev) => ({ ...prev, [contentId]: true }));
    try {
      const created = await postContentComment(contentId, commentText);
      setCommentsByContent((prev) => {
        const current = prev[contentId] ?? [];
        return {
          ...prev,
          [contentId]: [...current, mapApiComment(created)],
        };
      });
      setCommentCountsByContent((prev) => ({
        ...prev,
        [contentId]: (prev[contentId] ?? 0) + 1,
      }));
      return true;
    } catch (error) {
      console.warn('Comment failed', error);
      toast('Failed to post comment', { position: 'bottom-center' });
      return false;
    } finally {
      setPostingCommentByContent((prev) => ({ ...prev, [contentId]: false }));
    }
  }, []);

  const handleDeleteComment = useCallback(
    async (contentId: string, commentId: string) => {
      setDeletingCommentByContent((prev) => ({ ...prev, [contentId]: commentId }));
      try {
        await deleteContentComment(contentId, commentId);
        setCommentsByContent((prev) => ({
          ...prev,
          [contentId]: (prev[contentId] ?? []).filter((comment) => comment.id !== commentId),
        }));
        setCommentCountsByContent((prev) => ({
          ...prev,
          [contentId]: Math.max(0, (prev[contentId] ?? 0) - 1),
        }));
        return true;
      } catch (error) {
        console.warn('Delete comment failed', error);
        toast('Failed to delete comment', { position: 'bottom-center' });
        return false;
      } finally {
        setDeletingCommentByContent((prev) => ({ ...prev, [contentId]: null }));
      }
    },
    []
  );

  const canDeleteComment = useCallback(
    (comment: FeedComment) => {
      if (isAdminUser) {
        return true;
      }
      return Boolean(currentUserId) && comment.userId === currentUserId;
    },
    [currentUserId, isAdminUser]
  );

  const handleQuizClick = (content: Content) => {
    setActiveQuiz(null);
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

  const handleLikeToggle = async (content: Content, nextLiked: boolean) => {
    const contentId = content.id;
    if (likePendingByContent[contentId]) {
      return;
    }

    const previousLiked = Boolean(likedByContent[contentId]);
    const previousCount = getLikeCountForContent(content);
    const nextCount = nextLiked ? previousCount + 1 : Math.max(0, previousCount - 1);

    setLikedByContent((prev) => ({ ...prev, [contentId]: nextLiked }));
    setLikeCountsByContent((prev) => ({ ...prev, [contentId]: nextCount }));
    setLikePendingByContent((prev) => ({ ...prev, [contentId]: true }));

    try {
      if (nextLiked) {
        await likeContent(contentId);
      } else {
        await unlikeContent(contentId);
      }
    } catch (error) {
      console.warn('Like toggle failed', error);
      setLikedByContent((prev) => ({ ...prev, [contentId]: previousLiked }));
      setLikeCountsByContent((prev) => ({ ...prev, [contentId]: previousCount }));
      toast('Failed to update like', { position: 'bottom-center' });
    } finally {
      setLikePendingByContent((prev) => ({ ...prev, [contentId]: false }));
    }
  };

  const handleSave = async (contentId: string) => {
    if (savePendingByContent[contentId]) {
      return;
    }
    const nextSaved = !savedByContent[contentId];
    const previousSaved = Boolean(savedByContent[contentId]);
    setSavedByContent((prev) => ({
      ...prev,
      [contentId]: nextSaved,
    }));
    setSavePendingByContent((prev) => ({ ...prev, [contentId]: true }));

    try {
      if (nextSaved) {
        await saveContent(contentId);
      } else {
        await unsaveContent(contentId);
      }
      toast(nextSaved ? 'Saved' : 'Unsaved', {
        position: 'bottom-center',
      });
    } catch (error) {
      console.warn('Save failed', error);
      setSavedByContent((prev) => ({
        ...prev,
        [contentId]: previousSaved,
      }));
      toast('Failed to update save', {
        position: 'bottom-center',
      });
    } finally {
      setSavePendingByContent((prev) => ({ ...prev, [contentId]: false }));
    }
  };

  const handleShare = async (content: Content) => {
    let shareSucceeded = false;
    const shareUrl = `${window.location.origin}/content/${content.id}`;

    if (navigator.share) {
      try {
        await navigator.share({
          title: content.title,
          text: content.description || '',
          url: shareUrl,
        });
        shareSucceeded = true;
      } catch (_err) {
        console.log('Share cancelled');
      }
    } else if (navigator.clipboard?.writeText) {
      try {
        await navigator.clipboard.writeText(shareUrl);
        shareSucceeded = true;
        toast('Link copied', { position: 'bottom-center' });
      } catch (error) {
        console.warn('Failed to copy share link', error);
        toast('Failed to share', { position: 'bottom-center' });
      }
    }

    if (!shareSucceeded) {
      return;
    }

    try {
      await shareContent(content.id);
    } catch (error) {
      console.warn('Failed to track share', error);
      toast('Share completed, but tracking failed', { position: 'bottom-center' });
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

  const handleTogglePlayback = useCallback((contentId: string) => {
    setPausedByContent((prev) => ({
      ...prev,
      [contentId]: !Boolean(prev[contentId]),
    }));
  }, []);

  const handlePlaybackBlocked = useCallback((contentId: string) => {
    setPausedByContent((prev) => {
      if (prev[contentId]) {
        return prev;
      }
      return {
        ...prev,
        [contentId]: true,
      };
    });
  }, []);

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
          <div
            key={content.id}
            ref={setCardRef(content.id)}
            data-content-id={content.id}
            className="h-full w-full snap-start snap-always"
          >
            <FeedCard
              content={content}
              isActive={index === activeIndex}
              shouldMountMedia={Math.abs(index - activeIndex) <= 2}
              isPaused={Boolean(pausedByContent[content.id])}
              isSaved={Boolean(savedByContent[content.id])}
              isSavePending={Boolean(savePendingByContent[content.id])}
              isLiked={Boolean(likedByContent[content.id])}
              likeCount={getLikeCountForContent(content)}
              isLikePending={Boolean(likePendingByContent[content.id])}
              commentCount={getCommentCountForContent(content)}
              onLearnMoreClick={() => handleLearnMoreClick(content)}
              onCommentClick={() => handleCommentClick(content)}
              onLikeToggle={(nextLiked) => void handleLikeToggle(content, nextLiked)}
              onSave={() => void handleSave(content.id)}
              onShare={() => handleShare(content)}
              onFlag={() => handleFlag(content.id)}
              onQuizClick={() => handleQuizClick(content)}
              onTogglePlayback={() => handleTogglePlayback(content.id)}
              onPlaybackBlocked={() => handlePlaybackBlocked(content.id)}
              showEdit={isAdminUser && content.content_type === 'video'}
              onEdit={() => handleEdit(content)}
              showTakeDown={isAdminUser && content.content_type === 'video'}
              onTakeDown={() => handleTakeDown(content.id)}
              isTakingDown={takingDownContentId === content.id}
            />
          </div>
        ))}

        {!isLoading && !hasMore && visibleContents.length > 0 && (
          <div className="h-full w-full snap-start snap-always flex items-center justify-center p-6">
            <div className="w-full max-w-md rounded-2xl border border-border/60 bg-background/95 p-6 text-center shadow-lg">
              <h3 className="text-xl font-semibold">You reached the end of the feed</h3>
              <p className="mt-2 text-sm text-muted-foreground">
                Explore the lessons page to keep learning.
              </p>
              <Button className="mt-5 w-full" onClick={() => navigate('/lessons')}>
                Go to Lessons
              </Button>
            </div>
          </div>
        )}

        {isLoading && (
          <div className="h-full w-full flex items-center justify-center">
            <div className="animate-pulse-soft">
              <span className="text-2xl">Loading...</span>
            </div>
          </div>
        )}
      </div>

      <ContentDetailSheet
        content={selectedContent}
        open={showDetail}
        onOpenChange={setShowDetail}
      />

      <CommentSheet
        content={selectedContent}
        open={showComments}
        comments={selectedContent ? getCommentsForContent(selectedContent.id) : []}
        isLoading={selectedContent ? Boolean(commentsLoadingByContent[selectedContent.id]) : false}
        isPosting={selectedContent ? Boolean(postingCommentByContent[selectedContent.id]) : false}
        deletingCommentId={selectedContent ? deletingCommentByContent[selectedContent.id] ?? null : null}
        onOpenChange={setShowComments}
        onPostComment={handlePostComment}
        onDeleteComment={handleDeleteComment}
        canDeleteComment={canDeleteComment}
      />

      <QuizSheet
        quiz={activeQuiz}
        content={selectedContent}
        open={showQuiz}
        onOpenChange={setShowQuiz}
      />
    </>
  );
}
