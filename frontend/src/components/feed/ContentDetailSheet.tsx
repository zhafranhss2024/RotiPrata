import React, { useEffect, useMemo, useRef, useState } from "react";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { BookOpen, Clock, History } from "lucide-react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import type { Content } from "@/types";
import { fetchSimilarContent, SIMILAR_CONTENT_LIMIT } from "@/lib/api";
import { CompactVideoTile } from "./CompactVideoTile";
import type { ContentViewerLocationState } from "@/lib/contentViewer";

interface ContentDetailSheetProps {
  content: Content | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ContentDetailSheet({ content, open, onOpenChange }: ContentDetailSheetProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const requestVersionRef = useRef(0);
  const [similarVideos, setSimilarVideos] = useState<Content[]>([]);
  const [isLoadingSimilar, setIsLoadingSimilar] = useState(false);

  useEffect(() => {
    if (!open || !content?.id) {
      setIsLoadingSimilar(false);
      setSimilarVideos([]);
      return;
    }

    requestVersionRef.current += 1;
    const requestVersion = requestVersionRef.current;
    setIsLoadingSimilar(true);

    fetchSimilarContent(content.id, SIMILAR_CONTENT_LIMIT)
      .then((items) => {
        if (requestVersion !== requestVersionRef.current) {
          return;
        }
        setSimilarVideos(
          items
            .filter((item) => item.id !== content.id)
            .slice(0, SIMILAR_CONTENT_LIMIT)
        );
      })
      .catch((error) => {
        if (requestVersion !== requestVersionRef.current) {
          return;
        }
        console.warn("Failed to load similar videos", error);
        setSimilarVideos([]);
      })
      .finally(() => {
        if (requestVersion === requestVersionRef.current) {
          setIsLoadingSimilar(false);
        }
      });
  }, [content?.id, open]);

  const visibleSimilarVideos = useMemo(
    () =>
      similarVideos
        .filter((item) => item.id !== content?.id)
        .slice(0, SIMILAR_CONTENT_LIMIT),
    [content?.id, similarVideos]
  );

  if (!content) return null;

  const buildBackLabel = () => {
    if (location.pathname === "/") {
      return "Back to Feed";
    }
    if (location.pathname.startsWith("/explore")) {
      return "Back to Explore";
    }
    return "Back";
  };

  const handleSimilarVideoClick = (contentId: string, index: number) => {
    onOpenChange(false);
    navigate(`/content/${contentId}`, {
      state: {
        queueContents: visibleSimilarVideos,
        initialIndex: index,
        returnTo: `${location.pathname}${location.search}`,
        backLabel: buildBackLabel(),
      } satisfies ContentViewerLocationState,
    });
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="h-[100dvh] w-full max-w-full rounded-none flex flex-col overflow-hidden sm:max-w-xl"
      >
        <SheetHeader className="text-left shrink-0">
          <div className="flex items-start gap-3">
            {content.category && <Badge variant="secondary">{content.category.name}</Badge>}
            {content.learning_objective && (
              <Badge className="gradient-primary text-white border-0">
                Goal: {content.learning_objective}
              </Badge>
            )}
          </div>
          <SheetTitle className="text-2xl mt-2">{content.title}</SheetTitle>
        </SheetHeader>

        <div className="mt-6 min-h-0 flex-1 space-y-6 overflow-y-auto pb-safe pr-1">
          {content.status === "rejected" && content.review_feedback ? (
            <div className="rounded-2xl border border-rose-300/70 bg-rose-50 px-4 py-3 text-rose-950">
              <div className="flex items-center gap-2">
                <Badge className="border-0 bg-rose-600 text-white">Removed by moderation</Badge>
              </div>
              <p className="mt-3 text-sm leading-6">{content.review_feedback}</p>
            </div>
          ) : null}

          {content.description && (
            <div>
              <h3 className="font-semibold text-sm text-muted-foreground mb-2">About</h3>
              <p className="text-foreground">{content.description}</p>
            </div>
          )}

          <Separator />

          {content.origin_explanation && (
            <div>
              <h3 className="font-semibold text-sm text-muted-foreground mb-2 flex items-center gap-2">
                <History className="h-4 w-4" />
                Origin
              </h3>
              <p className="text-foreground">{content.origin_explanation}</p>
            </div>
          )}

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

          {content.older_version_reference && (
            <div>
              <h3 className="font-semibold text-sm text-muted-foreground mb-2 flex items-center gap-2">
                <Clock className="h-4 w-4" />
                Boomer Translation
              </h3>
              <p className="text-foreground italic">"{content.older_version_reference}"</p>
            </div>
          )}

          <Separator />

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

          <div className="mx-auto w-full max-w-2xl">
            <div className="flex items-center justify-between gap-3 mb-3">
              <div>
                <h3 className="font-semibold text-sm text-muted-foreground">Similar Videos</h3>
                <p className="text-sm text-muted-foreground">
                  Keep scrolling through related videos after you open one.
                </p>
              </div>
              <Badge variant="outline">Top {SIMILAR_CONTENT_LIMIT}</Badge>
            </div>

            {isLoadingSimilar ? (
              <div className="grid grid-cols-[repeat(auto-fit,minmax(6.75rem,8.5rem))] justify-center gap-2 sm:grid-cols-[repeat(auto-fit,minmax(7.5rem,9rem))] sm:gap-3">
                {Array.from({ length: 4 }).map((_, index) => (
                  <div
                    key={`similar-skeleton-${index}`}
                    className="rounded-2xl border border-border/60 bg-muted/30 p-2.5"
                  >
                    <Skeleton className="aspect-[9/16] w-full rounded-xl" />
                    <Skeleton className="mt-2.5 h-3 w-5/6" />
                    <Skeleton className="mt-2 h-2.5 w-2/3" />
                  </div>
                ))}
              </div>
            ) : visibleSimilarVideos.length > 0 ? (
              <div className="grid grid-cols-[repeat(auto-fit,minmax(6.75rem,8.5rem))] justify-center gap-2 sm:grid-cols-[repeat(auto-fit,minmax(7.5rem,9rem))] sm:gap-3">
                {visibleSimilarVideos.map((video, index) => (
                  <CompactVideoTile
                    key={video.id}
                    title={video.title}
                    snippet={video.description}
                    thumbnailUrl={video.thumbnail_url}
                    mediaUrl={video.stream_type === "hls" ? null : video.media_url}
                    onClick={() => handleSimilarVideoClick(video.id, index)}
                  />
                ))}
              </div>
            ) : (
              <div className="rounded-xl border border-dashed border-border/70 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
                No similar videos available right now.
              </div>
            )}
          </div>

          <div className="bg-surfaceSoft border border-mainAlt/70 rounded-xl p-4">
            <div className="flex items-center gap-3 mb-3">
              <div className="gradient-secondary p-2 rounded-lg">
                <BookOpen className="h-5 w-5 text-white" />
              </div>
              <div>
                <h4 className="font-semibold">Want to learn more?</h4>
                <p className="text-sm text-muted-foreground">Check out related lessons in the Lesson Hub</p>
              </div>
            </div>
            <Button asChild className="w-full">
              <Link to="/lessons">
                <BookOpen className="h-4 w-4 mr-2" />
                Explore Lessons
              </Link>
            </Button>
          </div>

        </div>
      </SheetContent>
    </Sheet>
  );
}
