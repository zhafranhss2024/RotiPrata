import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft, Award, Lock } from "lucide-react";

import { MainLayout } from "@/components/layout/MainLayout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { fetchUserBadges } from "@/lib/api";
import type { UserBadge } from "@/types";

const BadgeSection = ({
  title,
  items,
  emptyLabel,
  icon,
}: {
  title: string;
  items: UserBadge[];
  emptyLabel: string;
  icon: ReactNode;
}) => (
  <Card>
    <CardHeader>
      <CardTitle className="text-lg">{title}</CardTitle>
    </CardHeader>
    <CardContent>
      {items.length === 0 ? (
        <div className="rounded-3xl border border-dashed border-mainAlt/80 px-4 py-8 text-center text-sm text-muted-foreground">
          {emptyLabel}
        </div>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {items.map((badge) => (
            <div
              key={`${badge.lessonId ?? badge.badgeName}-${badge.earned ? "earned" : "locked"}`}
              className="rounded-3xl border border-mainAlt/80 bg-main p-4"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-[#ff4d88] via-[#ff8f6b] to-[#ffd36e] text-white">
                    {badge.badgeIconUrl ? (
                      <img src={badge.badgeIconUrl} alt={badge.badgeName} className="h-full w-full rounded-2xl object-cover" />
                    ) : (
                      icon
                    )}
                  </div>
                  <div>
                    <p className="font-semibold">{badge.badgeName}</p>
                    <p className="text-sm text-muted-foreground">{badge.lessonTitle ?? "Lesson badge"}</p>
                  </div>
                </div>
                <Badge variant={badge.earned ? "default" : "outline"}>{badge.earned ? "Earned" : "Locked"}</Badge>
              </div>
              {badge.earned && badge.earnedAt ? (
                <p className="mt-3 text-xs text-muted-foreground">
                  Earned {new Date(badge.earnedAt).toLocaleDateString()}
                </p>
              ) : null}
            </div>
          ))}
        </div>
      )}
    </CardContent>
  </Card>
);

const ProfileBadgesPage = () => {
  const [badges, setBadges] = useState<UserBadge[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchUserBadges()
      .then(setBadges)
      .catch((error) => console.warn("Failed to load badges", error))
      .finally(() => setLoading(false));
  }, []);

  const earnedBadges = useMemo(() => badges.filter((badge) => badge.earned), [badges]);
  const lockedBadges = useMemo(() => badges.filter((badge) => !badge.earned), [badges]);

  return (
    <MainLayout>
      <div className="container max-w-4xl mx-auto px-4 py-6 md:py-8 pb-safe space-y-6">
        <div className="flex items-center gap-3">
          <Button asChild variant="ghost" size="icon">
            <Link to="/profile">
              <ArrowLeft className="h-5 w-5" />
            </Link>
          </Button>
          <div>
            <h1 className="text-2xl font-bold">Badges</h1>
            <p className="text-sm text-muted-foreground">Track the lesson badges you've earned and what's still locked.</p>
          </div>
        </div>

        {loading ? (
          <Card>
            <CardContent className="py-10 text-center text-muted-foreground">Loading badges...</CardContent>
          </Card>
        ) : (
          <>
            <BadgeSection
              title={`Earned Badges (${earnedBadges.length})`}
              items={earnedBadges}
              emptyLabel="Complete lessons and pass their quizzes to start earning badges."
              icon={<Award className="h-6 w-6" />}
            />
            <BadgeSection
              title={`Locked Badges (${lockedBadges.length})`}
              items={lockedBadges}
              emptyLabel="No locked lesson badges are available right now."
              icon={<Lock className="h-6 w-6" />}
            />
            {earnedBadges.length === 0 && lockedBadges.length === 0 ? (
              <Card>
                <CardContent className="py-10 text-center text-muted-foreground">
                  No lesson badges are available yet.
                </CardContent>
              </Card>
            ) : null}
          </>
        )}
      </div>
    </MainLayout>
  );
};

export default ProfileBadgesPage;
