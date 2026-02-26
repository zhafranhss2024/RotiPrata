import React, { useCallback, useEffect, useRef, useState } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { FeedContainer } from '@/components/feed/FeedContainer';
import type { Content } from '@/types';
import { fetchFeed } from '@/lib/api';
import { ApiError } from '@/lib/apiClient';

// Backend: GET /api/feed?cursor={cursor}&limit={limit}
// If VITE_USE_MOCKS=true (or auto fallback), dummy feed data is returned instead.

const FeedPage = () => {
  const [contents, setContents] = useState<Content[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const inFlightRef = useRef(false);
  const hasInitializedRef = useRef(false);

  const mergeDedupById = (existing: Content[], incoming: Content[]) => {
    const dedup = new Map(existing.map((item) => [item.id, item]));
    incoming.forEach((item) => dedup.set(item.id, item));
    return Array.from(dedup.values());
  };

  const loadFeed = useCallback(async (reset: boolean) => {
    if (inFlightRef.current) {
      return;
    }
    if (!reset && (!hasMore || !nextCursor)) {
      return;
    }
    inFlightRef.current = true;
    if (reset) {
      setIsLoading(true);
    } else {
      setIsLoadingMore(true);
    }
    try {
      const data = await fetchFeed(reset ? null : nextCursor);
      setContents((prev) => (reset ? data.items : mergeDedupById(prev, data.items)));
      setHasMore(data.hasMore);
      setNextCursor(data.nextCursor ?? null);
    } catch (error) {
      if (!reset && error instanceof ApiError && error.status === 400 && error.code === 'validation_error') {
        inFlightRef.current = false;
        await loadFeed(true);
        return;
      }
      console.error('Failed to load feed', error);
    } finally {
      inFlightRef.current = false;
      if (reset) {
        setIsLoading(false);
      } else {
        setIsLoadingMore(false);
      }
    }
  }, [hasMore, nextCursor]);

  useEffect(() => {
    if (hasInitializedRef.current) {
      return;
    }
    hasInitializedRef.current = true;
    void loadFeed(true);
  }, [loadFeed]);

  const loadMore = async () => {
    if (isLoading || isLoadingMore || !hasMore) return;
    await loadFeed(false);
  };

  return (
    <MainLayout fullScreen>
      <FeedContainer
        contents={contents}
        onLoadMore={loadMore}
        hasMore={hasMore}
        isLoading={isLoading || isLoadingMore}
      />
    </MainLayout>
  );
};

export default FeedPage;
