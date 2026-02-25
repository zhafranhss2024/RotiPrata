import React, { useEffect, useMemo, useState } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Progress } from '@/components/ui/progress';
import { Separator } from '@/components/ui/separator';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Settings,
  Moon,
  Sun,
  LogOut,
  Trophy,
  Flame,
  Star,
  Clock,
  BookOpen,
  ChevronRight,
  Edit,
  Shield,
  Heart,
  MessageCircle,
  Play,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuthContext } from '@/contexts/AuthContext';
import { useThemeContext } from '@/contexts/ThemeContext';
import type { Profile, UserAchievement } from '@/types';
import { fetchAchievements, fetchBrowsingHistory, fetchProfile, fetchUserStats, type GetHistoryDTO } from '@/lib/api';
import { getLikeHistory, getWatchHistory, type LocalActivityItem } from '@/lib/activityHistory';

// Backend: /api/users/me, /api/users/me/stats, /api/users/me/achievements
// Watch history is merged from /api/users/me/history and local feed watch events.

const mapRemoteWatchHistory = (items: GetHistoryDTO[]): LocalActivityItem[] =>
  items
    .map((item) => {
      const itemId = item.lesson_id ?? item.content_id ?? item.item_id;
      if (!itemId) return null;

      return {
        id: `${item.lesson_id ? 'lesson' : 'content'}-${itemId}`,
        itemId,
        title: item.title || 'Untitled',
        itemType: item.lesson_id ? 'lesson' : 'content',
        actedAt: item.viewed_at,
      } as LocalActivityItem;
    })
    .filter((item): item is LocalActivityItem => item !== null);

const mergeWatchHistory = (localItems: LocalActivityItem[], remoteItems: LocalActivityItem[]) => {
  const byId = new Map<string, LocalActivityItem>();

  [...localItems, ...remoteItems].forEach((item) => {
    const existing = byId.get(item.id);
    if (!existing || new Date(item.actedAt).getTime() > new Date(existing.actedAt).getTime()) {
      byId.set(item.id, item);
    }
  });

  return Array.from(byId.values())
    .sort((a, b) => new Date(b.actedAt).getTime() - new Date(a.actedAt).getTime())
    .slice(0, 20);
};

const ProfilePage = () => {
  const { isAuthenticated, logout, isAdmin, isContributor } = useAuthContext();
  const { toggleTheme, isDark } = useThemeContext();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [achievements, setAchievements] = useState<UserAchievement[]>([]);
  const [likeHistory, setLikeHistory] = useState<LocalActivityItem[]>([]);
  const [watchHistory, setWatchHistory] = useState<LocalActivityItem[]>([]);
  const [stats, setStats] = useState({
    lessonsEnrolled: 0,
    lessonsCompleted: 0,
    quizzesTaken: 0,
    averageScore: 0,
    conceptsMastered: 0,
  });

  useEffect(() => {
    if (!isAuthenticated) return;

    fetchProfile()
      .then(setProfile)
      .catch((error) => console.warn('Failed to load profile', error));

    fetchAchievements()
      .then(setAchievements)
      .catch((error) => console.warn('Failed to load achievements', error));

    fetchUserStats()
      .then((userStats) =>
        setStats({
          lessonsEnrolled: userStats.lessonsEnrolled,
          lessonsCompleted: userStats.lessonsCompleted,
          quizzesTaken: userStats.quizzesTaken || 0,
          averageScore: userStats.averageScore || 0,
          conceptsMastered: userStats.conceptsMastered,
        })
      )
      .catch((error) => console.warn('Failed to load user stats', error));

    const localLikes = getLikeHistory();
    const localWatches = getWatchHistory();
    setLikeHistory(localLikes);

    fetchBrowsingHistory()
      .then((remoteHistory) => {
        const mapped = mapRemoteWatchHistory(remoteHistory);
        setWatchHistory(mergeWatchHistory(localWatches, mapped));
      })
      .catch((error) => {
        console.warn('Failed to load watch history', error);
        setWatchHistory(localWatches);
      });
  }, [isAuthenticated]);

  const handleLogout = async () => {
    await logout();
  };

  const commentHistory: LocalActivityItem[] = useMemo(() => [], []);

  if (!isAuthenticated) {
    return (
      <MainLayout>
        <div className="container max-w-md mx-auto px-4 py-16 text-center">
          <div className="mb-8">
            <div className="w-20 h-20 mx-auto mb-4 gradient-primary rounded-full flex items-center justify-center">
              <span className="text-4xl">🥞</span>
            </div>
            <h1 className="text-2xl font-bold mb-2">Join Rotiprata</h1>
            <p className="text-muted-foreground">
              Create an account to track your progress, earn badges, and learn Gen Alpha culture!
            </p>
          </div>

          <div className="space-y-3">
            <Button asChild className="w-full" size="lg">
              <Link to="/login">Sign In</Link>
            </Button>
            <Button asChild variant="outline" className="w-full" size="lg">
              <Link to="/register">Create Account</Link>
            </Button>
          </div>
        </div>
      </MainLayout>
    );
  }

  if (!profile) {
    return (
      <MainLayout>
        <div className="container max-w-md mx-auto px-4 py-16 text-center text-muted-foreground">
          Loading profile...
        </div>
      </MainLayout>
    );
  }

  const displayName = profile.display_name || 'User';
  const displayInitial = displayName ? displayName[0] : 'U';

  return (
    <MainLayout>
      <div className="container max-w-2xl mx-auto px-4 py-6 md:py-8 pb-safe">
        <div className="flex items-start justify-between mb-6">
          <div className="flex items-center gap-4">
            <Avatar className="w-20 h-20">
              <AvatarImage src={profile.avatar_url || undefined} />
              <AvatarFallback className="gradient-primary text-white text-2xl">{displayInitial}</AvatarFallback>
            </Avatar>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-bold">{displayName}</h1>
                {profile.is_verified && (
                  <Badge className="bg-secondary text-secondary-foreground">Verified</Badge>
                )}
              </div>
              {profile.display_name && <p className="text-muted-foreground">@{profile.display_name}</p>}
              {profile.bio && <p className="text-sm mt-1">{profile.bio}</p>}

              <div className="flex gap-2 mt-2">
                {isAdmin() && <Badge variant="destructive">Admin</Badge>}
                {isContributor() && <Badge variant="secondary">User</Badge>}
              </div>
            </div>
          </div>

          <Link to="/profile/edit">
            <Button variant="ghost" size="icon">
              <Edit className="h-5 w-5" />
            </Button>
          </Link>
        </div>

        <Tabs defaultValue="overview">
          <TabsList className="w-full grid grid-cols-2 mb-6">
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="activity">Activity</TabsTrigger>
          </TabsList>

          <TabsContent value="overview">
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
              <Card>
                <CardContent className="p-3 text-center">
                  <Flame className="h-6 w-6 mx-auto mb-1 text-destructive" />
                  <p className="text-2xl font-bold">{profile.current_streak}</p>
                  <p className="text-xs text-muted-foreground">Day Streak</p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="p-3 text-center">
                  <Trophy className="h-6 w-6 mx-auto mb-1 text-warning" />
                  <p className="text-2xl font-bold">{achievements.length}</p>
                  <p className="text-xs text-muted-foreground">Badges</p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="p-3 text-center">
                  <Star className="h-6 w-6 mx-auto mb-1 text-primary" />
                  <p className="text-2xl font-bold">{profile.reputation_points}</p>
                  <p className="text-xs text-muted-foreground">Rep Points</p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="p-3 text-center">
                  <Clock className="h-6 w-6 mx-auto mb-1 text-secondary" />
                  <p className="text-2xl font-bold">{profile.total_hours_learned}h</p>
                  <p className="text-xs text-muted-foreground">Learned</p>
                </CardContent>
              </Card>
            </div>

            <Card className="mb-6">
              <CardHeader>
                <CardTitle className="text-lg flex items-center gap-2">
                  <BookOpen className="h-5 w-5" />
                  Learning Progress
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <div className="flex justify-between text-sm mb-1">
                    <span>Lessons Completed</span>
                    <span>{stats.lessonsCompleted}/{stats.lessonsEnrolled}</span>
                  </div>
                  <Progress
                    value={stats.lessonsEnrolled ? (stats.lessonsCompleted / stats.lessonsEnrolled) * 100 : 0}
                  />
                </div>
                <div className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <p className="text-muted-foreground">Quizzes Taken</p>
                    <p className="font-semibold">{stats.quizzesTaken}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground">Average Score</p>
                    <p className="font-semibold">{stats.averageScore}%</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground">Concepts Mastered</p>
                    <p className="font-semibold">{stats.conceptsMastered}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground">Longest Streak</p>
                    <p className="font-semibold">{profile.longest_streak} days</p>
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card className="mb-6">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle className="text-lg flex items-center gap-2">
                    <Trophy className="h-5 w-5" />
                    Achievements
                  </CardTitle>
                  <Link to="/profile/achievements" className="text-sm text-primary">
                    View All
                  </Link>
                </div>
              </CardHeader>
              <CardContent>
                <div className="flex gap-4 overflow-x-auto pb-2 scrollbar-hide">
                  {achievements.map((achievement) => (
                    <div key={achievement.id} className="flex-shrink-0 text-center">
                      <div className="w-16 h-16 rounded-full gradient-primary flex items-center justify-center mb-2">
                        <span className="text-2xl">🏆</span>
                      </div>
                      <p className="text-xs font-medium max-w-16 truncate">{achievement.achievement_name}</p>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardContent className="p-0">
                <button
                  onClick={toggleTheme}
                  className="w-full flex items-center justify-between p-4 hover:bg-muted/50 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    {isDark ? <Moon className="h-5 w-5" /> : <Sun className="h-5 w-5" />}
                    <span>Theme</span>
                  </div>
                  <Badge variant="secondary">{isDark ? 'Dark' : 'Light'}</Badge>
                </button>

                <Separator />

                <Link to="/profile/settings" className="flex items-center justify-between p-4 hover:bg-muted/50 transition-colors">
                  <div className="flex items-center gap-3">
                    <Settings className="h-5 w-5" />
                    <span>Settings</span>
                  </div>
                  <ChevronRight className="h-5 w-5 text-muted-foreground" />
                </Link>

                {isAdmin() && (
                  <>
                    <Separator />
                    <Link to="/admin" className="flex items-center justify-between p-4 hover:bg-muted/50 transition-colors">
                      <div className="flex items-center gap-3">
                        <Shield className="h-5 w-5 text-destructive" />
                        <span>Admin Panel</span>
                      </div>
                      <ChevronRight className="h-5 w-5 text-muted-foreground" />
                    </Link>
                  </>
                )}

                <Separator />

                <button
                  onClick={handleLogout}
                  className="w-full flex items-center gap-3 p-4 text-destructive hover:bg-muted/50 transition-colors"
                >
                  <LogOut className="h-5 w-5" />
                  <span>Log Out</span>
                </button>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="activity" className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <Heart className="h-4 w-4" />
                  Like History
                </CardTitle>
              </CardHeader>
              <CardContent>
                {likeHistory.length > 0 ? (
                  <div className="space-y-3">
                    {likeHistory.map((item) => (
                      <Link key={item.id} to={`/content/${item.itemId}`}>
                        <div className="rounded-md border p-3 hover:bg-muted/50 transition-colors">
                          <p className="font-medium">{item.title}</p>
                          <p className="text-xs text-muted-foreground">{new Date(item.actedAt).toLocaleString()}</p>
                        </div>
                      </Link>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">No likes yet.</p>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <MessageCircle className="h-4 w-4" />
                  Comment History
                </CardTitle>
              </CardHeader>
              <CardContent>
                {commentHistory.length > 0 ? (
                  <div className="space-y-3">
                    {commentHistory.map((item) => (
                      <div key={item.id} className="rounded-md border p-3">
                        <p className="font-medium">{item.title}</p>
                        <p className="text-xs text-muted-foreground">{new Date(item.actedAt).toLocaleString()}</p>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">No comments yet.</p>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <Play className="h-4 w-4" />
                  Watch History
                </CardTitle>
              </CardHeader>
              <CardContent>
                {watchHistory.length > 0 ? (
                  <div className="space-y-3">
                    {watchHistory.map((item) => (
                      <Link
                        key={item.id}
                        to={item.itemType === 'lesson' ? `/lessons/${item.itemId}` : `/content/${item.itemId}`}
                      >
                        <div className="rounded-md border p-3 hover:bg-muted/50 transition-colors">
                          <p className="font-medium">{item.title}</p>
                          <p className="text-xs text-muted-foreground">{new Date(item.actedAt).toLocaleString()}</p>
                        </div>
                      </Link>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">No watch history yet.</p>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </MainLayout>
  );
};

export default ProfilePage;
