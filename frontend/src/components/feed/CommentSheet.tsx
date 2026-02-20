import React, { useMemo, useState } from 'react';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import type { Content } from '@/types';

export interface FeedComment {
  id: string;
  author: string;
  text: string;
}

interface CommentSheetProps {
  content: Content | null;
  open: boolean;
  comments: FeedComment[];
  onOpenChange: (open: boolean) => void;
  onPostComment: (contentId: string, commentText: string) => void;
}

export function CommentSheet({
  content,
  open,
  comments,
  onOpenChange,
  onPostComment,
}: CommentSheetProps) {
  const [newComment, setNewComment] = useState('');

  const title = useMemo(() => {
    if (!content) return 'Comments';
    return `${content.title} • Comments`;
  }, [content]);

  if (!content) return null;

  const handlePostComment = () => {
    const trimmedComment = newComment.trim();
    if (!trimmedComment) return;

    onPostComment(content.id, trimmedComment);
    setNewComment('');
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="bottom" className="h-[70vh] rounded-t-3xl">
        <SheetHeader className="text-left">
          <SheetTitle className="text-xl">{title}</SheetTitle>
        </SheetHeader>

        <div className="mt-6 flex h-[calc(100%-3rem)] flex-col">
          <div className="flex-1 space-y-3 overflow-y-auto pr-1">
            {comments.map((comment) => (
              <div key={comment.id} className="rounded-lg bg-muted p-3">
                <p className="text-xs font-semibold text-muted-foreground">@{comment.author}</p>
                <p className="mt-1 text-sm">{comment.text}</p>
              </div>
            ))}
          </div>

          <div className="mt-4 space-y-2 border-t pt-4">
            <Textarea
              placeholder="Add a comment..."
              value={newComment}
              onChange={(event) => setNewComment(event.target.value)}
              rows={3}
            />
            <Button className="w-full" onClick={handlePostComment} disabled={!newComment.trim()}>
              Post comment
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
