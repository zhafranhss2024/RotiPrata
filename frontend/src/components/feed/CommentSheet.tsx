import React, { useMemo, useState } from 'react';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import type { Content } from '@/types';

export interface FeedComment {
  id: string;
  userId: string;
  author: string;
  text: string;
}

interface CommentSheetProps {
  content: Content | null;
  open: boolean;
  comments: FeedComment[];
  isLoading?: boolean;
  isPosting?: boolean;
  deletingCommentId?: string | null;
  onOpenChange: (open: boolean) => void;
  onPostComment: (contentId: string, commentText: string) => Promise<boolean>;
  onDeleteComment: (contentId: string, commentId: string) => Promise<boolean>;
  canDeleteComment: (comment: FeedComment) => boolean;
}

export function CommentSheet({
  content,
  open,
  comments,
  isLoading = false,
  isPosting = false,
  deletingCommentId = null,
  onOpenChange,
  onPostComment,
  onDeleteComment,
  canDeleteComment,
}: CommentSheetProps) {
  const [newComment, setNewComment] = useState('');

  const title = useMemo(() => {
    if (!content) return 'Comments';
    return `${content.title} - Comments`;
  }, [content]);

  if (!content) return null;

  const handlePostComment = async () => {
    const trimmedComment = newComment.trim();
    if (!trimmedComment) return;

    const success = await onPostComment(content.id, trimmedComment);
    if (success) {
      setNewComment('');
    }
  };

  const handleDeleteComment = async (commentId: string) => {
    await onDeleteComment(content.id, commentId);
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="bottom" className="h-[70vh] rounded-t-3xl">
        <SheetHeader className="text-left">
          <SheetTitle className="text-xl">{title}</SheetTitle>
        </SheetHeader>

        <div className="mt-6 flex h-[calc(100%-3rem)] flex-col">
          <div className="flex-1 space-y-3 overflow-y-auto pr-1">
            {isLoading ? (
              <div className="rounded-lg bg-muted p-3 text-sm text-muted-foreground">Loading comments...</div>
            ) : comments.length === 0 ? (
              <div className="rounded-lg bg-muted p-3 text-sm text-muted-foreground">No comments yet.</div>
            ) : (
              comments.map((comment) => (
                <div key={comment.id} className="rounded-lg bg-muted p-3">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-xs font-semibold text-muted-foreground">@{comment.author}</p>
                    {canDeleteComment(comment) && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="h-auto px-2 py-0 text-xs text-destructive hover:text-destructive"
                        disabled={deletingCommentId === comment.id}
                        onClick={() => {
                          void handleDeleteComment(comment.id);
                        }}
                      >
                        {deletingCommentId === comment.id ? 'Deleting...' : 'Delete'}
                      </Button>
                    )}
                  </div>
                  <p className="mt-1 text-sm">{comment.text}</p>
                </div>
              ))
            )}
          </div>

          <div className="mt-4 space-y-2 border-t pt-4">
            <Textarea
              placeholder="Add a comment..."
              value={newComment}
              onChange={(event) => setNewComment(event.target.value)}
              rows={3}
              disabled={isPosting}
            />
            <Button
              className="w-full"
              onClick={() => {
                void handlePostComment();
              }}
              disabled={!newComment.trim() || isPosting}
            >
              {isPosting ? 'Posting...' : 'Post comment'}
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
