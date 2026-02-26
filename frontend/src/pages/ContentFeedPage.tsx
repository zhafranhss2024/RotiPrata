import React, { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { MainLayout } from "@/components/layout/MainLayout";
import { FeedContainer } from "@/components/feed/FeedContainer";
import type { Content } from "@/types";
import { fetchContentById, fetchFeed } from "@/lib/api";
import { ApiError } from "@/lib/apiClient";

const ContentFeedPage = () => {
  const { id } = useParams<{ id: string }>();
  const [contents, setContents] = useState<Content[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const pinnedContentRef = useRef<Content | null>(null);
  const inFlightRef = useRef(false);

  const mergeDedupById = (existing: Content[], incoming: Content[]) => {
    const dedup = new Map(existing.map((item) => [item.id, item]));
    incoming.forEach((item) => dedup.set(item.id, item));
    return Array.from(dedup.values());
  };

  const loadInitial = useCallback(async () => {
    if (!id || inFlightRef.current) {
      return;
    }
    inFlightRef.current = true;
    setIsLoading(true);
    setError(null);
    try {
      const [content, feed] = await Promise.all([fetchContentById(id), fetchFeed(null)]);
      pinnedContentRef.current = content;
      const merged = [content, ...feed.items.filter((item) => item.id !== content.id)];
      setContents(merged);
      setHasMore(feed.hasMore);
      setNextCursor(feed.nextCursor ?? null);
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
    void loadInitial();
  }, [id, loadInitial]);

  const loadMore = useCallback(async () => {
    if (!id || isLoading || isLoadingMore || inFlightRef.current || !hasMore || !nextCursor) {
      return;
    }
    inFlightRef.current = true;
    setIsLoadingMore(true);
    try {
      const data = await fetchFeed(nextCursor);
      setContents((prev) => mergeDedupById(prev, data.items));
      setHasMore(data.hasMore);
      setNextCursor(data.nextCursor ?? null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 400 && err.code === "validation_error") {
        const pinned = pinnedContentRef.current;
        try {
          const data = await fetchFeed(null);
          const merged = pinned
            ? [pinned, ...data.items.filter((item) => item.id !== pinned.id)]
            : data.items;
          setContents(merged);
          setHasMore(data.hasMore);
          setNextCursor(data.nextCursor ?? null);
          return;
        } catch (reloadError) {
          console.warn("Failed to reset content feed after cursor error", reloadError);
        }
      }
      console.warn("Failed to load more feed", err);
    } finally {
      inFlightRef.current = false;
      setIsLoadingMore(false);
    }
  }, [hasMore, id, isLoading, isLoadingMore, nextCursor]);


  return (
    <MainLayout fullScreen>
      {error ? (
        <div className="h-[calc(100vh-var(--bottom-nav-height)-var(--safe-area-bottom))] flex items-center justify-center">
          <div className="text-center p-6">
            <h2 className="text-xl font-semibold mb-2">Unable to load content</h2>
            <p className="text-muted-foreground">{error}</p>
          </div>
        </div>
      ) : (
        <FeedContainer
          contents={contents}
          onLoadMore={loadMore}
          hasMore={hasMore}
          isLoading={isLoading || isLoadingMore}
          initialIndex={0}
        />
      )}
    </MainLayout>
  );
};

export default ContentFeedPage;
