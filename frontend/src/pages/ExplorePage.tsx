import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { FeedContainer } from '@/components/feed/FeedContainer';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Card, CardContent } from '@/components/ui/card';
import { ArrowLeft, BookOpen, Search, Video } from 'lucide-react';
import type { Content } from '@/types';
import {
  clearBrowsingHistory,
  fetchBrowsingHistory,
  fetchFeed,
  fetchRecommendations,
  saveBrowsingHistory,
  searchContent,
} from '@/lib/api';
import { cn } from '@/lib/utils';
import { CompactVideoTile } from '@/components/feed/CompactVideoTile';

type SearchKind = 'video' | 'lesson' | 'profile' | 'unknown';

type SearchResultItem = {
  id: string;
  title: string;
  snippet?: string;
  kind: SearchKind;
  thumbnailUrl?: string;
  mediaUrl?: string;
};

const ExplorePage = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [searchTab, setSearchTab] = useState<'videos' | 'lessons'>('videos');
  const [isSearching, setIsSearching] = useState(false);
  const [showHistory, setShowHistory] = useState(true);
  const [searchResults, setSearchResults] = useState<SearchResultItem[]>([]);
  const [recommendedContents, setRecommendedContents] = useState<Content[]>([]);
  const [isRecommendationsLoading, setIsRecommendationsLoading] = useState(true);
  const [videoViewerState, setVideoViewerState] = useState<{ mode: 'search' | 'recommendations'; index: number } | null>(null);
  const [contentLookup, setContentLookup] = useState<Record<string, Content>>({});
  const [browsingHistory, setBrowsingHistory] = useState<{ id: string; query: string; searched_at: string }[]>([]);
  const searchRequestVersionRef = useRef(0);
  const contentLookupRef = useRef<Record<string, Content>>({});

  useEffect(() => {
    fetchBrowsingHistory()
      .then(setBrowsingHistory)
      .catch((error) => console.warn('Failed to load browsing history', error));
  }, []);

  useEffect(() => {
    let active = true;
    setIsRecommendationsLoading(true);
    fetchRecommendations()
      .then((response) => {
        if (!active) {
          return;
        }
        setRecommendedContents(response.items ?? []);
        setContentLookup((prev) => {
          const next = { ...prev };
          (response.items ?? []).forEach((item) => {
            next[item.id] = item;
          });
          return next;
        });
      })
      .catch((error) => {
        if (active) {
          console.warn('Failed to load recommendations', error);
          setRecommendedContents([]);
        }
      })
      .finally(() => {
        if (active) {
          setIsRecommendationsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    const loadContentLookup = async () => {
      const map: Record<string, Content> = {};
      let cursor: string | null = null;
      for (let i = 0; i < 5; i += 1) {
        try {
          const response = await fetchFeed(cursor);
          response.items.forEach((item) => {
            map[item.id] = item;
          });
          if (!response.hasMore || !response.nextCursor) break;
          cursor = response.nextCursor;
        } catch (error) {
          console.warn('Failed to load feed catalog for search thumbnails', error);
          break;
        }
      }
      if (active) {
        setContentLookup(map);
      }
    };

    void loadContentLookup();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    contentLookupRef.current = contentLookup;
  }, [contentLookup]);

  useEffect(() => {
    const normalizedQuery = searchQuery.trim();
    searchRequestVersionRef.current += 1;

    if (!normalizedQuery) {
      setDebouncedQuery('');
      return;
    }

    setIsSearching(true);
    const debounceTimeout = setTimeout(() => {
      setDebouncedQuery(normalizedQuery);
    }, 350);

    return () => clearTimeout(debounceTimeout);
  }, [searchQuery]);

  useEffect(() => {
    if (!debouncedQuery) {
      setSearchResults([]);
      setIsSearching(false);
      return;
    }

    const requestVersion = searchRequestVersionRef.current;
    saveBrowsingHistory(debouncedQuery);

    searchContent(debouncedQuery, null)
      .then((results) => {
        if (requestVersion !== searchRequestVersionRef.current) return;

        const normalized = (results as Array<Record<string, unknown>>).map((result) => {
          const rawType = String(result.type ?? result.content_type ?? '').toLowerCase();
          const kind: SearchKind =
            rawType === 'lesson'
              ? 'lesson'
              : rawType === 'content' || rawType === 'video'
                ? 'video'
                : rawType === 'profile'
                  ? 'profile'
                  : 'unknown';

          const lookup = typeof result.id === 'string' ? contentLookupRef.current[result.id] : undefined;
          const mediaUrl =
            (typeof result.media_url === 'string' ? result.media_url : undefined) ??
            (typeof result.mediaUrl === 'string' ? result.mediaUrl : undefined) ??
            lookup?.media_url ??
            undefined;
          const thumbnailUrl =
            (typeof result.thumbnail_url === 'string' ? result.thumbnail_url : undefined) ??
            (typeof result.thumbnailUrl === 'string' ? result.thumbnailUrl : undefined) ??
            lookup?.thumbnail_url ??
            mediaUrl;

          return {
            id: String(result.id ?? ''),
            title: String(result.title ?? lookup?.title ?? 'Untitled'),
            snippet:
              (typeof result.snippet === 'string' ? result.snippet : undefined) ??
              lookup?.description ??
              undefined,
            kind,
            thumbnailUrl,
            mediaUrl,
          } as SearchResultItem;
        });

        setSearchResults(normalized);
      })
      .catch((error) => {
        if (requestVersion !== searchRequestVersionRef.current) return;
        console.warn('Search failed', error);
        setSearchResults([]);
      })
      .finally(() => {
        if (requestVersion === searchRequestVersionRef.current) {
          setIsSearching(false);
        }
      });
  }, [debouncedQuery]);

  const recentHistory = useMemo(
    () =>
      [...browsingHistory]
        .sort((a, b) => {
          const dateA = new Date(a.searched_at.replace(' ', 'T')).getTime();
          const dateB = new Date(b.searched_at.replace(' ', 'T')).getTime();
          return dateB - dateA;
        })
        .slice(0, 5),
    [browsingHistory]
  );

  const videoResults = useMemo(
    () => searchResults.filter((result) => result.kind === 'video'),
    [searchResults]
  );

  const lessonResults = useMemo(
    () => searchResults.filter((result) => result.kind === 'lesson'),
    [searchResults]
  );

  const loadBrowsingHistory = async () => {
    try {
      const history = await fetchBrowsingHistory();
      setBrowsingHistory(history);
    } catch (error) {
      console.warn('Failed to load browsing history', error);
    }
  };

  const searchFeedContents = useMemo<Content[]>(() => {
    const now = new Date().toISOString();
    return videoResults.map((result) => {
      const existing = contentLookup[result.id];
      if (existing) {
        return {
          ...existing,
          title: existing.title || result.title,
          description: existing.description ?? result.snippet ?? null,
          media_url: existing.media_url ?? result.mediaUrl ?? existing.thumbnail_url ?? result.thumbnailUrl ?? null,
          thumbnail_url:
            existing.thumbnail_url ?? result.thumbnailUrl ?? existing.media_url ?? result.mediaUrl ?? null,
        };
      }

      return {
        id: result.id,
        creator_id: '',
        title: result.title,
        description: result.snippet ?? null,
        content_type: result.mediaUrl ? 'video' : 'image',
        media_url: result.mediaUrl ?? result.thumbnailUrl ?? null,
        thumbnail_url: result.thumbnailUrl ?? result.mediaUrl ?? null,
        category_id: null,
        status: 'approved',
        learning_objective: null,
        origin_explanation: null,
        definition_literal: null,
        definition_used: null,
        older_version_reference: null,
        educational_value_votes: 0,
        view_count: 0,
        is_featured: false,
        reviewed_by: null,
        reviewed_at: null,
        review_feedback: null,
        created_at: now,
        updated_at: now,
      };
    });
  }, [videoResults, contentLookup]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const query = searchQuery.trim();
    if (!query) return;
    searchRequestVersionRef.current += 1;
    setIsSearching(true);
    setShowHistory(false);
    setDebouncedQuery(query);
  };

  const applyHistorySearch = (query: string) => {
    const text = query.trim();
    if (!text) return;
    searchRequestVersionRef.current += 1;
    setIsSearching(true);
    setSearchQuery(text);
    setDebouncedQuery(text);
    setShowHistory(false);
  };

  const removeHistoryItem = async (id: string) => {
    try {
      await clearBrowsingHistory(id);
      setBrowsingHistory((prev) => prev.filter((item) => item.id !== id));
    } catch (error) {
      console.warn('Failed to delete history item', error);
    }
  };

  const openVideoViewer = (mode: 'search' | 'recommendations', index: number) => {
    setVideoViewerState({ mode, index });
  };

  const viewerContents = videoViewerState?.mode === 'recommendations' ? recommendedContents : searchFeedContents;
  const viewerLabel = videoViewerState?.mode === 'recommendations' ? 'Back to Explore' : 'Back to Search';
  const viewerCountLabel =
    videoViewerState?.mode === 'recommendations'
      ? `${recommendedContents.length} recommended`
      : `${videoResults.length} videos`;

  if (videoViewerState !== null) {
    return (
      <MainLayout fullScreen>
        <div className="sticky top-0 z-30 h-12 flex items-center justify-between gap-2 px-4 border-b border-mainAlt bg-main dark:bg-mainDark">
          <Button
            variant="ghost"
            onClick={() => setVideoViewerState(null)}
            className="text-mainAccent dark:text-white hover:bg-mainAlt"
          >
            <ArrowLeft className="h-4 w-4 mr-2" />
            {viewerLabel}
          </Button>
          <span className="text-sm text-mainAccent">{viewerCountLabel}</span>
        </div>
        <FeedContainer
          contents={viewerContents}
          initialIndex={videoViewerState.index}
          containerClassName="h-[calc(100dvh-var(--bottom-nav-height)-var(--safe-area-bottom)-3rem)] md:h-[calc(100dvh-4rem-3rem)] md:!mt-0"
        />
      </MainLayout>
    );
  }

  return (
    <MainLayout>
      <div className="container max-w-4xl mx-auto px-4 py-6 md:py-8 pb-safe">
        <div className="mb-6">
          <h1 className="text-2xl font-bold mb-4">Explore</h1>

          <div className="flex items-center gap-2">
            <form onSubmit={handleSearch} className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-muted-foreground" />
              <Input
                type="search"
                placeholder="Search videos and lessons..."
                value={searchQuery}
                onChange={(e) => {
                  const next = e.target.value;
                  setSearchQuery(next);
                  searchRequestVersionRef.current += 1;
                  setSearchResults([]);

                  if (!next.trim()) {
                    setShowHistory(true);
                    setDebouncedQuery('');
                    setIsSearching(false);
                    loadBrowsingHistory();
                    return;
                  }

                  setShowHistory(false);
                  setIsSearching(true);
                }}
                className="pl-10 pr-4 h-12 rounded-xl"
              />
            </form>
          </div>

          {showHistory && !searchQuery.trim() && (
            <Card className="mt-3 bg-mainDark/70 border border-mainAlt/60">
              <CardContent className="p-3 space-y-2">
                <p className="text-xs uppercase tracking-wide text-mainAccent">Recent (Top 5)</p>
                {recentHistory.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No recent history yet.</p>
                ) : (
                  recentHistory.map((item) => {
                    return (
                      <div key={item.id} className="flex items-center justify-between rounded-lg px-2 py-2 hover:bg-mainAlt/30 transition-colors">
                        <button
                          type="button"
                          className="text-sm text-mainAccent dark:text-white truncate pr-3 flex-1 text-left"
                          onClick={() => applyHistorySearch(item.query)}
                        >
                          {item.query ?? 'Untitled'}
                        </button>
                        <button
                          type="button"
                          onClick={() => removeHistoryItem(item.id)}
                          aria-label={`Delete ${item.query ?? 'history item'}`}
                          className="text-red-400 hover:text-red-600 ml-2"
                        >
                          x
                        </button>
                      </div>
                    );
                  })
                )}
              </CardContent>
            </Card>
          )}
        </div>

        {searchQuery.trim() ? (
          <div>
            <h2 className="font-semibold text-muted-foreground text-sm mb-3">
              Search Results {isSearching ? '(loading...)' : ''}
            </h2>
            <Tabs value={searchTab} onValueChange={(value) => setSearchTab(value as 'videos' | 'lessons')}>
              <TabsList className="mb-4 grid h-auto w-full grid-cols-2 rounded-2xl bg-muted p-1 md:h-10 md:rounded-md">
                <TabsTrigger
                  value="videos"
                  className="min-h-11 rounded-xl px-2 text-xs sm:text-sm md:min-h-0 md:rounded-sm flex items-center gap-2"
                >
                  <Video className="h-4 w-4" />
                  Videos ({videoResults.length})
                </TabsTrigger>
                <TabsTrigger
                  value="lessons"
                  className="min-h-11 rounded-xl px-2 text-xs sm:text-sm md:min-h-0 md:rounded-sm flex items-center gap-2"
                >
                  <BookOpen className="h-4 w-4" />
                  Lessons ({lessonResults.length})
                </TabsTrigger>
              </TabsList>

              <TabsContent value="videos">
                {videoResults.length === 0 && !isSearching ? (
                  <Card className="bg-muted/50">
                    <CardContent className="p-4 text-center text-sm text-muted-foreground">
                      No video results found.
                    </CardContent>
                  </Card>
                ) : (
                  <div className="grid grid-cols-2 gap-3 sm:gap-4">
                    {videoResults.map((result, index) => (
                      <CompactVideoTile
                        key={`video-${result.id}`}
                        onClick={() => openVideoViewer('search', index)}
                        title={result.title}
                        snippet={result.snippet}
                        thumbnailUrl={result.thumbnailUrl}
                        mediaUrl={result.mediaUrl}
                      />
                    ))}
                  </div>
                )}
              </TabsContent>

              <TabsContent value="lessons" className="space-y-3">
                {lessonResults.length === 0 && !isSearching ? (
                  <Card className="bg-muted/50">
                    <CardContent className="p-4 text-center text-sm text-muted-foreground">
                      No lesson results found.
                    </CardContent>
                  </Card>
                ) : (
                  lessonResults.map((result) => (
                    <Link
                      key={`lesson-${result.id}`}
                      to={`/lessons/${result.id}`}
                    >
                      <Card className={cn('transition-colors hover:bg-mainAlt/25')}>
                        <CardContent className="p-4 flex items-start gap-3">
                          <div className="w-10 h-10 rounded-lg bg-mainAlt/35 flex items-center justify-center">
                            <BookOpen className="h-5 w-5 text-mainAccent" />
                          </div>
                          <div className="min-w-0">
                            <h3 className="font-semibold">{result.title}</h3>
                            {result.snippet ? (
                              <p className="text-sm text-muted-foreground line-clamp-2">{result.snippet}</p>
                            ) : null}
                          </div>
                        </CardContent>
                      </Card>
                    </Link>
                  ))
                )}
              </TabsContent>
            </Tabs>
          </div>
        ) : (
          <div className="space-y-4">
            <div>
              <h2 className="font-semibold text-muted-foreground text-sm mb-3">Recommended for you</h2>
              {isRecommendationsLoading ? (
                <Card className="bg-muted/40">
                  <CardContent className="p-6 text-sm text-muted-foreground text-center">
                    Loading recommendations...
                  </CardContent>
                </Card>
              ) : recommendedContents.length === 0 ? (
                <Card className="bg-muted/40">
                  <CardContent className="p-6 text-sm text-muted-foreground text-center">
                    No recommendations available yet. Type to search videos and lessons.
                  </CardContent>
                </Card>
              ) : (
                <div className="grid grid-cols-2 gap-3 sm:gap-4">
                  {recommendedContents.map((content, index) => (
                    <CompactVideoTile
                      key={`recommended-${content.id}`}
                      onClick={() => openVideoViewer('recommendations', index)}
                      title={content.title}
                      snippet={content.description ?? undefined}
                      thumbnailUrl={content.thumbnail_url}
                      mediaUrl={content.media_url}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </MainLayout>
  );
};

export default ExplorePage;
