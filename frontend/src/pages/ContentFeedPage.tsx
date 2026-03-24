import React, { useCallback, useEffect, useRef, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { MainLayout } from "@/components/layout/MainLayout";
import { FeedContainer } from "@/components/feed/FeedContainer";
import { Button } from "@/components/ui/button";
import type { Content } from "@/types";
import { fetchContentById, fetchSimilarContent, SIMILAR_CONTENT_LIMIT } from "@/lib/api";
import type { ContentViewerLocationState } from "@/lib/contentViewer";

const ContentFeedPage = () => {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const [contents, setContents] = useState<Content[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const inFlightRef = useRef(false);
  const viewerState = (location.state as ContentViewerLocationState | null) ?? null;
  const queueContents = viewerState?.queueContents ?? null;
  const hasRouteQueue = Boolean(queueContents?.length && id && queueContents.some((content) => content.id === id));
  const initialIndex = hasRouteQueue
    ? Math.max(
        0,
        Math.min(
          typeof viewerState?.initialIndex === "number" ? viewerState.initialIndex : 0,
          (queueContents?.length ?? 1) - 1
        )
      )
    : 0;

  const handleBack = () => {
    const historyIndex =
      typeof window !== "undefined" && typeof window.history.state?.idx === "number"
        ? window.history.state.idx
        : 0;
    if (historyIndex > 0) {
      navigate(-1);
      return;
    }
    navigate(viewerState?.returnTo ?? "/");
  };

  const loadInitial = useCallback(async () => {
    if (!id || inFlightRef.current) {
      return;
    }
    inFlightRef.current = true;
    setIsLoading(true);
    setError(null);
    try {
      const [content, similarVideos] = await Promise.all([
        fetchContentById(id),
        fetchSimilarContent(id, SIMILAR_CONTENT_LIMIT),
      ]);
      const queueById = new Map<string, Content>([[content.id, content]]);
      similarVideos.forEach((item) => {
        if (item.id !== content.id) {
          queueById.set(item.id, item);
        }
      });
      setContents(Array.from(queueById.values()));
    } catch (err) {
      console.warn("Failed to load content feed", err);
      setError("Unable to load this content right now.");
    } finally {
      inFlightRef.current = false;
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (!id) {
      setError("Missing content id.");
      setIsLoading(false);
      return;
    }

    if (hasRouteQueue && queueContents) {
      const dedupedQueue = Array.from(
        new Map(
          queueContents
            .filter((content) => content.content_type === "video")
            .map((content) => [content.id, content])
        ).values()
      );
      setContents(dedupedQueue);
      setError(null);
      setIsLoading(false);
      return;
    }

    void loadInitial();
  }, [hasRouteQueue, id, loadInitial, queueContents]);

  return (
    <MainLayout fullScreen>
      <div className="sticky top-0 z-30 h-12 flex items-center justify-between gap-2 px-4 border-b border-mainAlt bg-main dark:bg-mainDark">
        <Button
          variant="ghost"
          onClick={handleBack}
          className="text-mainAccent dark:text-white hover:bg-mainAlt"
        >
          <ArrowLeft className="h-4 w-4 mr-2" />
          {viewerState?.backLabel ?? "Back"}
        </Button>
        <span className="text-sm text-mainAccent">{contents.length} videos</span>
      </div>
      {error ? (
        <div className="h-[calc(100dvh-var(--bottom-nav-height)-var(--safe-area-bottom)-3rem)] md:h-[calc(100dvh-4rem-3rem)] flex items-center justify-center">
          <div className="text-center p-6">
            <h2 className="text-xl font-semibold mb-2">Unable to load content</h2>
            <p className="text-muted-foreground">{error}</p>
          </div>
        </div>
      ) : (
        <FeedContainer
          contents={contents}
          hasMore={false}
          isLoading={isLoading}
          initialIndex={initialIndex}
          containerClassName="h-[calc(100dvh-var(--bottom-nav-height)-var(--safe-area-bottom)-3rem)] md:h-[calc(100dvh-4rem-3rem)] md:!mt-0"
        />
      )}
    </MainLayout>
  );
};

export default ContentFeedPage;
