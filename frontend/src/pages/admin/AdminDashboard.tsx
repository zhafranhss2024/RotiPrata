import React, { useEffect, useState } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { 
  Search,
  CheckCircle,
  XCircle,
  Clock,
  Users,
  FileText,
  Flag,
  TrendingUp,
  Eye,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import type { Content, ModerationQueueItem, ContentFlag } from '@/types';
import {
  approveContent,
  fetchAdminStats,
  fetchContentFlags,
  fetchModerationQueue,
  rejectContent,
  resolveFlag,
} from '@/lib/api';

// Backend: /api/admin/*
// Dummy data is returned when mocks are enabled.

const AdminDashboard = () => {
  const [activeTab, setActiveTab] = useState('overview');
  const [searchQuery, setSearchQuery] = useState('');
  const [stats, setStats] = useState({
    totalUsers: 0,
    activeUsers: 0,
    totalContent: 0,
    pendingModeration: 0,
    totalLessons: 0,
    contentApprovalRate: 0,
  });
  const [moderationQueue, setModerationQueue] = useState<(ModerationQueueItem & { content: Content })[]>([]);
  const [flags, setFlags] = useState<ContentFlag[]>([]);

  useEffect(() => {
    fetchAdminStats()
      .then(setStats)
      .catch((error) => console.warn('Failed to load admin stats', error));

    fetchModerationQueue()
      .then(setModerationQueue)
      .catch((error) => console.warn('Failed to load moderation queue', error));

    fetchContentFlags()
      .then(setFlags)
      .catch((error) => console.warn('Failed to load flags', error));
  }, []);

  const handleApprove = async (contentId: string) => {
    try {
      await approveContent(contentId);
      setModerationQueue((items) => items.filter((item) => item.content_id !== contentId));
    } catch (error) {
      console.warn('Approve failed', error);
    }
  };

  const handleReject = async (contentId: string) => {
    const reason = window.prompt('Enter rejection reason:');
    if (reason === null) {
      return;
    }
    const feedback = reason.trim();
    if (!feedback) {
      console.warn('Reject reason is required');
      return;
    }
    try {
      await rejectContent(contentId, feedback);
      setModerationQueue((items) => items.filter((item) => item.content_id !== contentId));
    } catch (error) {
      console.warn('Reject failed', error);
    }
  };

  const handleResolveFlag = async (flagId: string) => {
    try {
      await resolveFlag(flagId);
      setFlags((items) => items.filter((flag) => flag.id !== flagId));
    } catch (error) {
      console.warn('Resolve flag failed', error);
    }
  };

  return (
    <MainLayout>
      <div className="container max-w-6xl mx-auto px-4 py-6 md:py-8 pb-safe">
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold">Admin Dashboard</h1>
            <p className="text-muted-foreground">Manage content and users</p>
          </div>
          <Link to="/admin/lessons/create">
            <Button>Create Lesson</Button>
          </Link>
        </div>

        {/* Tabs */}
        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="w-full grid grid-cols-4 mb-6">
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="moderation">
              Moderation
              {moderationQueue.length > 0 && (
                <Badge variant="destructive" className="ml-2">
                  {moderationQueue.length}
                </Badge>
              )}
            </TabsTrigger>
            <TabsTrigger value="flags">
              Flags
              {flags.length > 0 && (
                <Badge variant="destructive" className="ml-2">
                  {flags.length}
                </Badge>
              )}
            </TabsTrigger>
            <TabsTrigger value="users">Users</TabsTrigger>
          </TabsList>

          {/* Overview Tab */}
          <TabsContent value="overview">
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4 mb-6">
              <Card>
                <CardContent className="p-4">
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded-lg bg-primary/10">
                      <Users className="h-5 w-5 text-primary" />
                    </div>
                    <div>
                      <p className="text-2xl font-bold">{stats.totalUsers}</p>
                      <p className="text-sm text-muted-foreground">Total Users</p>
                    </div>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardContent className="p-4">
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded-lg bg-success/10">
                      <TrendingUp className="h-5 w-5 text-success" />
                    </div>
                    <div>
                      <p className="text-2xl font-bold">{stats.activeUsers}</p>
                      <p className="text-sm text-muted-foreground">Active Today</p>
                    </div>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardContent className="p-4">
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded-lg bg-secondary/10">
                      <FileText className="h-5 w-5 text-secondary" />
                    </div>
                    <div>
                      <p className="text-2xl font-bold">{stats.totalContent}</p>
                      <p className="text-sm text-muted-foreground">Total Content</p>
                    </div>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardContent className="p-4">
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded-lg bg-warning/10">
                      <Clock className="h-5 w-5 text-warning" />
                    </div>
                    <div>
                      <p className="text-2xl font-bold">{stats.pendingModeration}</p>
                      <p className="text-sm text-muted-foreground">Pending Review</p>
                    </div>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardContent className="p-4">
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded-lg bg-accent/10">
                      <CheckCircle className="h-5 w-5 text-accent" />
                    </div>
                    <div>
                      <p className="text-2xl font-bold">{stats.contentApprovalRate}%</p>
                      <p className="text-sm text-muted-foreground">Approval Rate</p>
                    </div>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardContent className="p-4">
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded-lg bg-destructive/10">
                      <Flag className="h-5 w-5 text-destructive" />
                    </div>
                    <div>
                      <p className="text-2xl font-bold">{flags.length}</p>
                      <p className="text-sm text-muted-foreground">Open Flags</p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>

            {/* Quick Actions */}
            <Card>
              <CardHeader>
                <CardTitle>Quick Actions</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <Link to="/admin/lessons/create">
                  <Button variant="outline" className="w-full h-auto py-4 flex-col">
                    <FileText className="h-6 w-6 mb-2" />
                    Create Lesson
                  </Button>
                </Link>
                <Link to="/admin/categories">
                  <Button variant="outline" className="w-full h-auto py-4 flex-col">
                    <Flag className="h-6 w-6 mb-2" />
                    Manage Categories
                  </Button>
                </Link>
                <Link to="/admin/users">
                  <Button variant="outline" className="w-full h-auto py-4 flex-col">
                    <Users className="h-6 w-6 mb-2" />
                    Manage Users
                  </Button>
                </Link>
                <Link to="/admin/analytics">
                  <Button variant="outline" className="w-full h-auto py-4 flex-col">
                    <TrendingUp className="h-6 w-6 mb-2" />
                    View Analytics
                  </Button>
                </Link>
              </CardContent>
            </Card>
          </TabsContent>

          {/* Moderation Tab */}
          <TabsContent value="moderation">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Clock className="h-5 w-5" />
                  Pending Review ({moderationQueue.length})
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {moderationQueue.length === 0 ? (
                  <div className="text-center py-8 text-muted-foreground">
                    <CheckCircle className="h-12 w-12 mx-auto mb-3 text-success" />
                    <p>All caught up! No content pending review.</p>
                  </div>
                ) : (
                  moderationQueue.map((item) => (
                    <div
                      key={item.id}
                      className="flex items-start gap-4 p-4 border rounded-lg"
                    >
                      <div className="w-16 h-16 rounded-lg bg-muted flex items-center justify-center flex-shrink-0">
                        {item.content.content_type === 'video' ? '🎬' : item.content.content_type === 'image' ? '🖼️' : '📝'}
                      </div>
                      <div className="flex-1 min-w-0">
                        <h3 className="font-semibold truncate">{item.content.title}</h3>
                        <p className="text-sm text-muted-foreground line-clamp-2">
                          {item.content.description}
                        </p>
                        <div className="flex items-center gap-2 mt-2">
                          <Badge variant="outline">{item.content.content_type}</Badge>
                          <span className="text-xs text-muted-foreground">
                            Submitted {new Date(item.submitted_at).toLocaleDateString()}
                          </span>
                        </div>
                      </div>
                      <div className="flex gap-2 flex-shrink-0">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => {
                            const url = item.content.media_url || item.content.thumbnail_url;
                            if (!url) {
                              return;
                            }
                            window.open(url, '_blank', 'noopener,noreferrer');
                          }}
                          disabled={!item.content.media_url && !item.content.thumbnail_url}
                        >
                          <Eye className="h-4 w-4 mr-1" />
                          Open
                        </Button>
                        <Button size="sm" variant="outline" onClick={() => handleReject(item.content_id)}>
                          <XCircle className="h-4 w-4 mr-1" />
                          Reject
                        </Button>
                        <Button size="sm" onClick={() => handleApprove(item.content_id)}>
                          <CheckCircle className="h-4 w-4 mr-1" />
                          Accept
                        </Button>
                      </div>
                    </div>
                  ))
                )}
              </CardContent>
            </Card>
          </TabsContent>

          {/* Flags Tab */}
          <TabsContent value="flags">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Flag className="h-5 w-5" />
                  Flagged Content ({flags.length})
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {flags.length === 0 ? (
                  <div className="text-center py-8 text-muted-foreground">
                    <CheckCircle className="h-12 w-12 mx-auto mb-3 text-success" />
                    <p>No flagged content to review.</p>
                  </div>
                ) : (
                  flags.map((flag) => (
                    <div
                      key={flag.id}
                      className="flex items-start gap-4 p-4 border rounded-lg border-destructive/30 bg-destructive/5"
                    >
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <Badge variant="destructive">{flag.reason}</Badge>
                          <span className="text-xs text-muted-foreground">
                            {new Date(flag.created_at).toLocaleDateString()}
                          </span>
                        </div>
                        <p className="text-sm text-muted-foreground">{flag.description}</p>
                      </div>
                      <div className="flex gap-2">
                        <Button size="sm" variant="outline">
                          <Eye className="h-4 w-4 mr-1" />
                          View
                        </Button>
                        <Button size="sm" onClick={() => handleResolveFlag(flag.id)}>
                          Resolve
                        </Button>
                      </div>
                    </div>
                  ))
                )}
              </CardContent>
            </Card>
          </TabsContent>

          {/* Users Tab */}
          <TabsContent value="users">
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>User Management</CardTitle>
                  <div className="relative w-64">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                    <Input
                      placeholder="Search users..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      className="pl-9"
                    />
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="text-center py-8 text-muted-foreground">
                  <Users className="h-12 w-12 mx-auto mb-3" />
                  <p>User management interface</p>
                  <p className="text-sm">TODO: Implement user list with role management</p>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </MainLayout>
  );
};

export default AdminDashboard;
