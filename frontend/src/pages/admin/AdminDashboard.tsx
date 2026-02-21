import React, { useEffect, useState } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
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
  Loader2,
  Plus,
  X,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import type { Content, ModerationQueueItem, ContentFlag, Category } from '@/types';
import {
  approveContent,
  fetchAdminStats,
  fetchCategories,
  fetchContentFlags,
  fetchModerationQueue,
  fetchTags,
  updateAdminContent,
  rejectContent,
  resolveFlag,
} from '@/lib/api';

// Backend: /api/admin/*
// Dummy data is returned when mocks are enabled.

const MAX_TITLE = 80;
const MAX_DESCRIPTION = 500;
const MAX_OBJECTIVE = 160;
const MAX_LONG_TEXT = 500;
const MAX_OLDER_REFERENCE = 160;
const MAX_TAG = 30;

const sanitizeInputValue = (value: string, maxLength: number) => {
  const cleaned = value.replace(/[\u0000-\u001F\u007F]/g, '');
  if (maxLength > 0 && cleaned.length > maxLength) {
    return cleaned.slice(0, maxLength);
  }
  return cleaned;
};

const normalizeText = (value: string, maxLength: number) => {
  const cleaned = sanitizeInputValue(value, maxLength);
  return cleaned.replace(/\s+/g, ' ').trim();
};

const sanitizeTag = (value: string) => {
  const trimmed = value.trim().replace(/^#/, '');
  return normalizeText(trimmed, MAX_TAG);
};

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
  const [selectedModerationItem, setSelectedModerationItem] = useState<(ModerationQueueItem & { content: Content }) | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [editForm, setEditForm] = useState({
    title: '',
    description: '',
    learningObjective: '',
    originExplanation: '',
    definitionLiteral: '',
    definitionUsed: '',
    olderVersionReference: '',
    categoryId: '',
    tags: [] as string[],
  });
  const [hasAttemptedSave, setHasAttemptedSave] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isSaved, setIsSaved] = useState(false);
  const [isApproving, setIsApproving] = useState(false);
  const [tagQuery, setTagQuery] = useState('');
  const [tagSuggestions, setTagSuggestions] = useState<string[]>([]);
  const [isTagLoading, setIsTagLoading] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [rejectAttempted, setRejectAttempted] = useState(false);

  const fieldErrors = {
    title: normalizeText(editForm.title, MAX_TITLE) ? '' : 'Title is required.',
    description: normalizeText(editForm.description, MAX_DESCRIPTION) ? '' : 'Description is required.',
    learningObjective: normalizeText(editForm.learningObjective, MAX_OBJECTIVE) ? '' : 'Learning objective is required.',
    originExplanation: normalizeText(editForm.originExplanation, MAX_LONG_TEXT) ? '' : 'Origin explanation is required.',
    definitionLiteral: normalizeText(editForm.definitionLiteral, MAX_LONG_TEXT) ? '' : 'Definition (literal) is required.',
    definitionUsed: normalizeText(editForm.definitionUsed, MAX_LONG_TEXT) ? '' : 'Definition (used) is required.',
    olderVersionReference: normalizeText(editForm.olderVersionReference, MAX_OLDER_REFERENCE) ? '' : 'Older version reference is required.',
    categoryId: editForm.categoryId ? '' : 'Category is required.',
    tags: editForm.tags.length > 0 ? '' : 'At least one tag is required.',
  };
  const isFormValid = Object.values(fieldErrors).every((value) => !value);

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

    fetchCategories()
      .then(setCategories)
      .catch((error) => console.warn('Failed to load categories', error));
  }, []);

  useEffect(() => {
    if (!selectedModerationItem) {
      return;
    }
    const content = selectedModerationItem.content;
    setEditForm({
      title: content.title ?? '',
      description: content.description ?? '',
      learningObjective: content.learning_objective ?? '',
      originExplanation: content.origin_explanation ?? '',
      definitionLiteral: content.definition_literal ?? '',
      definitionUsed: content.definition_used ?? '',
      olderVersionReference: content.older_version_reference ?? '',
      categoryId: content.category_id ?? '',
      tags: content.tags ?? [],
    });
    setHasAttemptedSave(false);
    setIsSaved(false);
    setTagQuery('');
    setTagSuggestions([]);
  }, [selectedModerationItem?.content?.id]);

  useEffect(() => {
    if (!selectedModerationItem) {
      return;
    }
    setIsSaved(false);
  }, [editForm]);

  useEffect(() => {
    const query = tagQuery.trim();
    if (!query) {
      setTagSuggestions([]);
      return;
    }
    let active = true;
    setIsTagLoading(true);
    fetchTags(query)
      .then((tags) => {
        if (!active) {
          return;
        }
        const filtered = tags.filter((tag) => !editForm.tags.includes(tag));
        setTagSuggestions(filtered);
      })
      .catch((error) => console.warn('Failed to fetch tags', error))
      .finally(() => {
        if (active) {
          setIsTagLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [tagQuery, editForm.tags]);

  const handleApprove = async (contentId: string) => {
    if (!isSaved) {
      return;
    }
    try {
      setIsApproving(true);
      await approveContent(contentId);
      setModerationQueue((items) => items.filter((item) => item.content_id !== contentId));
      setSelectedModerationItem(null);
    } catch (error) {
      console.warn('Approve failed', error);
    } finally {
      setIsApproving(false);
    }
  };

  const handleReject = async (contentId: string) => {
    const feedback = normalizeText(rejectReason, MAX_LONG_TEXT);
    if (!feedback) {
      setRejectAttempted(true);
      return;
    }
    try {
      await rejectContent(contentId, feedback);
      setModerationQueue((items) => items.filter((item) => item.content_id !== contentId));
      setSelectedModerationItem(null);
      setRejectOpen(false);
      setRejectReason('');
      setRejectAttempted(false);
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

  const handleAddTag = (value?: string) => {
    const normalized = sanitizeTag(value ?? tagQuery);
    if (!normalized) {
      return;
    }
    if (editForm.tags.includes(normalized)) {
      setTagQuery('');
      setTagSuggestions([]);
      return;
    }
    setEditForm((prev) => ({ ...prev, tags: [...prev.tags, normalized] }));
    setTagQuery('');
    setTagSuggestions([]);
  };

  const handleRemoveTag = (tag: string) => {
    setEditForm((prev) => ({ ...prev, tags: prev.tags.filter((t) => t !== tag) }));
  };

  const handleSave = async () => {
    setHasAttemptedSave(true);
    if (!selectedModerationItem || !isFormValid) {
      return;
    }
    const payload = {
      title: normalizeText(editForm.title, MAX_TITLE),
      description: normalizeText(editForm.description, MAX_DESCRIPTION),
      learningObjective: normalizeText(editForm.learningObjective, MAX_OBJECTIVE),
      originExplanation: normalizeText(editForm.originExplanation, MAX_LONG_TEXT),
      definitionLiteral: normalizeText(editForm.definitionLiteral, MAX_LONG_TEXT),
      definitionUsed: normalizeText(editForm.definitionUsed, MAX_LONG_TEXT),
      olderVersionReference: normalizeText(editForm.olderVersionReference, MAX_OLDER_REFERENCE),
      categoryId: editForm.categoryId,
      tags: editForm.tags.map(sanitizeTag).filter(Boolean),
    };
    setIsSaving(true);
    try {
      await updateAdminContent(selectedModerationItem.content_id, payload);
      setIsSaved(true);
      const updatedContent = {
        ...selectedModerationItem.content,
        title: payload.title,
        description: payload.description,
        learning_objective: payload.learningObjective,
        origin_explanation: payload.originExplanation,
        definition_literal: payload.definitionLiteral,
        definition_used: payload.definitionUsed,
        older_version_reference: payload.olderVersionReference,
        category_id: payload.categoryId,
        tags: payload.tags as string[],
      };
      setModerationQueue((items) =>
        items.map((item) =>
          item.content_id === selectedModerationItem.content_id
            ? { ...item, content: updatedContent }
            : item
        )
      );
      setSelectedModerationItem((prev) =>
        prev ? { ...prev, content: updatedContent } : prev
      );
    } catch (error) {
      console.warn('Save failed', error);
    } finally {
      setIsSaving(false);
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
                          onClick={() => setSelectedModerationItem(item)}
                        >
                          <Eye className="h-4 w-4 mr-1" />
                          Open
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

        <Dialog
          open={selectedModerationItem !== null}
          onOpenChange={(open) => {
            if (!open) {
              setSelectedModerationItem(null);
            }
          }}
        >
          <DialogContent className="max-w-6xl w-[95vw] p-0 overflow-hidden">
            {selectedModerationItem && (
              <div className="max-h-[85vh] overflow-y-auto">
                <DialogHeader className="p-6 border-b">
                  <DialogTitle>Moderation Review</DialogTitle>
                  <DialogDescription>
                    Review all content details and media before approving or rejecting.
                  </DialogDescription>
                </DialogHeader>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-0">
                  <div className="p-6 border-b lg:border-b-0 lg:border-r space-y-4">
                    <div className="grid gap-2">
                      <p className="text-xs uppercase tracking-wide text-muted-foreground">Status</p>
                      <p className="text-sm">{selectedModerationItem.content.status}</p>
                    </div>
                    <div className="grid gap-2">
                      <p className="text-xs uppercase tracking-wide text-muted-foreground">Content Type</p>
                      <p className="text-sm">{selectedModerationItem.content.content_type}</p>
                    </div>
                    <div className="grid gap-2">
                      <p className="text-xs uppercase tracking-wide text-muted-foreground">Submitted At</p>
                      <p className="text-sm">
                        {selectedModerationItem.submitted_at
                          ? new Date(selectedModerationItem.submitted_at).toLocaleString()
                          : 'Unknown'}
                      </p>
                    </div>

                    <div className="grid gap-2">
                      <Label htmlFor="admin-title">Title</Label>
                      <Input
                        id="admin-title"
                        value={editForm.title}
                        onChange={(e) => setEditForm((prev) => ({ ...prev, title: sanitizeInputValue(e.target.value, MAX_TITLE) }))}
                        maxLength={MAX_TITLE}
                      />
                      {hasAttemptedSave && fieldErrors.title && (
                        <p className="text-xs text-destructive">{fieldErrors.title}</p>
                      )}
                    </div>

                    <div className="grid gap-2">
                      <Label htmlFor="admin-description">Description</Label>
                      <Textarea
                        id="admin-description"
                        value={editForm.description}
                        onChange={(e) => setEditForm((prev) => ({ ...prev, description: sanitizeInputValue(e.target.value, MAX_DESCRIPTION) }))}
                        maxLength={MAX_DESCRIPTION}
                        rows={4}
                      />
                      {hasAttemptedSave && fieldErrors.description && (
                        <p className="text-xs text-destructive">{fieldErrors.description}</p>
                      )}
                    </div>

                    <div className="grid gap-2">
                      <Label htmlFor="admin-learning-objective">Learning Objective</Label>
                      <Input
                        id="admin-learning-objective"
                        value={editForm.learningObjective}
                        onChange={(e) => setEditForm((prev) => ({ ...prev, learningObjective: sanitizeInputValue(e.target.value, MAX_OBJECTIVE) }))}
                        maxLength={MAX_OBJECTIVE}
                      />
                      {hasAttemptedSave && fieldErrors.learningObjective && (
                        <p className="text-xs text-destructive">{fieldErrors.learningObjective}</p>
                      )}
                    </div>

                    <div className="grid gap-2">
                      <Label htmlFor="admin-origin-explanation">Origin Explanation</Label>
                      <Textarea
                        id="admin-origin-explanation"
                        value={editForm.originExplanation}
                        onChange={(e) => setEditForm((prev) => ({ ...prev, originExplanation: sanitizeInputValue(e.target.value, MAX_LONG_TEXT) }))}
                        maxLength={MAX_LONG_TEXT}
                        rows={3}
                      />
                      {hasAttemptedSave && fieldErrors.originExplanation && (
                        <p className="text-xs text-destructive">{fieldErrors.originExplanation}</p>
                      )}
                    </div>

                    <div className="grid gap-2">
                      <Label htmlFor="admin-definition-literal">Definition (Literal)</Label>
                      <Textarea
                        id="admin-definition-literal"
                        value={editForm.definitionLiteral}
                        onChange={(e) => setEditForm((prev) => ({ ...prev, definitionLiteral: sanitizeInputValue(e.target.value, MAX_LONG_TEXT) }))}
                        maxLength={MAX_LONG_TEXT}
                        rows={3}
                      />
                      {hasAttemptedSave && fieldErrors.definitionLiteral && (
                        <p className="text-xs text-destructive">{fieldErrors.definitionLiteral}</p>
                      )}
                    </div>

                    <div className="grid gap-2">
                      <Label htmlFor="admin-definition-used">Definition (Used)</Label>
                      <Textarea
                        id="admin-definition-used"
                        value={editForm.definitionUsed}
                        onChange={(e) => setEditForm((prev) => ({ ...prev, definitionUsed: sanitizeInputValue(e.target.value, MAX_LONG_TEXT) }))}
                        maxLength={MAX_LONG_TEXT}
                        rows={3}
                      />
                      {hasAttemptedSave && fieldErrors.definitionUsed && (
                        <p className="text-xs text-destructive">{fieldErrors.definitionUsed}</p>
                      )}
                    </div>

                    <div className="grid gap-2">
                      <Label htmlFor="admin-older-version">Older Version Reference</Label>
                      <Input
                        id="admin-older-version"
                        value={editForm.olderVersionReference}
                        onChange={(e) =>
                          setEditForm((prev) => ({ ...prev, olderVersionReference: sanitizeInputValue(e.target.value, MAX_OLDER_REFERENCE) }))
                        }
                        maxLength={MAX_OLDER_REFERENCE}
                      />
                      {hasAttemptedSave && fieldErrors.olderVersionReference && (
                        <p className="text-xs text-destructive">{fieldErrors.olderVersionReference}</p>
                      )}
                    </div>

                    <div className="grid gap-2">
                      <Label>Category</Label>
                      <Select
                        value={editForm.categoryId}
                        onValueChange={(value) => setEditForm((prev) => ({ ...prev, categoryId: value }))}
                      >
                        <SelectTrigger>
                          <SelectValue placeholder="Select a category" />
                        </SelectTrigger>
                        <SelectContent>
                          {categories.map((category) => (
                            <SelectItem key={category.id} value={category.id}>
                              {category.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      {hasAttemptedSave && fieldErrors.categoryId && (
                        <p className="text-xs text-destructive">{fieldErrors.categoryId}</p>
                      )}
                    </div>

                    <div className="grid gap-2">
                      <Label>Tags</Label>
                      <div className="flex gap-2">
                        <Input
                          placeholder="Search or create a tag"
                          value={tagQuery}
                          onChange={(e) => setTagQuery(sanitizeInputValue(e.target.value, MAX_TAG))}
                        />
                        <Button type="button" variant="outline" onClick={() => handleAddTag()}>
                          <Plus className="h-4 w-4" />
                        </Button>
                      </div>
                      {isTagLoading && (
                        <p className="text-xs text-muted-foreground flex items-center gap-2">
                          <Loader2 className="h-3 w-3 animate-spin" />
                          Searching tags...
                        </p>
                      )}
                      {tagSuggestions.length > 0 && (
                        <div className="flex flex-wrap gap-2">
                          {tagSuggestions.map((tag) => (
                            <Button
                              key={tag}
                              type="button"
                              variant="secondary"
                              size="sm"
                              onClick={() => handleAddTag(tag)}
                            >
                              #{tag}
                            </Button>
                          ))}
                        </div>
                      )}
                      {tagQuery.trim() && tagSuggestions.length === 0 && !isTagLoading && (
                        <p className="text-xs text-muted-foreground">Press plus to add a new tag.</p>
                      )}
                      <div className="flex flex-wrap gap-2">
                        {editForm.tags.map((tag) => (
                          <Badge key={tag} variant="secondary" className="gap-1">
                            #{tag}
                            <button type="button" onClick={() => handleRemoveTag(tag)}>
                              <X className="h-3 w-3" />
                            </button>
                          </Badge>
                        ))}
                      </div>
                      {hasAttemptedSave && fieldErrors.tags && (
                        <p className="text-xs text-destructive">{fieldErrors.tags}</p>
                      )}
                    </div>

                    <div className="flex flex-wrap gap-2 pt-2">
                      <Button type="button" onClick={handleSave} disabled={!isFormValid || isSaving}>
                        {isSaving ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : null}
                        Save
                      </Button>
                      <Button
                        type="button"
                        onClick={() => handleApprove(selectedModerationItem.content_id)}
                        disabled={!isSaved || !isFormValid || isApproving}
                      >
                        {isApproving ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : null}
                        Approve
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => {
                          setRejectOpen(true);
                          setRejectAttempted(false);
                        }}
                      >
                        Reject
                      </Button>
                    </div>
                  </div>

                  <div className="p-6 space-y-3">
                    <p className="text-xs uppercase tracking-wide text-muted-foreground">Media</p>
                    {selectedModerationItem.content.content_type === 'video' && selectedModerationItem.content.media_url ? (
                      <video
                        controls
                        className="w-full max-h-[420px] rounded-md bg-black"
                        src={selectedModerationItem.content.media_url}
                      />
                    ) : null}

                    {selectedModerationItem.content.content_type !== 'video' && selectedModerationItem.content.media_url ? (
                      <img
                        src={selectedModerationItem.content.media_url}
                        alt={selectedModerationItem.content.title}
                        className="w-full max-h-[420px] rounded-md object-contain bg-muted"
                      />
                    ) : null}

                    {!selectedModerationItem.content.media_url && selectedModerationItem.content.thumbnail_url ? (
                      <img
                        src={selectedModerationItem.content.thumbnail_url}
                        alt={selectedModerationItem.content.title}
                        className="w-full max-h-[420px] rounded-md object-contain bg-muted"
                      />
                    ) : null}

                    {!selectedModerationItem.content.media_url && !selectedModerationItem.content.thumbnail_url ? (
                      <p className="text-sm text-muted-foreground">No media URL available.</p>
                    ) : null}
                  </div>
                </div>
              </div>
            )}
          </DialogContent>
        </Dialog>

        <Dialog
          open={rejectOpen}
          onOpenChange={(open) => {
            setRejectOpen(open);
            if (!open) {
              setRejectReason('');
              setRejectAttempted(false);
            }
          }}
        >
          <DialogContent className="max-w-lg">
            <DialogHeader>
              <DialogTitle>Reject Content</DialogTitle>
              <DialogDescription>
                Provide a clear reason so the creator can improve the submission.
              </DialogDescription>
            </DialogHeader>
            <div className="grid gap-2">
              <Label htmlFor="reject-reason">Rejection Reason</Label>
              <Textarea
                id="reject-reason"
                value={rejectReason}
                onChange={(e) => setRejectReason(sanitizeInputValue(e.target.value, MAX_LONG_TEXT))}
                maxLength={MAX_LONG_TEXT}
                rows={4}
              />
              {rejectAttempted && !normalizeText(rejectReason, MAX_LONG_TEXT) && (
                <p className="text-xs text-destructive">Rejection reason is required.</p>
              )}
              <p className="text-xs text-muted-foreground">
                {rejectReason.length}/{MAX_LONG_TEXT}
              </p>
            </div>
            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setRejectOpen(false);
                  setRejectReason('');
                  setRejectAttempted(false);
                }}
              >
                Cancel
              </Button>
              <Button
                type="button"
                onClick={() => {
                  if (selectedModerationItem) {
                    handleReject(selectedModerationItem.content_id);
                  }
                }}
              >
                Reject
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      </div>
    </MainLayout>
  );
};

export default AdminDashboard;
