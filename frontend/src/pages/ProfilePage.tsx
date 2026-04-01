import React, { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  Award,
  Bookmark,
  BookOpen,
  Clapperboard,
  Flame,
  Heart,
  Settings,
  Star,
} from "lucide-react";

import { MainLayout } from "@/components/layout/MainLayout";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Progress } from "@/components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ContentDetailSheet } from "@/components/feed/ContentDetailSheet";
import { ProfileContentTile } from "@/components/profile/ProfileContentTile";
import { useAuthContext } from "@/contexts/AuthContext";
import {
  fetchProfile,
  fetchProfileContentCollection,
  fetchLeaderboard,
  fetchUserBadges,
  fetchUserStats,
} from "@/lib/api";
import type { Content, Profile, ProfileContentCollection, UserBadge } from "@/types";

const COLLECTION_ORDER: ProfileContentCollection[] = ["posted", "saved", "liked"];

const ProfilePage = () => {
  const navigate = useNavigate();
  const { isAuthenticated, isAdmin } = useAuthContext();

  const [profile, setProfile] = useState<Profile | null>(null);
  const [badges, setBadges] = useState<UserBadge[]>([]);
  const [stats, setStats] = useState({
    lessonsEnrolled: 0,
    lessonsCompleted: 0,
    quizzesTaken: 0,
    hoursLearned: 0,
  });
  const [leaderboardRank, setLeaderboardRank] = useState<number | null>(null);
  const [activeCollection, setActiveCollection] = useState<ProfileContentCollection>("posted");
  const [collectionItems, setCollectionItems] = useState<Record<ProfileContentCollection, Content[]>>({
    posted: [],
    saved: [],
    liked: [],
  });
  const [loadedCollections, setLoadedCollections] = useState<Record<ProfileContentCollection, boolean>>({
    posted: false,
    saved: false,
    liked: false,
  });
  const [collectionErrors, setCollectionErrors] = useState<Record<ProfileContentCollection, string | null>>({
    posted: null,
    saved: null,
    liked: null,
  });
  const [loadingCollection, setLoadingCollection] = useState(false);
  const [selectedContent, setSelectedContent] = useState<Content | null>(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    fetchProfile()
      .then(setProfile)
      .catch((error) => console.warn("Failed to load profile", error));

    fetchUserBadges()
      .then(setBadges)
      .catch((error) => console.warn("Failed to load badges", error));

    fetchUserStats()
      .then((userStats) =>
        setStats({
          lessonsEnrolled: userStats.lessonsEnrolled,
          lessonsCompleted: userStats.lessonsCompleted,
          quizzesTaken: userStats.quizzesTaken || 0,
          hoursLearned: userStats.hoursLearned || 0,
        })
      )
      .catch((error) => console.warn("Failed to load user stats", error));

    fetchLeaderboard(1, 1, "")
      .then((response) => setLeaderboardRank(response.currentUser?.rank ?? null))
      .catch((error) => console.warn("Failed to load leaderboard rank", error));
  }, [isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated || loadedCollections[activeCollection]) {
      return;
    }

    setLoadingCollection(true);
    setCollectionErrors((prev) => ({ ...prev, [activeCollection]: null }));
    fetchProfileContentCollection(activeCollection)
      .then((items) => {
        setCollectionItems((prev) => ({ ...prev, [activeCollection]: items }));
        setLoadedCollections((prev) => ({ ...prev, [activeCollection]: true }));
      })
      .catch((error) => {
        console.warn(`Failed to load ${activeCollection} content`, error);
        setCollectionErrors((prev) => ({
          ...prev,
          [activeCollection]: `Unable to load ${activeCollection} content right now.`,
        }));
      })
      .finally(() => setLoadingCollection(false));
  }, [activeCollection, isAuthenticated, loadedCollections]);

  const earnedBadges = useMemo(() => badges.filter((badge) => badge.earned), [badges]);
  const activeItems = collectionItems[activeCollection];
  const displayName = profile?.display_name?.trim() || "User";
  const displayInitial = displayName ? displayName[0] : "U";

  const openContent = (content: Content) => {
    if (content.content_type === "video") {
      const queueContents = activeItems.filter((item) => item.content_type === "video");
      const initialIndex = queueContents.findIndex((item) => item.id === content.id);
      navigate(`/content/${content.id}`, {
        state: {
          queueContents,
          initialIndex: Math.max(initialIndex, 0),
          returnTo: "/profile",
          backLabel: "Back to Profile",
        },
      });
      return;
    }

    setSelectedContent(content);
    setIsDetailOpen(true);
  };

  if (!isAuthenticated) {
    return (
      <MainLayout>
        <div className="container max-w-md mx-auto px-4 py-16 text-center">
          <div className="mb-8">
            <div className="w-20 h-20 mx-auto mb-4 gradient-primary rounded-full flex items-center justify-center">
              <span className="text-xl font-bold text-white">RP</span>
            </div>
            <h1 className="text-2xl font-bold mb-2">Join Rotiprata</h1>
            <p className="text-muted-foreground">
              Create an account to track progress, earn lesson badges, and build your brainrot profile.
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

  return (
    <MainLayout>
      <div className="container max-w-4xl mx-auto px-4 py-6 md:py-8 pb-safe space-y-6">
        <Card className="overflow-hidden border-mainAlt/80 bg-main shadow-sm">
          <CardContent className="p-5 md:p-6">
            <div className="flex flex-col gap-5 md:flex-row md:items-start md:justify-between">
              <div className="flex items-start gap-4">
                <Avatar className="h-24 w-24 border-4 border-mainAlt/70 shadow-sm">
                  <AvatarImage src={profile.avatar_url || undefined} />
                  <AvatarFallback className="bg-gradient-to-br from-[#ff4d88] via-[#ff6d6d] to-[#ffb56b] text-3xl text-white">
                    {displayInitial}
                  </AvatarFallback>
                </Avatar>
                <div className="space-y-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <h1 className="text-2xl font-bold">{displayName}</h1>
                    {profile.is_verified ? <Badge className="bg-secondary text-secondary-foreground">Verified</Badge> : null}
                    {isAdmin() ? <Badge variant="destructive">Admin</Badge> : null}
                  </div>
                  <p className="text-sm text-muted-foreground">@{displayName.toLowerCase().replace(/\s+/g, "_")}</p>
                  {profile.bio ? <p className="max-w-xl text-sm leading-6">{profile.bio}</p> : null}
                </div>
              </div>

              <div className="flex items-center gap-2">
                <Button asChild variant="ghost" size="icon" className="rounded-full">
                  <Link to="/profile/settings" aria-label="Open profile settings">
                    <Settings className="h-5 w-5" />
                  </Link>
                </Button>
              </div>
            </div>

            <div className="mt-6 grid grid-cols-2 gap-3 md:grid-cols-4">
              <div className="rounded-3xl border border-mainAlt/80 bg-main p-4 text-center">
                <Flame className="mx-auto mb-2 h-5 w-5 text-destructive" />
                <p className="text-2xl font-bold">{profile.current_streak}</p>
                <p className="text-xs text-muted-foreground">Day Streak</p>
              </div>
              <Link
                to="/profile/badges"
                className="rounded-3xl border border-mainAlt/80 bg-main p-4 text-center transition hover:border-primary/50 hover:bg-muted/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                aria-label="Open badges page"
              >
                <Award className="mx-auto mb-2 h-5 w-5 text-yellow-500" />
                <p className="text-2xl font-bold">{earnedBadges.length}</p>
                <p className="text-xs text-muted-foreground">Badges</p>
              </Link>
              <div className="rounded-3xl border border-mainAlt/80 bg-main p-4 text-center">
                <Star className="mx-auto mb-2 h-5 w-5 text-primary" />
                <p className="text-2xl font-bold">{profile.reputation_points}</p>
                <p className="text-xs text-muted-foreground">XP</p>
              </div>
              <Link
                to="/leaderboard"
                className="rounded-3xl border border-mainAlt/80 bg-main p-4 text-center transition hover:border-primary/50 hover:bg-muted/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                aria-label="Open leaderboard page"
              >
                <div className="mx-auto mb-2 text-2xl leading-none">🏆</div>
                <p className="text-2xl font-bold">{leaderboardRank ? `#${leaderboardRank}` : "View"}</p>
                <p className="text-xs text-muted-foreground">
                  {leaderboardRank ? "Global rank" : "See the top XP ranks"}
                </p>
              </Link>
            </div>
          </CardContent>
        </Card>

        <Card className="border-mainAlt/80 bg-main shadow-sm">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <BookOpen className="h-5 w-5" />
              Learning Progress
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <div className="mb-1 flex justify-between text-sm">
                <span>Lessons Completed</span>
                <span>
                  {stats.lessonsCompleted}/{stats.lessonsEnrolled}
                </span>
              </div>
              <Progress value={stats.lessonsEnrolled ? (stats.lessonsCompleted / stats.lessonsEnrolled) * 100 : 0} />
            </div>
            <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-2">
              <div>
                <p className="text-muted-foreground">Longest Streak</p>
                <p className="font-semibold">{profile.longest_streak} days</p>
              </div>
              <div>
                <p className="text-muted-foreground">Lessons Enrolled</p>
                <p className="font-semibold">{stats.lessonsEnrolled}</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="border-mainAlt/80 bg-main shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="text-lg">Your Content</CardTitle>
          </CardHeader>
          <CardContent>
            <Tabs value={activeCollection} onValueChange={(value) => setActiveCollection(value as ProfileContentCollection)}>
              <TabsList className="grid h-12 w-full grid-cols-3 rounded-full bg-muted/70 p-1">
                <TabsTrigger value="posted" className="rounded-full">
                  <Clapperboard className="mr-2 h-4 w-4" />
                  Posted
                </TabsTrigger>
                <TabsTrigger value="saved" className="rounded-full">
                  <Bookmark className="mr-2 h-4 w-4" />
                  Saved
                </TabsTrigger>
                <TabsTrigger value="liked" className="rounded-full">
                  <Heart className="mr-2 h-4 w-4" />
                  Liked
                </TabsTrigger>
              </TabsList>

              {COLLECTION_ORDER.map((collection) => (
                <TabsContent key={collection} value={collection} className="mt-5">
                  {loadingCollection && collection === activeCollection && !loadedCollections[collection] ? (
                    <div className="rounded-3xl border border-dashed border-mainAlt/80 px-4 py-10 text-center text-sm text-muted-foreground">
                      Loading {collection} content...
                    </div>
                  ) : collectionErrors[collection] ? (
                    <div className="rounded-3xl border border-dashed border-mainAlt/80 px-4 py-10 text-center text-sm text-muted-foreground">
                      {collectionErrors[collection]}
                    </div>
                  ) : collectionItems[collection].length === 0 ? (
                    <div className="rounded-3xl border border-dashed border-mainAlt/80 px-4 py-10 text-center text-sm text-muted-foreground">
                      {collection === "posted"
                        ? "No uploads yet. Create your first post to fill this grid."
                        : collection === "saved"
                          ? "No saved videos yet."
                          : "No liked videos yet."}
                    </div>
                  ) : (
                    <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
                      {collectionItems[collection].map((content) => (
                        <ProfileContentTile
                          key={content.id}
                          content={content}
                          showStatus={collection === "posted"}
                          onClick={() => openContent(content)}
                        />
                      ))}
                    </div>
                  )}
                </TabsContent>
              ))}
            </Tabs>
          </CardContent>
        </Card>
      </div>

      <ContentDetailSheet content={selectedContent} open={isDetailOpen} onOpenChange={setIsDetailOpen} />
    </MainLayout>
  );
};

export default ProfilePage;
