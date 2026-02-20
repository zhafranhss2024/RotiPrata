import React, { useState, useRef, useEffect, useCallback } from 'react';
import { FeedCard } from './FeedCard';
import { CommentSheet, type FeedComment } from './CommentSheet';
import { QuizSheet } from '../quiz/QuizSheet';
import type { Content, Quiz } from '@/types';
import { fetchContentQuiz, flagContent, saveContent, trackContentView, voteContent } from '@/lib/api';

interface FeedContainerProps {
  contents: Content[];
  onLoadMore?: () => void;
  hasMore?: boolean;
  isLoading?: boolean;
}

// Backend: /api/content/{id} actions.
// Dummy behavior is used when mocks are enabled.


const defaultComments: Omit<FeedComment, 'id'>[] = [
  { author: 'brainrot_learner', text: 'This actually made the slang click for me.' },
  { author: 'memeprof', text: 'The usage example is super accurate 😂' },
];

export function FeedContainer({
  contents,
  onLoadMore,
  hasMore = false,
  isLoading = false,
}: FeedContainerProps) {
  const containerHeightClass =
    "h-[calc(100vh-var(--bottom-nav-height)-var(--safe-area-bottom))] md:h-[calc(100vh-4rem)] md:mt-16";
  const [activeIndex, setActiveIndex] = useState(0);
  const [selectedContent, setSelectedContent] = useState<Content | null>(null);
  const [showComments, setShowComments] = useState(false);
  const [showQuiz, setShowQuiz] = useState(false);
  const [activeQuiz, setActiveQuiz] = useState<Quiz | null>(null);
  const [commentsByContent, setCommentsByContent] = useState<Record<string, FeedComment[]>>({});
  const containerRef = useRef<HTMLDivElement>(null);

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
      const contentId = contents[newIndex]?.id;
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
  }, [activeIndex, hasMore, isLoading, onLoadMore]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    
    container.addEventListener('scroll', handleScroll);
    return () => container.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);


  useEffect(() => {
    setCommentsByContent((prev) => {
      const next = { ...prev };
      let changed = false;

      contents.forEach((content) => {
        if (next[content.id]) return;

        next[content.id] = defaultComments.map((comment, index) => ({
          id: `seed-${content.id}-${index}`,
          ...comment,
        }));
        changed = true;
      });

      return changed ? next : prev;
    });
  }, [contents]);

  const handleCommentClick = (content: Content) => {
    setSelectedContent(content);
    setShowComments(true);
  };

  const handlePostComment = (contentId: string, commentText: string) => {
    setCommentsByContent((prev) => ({
      ...prev,
      [contentId]: [
        ...(prev[contentId] ?? []),
        { id: `comment-${Date.now()}`, author: 'you', text: commentText },
      ],
    }));
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

  const handleSave = async (contentId: string) => {
    try {
      await saveContent(contentId);
    } catch (error) {
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

  if (contents.length === 0 && !isLoading) {
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
        className={`${containerHeightClass} overflow-y-scroll snap-mandatory-y scrollbar-hide`}
      >
        {contents.map((content, index) => (
          <div key={content.id} className="h-full w-full">
            <FeedCard
              content={content}
              isActive={index === activeIndex}
              commentCount={(commentsByContent[content.id] ?? []).length}
              onCommentClick={() => handleCommentClick(content)}
              onVote={(type) => handleVote(content.id, type)}
              onSave={() => handleSave(content.id)}
              onShare={() => handleShare(content)}
              onFlag={() => handleFlag(content.id)}
              onQuizClick={() => handleQuizClick(content)}
            />
          </div>
        ))}
        
        {/* Loading indicator */}
        {isLoading && (
          <div className="h-full w-full flex items-center justify-center">
            <div className="animate-pulse-soft">
              <span className="text-4xl">🥞</span>
            </div>
          </div>
        )}
      </div>

      {/* Comment sheet */}
      <CommentSheet
        content={selectedContent}
        open={showComments}
        comments={selectedContent ? commentsByContent[selectedContent.id] ?? [] : []}
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
