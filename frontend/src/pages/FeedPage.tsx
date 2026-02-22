import React, { useState, useEffect } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { FeedContainer } from '@/components/feed/FeedContainer';
import type { Content } from '@/types';
import { fetchFeed } from '@/lib/api';

// Backend: GET /api/feed?page={page}
// If VITE_USE_MOCKS=true (or auto fallback), dummy feed data is returned instead.

const FeedPage = () => {
  const [contents, setContents] = useState<Content[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasMore, setHasMore] = useState(true);
  const [page, setPage] = useState(1);

  useEffect(() => {
    loadFeed();
  }, [page]);

  const loadFeed = async () => {
    setIsLoading(true);

    try {
      const data = await fetchFeed(page);
      setContents(prev => (page === 1 ? data.items : [...prev, ...data.items]));
      setHasMore(data.hasMore);
    } catch (error) {
      console.error('Failed to load feed', error);
    } finally {
      setIsLoading(false);
    }
  };

  const loadMore = async () => {
    if (isLoading || !hasMore) return;
    
    setPage(prev => prev + 1);
  };

  return (
    <MainLayout fullScreen>
      <FeedContainer
        contents={contents}
        onLoadMore={loadMore}
        hasMore={hasMore}
        isLoading={isLoading}
      />
    </MainLayout>
  );
};

export default FeedPage;
