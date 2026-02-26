import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { FeedContainer } from '@/components/feed/FeedContainer';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Card, CardContent } from '@/components/ui/card';
import { ArrowLeft, BookOpen, History, Search, Video } from 'lucide-react';
import type { Content } from '@/types';
import { fetchBrowsingHistory, fetchFeed, saveBrowsingHistory, searchContent } from '@/lib/api';
import { cn } from '@/lib/utils';

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
  const [searchTab, setSearchTab] = useState<'videos' | 'lessons'>('videos');
  const [isSearching, setIsSearching] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [searchResults, setSearchResults] = useState<SearchResultItem[]>([]);
  const [videoViewerStartIndex, setVideoViewerStartIndex] = useState<number | null>(null);
  const [contentLookup, setContentLookup] = useState<Record<string, Content>>({});
  const [browsingHistory, setBrowsingHistory] = useState<
    { id: string; item_id: string; title?: string | null; content_id?: string | null; lesson_id?: string | null; viewed_at: string }[]
  >([]);
  const searchWrapRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    fetchBrowsingHistory()
      .then(setBrowsingHistory)
      .catch((error) => console.warn('Failed to load browsing history', error));
  }, []);

  useEffect(() => {
    let active = true;
    const loadContentLookup = async () => {
      const map: Record<string, Content> = {};
      let page = 1;
      for (let i = 0; i < 5; i += 1) {
        try {
          const response = await fetchFeed(page);
          response.items.forEach((item) => {
            map[item.id] = item;
          });
          if (!response.hasMore) break;
          page += 1;
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
    const handleOutsideClick = (event: MouseEvent) => {
      if (!searchWrapRef.current) return;
      if (!searchWrapRef.current.contains(event.target as Node)) {
        setShowHistory(false);
      }
    };
    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, []);

  useEffect(() => {
    if (!searchQuery.trim()) {
      setSearchResults([]);
      setIsSearching(false);
      return;
    }

    setIsSearching(true);
    const debounceTimeout = setTimeout(() => {
      searchContent(searchQuery, null)
        .then((results) => {
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

            const lookup = typeof result.id === 'string' ? contentLookup[result.id] : undefined;
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
        .catch((error) => console.warn('Search failed', error))
        .finally(() => setIsSearching(false));
    }, 300);

    return () => clearTimeout(debounceTimeout);
  }, [searchQuery, contentLookup]);

  const recentHistory = useMemo(
    () =>
      [...browsingHistory]
        .sort((a, b) => new Date(b.viewed_at).getTime() - new Date(a.viewed_at).getTime())
        .slice(0, 4),
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
    if (searchQuery.trim()) {
      saveBrowsingHistory(searchQuery.trim());
    }
  };

  const applyHistorySearch = (item: {
    title?: string | null;
    lesson_id?: string | null;
  }) => {
    const text = (item.title ?? '').trim();
    if (!text) return;
    setSearchQuery(text);
    setShowHistory(false);
    setSearchTab(item.lesson_id ? 'lessons' : 'videos');
  };

  if (videoViewerStartIndex !== null) {
    return (
      <MainLayout fullScreen>
        <div className="sticky top-0 z-30 h-12 flex items-center justify-between gap-2 px-4 border-b border-mainAlt bg-mainDark/95 backdrop-blur">
          <Button variant="ghost" onClick={() => setVideoViewerStartIndex(null)} className="text-white hover:bg-mainAlt">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Search
          </Button>
          <span className="text-sm text-mainAccent">{videoResults.length} videos</span>
        </div>
        <FeedContainer
          contents={searchFeedContents}
          initialIndex={videoViewerStartIndex}
          containerClassName="h-[calc(100vh-var(--bottom-nav-height)-var(--safe-area-bottom)-3rem)] md:h-[calc(100vh-4rem-3rem)] md:!mt-0"
        />
      </MainLayout>
    );
  }

  return (
    <MainLayout>
      <div className="container max-w-4xl mx-auto px-4 py-6 md:py-8 pb-safe">
        <div className="mb-6" ref={searchWrapRef}>
          <h1 className="text-2xl font-bold mb-4">Explore</h1>

          <div className="flex items-center gap-2">
            <form onSubmit={handleSearch} className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-muted-foreground" />
              <Input
                type="search"
                placeholder="Search videos and lessons..."
                value={searchQuery}
                onFocus={() => {
                  if (!searchQuery.trim()) setShowHistory(true);
                }}
                onChange={(e) => {
                  const next = e.target.value;
                  setSearchQuery(next);
                  if (!next.trim()) {
                    setShowHistory(true);
                  }
                }}
                className="pl-10 pr-4 h-12 rounded-xl"
              />
            </form>
            <Button
              type="button"
              variant="outline"
              className="h-12 w-12 p-0"
              onClick={() => setShowHistory((prev) => !prev)}
              aria-label="Recent history"
            >
              <History className="h-5 w-5" />
            </Button>
          </div>

          {showHistory && !searchQuery.trim() && (
            <Card className="mt-3 bg-mainDark/70 border border-mainAlt/60">
              <CardContent className="p-3 space-y-2">
                <p className="text-xs uppercase tracking-wide text-mainAccent">Recent (Top 4)</p>
                {recentHistory.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No recent history yet.</p>
                ) : (
                  recentHistory.map((item) => {
                    return (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => applyHistorySearch(item)}
                        className="flex items-center justify-between rounded-lg px-2 py-2 hover:bg-mainAlt/30 transition-colors"
                      >
                        <span className="text-sm text-white truncate pr-3">{item.title ?? 'Untitled'}</span>
                        <span className="text-xs text-muted-foreground">
                          {new Date(item.viewed_at).toLocaleDateString()}
                        </span>
                      </button>
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
              <TabsList className="w-full grid grid-cols-2 mb-4">
                <TabsTrigger value="videos" className="flex items-center gap-2">
                  <Video className="h-4 w-4" />
                  Videos ({videoResults.length})
                </TabsTrigger>
                <TabsTrigger value="lessons" className="flex items-center gap-2">
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
                  <div className="grid grid-cols-2 gap-3">
                    {videoResults.map((result, index) => (
                      <button
                        key={`video-${result.id}`}
                        type="button"
                        className="text-left group"
                      >
                        <div className="relative aspect-[9/16] overflow-hidden rounded-2xl bg-mainDark border border-mainAlt/70">
                          {result.thumbnailUrl || result.mediaUrl ? (
                            <img
                              src={result.thumbnailUrl ?? result.mediaUrl}
                              alt={result.title}
                              className="absolute inset-0 h-full w-full object-cover"
                            />
                          ) : (
                            <div className="absolute inset-0 bg-mainDark" />
                          )}
                          <div className="absolute inset-0 bg-gradient-to-b from-mainAlt/15 via-black/15 to-black/80" />
                          <div className="absolute top-2 left-2">
                            <Badge variant="secondary" className="bg-black/45 text-white border-0">
                              Video
                            </Badge>
                          </div>
                          <div className="absolute bottom-0 left-0 right-0 p-3">
                            <h3 className="text-sm font-semibold text-white line-clamp-2">{result.title}</h3>
                            {result.snippet ? (
                              <p className="mt-1 text-xs text-white/75 line-clamp-2">{result.snippet}</p>
                            ) : null}
                          </div>
                        </div>
                      </button>
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
          <Card className="bg-muted/40">
            <CardContent className="p-6 text-sm text-muted-foreground text-center">
              Type to search videos and lessons.
            </CardContent>
          </Card>
        )}
      </div>
    </MainLayout>
  );
};

export default ExplorePage;
