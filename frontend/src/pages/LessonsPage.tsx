import React, { useEffect, useMemo, useState } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Progress } from '@/components/ui/progress';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Search,
  Clock,
  Star,
  Users,
  ChevronRight,
  Trophy,
  Flame,
  BookOpen,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import type { Lesson } from '@/types';
import { fetchLessonFeed, fetchLessonProgress, fetchUserStats, type LessonFeedSort } from '@/lib/api';

const PAGE_SIZE = 12;
const DEFAULT_SORT: LessonFeedSort = 'newest';
type DifficultyFilter = 'all' | '1' | '2' | '3';
type DurationFilter = 'all' | '15' | '30' | '45';

const LessonsPage = () => {
  const [searchInput, setSearchInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [difficultyFilter, setDifficultyFilter] = useState<DifficultyFilter>('all');
  const [durationFilter, setDurationFilter] = useState<DurationFilter>('all');
  const [sortBy, setSortBy] = useState<LessonFeedSort>(DEFAULT_SORT);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [isLoadingLessons, setIsLoadingLessons] = useState(true);
  const [lessonsError, setLessonsError] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);

  const [lessons, setLessons] = useState<Lesson[]>([]);
  const [userProgress, setUserProgress] = useState<Record<string, number>>({});
  const [userStats, setUserStats] = useState({
    lessonsEnrolled: 0,
    lessonsCompleted: 0,
    currentStreak: 0,
    conceptsMastered: 0,
    hoursLearned: 0,
  });

  useEffect(() => {
    fetchLessonProgress()
      .then(setUserProgress)
      .catch((error) => console.warn('Failed to load lesson progress', error));

    fetchUserStats()
      .then((stats) =>
        setUserStats({
          lessonsEnrolled: stats.lessonsEnrolled,
          lessonsCompleted: stats.lessonsCompleted,
          currentStreak: stats.currentStreak || 0,
          conceptsMastered: stats.conceptsMastered,
          hoursLearned: stats.hoursLearned || 0,
        })
      )
      .catch((error) => console.warn('Failed to load user stats', error));
  }, []);

  useEffect(() => {
    let cancelled = false;

    const difficulty = difficultyFilter === 'all' ? undefined : Number(difficultyFilter) as 1 | 2 | 3;
    const maxMinutes = durationFilter === 'all' ? undefined : Number(durationFilter);

    fetchLessonFeed({
      page,
      pageSize: PAGE_SIZE,
      q: searchQuery || undefined,
      difficulty,
      maxMinutes,
      sort: sortBy,
    })
      .then((response) => {
        if (cancelled) return;
        setLessons((prev) => (page === 1 ? response.items : [...prev, ...response.items]));
        setHasMore(response.hasMore);
      })
      .catch((error: Error) => {
        if (cancelled) return;
        if (page === 1) {
          setLessons([]);
        }
        setHasMore(false);
        setLessonsError(error.message || 'Failed to load lessons');
      })
      .finally(() => {
        if (cancelled) return;
        setIsLoadingLessons(false);
      });

    return () => {
      cancelled = true;
    };
  }, [difficultyFilter, durationFilter, page, retryKey, searchQuery, sortBy]);

  const getDifficultyLabel = (level: number) => {
    switch (level) {
      case 1:
        return { label: 'Beginner', color: 'bg-success' };
      case 2:
        return { label: 'Intermediate', color: 'bg-warning' };
      case 3:
        return { label: 'Advanced', color: 'bg-destructive' };
      default:
        return { label: 'Beginner', color: 'bg-success' };
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = searchInput.trim();
    setIsLoadingLessons(true);
    setLessonsError(null);
    if (trimmed === searchQuery && page === 1) {
      setRetryKey((value) => value + 1);
      return;
    }
    setSearchQuery(trimmed);
    setPage(1);
  };

  const handleDifficultyChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    setIsLoadingLessons(true);
    setLessonsError(null);
    setDifficultyFilter(event.target.value as DifficultyFilter);
    setPage(1);
  };

  const handleDurationChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    setIsLoadingLessons(true);
    setLessonsError(null);
    setDurationFilter(event.target.value as DurationFilter);
    setPage(1);
  };

  const handleSortChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    setIsLoadingLessons(true);
    setLessonsError(null);
    setSortBy(event.target.value as LessonFeedSort);
    setPage(1);
  };

  const handleClearFilters = () => {
    setIsLoadingLessons(true);
    setLessonsError(null);
    setSearchInput('');
    setSearchQuery('');
    setDifficultyFilter('all');
    setDurationFilter('all');
    setSortBy(DEFAULT_SORT);
    setPage(1);
    setRetryKey((value) => value + 1);
  };

  const handleRetry = () => {
    setIsLoadingLessons(true);
    setLessonsError(null);
    setPage(1);
    setRetryKey((value) => value + 1);
  };

  const handleLoadMore = () => {
    if (isLoadingLessons || !hasMore) return;
    setIsLoadingLessons(true);
    setLessonsError(null);
    setPage((current) => current + 1);
  };

  const hasActiveFilters = Boolean(
    searchQuery || difficultyFilter !== 'all' || durationFilter !== 'all' || sortBy !== DEFAULT_SORT
  );

  const continueLearningLessons = useMemo(
    () => lessons.filter((lesson) => userProgress[lesson.id] > 0 && userProgress[lesson.id] < 100),
    [lessons, userProgress]
  );

  return (
    <MainLayout>
      <main className="container max-w-4xl mx-auto px-4 py-6 md:py-8 pb-safe" aria-labelledby="lesson-hub-heading">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 id="lesson-hub-heading" className="text-2xl font-bold">Lesson Hub</h1>
            <p className="text-muted-foreground">Curated learning paths</p>
          </div>

          <Link to="/profile/progress">
            <Button variant="outline" className="hidden sm:flex">
              <Trophy className="h-4 w-4 mr-2" />
              My Progress
            </Button>
          </Link>
        </div>

        <div className="grid grid-cols-3 sm:grid-cols-5 gap-3 mb-6">
          <Card>
            <CardContent className="p-3 text-center">
              <BookOpen className="h-5 w-5 mx-auto mb-1 text-primary" />
              <p className="text-lg font-bold">{userStats.lessonsEnrolled}</p>
              <p className="text-xs text-muted-foreground">Enrolled</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-3 text-center">
              <Trophy className="h-5 w-5 mx-auto mb-1 text-warning" />
              <p className="text-lg font-bold">{userStats.lessonsCompleted}</p>
              <p className="text-xs text-muted-foreground">Completed</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-3 text-center">
              <Flame className="h-5 w-5 mx-auto mb-1 text-destructive" />
              <p className="text-lg font-bold">{userStats.currentStreak}</p>
              <p className="text-xs text-muted-foreground">Day Streak</p>
            </CardContent>
          </Card>
          <Card className="hidden sm:block">
            <CardContent className="p-3 text-center">
              <Star className="h-5 w-5 mx-auto mb-1 text-accent" />
              <p className="text-lg font-bold">{userStats.conceptsMastered}</p>
              <p className="text-xs text-muted-foreground">Mastered</p>
            </CardContent>
          </Card>
          <Card className="hidden sm:block">
            <CardContent className="p-3 text-center">
              <Clock className="h-5 w-5 mx-auto mb-1 text-secondary" />
              <p className="text-lg font-bold">{userStats.hoursLearned}h</p>
              <p className="text-xs text-muted-foreground">Learned</p>
            </CardContent>
          </Card>
        </div>

        <form onSubmit={handleSearch} className="mb-6 space-y-3" aria-label="Lesson feed filters">
          <div className="relative">
            <label htmlFor="lesson-search" className="sr-only">
              Search lessons
            </label>
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-muted-foreground" />
            <Input
              id="lesson-search"
              type="search"
              placeholder="Search lessons..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className="pl-10 h-12 rounded-xl"
            />
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="space-y-1">
              <label htmlFor="lesson-difficulty" className="text-sm font-medium">
                Difficulty
              </label>
              <select
                id="lesson-difficulty"
                value={difficultyFilter}
                onChange={handleDifficultyChange}
                className="w-full h-10 rounded-md border border-input bg-background px-3 text-sm"
              >
                <option value="all">All levels</option>
                <option value="1">Beginner</option>
                <option value="2">Intermediate</option>
                <option value="3">Advanced</option>
              </select>
            </div>
            <div className="space-y-1">
              <label htmlFor="lesson-duration" className="text-sm font-medium">
                Max duration
              </label>
              <select
                id="lesson-duration"
                value={durationFilter}
                onChange={handleDurationChange}
                className="w-full h-10 rounded-md border border-input bg-background px-3 text-sm"
              >
                <option value="all">Any length</option>
                <option value="15">15 min</option>
                <option value="30">30 min</option>
                <option value="45">45 min</option>
              </select>
            </div>
            <div className="space-y-1">
              <label htmlFor="lesson-sort" className="text-sm font-medium">
                Sort by
              </label>
              <select
                id="lesson-sort"
                value={sortBy}
                onChange={handleSortChange}
                className="w-full h-10 rounded-md border border-input bg-background px-3 text-sm"
              >
                <option value="newest">Newest</option>
                <option value="popular">Most popular</option>
                <option value="duration_asc">Shortest first</option>
                <option value="duration_desc">Longest first</option>
              </select>
            </div>
          </div>
          <div className="flex gap-2">
            <Button type="submit">Apply filters</Button>
            <Button type="button" variant="outline" onClick={handleClearFilters}>
              Reset
            </Button>
          </div>
        </form>

        <p className="mb-4 text-sm text-muted-foreground" role="status" aria-live="polite">
          {isLoadingLessons
            ? 'Loading lessons...'
            : lessonsError
              ? 'Unable to load lessons.'
              : `${lessons.length} lesson${lessons.length === 1 ? '' : 's'} loaded${hasMore ? ', more available.' : '.'}`}
        </p>

        {lessonsError && (
          <Alert variant="destructive" className="mb-6">
            <AlertTitle>Couldn&apos;t load lesson feed</AlertTitle>
            <AlertDescription className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <span>{lessonsError}</span>
              <Button type="button" variant="outline" onClick={handleRetry}>
                Retry
              </Button>
            </AlertDescription>
          </Alert>
        )}

        {!lessonsError && continueLearningLessons.length > 0 && (
          <div className="mb-8">
            <h2 className="font-semibold mb-4">Continue Learning</h2>
            {continueLearningLessons.map((lesson) => (
              <Link key={lesson.id} to={`/lessons/${lesson.id}`}>
                <Card className="mb-3 hover:shadow-md transition-shadow">
                  <CardContent className="p-4">
                    <div className="flex items-center gap-4">
                      <div className="w-16 h-16 rounded-xl gradient-primary flex items-center justify-center text-2xl">
                        📚
                      </div>
                      <div className="flex-1 min-w-0">
                        <h3 className="font-semibold truncate">{lesson.title}</h3>
                        <Progress value={userProgress[lesson.id]} className="h-2 mt-2" />
                        <p className="text-sm text-muted-foreground mt-1">
                          {userProgress[lesson.id]}% complete
                        </p>
                      </div>
                      <ChevronRight className="h-5 w-5 text-muted-foreground flex-shrink-0" />
                    </div>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
        )}

        <div>
          <h2 className="font-semibold mb-4">All Lessons</h2>
          {isLoadingLessons && page === 1 ? (
            <div className="grid gap-4 sm:grid-cols-2" aria-hidden="true">
              {Array.from({ length: 4 }).map((_, index) => (
                <Card key={index} className="overflow-hidden">
                  <Skeleton className="h-32 w-full rounded-none" />
                  <CardContent className="p-4 space-y-3">
                    <Skeleton className="h-4 w-24" />
                    <Skeleton className="h-5 w-3/4" />
                    <Skeleton className="h-4 w-full" />
                    <Skeleton className="h-4 w-2/3" />
                  </CardContent>
                </Card>
              ))}
            </div>
          ) : lessons.length === 0 ? (
            <Card>
              <CardContent className="p-6 text-center">
                <h3 className="font-semibold text-lg mb-2">No lessons found</h3>
                <p className="text-muted-foreground mb-4">
                  {hasActiveFilters
                    ? 'Try adjusting your filters or search term.'
                    : 'No published lessons are available yet.'}
                </p>
                {hasActiveFilters && (
                  <Button type="button" variant="outline" onClick={handleClearFilters}>
                    Clear filters
                  </Button>
                )}
              </CardContent>
            </Card>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              {lessons.map((lesson) => {
                const difficulty = getDifficultyLabel(lesson.difficulty_level);

                return (
                  <Link key={lesson.id} to={`/lessons/${lesson.id}`}>
                    <Card className="h-full hover:shadow-lg transition-shadow overflow-hidden">
                      <div className="h-32 gradient-secondary flex items-center justify-center">
                        <span className="text-5xl" aria-hidden="true">📖</span>
                      </div>

                      <CardContent className="p-4">
                        <div className="flex items-center gap-2 mb-2">
                          <Badge className={`${difficulty.color} text-white text-xs`}>
                            {difficulty.label}
                          </Badge>
                          {lesson.badge_name && (
                            <Badge variant="outline" className="text-xs">
                              🏆 {lesson.badge_name}
                            </Badge>
                          )}
                        </div>

                        <h3 className="font-bold text-lg mb-1">{lesson.title}</h3>
                        <p className="text-sm text-muted-foreground line-clamp-2 mb-3">
                          {lesson.description}
                        </p>

                        <div className="flex items-center gap-4 text-sm text-muted-foreground">
                          <span className="flex items-center gap-1">
                            <Clock className="h-4 w-4" />
                            {lesson.estimated_minutes}m
                          </span>
                          <span className="flex items-center gap-1">
                            <Star className="h-4 w-4" />
                            {lesson.xp_reward} XP
                          </span>
                          <span className="flex items-center gap-1">
                            <Users className="h-4 w-4" />
                            {lesson.completion_count}
                          </span>
                        </div>
                      </CardContent>
                    </Card>
                  </Link>
                );
              })}
            </div>
          )}

          {isLoadingLessons && page > 1 && (
            <div className="flex justify-center mt-4">
              <span className="text-sm text-muted-foreground">Loading more lessons...</span>
            </div>
          )}

          {!lessonsError && hasMore && lessons.length > 0 && (
            <div className="flex justify-center mt-6">
              <Button type="button" onClick={handleLoadMore} disabled={isLoadingLessons}>
                Load more lessons
              </Button>
            </div>
          )}
        </div>
      </main>
    </MainLayout>
  );
};

export default LessonsPage;
