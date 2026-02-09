import React, { useEffect, useState } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Progress } from '@/components/ui/progress';
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
import { fetchLessonProgress, fetchLessons, fetchUserStats, searchLessons } from '@/lib/api';

// Backend: /api/lessons, /api/users/me/lessons/progress, /api/lessons/search
// Dummy data is returned when mocks are enabled.

const LessonsPage = () => {
  const [searchQuery, setSearchQuery] = useState('');
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
    fetchLessons()
      .then(setLessons)
      .catch((error) => console.warn('Failed to load lessons', error));

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

  const getDifficultyLabel = (level: number) => {
    switch (level) {
      case 1: return { label: 'Beginner', color: 'bg-success' };
      case 2: return { label: 'Intermediate', color: 'bg-warning' };
      case 3: return { label: 'Advanced', color: 'bg-destructive' };
      default: return { label: 'Beginner', color: 'bg-success' };
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    searchLessons(searchQuery)
      .then(setLessons)
      .catch((error) => console.warn('Lesson search failed', error));
  };

  return (
    <MainLayout>
      <div className="container max-w-4xl mx-auto px-4 py-6 md:py-8 pb-safe">
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold">Lesson Hub</h1>
            <p className="text-muted-foreground">Curated learning paths</p>
          </div>
          
          {/* Progress overview button */}
          <Link to="/profile/progress">
            <Button variant="outline" className="hidden sm:flex">
              <Trophy className="h-4 w-4 mr-2" />
              My Progress
            </Button>
          </Link>
        </div>

        {/* Quick Stats */}
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

        {/* Search */}
        <form onSubmit={handleSearch} className="mb-6">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-muted-foreground" />
            <Input
              type="search"
              placeholder="Search lessons..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10 h-12 rounded-xl"
            />
          </div>
        </form>

        {/* Continue Learning */}
        {Object.values(userProgress).some(p => p > 0 && p < 100) && (
          <div className="mb-8">
            <h2 className="font-semibold mb-4">Continue Learning</h2>
            {lessons.filter(l => userProgress[l.id] > 0 && userProgress[l.id] < 100).map((lesson) => (
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

        {/* All Lessons */}
        <div>
          <h2 className="font-semibold mb-4">All Lessons</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            {lessons.map((lesson) => {
              const difficulty = getDifficultyLabel(lesson.difficulty_level);
              
              return (
                <Link key={lesson.id} to={`/lessons/${lesson.id}`}>
                  <Card className="h-full hover:shadow-lg transition-shadow overflow-hidden">
                    {/* Header Image */}
                    <div className="h-32 gradient-secondary flex items-center justify-center">
                      <span className="text-5xl">📖</span>
                    </div>
                    
                    <CardContent className="p-4">
                      {/* Badges */}
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
                      
                      {/* Title & Description */}
                      <h3 className="font-bold text-lg mb-1">{lesson.title}</h3>
                      <p className="text-sm text-muted-foreground line-clamp-2 mb-3">
                        {lesson.description}
                      </p>
                      
                      {/* Meta info */}
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
        </div>
      </div>
    </MainLayout>
  );
};

export default LessonsPage;
