import React, { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { MainLayout } from "@/components/layout/MainLayout";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import {
  BookOpen,
  Clock,
  Filter,
  Search,
  Star,
  Users,
  X,
} from "lucide-react";
import type { Lesson } from "@/types";
import {
  fetchLessonFeed,
  type LessonFeedDifficultyFilter,
  type LessonFeedDurationFilter,
  type LessonFeedSort,
} from "@/lib/api";

const difficultyOptions: { value: LessonFeedDifficultyFilter; label: string }[] = [
  { value: "all", label: "All levels" },
  { value: "beginner", label: "Beginner" },
  { value: "intermediate", label: "Intermediate" },
  { value: "advanced", label: "Advanced" },
];

const durationOptions: { value: LessonFeedDurationFilter; label: string }[] = [
  { value: "all", label: "Any length" },
  { value: "short", label: "Short (≤10 min)" },
  { value: "medium", label: "Medium (11-20 min)" },
  { value: "long", label: "Long (21+ min)" },
];

const sortOptions: { value: LessonFeedSort; label: string }[] = [
  { value: "popular", label: "Most popular" },
  { value: "newest", label: "Newest" },
  { value: "shortest", label: "Shortest" },
  { value: "highest_xp", label: "Highest XP" },
];

const getDifficultyMeta = (level: number) => {
  switch (level) {
    case 2:
      return { label: "Intermediate", color: "bg-warning" };
    case 3:
      return { label: "Advanced", color: "bg-destructive" };
    case 1:
    default:
      return { label: "Beginner", color: "bg-success" };
  }
};

const LessonsPage = () => {
  const [lessons, setLessons] = useState<Lesson[]>([]);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [searchInput, setSearchInput] = useState("");
  const [query, setQuery] = useState("");
  const [difficulty, setDifficulty] = useState<LessonFeedDifficultyFilter>("all");
  const [duration, setDuration] = useState<LessonFeedDurationFilter>("all");
  const [sort, setSort] = useState<LessonFeedSort>("popular");

  const hasActiveFilters = useMemo(
    () =>
      !!query ||
      difficulty !== "all" ||
      duration !== "all" ||
      sort !== "popular",
    [query, difficulty, duration, sort]
  );

  useEffect(() => {
    const debounce = setTimeout(() => {
      const trimmed = searchInput.trim();
      setLessons([]);
      setHasMore(true);
      setPage(1);
      setQuery(trimmed);
    }, 300);

    return () => clearTimeout(debounce);
  }, [searchInput]);

  useEffect(() => {
    let isActive = true;

    const loadLessons = async () => {
      setIsLoading(true);
      setError(null);
      try {
        const data = await fetchLessonFeed({
          query: query || undefined,
          difficulty,
          duration,
          sort,
          page,
        });
        if (!isActive) return;
        setLessons((prev) => (page === 1 ? data.items : [...prev, ...data.items]));
        setHasMore(data.hasMore);
      } catch (err) {
        if (!isActive) return;
        setError("Failed to load lessons. Please try again.");
      } finally {
        if (isActive) {
          setIsLoading(false);
        }
      }
    };

    loadLessons();

    return () => {
      isActive = false;
    };
  }, [query, difficulty, duration, sort, page]);

  const resetFeed = () => {
    setLessons([]);
    setHasMore(true);
    setPage(1);
  };

  const handleDifficultyChange = (value: string) => {
    setDifficulty(value as LessonFeedDifficultyFilter);
    resetFeed();
  };

  const handleDurationChange = (value: string) => {
    setDuration(value as LessonFeedDurationFilter);
    resetFeed();
  };

  const handleSortChange = (value: string) => {
    setSort(value as LessonFeedSort);
    resetFeed();
  };

  const handleClearFilters = () => {
    setSearchInput("");
    setQuery("");
    setDifficulty("all");
    setDuration("all");
    setSort("popular");
    resetFeed();
  };

  const handleLoadMore = () => {
    if (isLoading || !hasMore) return;
    setPage((prev) => prev + 1);
  };

  return (
    <MainLayout>
      <div className="container max-w-4xl mx-auto px-4 py-6 md:py-8 pb-safe">
        <div className="mb-6">
          <div className="flex items-center justify-between gap-3 mb-4">
            <div>
              <h1 className="text-2xl font-bold">Lesson Hub</h1>
              <p className="text-sm text-muted-foreground">
                Browse bite-sized lessons and level up fast.
              </p>
            </div>
            <Badge variant="secondary" className="hidden sm:inline-flex">
              {lessons.length} loaded
            </Badge>
          </div>

          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-muted-foreground" />
            <Input
              type="search"
              placeholder="Search lessons, slang, or meme lore..."
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              className="pl-10 pr-10 h-12 rounded-xl"
            />
            {searchInput && (
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="absolute right-2 top-1/2 -translate-y-1/2 h-8 w-8"
                onClick={() => setSearchInput("")}
              >
                <X className="h-4 w-4" />
              </Button>
            )}
          </div>

          <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-3">
            <div className="space-y-1">
              <span className="text-xs font-medium text-muted-foreground flex items-center gap-2">
                <Filter className="h-3 w-3" />
                Difficulty
              </span>
              <Select value={difficulty} onValueChange={handleDifficultyChange}>
                <SelectTrigger>
                  <SelectValue placeholder="Difficulty" />
                </SelectTrigger>
                <SelectContent>
                  {difficultyOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <span className="text-xs font-medium text-muted-foreground flex items-center gap-2">
                <Clock className="h-3 w-3" />
                Duration
              </span>
              <Select value={duration} onValueChange={handleDurationChange}>
                <SelectTrigger>
                  <SelectValue placeholder="Duration" />
                </SelectTrigger>
                <SelectContent>
                  {durationOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <span className="text-xs font-medium text-muted-foreground flex items-center gap-2">
                <Star className="h-3 w-3" />
                Sort by
              </span>
              <Select value={sort} onValueChange={handleSortChange}>
                <SelectTrigger>
                  <SelectValue placeholder="Sort" />
                </SelectTrigger>
                <SelectContent>
                  {sortOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {hasActiveFilters && (
            <div className="mt-3 flex flex-wrap items-center gap-2">
              {query && <Badge variant="outline">Query: {query}</Badge>}
              {difficulty !== "all" && <Badge variant="outline">{difficulty}</Badge>}
              {duration !== "all" && <Badge variant="outline">{duration}</Badge>}
              {sort !== "popular" && <Badge variant="outline">{sort}</Badge>}
              <Button variant="ghost" size="sm" onClick={handleClearFilters}>
                Clear filters
              </Button>
            </div>
          )}
        </div>

        {error && (
          <Card className="mb-6 border-destructive/30 bg-destructive/10">
            <CardContent className="p-4 text-sm text-destructive">
              {error}
            </CardContent>
          </Card>
        )}

        {lessons.length === 0 && isLoading ? (
          <div className="space-y-4">
            {Array.from({ length: 4 }).map((_, idx) => (
              <Card key={idx}>
                <CardContent className="p-4 flex gap-4">
                  <Skeleton className="h-14 w-14 rounded-xl" />
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-4 w-1/2" />
                    <Skeleton className="h-3 w-full" />
                    <Skeleton className="h-3 w-3/4" />
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : lessons.length === 0 ? (
          <Card className="bg-muted/50">
            <CardContent className="p-8 text-center">
              <BookOpen className="h-10 w-10 mx-auto mb-3 text-muted-foreground" />
              <h2 className="font-semibold mb-1">No lessons found</h2>
              <p className="text-sm text-muted-foreground">
                Try adjusting your filters or search terms.
              </p>
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-4">
            {lessons.map((lesson) => {
              const difficultyMeta = getDifficultyMeta(lesson.difficulty_level);
              return (
                <Link key={lesson.id} to={`/lessons/${lesson.id}`} className="block">
                  <Card className="hover:shadow-soft transition-shadow">
                    <CardContent className="p-4">
                      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                        <div className="h-16 w-16 rounded-2xl gradient-secondary flex items-center justify-center text-white text-xl font-bold">
                          {lesson.title?.charAt(0) ?? "L"}
                        </div>
                        <div className="flex-1 space-y-2">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <h3 className="text-lg font-semibold">{lesson.title}</h3>
                            <Badge className={`${difficultyMeta.color} text-white`}>
                              {difficultyMeta.label}
                            </Badge>
                          </div>
                          <p className="text-sm text-muted-foreground line-clamp-2">
                            {lesson.summary || lesson.description || "No summary yet."}
                          </p>
                          {lesson.learning_objectives && lesson.learning_objectives.length > 0 && (
                            <div className="flex flex-wrap gap-2">
                              {lesson.learning_objectives.slice(0, 2).map((objective) => (
                                <Badge key={objective} variant="outline">
                                  {objective}
                                </Badge>
                              ))}
                              {lesson.learning_objectives.length > 2 && (
                                <Badge variant="outline">+{lesson.learning_objectives.length - 2} more</Badge>
                              )}
                            </div>
                          )}
                          <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                            <span className="flex items-center gap-1">
                              <Clock className="h-3.5 w-3.5" />
                              {lesson.estimated_minutes} min
                            </span>
                            <span className="flex items-center gap-1">
                              <Star className="h-3.5 w-3.5" />
                              {lesson.xp_reward} XP
                            </span>
                            <span className="flex items-center gap-1">
                              <Users className="h-3.5 w-3.5" />
                              {lesson.completion_count} completed
                            </span>
                          </div>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                </Link>
              );
            })}
          </div>
        )}

        <div className="mt-6 flex items-center justify-center">
          {hasMore && (
            <Button
              onClick={handleLoadMore}
              disabled={isLoading}
              className="min-w-[160px]"
              variant="secondary"
            >
              {isLoading ? "Loading..." : "Load more"}
            </Button>
          )}
        </div>
      </div>
    </MainLayout>
  );
};

export default LessonsPage;
