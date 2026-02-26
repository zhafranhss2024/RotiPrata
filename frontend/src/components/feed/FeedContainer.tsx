import React, { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { FeedCard } from './FeedCard';
import { ContentDetailSheet } from './ContentDetailSheet';
import { CommentSheet, type FeedComment } from './CommentSheet';
import { QuizSheet } from '../quiz/QuizSheet';
import type { Content, Quiz } from '@/types';
import {
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
  const { isAdmin } = useAuthContext();
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
  const [commentsLoadingByContent, setCommentsLoadingByContent] = useState<Record<string, boolean>>({});
  const [postingCommentByContent, setPostingCommentByContent] = useState<Record<string, boolean>>({});
  const [savedByContent, setSavedByContent] = useState<Record<string, boolean>>({});
  const [savePendingByContent, setSavePendingByContent] = useState<Record<string, boolean>>({});
  const [likedByContent, setLikedByContent] = useState<Record<string, boolean>>({});
  const [likeCountsByContent, setLikeCountsByContent] = useState<Record<string, number>>({});
  const [likePendingByContent, setLikePendingByContent] = useState<Record<string, boolean>>({});
  const containerRef = useRef<HTMLDivElement>(null);
  const isAdminUser = isAdmin();
  const visibleContents = useMemo(
    () => contents.filter((content) => !hiddenContentIds.has(content.id)),
    [contents, hiddenContentIds]
  );

  const getCommentsForContent = useCallback(
    (contentId: string) => commentsByContent[contentId] ?? [],
    [commentsByContent]
  );

  const getLikeCountForContent = useCallback(
    (content: Content) => likeCountsByContent[content.id] ?? content.educational_value_votes,
    [likeCountsByContent]
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

  const loadComments = useCallback(async (contentId: string) => {
    setCommentsLoadingByContent((prev) => ({ ...prev, [contentId]: true }));
    try {
      const comments = await fetchContentComments(contentId);
      setCommentsByContent((prev) => ({
        ...prev,
        [contentId]: comments.map(mapApiComment),
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
      return true;
    } catch (error) {
      console.warn('Comment failed', error);
      toast('Failed to post comment', { position: 'bottom-center' });
      return false;
    } finally {
      setPostingCommentByContent((prev) => ({ ...prev, [contentId]: false }));
    }
  }, []);

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
    const shareUrl = window.location.href;

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
              isSavePending={Boolean(savePendingByContent[content.id])}
              isLiked={Boolean(likedByContent[content.id])}
              likeCount={getLikeCountForContent(content)}
              isLikePending={Boolean(likePendingByContent[content.id])}
              commentCount={getCommentsForContent(content.id).length}
              onLearnMoreClick={() => handleLearnMoreClick(content)}
              onCommentClick={() => handleCommentClick(content)}
              onLikeToggle={(nextLiked) => void handleLikeToggle(content, nextLiked)}
              onSave={() => void handleSave(content.id)}
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

      <ContentDetailSheet
        content={selectedContent}
        open={showDetail}
        onOpenChange={setShowDetail}
      />

      {/* Comment sheet */}
      <CommentSheet
        content={selectedContent}
        open={showComments}
        comments={selectedContent ? getCommentsForContent(selectedContent.id) : []}
        isLoading={selectedContent ? Boolean(commentsLoadingByContent[selectedContent.id]) : false}
        isPosting={selectedContent ? Boolean(postingCommentByContent[selectedContent.id]) : false}
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
