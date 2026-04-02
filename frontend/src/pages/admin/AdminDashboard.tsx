import React, { useEffect, useMemo, useState } from 'react';
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
  DialogFooter,
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
import { toast } from '@/components/ui/sonner';
import type {
  AdminContentFlagGroup,
  AdminContentFlagReport,
  AdminUserDetail,
  AdminUserSummary,
  AppRole,
  Category,
  Content,
  ModerationQueueItem,
  QuizQuestion,
} from '@/types';
import {
  getFlagDescriptionCount,
  getFlagReasonSummary,
  getFlagReasons,
  getFlagReportCount,
} from '@/pages/admin/flagModeration';
import {
  approveContent,
  deleteContentComment,
  fetchAdminUserDetail,
  fetchAdminUsers,
  fetchAdminStats,
  fetchCategories,
  fetchAdminContentQuiz,
  fetchContentFlags,
  fetchFlagReports,
  fetchModerationQueue,
  fetchTags,
  resetAdminUserLessonProgress,
  takeDownFlag,
  updateAdminContent,
  updateAdminUserRole,
  updateAdminUserStatus,
  rejectContent,
  resolveFlag,
  saveAdminContentQuiz,
} from '@/lib/api';

// Backend: /api/admin/*
// Dummy data is returned when mocks are enabled.

const MAX_TITLE = 80;
const MAX_DESCRIPTION = 500;
const MAX_OBJECTIVE = 160;
const MAX_LONG_TEXT = 500;
const MAX_OLDER_REFERENCE = 160;
const MAX_TAG = 30;
const FLAG_REPORTS_PAGE_SIZE = 5;

const stripControlCharacters = (value: string) =>
  Array.from(value)
    .filter((character) => {
      const code = character.charCodeAt(0);
      return code > 31 && code !== 127;
    })
    .join('');

const sanitizeInputValue = (value: string, maxLength: number) => {
  const cleaned = stripControlCharacters(value);
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

const formatContentStatus = (status?: string | null) => {
  switch ((status ?? '').toLowerCase()) {
    case 'approved':
    case 'accepted':
      return 'Approved';
    case 'pending':
      return 'Pending';
    case 'rejected':
      return 'Taken down';
    default:
      return status ?? 'Unknown';
  }
};

type ContentQuizDraftQuestion = {
  id: string;
  question_text: string;
  options: Record<string, string>;
  correct_answer: string;
  explanation: string;
  points: number;
  order_index: number;
};

const createBlankQuizQuestion = (index: number): ContentQuizDraftQuestion => ({
  id: `draft-${Date.now()}-${index}`,
  question_text: '',
  options: { A: '', B: '', C: '', D: '' },
  correct_answer: 'A',
  explanation: '',
  points: 10,
  order_index: index,
});

const normalizeQuizOptions = (options?: Record<string, unknown> | null) => {
  const normalized: Record<string, string> = { A: '', B: '', C: '', D: '' };
  if (!options) {
    return normalized;
  }
  Object.entries(options).forEach(([key, value]) => {
    const trimmedKey = key.trim().toUpperCase();
    if (trimmedKey in normalized) {
      normalized[trimmedKey] = String(value ?? '');
    }
  });
  return normalized;
};

const mapQuizQuestionToDraft = (question: QuizQuestion, index: number): ContentQuizDraftQuestion => ({
  id: question.id ?? `draft-${Date.now()}-${index}`,
  question_text: question.question_text ?? '',
  options: normalizeQuizOptions(question.options as Record<string, unknown> | null),
  correct_answer: (question.correct_answer ?? 'A').toUpperCase(),
  explanation: question.explanation ?? '',
  points: question.points ?? 10,
  order_index: question.order_index ?? index,
});

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
  const [flags, setFlags] = useState<AdminContentFlagGroup[]>([]);
  const [selectedModerationItem, setSelectedModerationItem] = useState<(ModerationQueueItem & { content: Content }) | null>(null);
  const [selectedFlag, setSelectedFlag] = useState<AdminContentFlagGroup | null>(null);
  const [flagTakeDownTarget, setFlagTakeDownTarget] = useState<AdminContentFlagGroup | null>(null);
  const [flagTakeDownReason, setFlagTakeDownReason] = useState('');
  const [flagTakeDownAttempted, setFlagTakeDownAttempted] = useState(false);
  const [isTakingDownFlag, setIsTakingDownFlag] = useState(false);
  const [selectedFlagReports, setSelectedFlagReports] = useState<AdminContentFlagReport[]>([]);
  const [selectedFlagReportsPage, setSelectedFlagReportsPage] = useState(1);
  const [selectedFlagReportsHasNext, setSelectedFlagReportsHasNext] = useState(false);
  const [selectedFlagReportsLoading, setSelectedFlagReportsLoading] = useState(false);
  const [selectedFlagReporterSearch, setSelectedFlagReporterSearch] = useState('');
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
  const [quizQuestions, setQuizQuestions] = useState<ContentQuizDraftQuestion[]>([]);
  const [quizLoading, setQuizLoading] = useState(false);
  const [quizSaving, setQuizSaving] = useState(false);
  const [quizDirty, setQuizDirty] = useState(false);
  const [quizError, setQuizError] = useState<string | null>(null);
  const [hasAttemptedQuizSave, setHasAttemptedQuizSave] = useState(false);
  const [adminUsers, setAdminUsers] = useState<AdminUserSummary[]>([]);
  const [isUsersLoading, setIsUsersLoading] = useState(false);
  const [selectedUser, setSelectedUser] = useState<AdminUserDetail | null>(null);
  const [isUserDetailOpen, setIsUserDetailOpen] = useState(false);
  const [isUserDetailLoading, setIsUserDetailLoading] = useState(false);
  const [userActionKey, setUserActionKey] = useState<string | null>(null);
  const [userContentRejectTarget, setUserContentRejectTarget] = useState<Content | null>(null);
  const [userContentRejectReason, setUserContentRejectReason] = useState('');
  const [userContentRejectAttempted, setUserContentRejectAttempted] = useState(false);

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
  const quizValidationError = useMemo(() => {
    if (!quizQuestions.length) {
      return null;
    }
    for (let index = 0; index < quizQuestions.length; index += 1) {
      const question = quizQuestions[index];
      if (!normalizeText(question.question_text, MAX_LONG_TEXT)) {
        return `Question ${index + 1}: question text is required.`;
      }
      const optionEntries = Object.entries(question.options ?? {}).filter(([, value]) =>
        normalizeText(String(value ?? ''), MAX_LONG_TEXT)
      );
      if (optionEntries.length < 2) {
        return `Question ${index + 1}: at least two options are required.`;
      }
      const correctKey = question.correct_answer?.trim().toUpperCase();
      if (!correctKey) {
        return `Question ${index + 1}: choose the correct answer.`;
      }
      const correctValue = question.options?.[correctKey];
      if (!normalizeText(String(correctValue ?? ''), MAX_LONG_TEXT)) {
        return `Question ${index + 1}: correct answer must have text.`;
      }
    }
    return null;
  }, [quizQuestions]);
  const isQuizValid = !quizValidationError;
  const canApprove = isSaved && isFormValid && !quizDirty && isQuizValid;
  const totalFlagReports = useMemo(
    () => flags.reduce((sum, flag) => sum + getFlagReportCount(flag), 0),
    [flags]
  );
  const filteredUsers = useMemo(() => {
    const normalized = searchQuery.trim().toLowerCase();
    if (!normalized) {
      return adminUsers;
    }
    return adminUsers.filter((user) =>
      [user.displayName, user.email ?? '', user.userId].some((value) => value.toLowerCase().includes(normalized))
    );
  }, [adminUsers, searchQuery]);

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

    setIsUsersLoading(true);
    fetchAdminUsers()
      .then(setAdminUsers)
      .catch((error) => console.warn('Failed to load admin users', error))
      .finally(() => setIsUsersLoading(false));
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
      setQuizQuestions([]);
      setQuizDirty(false);
      setQuizError(null);
      setHasAttemptedQuizSave(false);
      return;
    }
    let active = true;
    setQuizLoading(true);
    setQuizError(null);
    setQuizDirty(false);
    setHasAttemptedQuizSave(false);

    fetchAdminContentQuiz(selectedModerationItem.content_id)
      .then((questions) => {
        if (!active) {
          return;
        }
        const normalized = (questions ?? []).map(mapQuizQuestionToDraft);
        setQuizQuestions(normalized);
      })
      .catch((error) => {
        console.warn('Failed to load content quiz', error);
        if (active) {
          setQuizError('Failed to load quiz.');
          setQuizQuestions([]);
        }
      })
      .finally(() => {
        if (active) {
          setQuizLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [selectedModerationItem?.content_id]);

  useEffect(() => {
    if (!selectedFlag) {
      setSelectedFlagReports([]);
      setSelectedFlagReportsPage(1);
      setSelectedFlagReportsHasNext(false);
      setSelectedFlagReporterSearch('');
      setSelectedFlagReportsLoading(false);
      return;
    }

    let active = true;
    setSelectedFlagReportsLoading(true);
    fetchFlagReports(selectedFlag.id, selectedFlagReportsPage, selectedFlagReporterSearch)
      .then((response) => {
        if (!active) {
          return;
        }
        setSelectedFlagReports(response.items ?? []);
        setSelectedFlagReportsHasNext(Boolean(response.has_next));
      })
      .catch((error) => {
        console.warn('Failed to load flag reports', error);
        if (active) {
          setSelectedFlagReports([]);
          setSelectedFlagReportsHasNext(false);
        }
      })
      .finally(() => {
        if (active) {
          setSelectedFlagReportsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [selectedFlag?.id, selectedFlagReportsPage, selectedFlagReporterSearch]);

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
    if (!canApprove) {
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
      setSelectedFlag((current) => (current?.id === flagId ? null : current));
      setFlagTakeDownTarget((current) => (current?.id === flagId ? null : current));
    } catch (error) {
      console.warn('Resolve flag failed', error);
    }
  };

  const handleOpenFlagTakeDown = (flag: AdminContentFlagGroup) => {
    setSelectedFlag(null);
    setFlagTakeDownTarget(flag);
    setFlagTakeDownReason('');
    setFlagTakeDownAttempted(false);
  };

  const handleConfirmFlagTakeDown = async () => {
    if (!flagTakeDownTarget) {
      return;
    }
    const feedback = normalizeText(flagTakeDownReason, MAX_LONG_TEXT);
    if (!feedback) {
      setFlagTakeDownAttempted(true);
      return;
    }
    try {
      setIsTakingDownFlag(true);
      await takeDownFlag(flagTakeDownTarget.id, feedback);
      setFlags((items) => items.filter((flag) => flag.content_id !== flagTakeDownTarget.content_id));
      setSelectedFlag((current) => (current?.content_id === flagTakeDownTarget.content_id ? null : current));
      setFlagTakeDownTarget(null);
      setFlagTakeDownReason('');
      setFlagTakeDownAttempted(false);
    } catch (error) {
      console.warn('Flag take down failed', error);
    } finally {
      setIsTakingDownFlag(false);
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

  const markQuizDirty = () => {
    setQuizDirty(true);
    setHasAttemptedQuizSave(false);
  };

  const handleAddQuizQuestion = () => {
    setQuizQuestions((prev) => [...prev, createBlankQuizQuestion(prev.length)]);
    markQuizDirty();
  };

  const handleRemoveQuizQuestion = (questionId: string) => {
    setQuizQuestions((prev) =>
      prev
        .filter((question) => question.id !== questionId)
        .map((question, index) => ({ ...question, order_index: index }))
    );
    markQuizDirty();
  };

  const updateQuizQuestion = (questionId: string, updates: Partial<ContentQuizDraftQuestion>) => {
    setQuizQuestions((prev) =>
      prev.map((question) => (question.id === questionId ? { ...question, ...updates } : question))
    );
    markQuizDirty();
  };

  const updateQuizOption = (questionId: string, optionKey: string, value: string) => {
    setQuizQuestions((prev) =>
      prev.map((question) => {
        if (question.id !== questionId) {
          return question;
        }
        return {
          ...question,
          options: {
            ...question.options,
            [optionKey]: value,
          },
        };
      })
    );
    markQuizDirty();
  };

  const handleSave = async () => {
    setHasAttemptedSave(true);
    setHasAttemptedQuizSave(true);
    if (!selectedModerationItem || !isFormValid) {
      return;
    }
    if (quizValidationError) {
      setQuizError(quizValidationError);
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
    setQuizSaving(quizDirty);
    try {
      await updateAdminContent(selectedModerationItem.content_id, payload);
      if (quizDirty) {
        const quizPayload = quizQuestions.map((question, index) => ({
          question_text: normalizeText(question.question_text, MAX_LONG_TEXT),
          options: Object.fromEntries(
            Object.entries(question.options).map(([key, value]) => [
              key,
              normalizeText(String(value ?? ''), MAX_LONG_TEXT),
            ])
          ),
          correct_answer: question.correct_answer?.trim().toUpperCase() ?? 'A',
          explanation: normalizeText(question.explanation, MAX_LONG_TEXT),
          points: question.points,
          order_index: index,
        }));
        await saveAdminContentQuiz(selectedModerationItem.content_id, quizPayload);
        setQuizDirty(false);
      }
      setIsSaved(true);
      setQuizError(null);
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
      setQuizSaving(false);
    }
  };

  const upsertUserSummary = (summary: AdminUserSummary) => {
    setAdminUsers((users) =>
      users.map((user) => (user.userId === summary.userId ? summary : user))
    );
    setSelectedUser((current) =>
      current && current.summary.userId === summary.userId
        ? { ...current, summary }
        : current
    );
  };

  const loadUserDetail = async (userId: string, keepDialogOpen = true) => {
    try {
      setIsUserDetailLoading(true);
      if (keepDialogOpen) {
        setIsUserDetailOpen(true);
      }
      const detail = await fetchAdminUserDetail(userId);
      setSelectedUser(detail);
      return detail;
    } catch (error) {
      console.warn('Failed to load admin user detail', error);
      toast('Failed to load user details', { position: 'bottom-center' });
      if (!selectedUser) {
        setIsUserDetailOpen(false);
      }
      return null;
    } finally {
      setIsUserDetailLoading(false);
    }
  };

  const handleUpdateUserRole = async (userId: string, role: AppRole) => {
    try {
      setUserActionKey(`role:${userId}:${role}`);
      const updated = await updateAdminUserRole(userId, role);
      upsertUserSummary(updated);
      await loadUserDetail(userId, false);
      toast(`User role updated to ${role}`, { position: 'bottom-center' });
    } catch (error) {
      console.warn('Failed to update user role', error);
      toast(error instanceof Error ? error.message : 'Failed to update user role', {
        position: 'bottom-center',
      });
    } finally {
      setUserActionKey(null);
    }
  };

  const handleToggleUserStatus = async (user: AdminUserSummary) => {
    const nextStatus = user.status === 'active' ? 'suspended' : 'active';
    try {
      setUserActionKey(`status:${user.userId}:${nextStatus}`);
      const updated = await updateAdminUserStatus(user.userId, nextStatus);
      upsertUserSummary(updated);
      await loadUserDetail(user.userId, false);
      toast(
        nextStatus === 'suspended' ? 'User suspended' : 'User reactivated',
        { position: 'bottom-center' }
      );
    } catch (error) {
      console.warn('Failed to update user status', error);
      toast(error instanceof Error ? error.message : 'Failed to update user status', {
        position: 'bottom-center',
      });
    } finally {
      setUserActionKey(null);
    }
  };

  const handleResetLessonProgress = async (lessonId: string | null) => {
    if (!selectedUser || !lessonId) {
      return;
    }
    try {
      setUserActionKey(`progress:${lessonId}`);
      await resetAdminUserLessonProgress(selectedUser.summary.userId, lessonId);
      const refreshed = await loadUserDetail(selectedUser.summary.userId, false);
      if (refreshed) {
        upsertUserSummary(refreshed.summary);
      }
      toast('Lesson progress reset', { position: 'bottom-center' });
    } catch (error) {
      console.warn('Failed to reset lesson progress', error);
      toast(error instanceof Error ? error.message : 'Failed to reset lesson progress', {
        position: 'bottom-center',
      });
    } finally {
      setUserActionKey(null);
    }
  };

  const handleDeleteUserComment = async (commentId: string, contentId: string | null) => {
    if (!selectedUser || !contentId) {
      return;
    }
    try {
      setUserActionKey(`comment:${commentId}`);
      await deleteContentComment(contentId, commentId);
      setSelectedUser((current) =>
        current
          ? {
              ...current,
              comments: current.comments.filter((comment) => comment.id !== commentId),
              activity: {
                ...current.activity,
                commentCount: Math.max(0, current.activity.commentCount - 1),
              },
            }
          : current
      );
      toast('Comment removed', { position: 'bottom-center' });
    } catch (error) {
      console.warn('Failed to delete user comment', error);
      toast(error instanceof Error ? error.message : 'Failed to delete comment', {
        position: 'bottom-center',
      });
    } finally {
      setUserActionKey(null);
    }
  };

  const handleTakeDownUserContent = async () => {
    if (!selectedUser || !userContentRejectTarget) {
      return;
    }
    const feedback = normalizeText(userContentRejectReason, MAX_LONG_TEXT);
    if (!feedback) {
      setUserContentRejectAttempted(true);
      return;
    }
    try {
      setUserActionKey(`content:${userContentRejectTarget.id}`);
      await rejectContent(userContentRejectTarget.id, feedback);
      toast('Content taken down', { position: 'bottom-center' });
      setUserContentRejectTarget(null);
      setUserContentRejectReason('');
      setUserContentRejectAttempted(false);
      await loadUserDetail(selectedUser.summary.userId, false);
    } catch (error) {
      console.warn('Failed to take down user content', error);
      toast(error instanceof Error ? error.message : 'Failed to take down content', {
        position: 'bottom-center',
      });
    } finally {
      setUserActionKey(null);
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
          <div className="flex gap-2">
            <Link to="/admin/lessons">
              <Button variant="outline">Manage Lessons</Button>
            </Link>
            <Link to="/admin/lessons/create">
              <Button>Create Lesson</Button>
            </Link>
          </div>
        </div>

        {/* Tabs */}
        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="mb-6 grid h-auto w-full grid-cols-4 rounded-2xl bg-muted p-1 md:h-10 md:rounded-md">
            <TabsTrigger value="overview" className="min-h-11 rounded-xl px-2 text-xs sm:text-sm md:min-h-0 md:rounded-sm">
              Overview
            </TabsTrigger>
            <TabsTrigger
              value="moderation"
              className="min-h-11 rounded-xl px-2 text-xs sm:text-sm md:min-h-0 md:rounded-sm"
            >
              Moderation
              {moderationQueue.length > 0 && (
                <Badge variant="destructive" className="ml-1.5 px-2 py-0 text-[10px] md:ml-2">
                  {moderationQueue.length}
                </Badge>
              )}
            </TabsTrigger>
            <TabsTrigger
              value="flags"
              className="min-h-11 rounded-xl px-2 text-xs sm:text-sm md:min-h-0 md:rounded-sm"
            >
              Flags
              {flags.length > 0 && (
                <Badge variant="destructive" className="ml-1.5 px-2 py-0 text-[10px] md:ml-2">
                  {flags.length}
                </Badge>
              )}
            </TabsTrigger>
            <TabsTrigger value="users" className="min-h-11 rounded-xl px-2 text-xs sm:text-sm md:min-h-0 md:rounded-sm">
              Users
            </TabsTrigger>
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
                      <p className="text-sm text-muted-foreground">Flagged Items</p>
                      <p className="text-xs text-muted-foreground">{totalFlagReports} reports</p>
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
                <Link to="/admin/lessons">
                  <Button variant="outline" className="w-full h-auto py-4 flex-col">
                    <FileText className="h-6 w-6 mb-2" />
                    Manage Lessons
                  </Button>
                </Link>
                <Link to="/admin/categories">
                  <Button variant="outline" className="w-full h-auto py-4 flex-col">
                    <Flag className="h-6 w-6 mb-2" />
                    Manage Categories
                  </Button>
                </Link>
                <Button
                  variant="outline"
                  className="w-full h-auto py-4 flex-col"
                  onClick={() => setActiveTab('users')}
                >
                  <Users className="h-6 w-6 mb-2" />
                  Manage Users
                </Button>
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
                      className="flex items-start gap-4 p-4 rounded-lg"
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
                <p className="text-sm text-muted-foreground">
                  {totalFlagReports} total reports across {flags.length} grouped item{flags.length === 1 ? '' : 's'}.
                </p>
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
                      className="rounded-lg border border-border/70 p-4"
                    >
                      <div className="flex flex-col gap-4 md:flex-row md:items-start">
                      {flag.content?.thumbnail_url ? (
                        <img
                          src={flag.content.thumbnail_url}
                          alt={flag.content.title}
                          className="mx-auto h-24 w-20 rounded-md object-cover bg-muted md:mx-0 md:h-20 md:w-14"
                        />
                      ) : (
                        <div className="mx-auto flex h-24 w-20 items-center justify-center rounded-md bg-muted text-xs text-muted-foreground md:mx-0 md:h-20 md:w-14">
                          {flag.content?.content_type ?? 'post'}
                        </div>
                      )}
                      <div className="min-w-0 flex-1">
                        <div className="mb-1 flex flex-wrap items-center gap-2">
                          <Badge variant="destructive" className="max-w-full whitespace-normal break-words leading-4">
                            {getFlagReasonSummary(flag)}
                          </Badge>
                          <Badge variant="secondary">{getFlagReportCount(flag)} reports</Badge>
                          {flag.content?.content_type ? (
                            <Badge variant="outline" className="capitalize">
                              {flag.content.content_type}
                            </Badge>
                          ) : null}
                          {flag.content?.status ? (
                            <Badge variant="secondary">{formatContentStatus(flag.content.status)}</Badge>
                          ) : null}
                          <span className="text-xs text-muted-foreground">
                            {new Date(flag.created_at).toLocaleDateString()}
                          </span>
                        </div>
                        <p className="break-words font-medium">{flag.content?.title ?? `Content ${flag.content_id}`}</p>
                        <p className="break-words text-sm text-muted-foreground">
                          Creator: @{flag.content?.creator?.display_name ?? 'anonymous'}
                        </p>
                        <p className="mt-2 break-words text-sm text-muted-foreground">
                          Reasons: {getFlagReasons(flag).join(', ') || 'No reason provided.'}
                        </p>
                        <p className="text-sm text-muted-foreground">
                          Reporter notes: {getFlagDescriptionCount(flag)} of {getFlagReportCount(flag)} reports
                        </p>
                      </div>
                      <div className="flex w-full flex-col gap-2 sm:flex-row md:w-auto md:flex-col lg:flex-row">
                        <Button
                          size="sm"
                          variant="outline"
                          className="w-full md:w-auto"
                          onClick={() => {
                            setSelectedFlagReportsPage(1);
                            setSelectedFlagReporterSearch('');
                            setSelectedFlag(flag);
                          }}
                        >
                          <Eye className="h-4 w-4 mr-1" />
                          View
                        </Button>
                        <Button
                          size="sm"
                          variant="destructive"
                          className="w-full md:w-auto"
                          onClick={() => handleOpenFlagTakeDown(flag)}
                        >
                          Take down
                        </Button>
                        <Button size="sm" className="w-full md:w-auto" onClick={() => handleResolveFlag(flag.id)}>
                          Resolve
                        </Button>
                      </div>
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
              <CardContent className="space-y-4">
                {isUsersLoading ? (
                  <div className="flex items-center justify-center py-8 text-muted-foreground">
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Loading users...
                  </div>
                ) : filteredUsers.length === 0 ? (
                  <div className="text-center py-8 text-muted-foreground">
                    <Users className="h-12 w-12 mx-auto mb-3" />
                    <p>No users match that search.</p>
                  </div>
                ) : (
                  filteredUsers.map((user) => (
                    <div
                      key={user.userId}
                      className="flex flex-col gap-4 rounded-lg border border-border/70 p-4 md:flex-row md:items-center md:justify-between"
                    >
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="font-semibold">{user.displayName}</p>
                          <Badge variant={user.status === 'active' ? 'secondary' : 'destructive'}>
                            {user.status}
                          </Badge>
                          {user.roles.map((role) => (
                            <Badge key={`${user.userId}-${role}`} variant="outline">
                              {role}
                            </Badge>
                          ))}
                        </div>
                        <p className="text-sm text-muted-foreground">{user.email ?? user.userId}</p>
                        <div className="mt-2 flex flex-wrap gap-3 text-xs text-muted-foreground">
                          <span>XP {user.reputationPoints}</span>
                          <span>Current streak {user.currentStreak}</span>
                          <span>Hours learned {user.totalHoursLearned}</span>
                          <span>
                            Last active {user.lastActivityDate ? new Date(user.lastActivityDate).toLocaleDateString() : 'Unknown'}
                          </span>
                        </div>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => {
                            void loadUserDetail(user.userId);
                          }}
                        >
                          <Eye className="mr-1 h-4 w-4" />
                          Open
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={userActionKey === `role:${user.userId}:${user.roles.includes('admin') ? 'user' : 'admin'}`}
                          onClick={() => {
                            void handleUpdateUserRole(user.userId, user.roles.includes('admin') ? 'user' : 'admin');
                          }}
                        >
                          {user.roles.includes('admin') ? 'Make User' : 'Make Admin'}
                        </Button>
                        <Button
                          size="sm"
                          variant={user.status === 'active' ? 'destructive' : 'secondary'}
                          disabled={userActionKey === `status:${user.userId}:${user.status === 'active' ? 'suspended' : 'active'}`}
                          onClick={() => {
                            void handleToggleUserStatus(user);
                          }}
                        >
                          {user.status === 'active' ? 'Suspend' : 'Reactivate'}
                        </Button>
                      </div>
                    </div>
                  ))
                )}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>

        <Dialog
          open={isUserDetailOpen}
          onOpenChange={(open) => {
            setIsUserDetailOpen(open);
            if (!open) {
              setSelectedUser(null);
              setUserContentRejectTarget(null);
              setUserContentRejectReason('');
              setUserContentRejectAttempted(false);
            }
          }}
        >
          <DialogContent className="max-w-6xl w-[95vw] p-0 overflow-hidden">
            <div className="max-h-[85vh] overflow-y-auto">
              <DialogHeader className="p-6">
                <DialogTitle>User Management</DialogTitle>
                <DialogDescription>
                  Review account status, moderation footprint, and learning activity for a user.
                </DialogDescription>
              </DialogHeader>

              {isUserDetailLoading ? (
                <div className="flex items-center justify-center px-6 pb-8 text-muted-foreground">
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Loading user details...
                </div>
              ) : selectedUser ? (
                <div className="grid gap-6 px-6 pb-6">
                  <div className="grid gap-4 rounded-lg border border-border/70 p-4 lg:grid-cols-[1.6fr_1fr]">
                    <div className="space-y-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="text-xl font-semibold">{selectedUser.summary.displayName}</p>
                        <Badge variant={selectedUser.summary.status === 'active' ? 'secondary' : 'destructive'}>
                          {selectedUser.summary.status}
                        </Badge>
                        {selectedUser.summary.roles.map((role) => (
                          <Badge key={`${selectedUser.summary.userId}-${role}`} variant="outline">
                            {role}
                          </Badge>
                        ))}
                      </div>
                      <p className="text-sm text-muted-foreground">
                        {selectedUser.summary.email ?? selectedUser.summary.userId}
                      </p>
                      <div className="grid gap-1 text-sm text-muted-foreground sm:grid-cols-2">
                        <p>Created: {selectedUser.summary.createdAt ? new Date(selectedUser.summary.createdAt).toLocaleString() : 'Unknown'}</p>
                        <p>Last sign in: {selectedUser.summary.lastSignInAt ? new Date(selectedUser.summary.lastSignInAt).toLocaleString() : 'Never'}</p>
                        <p>Last active: {selectedUser.summary.lastActivityDate ? new Date(selectedUser.summary.lastActivityDate).toLocaleDateString() : 'Unknown'}</p>
                        <p>Suspended until: {selectedUser.suspendedUntil ? new Date(selectedUser.suspendedUntil).toLocaleString() : 'Not suspended'}</p>
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-2 lg:justify-end">
                      <Button
                        type="button"
                        variant="outline"
                        disabled={userActionKey === `role:${selectedUser.summary.userId}:${selectedUser.summary.roles.includes('admin') ? 'user' : 'admin'}`}
                        onClick={() => {
                          void handleUpdateUserRole(
                            selectedUser.summary.userId,
                            selectedUser.summary.roles.includes('admin') ? 'user' : 'admin'
                          );
                        }}
                      >
                        {selectedUser.summary.roles.includes('admin') ? 'Make User' : 'Make Admin'}
                      </Button>
                      <Button
                        type="button"
                        variant={selectedUser.summary.status === 'active' ? 'destructive' : 'secondary'}
                        disabled={userActionKey === `status:${selectedUser.summary.userId}:${selectedUser.summary.status === 'active' ? 'suspended' : 'active'}`}
                        onClick={() => {
                          void handleToggleUserStatus(selectedUser.summary);
                        }}
                      >
                        {selectedUser.summary.status === 'active' ? 'Suspend Account' : 'Reactivate Account'}
                      </Button>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-5">
                    <Card>
                      <CardContent className="p-4">
                        <p className="text-xs text-muted-foreground">Posted</p>
                        <p className="text-xl font-semibold">{selectedUser.activity.postedContentCount}</p>
                      </CardContent>
                    </Card>
                    <Card>
                      <CardContent className="p-4">
                        <p className="text-xs text-muted-foreground">Comments</p>
                        <p className="text-xl font-semibold">{selectedUser.activity.commentCount}</p>
                      </CardContent>
                    </Card>
                    <Card>
                      <CardContent className="p-4">
                        <p className="text-xs text-muted-foreground">Lessons</p>
                        <p className="text-xl font-semibold">{selectedUser.activity.enrolledLessonCount}</p>
                      </CardContent>
                    </Card>
                    <Card>
                      <CardContent className="p-4">
                        <p className="text-xs text-muted-foreground">Completed</p>
                        <p className="text-xl font-semibold">{selectedUser.activity.completedLessonCount}</p>
                      </CardContent>
                    </Card>
                    <Card>
                      <CardContent className="p-4">
                        <p className="text-xs text-muted-foreground">Badges</p>
                        <p className="text-xl font-semibold">{selectedUser.activity.badgeCount}</p>
                      </CardContent>
                    </Card>
                  </div>

                  <div className="grid gap-6 lg:grid-cols-2">
                    <Card>
                      <CardHeader>
                        <CardTitle>Posted Content</CardTitle>
                      </CardHeader>
                      <CardContent className="space-y-3">
                        {selectedUser.postedContent.length === 0 ? (
                          <p className="text-sm text-muted-foreground">No posted content.</p>
                        ) : (
                          selectedUser.postedContent.map((content) => (
                            <div key={content.id} className="rounded-lg border border-border/70 p-3">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <div>
                                  <p className="font-medium">{content.title}</p>
                                  <p className="text-xs text-muted-foreground">
                                    {content.content_type} · {content.status}
                                  </p>
                                </div>
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="destructive"
                                  disabled={content.status === 'rejected'}
                                  onClick={() => {
                                    setUserContentRejectTarget(content);
                                    setUserContentRejectReason('');
                                    setUserContentRejectAttempted(false);
                                  }}
                                >
                                  {content.status === 'rejected' ? 'Taken down' : 'Take down'}
                                </Button>
                              </div>
                              {content.description ? (
                                <p className="mt-2 text-sm text-muted-foreground line-clamp-2">
                                  {content.description}
                                </p>
                              ) : null}
                            </div>
                          ))
                        )}
                      </CardContent>
                    </Card>

                    <Card>
                      <CardHeader>
                        <CardTitle>Comments</CardTitle>
                      </CardHeader>
                      <CardContent className="space-y-3">
                        {selectedUser.comments.length === 0 ? (
                          <p className="text-sm text-muted-foreground">No comments recorded.</p>
                        ) : (
                          selectedUser.comments.map((comment) => (
                            <div key={comment.id} className="rounded-lg border border-border/70 p-3">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <p className="text-sm font-medium">{comment.contentTitle ?? 'Unknown content'}</p>
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="outline"
                                  disabled={userActionKey === `comment:${comment.id}` || !comment.contentId}
                                  onClick={() => {
                                    void handleDeleteUserComment(comment.id, comment.contentId);
                                  }}
                                >
                                  Delete
                                </Button>
                              </div>
                              <p className="mt-2 text-sm text-muted-foreground">{comment.body ?? 'No comment body.'}</p>
                              <p className="mt-1 text-xs text-muted-foreground">
                                {comment.createdAt ? new Date(comment.createdAt).toLocaleString() : 'Unknown time'}
                              </p>
                            </div>
                          ))
                        )}
                      </CardContent>
                    </Card>
                  </div>

                  <div className="grid gap-6 lg:grid-cols-2">
                    <Card>
                      <CardHeader>
                        <CardTitle>Lesson Progress</CardTitle>
                      </CardHeader>
                      <CardContent className="space-y-3">
                        {selectedUser.lessonProgress.length === 0 ? (
                          <p className="text-sm text-muted-foreground">No lesson progress recorded.</p>
                        ) : (
                          selectedUser.lessonProgress.map((progress) => (
                            <div key={progress.id} className="rounded-lg border border-border/70 p-3">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <div>
                                  <p className="font-medium">{progress.lessonTitle ?? progress.lessonId ?? 'Unknown lesson'}</p>
                                  <p className="text-xs text-muted-foreground">
                                    {progress.status ?? 'unknown'} · {progress.progressPercentage}%
                                  </p>
                                </div>
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="outline"
                                  disabled={userActionKey === `progress:${progress.lessonId}` || !progress.lessonId}
                                  onClick={() => {
                                    void handleResetLessonProgress(progress.lessonId);
                                  }}
                                >
                                  Reset
                                </Button>
                              </div>
                              <p className="mt-1 text-xs text-muted-foreground">
                                Last accessed {progress.lastAccessedAt ? new Date(progress.lastAccessedAt).toLocaleString() : 'Unknown'}
                              </p>
                            </div>
                          ))
                        )}
                      </CardContent>
                    </Card>

                    <Card>
                      <CardHeader>
                        <CardTitle>Badges and Saved Activity</CardTitle>
                      </CardHeader>
                      <CardContent className="space-y-4">
                        <div className="space-y-2">
                          <p className="text-sm font-medium">Badges</p>
                          {selectedUser.badges.length === 0 ? (
                            <p className="text-sm text-muted-foreground">No badges yet.</p>
                          ) : (
                            selectedUser.badges.map((badge) => (
                              <div key={`${badge.lessonId ?? badge.badgeName}-${badge.earnedAt ?? 'locked'}`} className="rounded-md border border-border/70 p-3">
                                <p className="font-medium">{badge.badgeName}</p>
                                <p className="text-xs text-muted-foreground">
                                  {badge.lessonTitle ?? 'Lesson badge'} · {badge.earned ? 'Earned' : 'Locked'}
                                </p>
                              </div>
                            ))
                          )}
                        </div>
                        <div className="space-y-2">
                          <p className="text-sm font-medium">Liked and Saved</p>
                          <p className="text-sm text-muted-foreground">
                            {selectedUser.activity.likedContentCount} liked · {selectedUser.activity.savedContentCount} saved
                          </p>
                          {selectedUser.likedContent.slice(0, 3).map((content) => (
                            <div key={`liked-${content.id}`} className="rounded-md border border-border/70 p-3">
                              <p className="font-medium">{content.title}</p>
                              <p className="text-xs text-muted-foreground">Liked content</p>
                            </div>
                          ))}
                          {selectedUser.savedContent.slice(0, 3).map((content) => (
                            <div key={`saved-${content.id}`} className="rounded-md border border-border/70 p-3">
                              <p className="font-medium">{content.title}</p>
                              <p className="text-xs text-muted-foreground">Saved content</p>
                            </div>
                          ))}
                        </div>
                      </CardContent>
                    </Card>
                  </div>

                  <div className="grid gap-6 lg:grid-cols-2">
                    <Card>
                      <CardHeader>
                        <CardTitle>Browsing History</CardTitle>
                      </CardHeader>
                      <CardContent className="space-y-2">
                        {selectedUser.browsingHistory.length === 0 ? (
                          <p className="text-sm text-muted-foreground">No browsing history.</p>
                        ) : (
                          selectedUser.browsingHistory.map((entry) => (
                            <div key={entry.id} className="rounded-md border border-border/70 p-3">
                              <p className="font-medium">{entry.title ?? entry.itemId ?? 'Viewed item'}</p>
                              <p className="text-xs text-muted-foreground">
                                {entry.viewedAt ? new Date(entry.viewedAt).toLocaleString() : 'Unknown time'}
                              </p>
                            </div>
                          ))
                        )}
                      </CardContent>
                    </Card>

                    <Card>
                      <CardHeader>
                        <CardTitle>Search History</CardTitle>
                      </CardHeader>
                      <CardContent className="space-y-2">
                        {selectedUser.searchHistory.length === 0 ? (
                          <p className="text-sm text-muted-foreground">No search history.</p>
                        ) : (
                          selectedUser.searchHistory.map((entry) => (
                            <div key={entry.id} className="rounded-md border border-border/70 p-3">
                              <p className="font-medium">{entry.query ?? 'Untitled query'}</p>
                              <p className="text-xs text-muted-foreground">
                                {entry.searchedAt ? new Date(entry.searchedAt).toLocaleString() : 'Unknown time'}
                              </p>
                            </div>
                          ))
                        )}
                      </CardContent>
                    </Card>

                    <Card>
                      <CardHeader>
                        <CardTitle>Chat History</CardTitle>
                      </CardHeader>
                      <CardContent className="space-y-2">
                        {selectedUser.chatHistory.length === 0 ? (
                          <p className="text-sm text-muted-foreground">No chat history.</p>
                        ) : (
                          selectedUser.chatHistory.map((message, index) => (
                            <div key={`${message.timestamp}-${index}`} className="rounded-md border border-border/70 p-3">
                              <p className="text-xs uppercase text-muted-foreground">{message.role}</p>
                              <p className="mt-1 text-sm">{message.message}</p>
                              <p className="mt-1 text-xs text-muted-foreground">
                                {message.timestamp ? new Date(message.timestamp).toLocaleString() : 'Unknown time'}
                              </p>
                            </div>
                          ))
                        )}
                      </CardContent>
                    </Card>
                  </div>
                </div>
              ) : (
                <div className="px-6 pb-8 text-sm text-muted-foreground">Select a user to review.</div>
              )}
            </div>
          </DialogContent>
        </Dialog>

        <Dialog
          open={userContentRejectTarget !== null}
          onOpenChange={(open) => {
            if (!open) {
              setUserContentRejectTarget(null);
              setUserContentRejectReason('');
              setUserContentRejectAttempted(false);
            }
          }}
        >
          <DialogContent className="max-w-lg">
            <DialogHeader>
              <DialogTitle>Take Down User Content</DialogTitle>
              <DialogDescription>
                Provide a clear moderation reason before removing this content from the user profile.
              </DialogDescription>
            </DialogHeader>
            <div className="grid gap-2">
              <Label htmlFor="user-content-reject-reason">Reason</Label>
              <Textarea
                id="user-content-reject-reason"
                value={userContentRejectReason}
                onChange={(e) => setUserContentRejectReason(sanitizeInputValue(e.target.value, MAX_LONG_TEXT))}
                maxLength={MAX_LONG_TEXT}
                rows={4}
              />
              {userContentRejectAttempted && !normalizeText(userContentRejectReason, MAX_LONG_TEXT) ? (
                <p className="text-xs text-destructive">A takedown reason is required.</p>
              ) : null}
              <p className="text-xs text-muted-foreground">{userContentRejectReason.length}/{MAX_LONG_TEXT}</p>
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setUserContentRejectTarget(null);
                  setUserContentRejectReason('');
                  setUserContentRejectAttempted(false);
                }}
              >
                Cancel
              </Button>
              <Button
                type="button"
                variant="destructive"
                disabled={!!userContentRejectTarget && userActionKey === `content:${userContentRejectTarget.id}`}
                onClick={() => {
                  void handleTakeDownUserContent();
                }}
              >
                Take down
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

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
                <DialogHeader className="p-6">
                  <DialogTitle>Moderation Review</DialogTitle>
                  <DialogDescription>
                    Review all content details and media before approving or rejecting.
                  </DialogDescription>
                </DialogHeader>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-0">
                  <div className="p-6 space-y-4">
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

                    <div className="grid gap-2">
                      <Label>Quick Quiz (Optional)</Label>
                      <p className="text-xs text-muted-foreground">
                        Add a short multiple choice quiz for this video.
                      </p>
                      {quizLoading && (
                        <p className="text-xs text-muted-foreground flex items-center gap-2">
                          <Loader2 className="h-3 w-3 animate-spin" />
                          Loading quiz...
                        </p>
                      )}
                      {quizError && (
                        <p className="text-xs text-destructive">{quizError}</p>
                      )}
                      {quizQuestions.map((question, index) => (
                        <div key={question.id} className="rounded-lg border border-muted p-3 space-y-3">
                          <div className="flex items-center justify-between">
                            <span className="text-sm font-medium">Question {index + 1}</span>
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon"
                              onClick={() => handleRemoveQuizQuestion(question.id)}
                            >
                              <X className="h-4 w-4" />
                            </Button>
                          </div>

                          <div className="grid gap-2">
                            <Label>Question text</Label>
                            <Textarea
                              value={question.question_text}
                              rows={2}
                              maxLength={MAX_LONG_TEXT}
                              onChange={(e) =>
                                updateQuizQuestion(question.id, {
                                  question_text: sanitizeInputValue(e.target.value, MAX_LONG_TEXT),
                                })
                              }
                            />
                          </div>

                          <div className="grid gap-2">
                            <Label>Options</Label>
                            <div className="grid gap-2">
                              {Object.entries(question.options).map(([key, value]) => (
                                <div key={key} className="flex items-center gap-2">
                                  <Badge variant="secondary" className="w-8 justify-center">
                                    {key}
                                  </Badge>
                                  <Input
                                    value={value}
                                    maxLength={MAX_LONG_TEXT}
                                    onChange={(e) =>
                                      updateQuizOption(
                                        question.id,
                                        key,
                                        sanitizeInputValue(e.target.value, MAX_LONG_TEXT)
                                      )
                                    }
                                  />
                                </div>
                              ))}
                            </div>
                          </div>

                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <div className="grid gap-2">
                              <Label>Correct answer</Label>
                              <Select
                                value={question.correct_answer}
                                onValueChange={(value) =>
                                  updateQuizQuestion(question.id, { correct_answer: value })
                                }
                              >
                                <SelectTrigger>
                                  <SelectValue placeholder="Select correct option" />
                                </SelectTrigger>
                                <SelectContent>
                                  {Object.keys(question.options).map((optionKey) => (
                                    <SelectItem key={optionKey} value={optionKey}>
                                      {optionKey}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            </div>
                            <div className="grid gap-2">
                              <Label>Points</Label>
                              <Input
                                type="number"
                                min={1}
                                value={question.points}
                                onChange={(e) =>
                                  updateQuizQuestion(question.id, {
                                    points: Math.max(1, Number(e.target.value) || 1),
                                  })
                                }
                              />
                            </div>
                          </div>

                          <div className="grid gap-2">
                            <Label>Explanation (Optional)</Label>
                            <Textarea
                              value={question.explanation}
                              rows={2}
                              maxLength={MAX_LONG_TEXT}
                              onChange={(e) =>
                                updateQuizQuestion(question.id, {
                                  explanation: sanitizeInputValue(e.target.value, MAX_LONG_TEXT),
                                })
                              }
                            />
                          </div>
                        </div>
                      ))}
                      <Button
                        type="button"
                        variant="outline"
                        onClick={handleAddQuizQuestion}
                        disabled={quizLoading}
                      >
                        <Plus className="h-4 w-4 mr-2" />
                        Add question
                      </Button>
                      {hasAttemptedQuizSave && quizValidationError && (
                        <p className="text-xs text-destructive">{quizValidationError}</p>
                      )}
                    </div>

                    <div className="flex flex-wrap gap-2 pt-2">
                      <Button type="button" onClick={handleSave} disabled={!isFormValid || isSaving || quizSaving}>
                        {isSaving || quizSaving ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : null}
                        Save
                      </Button>
                      <Button
                        type="button"
                        onClick={() => handleApprove(selectedModerationItem.content_id)}
                        disabled={!canApprove || isApproving}
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

        <Dialog
          open={selectedFlag !== null}
          onOpenChange={(open) => {
            if (!open) {
              setSelectedFlag(null);
              setSelectedFlagReportsPage(1);
              setSelectedFlagReporterSearch('');
            }
          }}
        >
          <DialogContent className="max-h-[calc(100dvh-1.5rem)] max-w-2xl overflow-y-auto">
            <DialogHeader>
              <DialogTitle>Flag Review</DialogTitle>
              <DialogDescription>
                Review the reported post, reporter context, and moderation action.
              </DialogDescription>
            </DialogHeader>
            {selectedFlag ? (
              <div className="grid gap-6">
                <div className="grid gap-3 rounded-lg border border-border/70 p-4">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant="destructive">{getFlagReasonSummary(selectedFlag)}</Badge>
                    <Badge variant="secondary">{getFlagReportCount(selectedFlag)} reports</Badge>
                    {selectedFlag.content?.content_type ? (
                      <Badge variant="outline" className="capitalize">
                        {selectedFlag.content.content_type}
                      </Badge>
                    ) : null}
                    {selectedFlag.content?.status ? (
                      <Badge variant="secondary">{formatContentStatus(selectedFlag.content.status)}</Badge>
                    ) : null}
                  </div>
                  <div>
                    <p className="text-base font-semibold">
                      {selectedFlag.content?.title ?? `Content ${selectedFlag.content_id}`}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      Creator: @{selectedFlag.content?.creator?.display_name ?? 'anonymous'}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      Latest report on {new Date(selectedFlag.created_at).toLocaleString()}
                    </p>
                  </div>
                  <div className="grid gap-3 rounded-md bg-muted/40 p-3">
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                      <div>
                        <p className="text-sm font-medium">Submitted reports</p>
                        <p className="text-xs text-muted-foreground">
                          Showing {selectedFlagReports.length} of {selectedFlag.report_count} reports
                        </p>
                      </div>
                      <div className="grid gap-1">
                        <Label htmlFor="flag-report-search">Search reporter</Label>
                        <Input
                          id="flag-report-search"
                          value={selectedFlagReporterSearch}
                          onChange={(event) => {
                            setSelectedFlagReporterSearch(sanitizeInputValue(event.target.value, 80));
                            setSelectedFlagReportsPage(1);
                          }}
                          placeholder="Search by username"
                        />
                      </div>
                    </div>
                    <div className="grid gap-3">
                      {selectedFlagReportsLoading ? (
                        <div className="flex items-center gap-2 text-sm text-muted-foreground">
                          <Loader2 className="h-4 w-4 animate-spin" />
                          Loading reports...
                        </div>
                      ) : selectedFlagReports.length === 0 ? (
                        <p className="text-sm text-muted-foreground">
                          {selectedFlagReporterSearch.trim()
                            ? 'No reports match that username.'
                            : 'No submitted reports found.'}
                        </p>
                      ) : (
                        selectedFlagReports.map((report: AdminContentFlagReport, index) => (
                          <div key={report.id} className="rounded-md border border-border/70 bg-background px-3 py-3">
                            <div className="flex flex-wrap items-center gap-2">
                              <Badge variant="destructive" className="max-w-full whitespace-normal break-words leading-4">
                                {report.reason}
                              </Badge>
                              <Badge variant="outline">
                                Report {(selectedFlagReportsPage - 1) * FLAG_REPORTS_PAGE_SIZE + index + 1}
                              </Badge>
                              <span className="text-xs text-muted-foreground">
                                {new Date(report.created_at).toLocaleString()}
                              </span>
                            </div>
                            <p className="mt-2 break-words text-xs text-muted-foreground">
                              Reporter: @{report.reporter?.display_name ?? report.reported_by}
                            </p>
                            <p className="mt-2 break-words text-sm text-muted-foreground">
                              {normalizeText(report.description ?? '', MAX_LONG_TEXT)
                                ? `Reporter note: ${report.description}`
                                : 'No additional reporter note.'}
                            </p>
                          </div>
                        ))
                      )}
                    </div>
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-xs text-muted-foreground">
                        Page {selectedFlagReportsPage}
                        {!selectedFlagReporterSearch.trim()
                          ? ` of ${Math.max(1, Math.ceil(selectedFlag.report_count / FLAG_REPORTS_PAGE_SIZE))}`
                          : ''}
                      </p>
                      <div className="flex gap-2">
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={selectedFlagReportsLoading || selectedFlagReportsPage <= 1}
                          onClick={() => setSelectedFlagReportsPage((current) => Math.max(1, current - 1))}
                        >
                          Previous
                        </Button>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={selectedFlagReportsLoading || !selectedFlagReportsHasNext}
                          onClick={() => setSelectedFlagReportsPage((current) => current + 1)}
                        >
                          Next
                        </Button>
                      </div>
                    </div>
                  </div>
                  <div className="rounded-lg border border-border/70 bg-muted/30 p-3">
                    {selectedFlag.content?.content_type === 'video' && selectedFlag.content.media_url ? (
                      <video
                        controls
                        src={selectedFlag.content.media_url}
                        className="w-full max-h-[360px] rounded-md bg-black"
                      />
                    ) : selectedFlag.content?.media_url ? (
                      <img
                        src={selectedFlag.content.media_url}
                        alt={selectedFlag.content.title}
                        className="w-full max-h-[360px] rounded-md object-contain bg-muted"
                      />
                    ) : selectedFlag.content?.thumbnail_url ? (
                      <img
                        src={selectedFlag.content.thumbnail_url}
                        alt={selectedFlag.content.title ?? 'Flagged content preview'}
                        className="w-full max-h-[360px] rounded-md object-contain bg-muted"
                      />
                    ) : (
                      <p className="text-sm text-muted-foreground">No media preview available.</p>
                    )}
                  </div>
                </div>

                <DialogFooter>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => {
                      void handleResolveFlag(selectedFlag.id);
                    }}
                  >
                    Resolve
                  </Button>
                  <Button type="button" variant="destructive" onClick={() => handleOpenFlagTakeDown(selectedFlag)}>
                    Take down
                  </Button>
                </DialogFooter>
              </div>
            ) : null}
          </DialogContent>
        </Dialog>

        <Dialog
          open={flagTakeDownTarget !== null}
          onOpenChange={(open) => {
            if (isTakingDownFlag) {
              return;
            }
            if (!open) {
              setFlagTakeDownTarget(null);
              setFlagTakeDownReason('');
              setFlagTakeDownAttempted(false);
            }
          }}
        >
          <DialogContent className="max-w-lg">
            <DialogHeader>
              <DialogTitle>Take Down Flagged Content</DialogTitle>
              <DialogDescription>
                This will mark the post as taken down and show your feedback to the creator in their Posted videos.
              </DialogDescription>
            </DialogHeader>
            <div className="grid gap-2">
              <Label htmlFor="flag-takedown-reason">Reason for creator</Label>
              <Textarea
                id="flag-takedown-reason"
                value={flagTakeDownReason}
                onChange={(e) => setFlagTakeDownReason(sanitizeInputValue(e.target.value, MAX_LONG_TEXT))}
                maxLength={MAX_LONG_TEXT}
                rows={4}
              />
              {flagTakeDownAttempted && !normalizeText(flagTakeDownReason, MAX_LONG_TEXT) ? (
                <p className="text-xs text-destructive">A takedown reason is required.</p>
              ) : null}
              <p className="text-xs text-muted-foreground">
                {flagTakeDownReason.length}/{MAX_LONG_TEXT}
              </p>
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                disabled={isTakingDownFlag}
                onClick={() => {
                  setFlagTakeDownTarget(null);
                  setFlagTakeDownReason('');
                  setFlagTakeDownAttempted(false);
                }}
              >
                Cancel
              </Button>
              <Button
                type="button"
                variant="destructive"
                disabled={isTakingDownFlag}
                onClick={() => {
                  void handleConfirmFlagTakeDown();
                }}
              >
                {isTakingDownFlag ? 'Taking down...' : 'Take down'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </MainLayout>
  );
};

export default AdminDashboard;
