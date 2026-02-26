import React, { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { MainLayout } from "@/components/layout/MainLayout";
import { FeedContainer } from "@/components/feed/FeedContainer";
import type { Content } from "@/types";
import { fetchContentById, fetchFeed } from "@/lib/api";

const ContentFeedPage = () => {
  const { id } = useParams<{ id: string }>();
  const [contents, setContents] = useState<Content[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasMore, setHasMore] = useState(false);
  const [page, setPage] = useState(1);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setError("Missing content id.");
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setError(null);
    setPage(1);

    Promise.all([fetchContentById(id), fetchFeed(1)])
      .then(([content, feed]) => {
        const merged = [content, ...feed.items.filter((item) => item.id !== content.id)];
        setContents(merged);
        setHasMore(feed.hasMore);
      })
      .catch((err) => {
        console.warn("Failed to load content feed", err);
        setError("Unable to load this content right now.");
      })
      .finally(() => setIsLoading(false));
  }, [id]);

  useEffect(() => {
    if (!id || page === 1) {
      return;
    }
    setIsLoading(true);
    fetchFeed(page)
      .then((data) => {
        setContents((prev) => {
          const existing = new Set(prev.map((item) => item.id));
          const nextItems = data.items.filter((item) => !existing.has(item.id));
          return [...prev, ...nextItems];
        });
        setHasMore(data.hasMore);
      })
      .catch((err) => {
        console.warn("Failed to load more feed", err);
      })
      .finally(() => setIsLoading(false));
  }, [id, page]);

  const loadMore = async () => {
    if (isLoading || !hasMore) return;
    setPage((prev) => prev + 1);
  };

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
          isLoading={isLoading}
          initialIndex={0}
        />
      )}
    </MainLayout>
  );
};

export default ContentFeedPage;
