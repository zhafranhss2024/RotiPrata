import React from 'react';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { ExternalLink, BookOpen, Clock, History } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { Content } from '@/types';

interface ContentDetailSheetProps {
  content: Content | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

// TODO: Replace with Java backend API calls
// GET /api/content/{id}/details - Get full content details
// GET /api/content/{id}/related-lessons - Get related lessons

export function ContentDetailSheet({
  content,
  open,
  onOpenChange,
}: ContentDetailSheetProps) {
  if (!content) return null;
  const likeCount = Number(content.likes_count ?? content.educational_value_votes ?? 0);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="bottom" className="h-[85vh] rounded-t-3xl">
        <SheetHeader className="text-left">
          <div className="flex items-start gap-3">
            {content.category && (
              <Badge variant="secondary">{content.category.name}</Badge>
            )}
            {content.learning_objective && (
              <Badge className="gradient-primary text-white border-0">
                🎯 {content.learning_objective}
              </Badge>
            )}
          </div>
          <SheetTitle className="text-2xl mt-2">{content.title}</SheetTitle>
        </SheetHeader>

        <div className="mt-6 space-y-6 overflow-y-auto pb-safe">
          {/* Description */}
          {content.description && (
            <div>
              <h3 className="font-semibold text-sm text-muted-foreground mb-2">About</h3>
              <p className="text-foreground">{content.description}</p>
            </div>
          )}

          <Separator />

          {/* Origin */}
          {content.origin_explanation && (
            <div>
              <h3 className="font-semibold text-sm text-muted-foreground mb-2 flex items-center gap-2">
                <History className="h-4 w-4" />
                Origin
              </h3>
              <p className="text-foreground">{content.origin_explanation}</p>
            </div>
          )}

          {/* Definition */}
          {(content.definition_literal || content.definition_used) && (
            <div>
              <h3 className="font-semibold text-sm text-muted-foreground mb-2">Definition</h3>
              {content.definition_literal && (
                <div className="mb-2">
                  <span className="font-medium">Literal: </span>
                  <span className="text-muted-foreground">{content.definition_literal}</span>
                </div>
              )}
              {content.definition_used && (
                <div>
                  <span className="font-medium">How it's used: </span>
                  <span className="text-muted-foreground">{content.definition_used}</span>
                </div>
              )}
            </div>
          )}

          {/* Older reference */}
          {content.older_version_reference && (
            <div>
              <h3 className="font-semibold text-sm text-muted-foreground mb-2 flex items-center gap-2">
                <Clock className="h-4 w-4" />
                Boomer Translation
              </h3>
              <p className="text-foreground italic">
                "{content.older_version_reference}"
              </p>
            </div>
          )}

          <Separator />

          {/* Tags */}
          {content.tags && content.tags.length > 0 && (
            <div>
              <h3 className="font-semibold text-sm text-muted-foreground mb-2">Tags</h3>
              <div className="flex flex-wrap gap-2">
                {content.tags.map((tag) => (
                  <Badge key={tag} variant="outline">
                    #{tag}
                  </Badge>
                ))}
              </div>
            </div>
          )}

          {/* Related lesson CTA */}
          <div className="bg-muted rounded-xl p-4">
            <div className="flex items-center gap-3 mb-3">
              <div className="gradient-secondary p-2 rounded-lg">
                <BookOpen className="h-5 w-5 text-white" />
              </div>
              <div>
                <h4 className="font-semibold">Want to learn more?</h4>
                <p className="text-sm text-muted-foreground">
                  Check out related lessons in the Lesson Hub
                </p>
              </div>
            </div>
            <Button asChild className="w-full">
              <Link to="/lessons">
                <BookOpen className="h-4 w-4 mr-2" />
                Explore Lessons
              </Link>
            </Button>
          </div>

          {/* Stats */}
          <div className="flex items-center gap-4 text-sm text-muted-foreground">
            <span>{content.view_count} views</span>
            <span>•</span>
            <span>{likeCount} liked this</span>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
