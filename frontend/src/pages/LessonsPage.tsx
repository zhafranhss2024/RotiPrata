import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Progress } from '@/components/ui/progress';
import { Label } from '@/components/ui/label';
import {
  Search,
  Clock,
  Star,
  Users,
  ChevronRight,
  Trophy,
  Flame,
  BookOpen,
  AlertCircle,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import type { Lesson } from '@/types';
import { fetchLessonProgress, fetchLessons, fetchUserStats, searchLessons } from '@/lib/api';

const LessonsPage = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const [difficultyFilter, setDifficultyFilter] = useState<number | undefined>();
  const [maxMinutesFilter, setMaxMinutesFilter] = useState<number | undefined>();
  const [lessons, setLessons] = useState<Lesson[]>([]);
  const [userProgress, setUserProgress] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [userStats, setUserStats] = useState({
    lessonsEnrolled: 0,
    lessonsCompleted: 0,
    currentStreak: 0,
    conceptsMastered: 0,
    hoursLearned: 0,
  });

  const loadLessonFeed = useCallback(async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const [lessonItems, progress, stats] = await Promise.all([
        fetchLessons({ difficulty: difficultyFilter, maxMinutes: maxMinutesFilter }),
        fetchLessonProgress(),
        fetchUserStats(),
      ]);
      setLessons(lessonItems);
      setUserProgress(progress);
      setUserStats({
        lessonsEnrolled: stats.lessonsEnrolled,
        lessonsCompleted: stats.lessonsCompleted,
        currentStreak: stats.currentStreak || 0,
        conceptsMastered: stats.conceptsMastered,
        hoursLearned: stats.hoursLearned || 0,
      });
    } catch (error) {
      console.warn('Failed to load lesson feed', error);
      setErrorMessage('Unable to load lessons right now. Please try again.');
    } finally {
      setLoading(false);
    }
  }, [difficultyFilter, maxMinutesFilter]);

  useEffect(() => {
    void loadLessonFeed();
  }, [loadLessonFeed]);

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
    if (!searchQuery.trim()) {
      void loadLessonFeed();
      return;
    }
    setLoading(true);
    setErrorMessage(null);
    searchLessons(searchQuery)
      .then(setLessons)
      .catch((error) => {
        console.warn('Lesson search failed', error);
        setErrorMessage('Lesson search failed. Try another keyword.');
      })
      .finally(() => setLoading(false));
  };

  const continueLearningLessons = useMemo(
    () => lessons.filter((l) => userProgress[l.id] > 0 && userProgress[l.id] < 100),
    [lessons, userProgress]
  );

  return (
    <MainLayout>
      <div className="container max-w-4xl mx-auto px-4 py-6 md:py-8 pb-safe" aria-live="polite">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold">Lesson Hub</h1>
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
          <Card><CardContent className="p-3 text-center"><BookOpen className="h-5 w-5 mx-auto mb-1 text-primary" /><p className="text-lg font-bold">{userStats.lessonsEnrolled}</p><p className="text-xs text-muted-foreground">Enrolled</p></CardContent></Card>
          <Card><CardContent className="p-3 text-center"><Trophy className="h-5 w-5 mx-auto mb-1 text-warning" /><p className="text-lg font-bold">{userStats.lessonsCompleted}</p><p className="text-xs text-muted-foreground">Completed</p></CardContent></Card>
          <Card><CardContent className="p-3 text-center"><Flame className="h-5 w-5 mx-auto mb-1 text-destructive" /><p className="text-lg font-bold">{userStats.currentStreak}</p><p className="text-xs text-muted-foreground">Day Streak</p></CardContent></Card>
          <Card className="hidden sm:block"><CardContent className="p-3 text-center"><Star className="h-5 w-5 mx-auto mb-1 text-accent" /><p className="text-lg font-bold">{userStats.conceptsMastered}</p><p className="text-xs text-muted-foreground">Mastered</p></CardContent></Card>
          <Card className="hidden sm:block"><CardContent className="p-3 text-center"><Clock className="h-5 w-5 mx-auto mb-1 text-secondary" /><p className="text-lg font-bold">{userStats.hoursLearned}h</p><p className="text-xs text-muted-foreground">Learned</p></CardContent></Card>
        </div>

        <form onSubmit={handleSearch} className="mb-4" aria-label="Search lessons">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-muted-foreground" />
            <Input type="search" placeholder="Search lessons..." value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} className="pl-10 h-12 rounded-xl" aria-label="Search lessons" />
          </div>
        </form>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-6" role="group" aria-label="Lesson filters">
          <div>
            <Label htmlFor="difficulty-filter">Difficulty</Label>
            <select
              id="difficulty-filter"
              className="w-full h-10 mt-1 rounded-md border border-input bg-background px-3"
              value={difficultyFilter ?? ''}
              onChange={(e) => setDifficultyFilter(e.target.value ? Number(e.target.value) : undefined)}
            >
              <option value="">All levels</option>
              <option value="1">Beginner</option>
              <option value="2">Intermediate</option>
              <option value="3">Advanced</option>
            </select>
          </div>
          <div>
            <Label htmlFor="duration-filter">Max duration</Label>
            <select
              id="duration-filter"
              className="w-full h-10 mt-1 rounded-md border border-input bg-background px-3"
              value={maxMinutesFilter ?? ''}
              onChange={(e) => setMaxMinutesFilter(e.target.value ? Number(e.target.value) : undefined)}
            >
              <option value="">Any duration</option>
              <option value="10">Up to 10 min</option>
              <option value="20">Up to 20 min</option>
              <option value="30">Up to 30 min</option>
            </select>
          </div>
        </div>

        {loading && <p className="text-sm text-muted-foreground mb-6">Loading lesson feed…</p>}

        {errorMessage && (
          <Card className="mb-6 border-destructive">
            <CardContent className="p-4 flex items-start gap-2 text-destructive">
              <AlertCircle className="h-5 w-5 mt-0.5" />
              <div>
                <p className="font-medium">Could not load lesson feed</p>
                <p className="text-sm">{errorMessage}</p>
                <Button variant="outline" className="mt-3" onClick={() => void loadLessonFeed()}>Retry</Button>
              </div>
            </CardContent>
          </Card>
        )}

        {!loading && !errorMessage && lessons.length === 0 && (
          <Card className="mb-6">
            <CardContent className="p-6 text-center">
              <p className="font-medium">No lessons matched your filters.</p>
              <p className="text-sm text-muted-foreground">Try another search term or broaden the filters.</p>
            </CardContent>
          </Card>
        )}

        {continueLearningLessons.length > 0 && (
          <div className="mb-8">
            <h2 className="font-semibold mb-4">Continue Learning</h2>
            {continueLearningLessons.map((lesson) => (
              <Link key={lesson.id} to={`/lessons/${lesson.id}`}>
                <Card className="mb-3 hover:shadow-md transition-shadow">
                  <CardContent className="p-4">
                    <div className="flex items-center gap-4">
                      <div className="w-16 h-16 rounded-xl gradient-primary flex items-center justify-center text-2xl">📚</div>
                      <div className="flex-1 min-w-0">
                        <h3 className="font-semibold truncate">{lesson.title}</h3>
                        <Progress value={userProgress[lesson.id]} className="h-2 mt-2" />
                        <p className="text-sm text-muted-foreground mt-1">{userProgress[lesson.id]}% complete</p>
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
          <div className="grid gap-4 sm:grid-cols-2">
            {lessons.map((lesson) => {
              const difficulty = getDifficultyLabel(lesson.difficulty_level);
              return (
                <Link key={lesson.id} to={`/lessons/${lesson.id}`}>
                  <Card className="h-full hover:shadow-lg transition-shadow overflow-hidden">
                    <div className="h-32 gradient-secondary flex items-center justify-center"><span className="text-5xl" aria-hidden="true">📖</span></div>
                    <CardContent className="p-4">
                      <div className="flex items-center gap-2 mb-2">
                        <Badge className={`${difficulty.color} text-white text-xs`}>{difficulty.label}</Badge>
                        {lesson.badge_name && <Badge variant="outline" className="text-xs">🏆 {lesson.badge_name}</Badge>}
                      </div>
                      <h3 className="font-bold text-lg mb-1">{lesson.title}</h3>
                      <p className="text-sm text-muted-foreground line-clamp-2 mb-3">{lesson.description}</p>
                      <div className="flex items-center gap-4 text-sm text-muted-foreground">
                        <span className="flex items-center gap-1"><Clock className="h-4 w-4" />{lesson.estimated_minutes}m</span>
                        <span className="flex items-center gap-1"><Star className="h-4 w-4" />{lesson.xp_reward} XP</span>
                        <span className="flex items-center gap-1"><Users className="h-4 w-4" />{lesson.completion_count}</span>
                      </div>
                    </CardContent>
                  </Card>
                </Link>
              );
            })}
          </div>
        </div>
      </div>
    </MainLayout>
  );
};

export default LessonsPage;
