import React, { useEffect, useState } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Card, CardContent } from '@/components/ui/card';
import { 
  Search, 
  TrendingUp, 
  Clock, 
  Sparkles, 
  X, 
  RefreshCw,
  BookOpen,
  Video,
  Filter,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import {
  clearBrowsingHistory,
  fetchBrowsingHistory,
  fetchRecommendations,
  fetchTrendingContent,
  searchContent,
  saveBrowsingHistory,
} from '@/lib/api';

// Backend: /api/search, /api/trending, /api/users/me/history, /api/recommendations
// If mocks are enabled, dummy data is returned.

const ExplorePage = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const [activeTab, setActiveTab] = useState('trending');
  const [selectedFilter, setSelectedFilter] = useState<string | null>(null);
  const [isSearching, setIsSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<{ id: string; content_type: string; title: string; snippet?: string }[]>([]);
  const [trendingContent, setTrendingContent] = useState<{ id: string; title: string; category: string; views: string }[]>([]);
  const [aiSuggestions, setAiSuggestions] = useState<{ id: string; title: string; items: string[] }[]>([]);
  const [browsingHistory, setBrowsingHistory] = useState<{ id: string; item_id: string; title?: string | null; content_id?: string | null; lesson_id?: string | null; viewed_at: string }[]>([]);

  useEffect(() => {
    fetchTrendingContent()
      .then(setTrendingContent)
      .catch((error) => console.warn('Failed to load trending content', error));

    fetchRecommendations()
      .then(setAiSuggestions)
      .catch((error) => console.warn('Failed to load recommendations', error));

    fetchBrowsingHistory()
      .then(setBrowsingHistory)
      .catch((error) => console.warn('Failed to load browsing history', error));
  }, []);

  const filters = ['video', 'lesson'];

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault(); 
  };

  useEffect(() => {
    if (!searchQuery.trim()) {
      setSearchResults([]);
      return;
    }

    setIsSearching(true);

  const debounceTimeout = setTimeout(() => {
    searchContent(searchQuery, selectedFilter)
      .then((results) => {
        setSearchResults(results);
      })
      .catch((error) => console.warn('Search failed', error))
      .finally(() => setIsSearching(false));
  }, 300);


    return () => clearTimeout(debounceTimeout);
  }, [searchQuery, selectedFilter]);

  const handleClearHistory = () => {
    clearBrowsingHistory()
      .then(() => setBrowsingHistory([]))
      .catch((error) => console.warn('Failed to clear history', error));
  };

  const handleRefreshSuggestions = () => {
    fetchRecommendations()
      .then(setAiSuggestions)
      .catch((error) => console.warn('Failed to refresh recommendations', error));
  };

  return (
    <MainLayout>
      <div className="container max-w-2xl mx-auto px-4 py-6 md:py-8 pb-safe">
        {/* Search Header */}
        <div className="mb-6">
          <h1 className="text-2xl font-bold mb-4">Explore</h1>
          
          <form onSubmit={handleSearch} className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-muted-foreground" />
            <Input
              type="search"
              placeholder="Search videos and lessons..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10 pr-4 h-12 rounded-xl"
            />
          </form>

          {/* Filters */}
          <div className="flex gap-2 mt-3 overflow-x-auto pb-2 scrollbar-hide">
            {filters.map((filter) => (
              <Badge
                key={filter}
                variant={selectedFilter === filter ? "default" : "outline"}
                className="cursor-pointer whitespace-nowrap"
                onClick={() => setSelectedFilter(selectedFilter === filter ? null : filter)}
              >
                {filter}
              </Badge>
            ))}
          </div>
        </div>

        {/* Search Results (dummy in mock mode) */}
        {searchQuery.trim() && (
          <div className="mb-6">
            <h2 className="font-semibold text-muted-foreground text-sm mb-3">
              Search Results {isSearching ? '(loading...)' : ''}
            </h2>
            {searchResults.length === 0 && !isSearching ? (
              <Card className="bg-muted/50">
                <CardContent className="p-4 text-center text-sm text-muted-foreground">
                  No results found.
                </CardContent>
              </Card>
            ) : (
              <div className="space-y-3">
                {searchResults.map((result) => (
                  <Link
                    key={`${result.content_type}-${result.id}`}
                    to={result.content_type === 'lesson' ? `/lessons/${result.id}` : `/content/${result.id}`}
                    onClick={() => {
                        saveBrowsingHistory(
                          result.content_type === 'lesson' ? undefined : result.id,
                          result.content_type === 'lesson' ? result.id : undefined,
                          result.title || 'Untitled',
                        );
                      }}
                  >
                    <Card className="hover:bg-muted/50 transition-colors">
                      <CardContent className="p-4">
                        <div className="flex items-center gap-2 text-xs text-muted-foreground mb-1">
                          <Badge variant="secondary" className="text-xs">
                            {result.content_type}
                          </Badge>
                        </div>
                        <h3 className="font-semibold">{result.title}</h3>
                        {result.snippet && (
                          <p className="text-sm text-muted-foreground">{result.snippet}</p>
                        )}
                      </CardContent>
                    </Card>
                  </Link>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Tabs */}
        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="w-full grid grid-cols-3 mb-6">
            <TabsTrigger value="trending" className="flex items-center gap-2">
              <TrendingUp className="h-4 w-4" />
              <span className="hidden sm:inline">Trending</span>
            </TabsTrigger>
            <TabsTrigger value="foryou" className="flex items-center gap-2">
              <Sparkles className="h-4 w-4" />
              <span className="hidden sm:inline">For You</span>
            </TabsTrigger>
            <TabsTrigger value="history" className="flex items-center gap-2">
              <Clock className="h-4 w-4" />
              <span className="hidden sm:inline">History</span>
            </TabsTrigger>
          </TabsList>

          {/* Trending Tab */}
          <TabsContent value="trending" className="space-y-4">
            <h2 className="font-semibold text-muted-foreground text-sm">🔥 Trending Now</h2>
            {trendingContent.map((item, index) => (
              <Link key={item.id} to={`/content/${item.id}`}>
                <Card className="hover:bg-muted/50 transition-colors">
                  <CardContent className="p-4 flex items-center gap-4">
                    <span className="text-2xl font-bold text-muted-foreground">
                      {index + 1}
                    </span>
                    <div className="flex-1">
                      <h3 className="font-semibold">{item.title}</h3>
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <Badge variant="secondary" className="text-xs">
                          {item.category}
                        </Badge>
                        <span>•</span>
                        <span>{item.views} views</span>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </TabsContent>

          {/* For You Tab */}
          <TabsContent value="foryou" className="space-y-6">
            <div className="flex items-center justify-between">
              <h2 className="font-semibold text-muted-foreground text-sm">✨ AI Suggestions</h2>
              <Button variant="ghost" size="sm" onClick={handleRefreshSuggestions}>
                <RefreshCw className="h-4 w-4 mr-2" />
                Refresh
              </Button>
            </div>
            
            {aiSuggestions.map((suggestion) => (
              <div key={suggestion.id} className="space-y-3">
                <h3 className="font-medium">{suggestion.title}</h3>
                <div className="flex flex-wrap gap-2">
                  {suggestion.items.map((item) => (
                    <Link key={item} to={`/search?q=${encodeURIComponent(item)}`}>
                      <Badge 
                        variant="outline" 
                        className="cursor-pointer hover:bg-primary hover:text-primary-foreground transition-colors"
                      >
                        {item}
                      </Badge>
                    </Link>
                  ))}
                </div>
              </div>
            ))}

            <Card className="bg-muted/50">
              <CardContent className="p-4 text-center">
                <Sparkles className="h-8 w-8 mx-auto mb-2 text-primary" />
                <p className="text-sm text-muted-foreground">
                  Keep exploring to get better personalized recommendations!
                </p>
              </CardContent>
            </Card>
          </TabsContent>

          {/* History Tab */}
          <TabsContent value="history" className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="font-semibold text-muted-foreground text-sm">📜 Recently Viewed</h2>
              <Button variant="ghost" size="sm" onClick={handleClearHistory}>
                <X className="h-4 w-4 mr-2" />
                Clear
              </Button>
            </div>
            
            {browsingHistory.length > 0 ? (
             browsingHistory.map((item) => {
                const type = item.lesson_id ? 'lesson' : 'video'; 
                const renderId = item.lesson_id ?? item.content_id;   

                return (
                  <Link key={`${item.id}-${renderId}`} to={type === 'lesson' ? `/lessons/${renderId}` : `/content/${renderId}`}>
                    <Card className="hover:bg-muted/50 transition-colors">
                      <CardContent className="p-4 flex items-center gap-4">
                        <div className="w-10 h-10 rounded-lg bg-muted flex items-center justify-center">
                          {type === 'lesson' ? (
                            <BookOpen className="h-5 w-5 text-muted-foreground" />
                          ) : (
                            <Video className="h-5 w-5 text-muted-foreground" />
                          )}
                        </div>
                        <div className="flex-1">
                          <h3 className="font-semibold">{item.title}</h3> {/* or a title if you have one */}
                          <p className="text-sm text-muted-foreground">{new Date(item.viewed_at).toLocaleString()}</p>
                        </div>
                      </CardContent>
                    </Card>
                  </Link>
                );
              })
            ) : (
              <Card className="bg-muted/50">
                <CardContent className="p-8 text-center">
                  <Clock className="h-8 w-8 mx-auto mb-2 text-muted-foreground" />
                  <p className="text-muted-foreground">No browsing history yet</p>
                </CardContent>
              </Card>
            )}
          </TabsContent>
        </Tabs>
      </div>
    </MainLayout>
  );
};

export default ExplorePage;
