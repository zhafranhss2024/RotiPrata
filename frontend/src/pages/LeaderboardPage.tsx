import React, { useDeferredValue, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Flame, Search, Star, Trophy } from "lucide-react";

import { MainLayout } from "@/components/layout/MainLayout";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { fetchLeaderboard } from "@/lib/api";
import type { LeaderboardEntry, LeaderboardResponse } from "@/types";

const PAGE_SIZE = 20;

const formatLeaderboardName = (entry: LeaderboardEntry) =>
  entry.displayName?.trim() || `user-${entry.userId.slice(0, 8)}`;

const formatLeaderboardHandle = (entry: LeaderboardEntry) => `@${formatLeaderboardName(entry)}`;

const formatLeaderboardInitial = (entry: LeaderboardEntry) => formatLeaderboardName(entry).slice(0, 1).toUpperCase();

const LeaderboardPage = () => {
  const [leaderboard, setLeaderboard] = useState<LeaderboardResponse | null>(null);
  const [page, setPage] = useState(1);
  const [searchQuery, setSearchQuery] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const deferredSearchQuery = useDeferredValue(searchQuery);

  useEffect(() => {
    let active = true;

    fetchLeaderboard(page, PAGE_SIZE, deferredSearchQuery)
      .then((response) => {
        if (!active) {
          return;
        }
        setLeaderboard(response);
      })
      .catch((error) => {
        console.warn("Failed to load leaderboard", error);
        if (active) {
          setErrorMessage("Unable to load the leaderboard right now.");
          setLeaderboard(null);
        }
      })
      .finally(() => {
        if (active) {
          setIsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [page, deferredSearchQuery]);

  const items = leaderboard?.items ?? [];
  const currentUser = leaderboard?.currentUser ?? null;

  return (
    <MainLayout>
      <div className="container max-w-5xl mx-auto px-4 py-6 md:py-8 pb-safe space-y-6">
        <Card className="border-mainAlt/80 bg-main shadow-sm">
          <CardContent className="p-5 md:p-6">
            <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
              <div className="space-y-2">
                <Link to="/profile" className="text-sm text-muted-foreground hover:text-foreground">
                  Back to Profile
                </Link>
                <div className="flex items-center gap-3">
                  <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-mainAlt/80 bg-muted text-2xl">
                    🏆
                  </div>
                  <div>
                    <h1 className="text-2xl font-bold">XP Leaderboard</h1>
                    <p className="text-sm text-muted-foreground">
                      Global ranking by earned XP. Ties share the same rank.
                    </p>
                  </div>
                </div>
              </div>
              <div className="w-full md:max-w-sm">
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    value={searchQuery}
                    onChange={(event) => {
                      setIsLoading(true);
                      setErrorMessage(null);
                      setSearchQuery(event.target.value);
                      setPage(1);
                    }}
                    className="pl-9"
                    placeholder="Search by username"
                  />
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="border-mainAlt/80 bg-main shadow-sm">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <Trophy className="h-5 w-5 text-yellow-500" />
              My Rank
            </CardTitle>
          </CardHeader>
          <CardContent>
            {currentUser ? (
              <div className="grid gap-4 sm:grid-cols-3">
                <div className="rounded-3xl border border-mainAlt/80 bg-main p-4 text-center">
                  <p className="text-xs uppercase tracking-wide text-muted-foreground">Rank</p>
                  <p className="text-2xl font-bold">#{currentUser.rank}</p>
                </div>
                <div className="rounded-3xl border border-mainAlt/80 bg-main p-4 text-center">
                  <Star className="mx-auto mb-2 h-5 w-5 text-primary" />
                  <p className="text-2xl font-bold">{currentUser.xp}</p>
                  <p className="text-xs text-muted-foreground">XP</p>
                </div>
                <div className="rounded-3xl border border-mainAlt/80 bg-main p-4 text-center">
                  <Flame className="mx-auto mb-2 h-5 w-5 text-destructive" />
                  <p className="text-2xl font-bold">{currentUser.currentStreak}</p>
                  <p className="text-xs text-muted-foreground">Current Streak</p>
                </div>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                You are not currently ranked on the public leaderboard.
              </p>
            )}
          </CardContent>
        </Card>

        <Card className="border-mainAlt/80 bg-main shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="text-lg">Top Learners</CardTitle>
            <p className="text-sm text-muted-foreground">
              {leaderboard ? `${leaderboard.totalCount} matching users` : "Loading..."}
            </p>
          </CardHeader>
          <CardContent className="space-y-4">
            {isLoading ? (
              <div className="rounded-3xl border border-dashed border-mainAlt/80 px-4 py-10 text-center text-sm text-muted-foreground">
                Loading leaderboard...
              </div>
            ) : errorMessage ? (
              <div className="rounded-3xl border border-dashed border-mainAlt/80 px-4 py-10 text-center text-sm text-muted-foreground">
                {errorMessage}
              </div>
            ) : items.length === 0 ? (
              <div className="rounded-3xl border border-dashed border-mainAlt/80 px-4 py-10 text-center text-sm text-muted-foreground">
                No leaderboard users match that search.
              </div>
            ) : (
              <>
                <div className="space-y-3 md:hidden">
                  {items.map((entry) => (
                    <div
                      key={entry.userId}
                      className={`rounded-3xl border p-4 ${
                        entry.isCurrentUser ? "border-primary bg-primary/5" : "border-mainAlt/80 bg-main"
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-muted text-sm font-semibold">
                          #{entry.rank}
                        </div>
                        <Avatar className="h-12 w-12">
                          <AvatarImage src={entry.avatarUrl ?? undefined} />
                          <AvatarFallback>{formatLeaderboardInitial(entry)}</AvatarFallback>
                        </Avatar>
                        <div className="min-w-0 flex-1">
                          <p className="truncate font-semibold">{formatLeaderboardName(entry)}</p>
                          <p className="truncate text-sm text-muted-foreground">{formatLeaderboardHandle(entry)}</p>
                        </div>
                      </div>
                      <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
                        <div>
                          <p className="text-muted-foreground">XP</p>
                          <p className="font-semibold">{entry.xp}</p>
                        </div>
                        <div>
                          <p className="text-muted-foreground">Streak</p>
                          <p className="font-semibold">{entry.currentStreak} days</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="hidden md:block">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Rank</TableHead>
                        <TableHead>User</TableHead>
                        <TableHead>XP</TableHead>
                        <TableHead>Streak</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {items.map((entry) => (
                        <TableRow
                          key={entry.userId}
                          className={entry.isCurrentUser ? "bg-primary/5 hover:bg-primary/10" : undefined}
                        >
                          <TableCell className="font-semibold">#{entry.rank}</TableCell>
                          <TableCell>
                            <div className="flex items-center gap-3">
                              <Avatar className="h-10 w-10">
                                <AvatarImage src={entry.avatarUrl ?? undefined} />
                                <AvatarFallback>{formatLeaderboardInitial(entry)}</AvatarFallback>
                              </Avatar>
                              <div>
                                <p className="font-medium">{formatLeaderboardName(entry)}</p>
                                <p className="text-sm text-muted-foreground">{formatLeaderboardHandle(entry)}</p>
                              </div>
                            </div>
                          </TableCell>
                          <TableCell>{entry.xp}</TableCell>
                          <TableCell>{entry.currentStreak} days</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>

                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm text-muted-foreground">
                    Page {leaderboard?.page ?? page}
                  </p>
                  <div className="flex gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      disabled={(leaderboard?.page ?? page) <= 1}
                      onClick={() => {
                        setIsLoading(true);
                        setErrorMessage(null);
                        setPage((current) => Math.max(1, current - 1));
                      }}
                    >
                      Previous
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      disabled={!leaderboard?.hasNext}
                      onClick={() => {
                        setIsLoading(true);
                        setErrorMessage(null);
                        setPage((current) => current + 1);
                      }}
                    >
                      Next
                    </Button>
                  </div>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </MainLayout>
  );
};

export default LeaderboardPage;
