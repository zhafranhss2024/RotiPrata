import React, { useEffect, useMemo, useRef, useState } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import {
  ArrowLeft,
  Check,
  ChevronLeft,
  ChevronRight,
  Image as ImageIcon,
  Link2,
  Loader2,
  Plus,
  Upload,
  Video,
  X,
} from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import type { ContentType, Category, Content, QuizQuestion } from '@/types';
import { useAuthContext } from '@/contexts/AuthContext';
import { isHlsUrl, useHlsVideo } from '@/hooks/useHlsVideo';
import {
  fetchCategories,
  fetchContentById,
  fetchAdminContentQuiz,
  fetchContentMediaStatus,
  fetchTags,
  saveAdminContentQuiz,
  startContentMediaLink,
  startContentMediaUpload,
  submitContent,
  updateAdminContent,
  updateDraftContent,
} from '@/lib/api';

const steps = [
  { id: 'media', label: 'Media' },
  { id: 'basics', label: 'Basics' },
  { id: 'details', label: 'Details' },
  { id: 'tags', label: 'Tags' },
  { id: 'review', label: 'Review' },
];

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

const isValidMediaLink = (value: string) => {
  const lower = value.toLowerCase();
  return lower.includes('tiktok.com') || lower.includes('instagram.com/reel') || lower.includes('instagram.com/reels');
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

type CreateContentLocationState = {
  editContent?: Content;
  returnTo?: string;
};

const CreateContentPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAdmin } = useAuthContext();
  const locationState = (location.state as CreateContentLocationState | null) ?? null;
  const editingContent = locationState?.editContent ?? null;
  const isEditingContent = Boolean(editingContent?.id);
  const isAdminUser = isAdmin();
  const returnTo = locationState?.returnTo ?? '/';
  const [currentStep, setCurrentStep] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [categories, setCategories] = useState<Category[]>([]);
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    content_type: 'video' as ContentType,
    category_id: '',
    learning_objective: '',
    origin_explanation: '',
    definition_literal: '',
    definition_used: '',
    older_version_reference: '',
    tags: [] as string[],
  });
  const [contentId, setContentId] = useState<string | null>(null);
  const [mediaPreview, setMediaPreview] = useState<string | null>(null);
  const [mediaHlsUrl, setMediaHlsUrl] = useState<string | null>(null);
  const [mediaThumbnailUrl, setMediaThumbnailUrl] = useState<string | null>(null);
  const [mediaStatus, setMediaStatus] = useState<'idle' | 'uploading' | 'processing' | 'ready' | 'failed'>('idle');
  const [mediaError, setMediaError] = useState<string | null>(null);
  const [mediaMode, setMediaMode] = useState<'upload' | 'link'>('upload');
  const [mediaLink, setMediaLink] = useState('');
  const [linkError, setLinkError] = useState<string | null>(null);
  const [isDragActive, setIsDragActive] = useState(false);
  const [statusIndex, setStatusIndex] = useState(0);

  const [tagQuery, setTagQuery] = useState('');
  const [tagSuggestions, setTagSuggestions] = useState<string[]>([]);
  const [isTagLoading, setIsTagLoading] = useState(false);
  const [hasAttemptedTags, setHasAttemptedTags] = useState(false);

  const [fieldErrors, setFieldErrors] = useState({
    title: '',
    description: '',
    category_id: '',
  });

  const [quizQuestions, setQuizQuestions] = useState<ContentQuizDraftQuestion[]>([]);
  const [quizLoading, setQuizLoading] = useState(false);
  const [quizSaving, setQuizSaving] = useState(false);
  const [quizDirty, setQuizDirty] = useState(false);
  const [quizError, setQuizError] = useState<string | null>(null);
  const [hasAttemptedQuizSave, setHasAttemptedQuizSave] = useState(false);

  const lastDraftRef = useRef<string>('');
  const previewVideoRef = useRef<HTMLVideoElement | null>(null);

  useEffect(() => {
    fetchCategories()
      .then(setCategories)
      .catch((error) => console.warn('Failed to load categories', error));
  }, []);

  useEffect(() => {
    if (!editingContent) return;
    setFormData({
      title: editingContent.title ?? '',
      description: editingContent.description ?? '',
      content_type: editingContent.content_type ?? 'video',
      category_id: editingContent.category_id ?? '',
      learning_objective: editingContent.learning_objective ?? '',
      origin_explanation: editingContent.origin_explanation ?? '',
      definition_literal: editingContent.definition_literal ?? '',
      definition_used: editingContent.definition_used ?? '',
      older_version_reference: editingContent.older_version_reference ?? '',
      tags: editingContent.tags ?? [],
    });
    setContentId(editingContent.id);
    setMediaStatus(editingContent.media_url ? 'ready' : 'idle');
    setMediaPreview(null);
    setMediaHlsUrl(editingContent.content_type === 'video' ? editingContent.media_url : null);
    setMediaThumbnailUrl(editingContent.thumbnail_url ?? editingContent.media_url ?? null);
    setMediaError(null);
    setLinkError(null);
    setCurrentStep(editingContent.media_url ? 1 : 0);
  }, [editingContent?.id]);

  useEffect(() => {
    if (!editingContent?.id) {
      return;
    }
    let active = true;
    fetchContentById(editingContent.id)
      .then((content) => {
        if (!active) {
          return;
        }
        if (content.tags && content.tags.length > 0) {
          setFormData((prev) => ({
            ...prev,
            tags: content.tags ?? prev.tags,
          }));
        }
      })
      .catch((error) => console.warn('Failed to load content tags', error));
    return () => {
      active = false;
    };
  }, [editingContent?.id]);

  useEffect(() => {
    if (!isAdminUser || !contentId) {
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

    fetchAdminContentQuiz(contentId)
      .then((questions) => {
        if (!active) return;
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
  }, [contentId, isAdminUser]);

  useEffect(() => {
    if (!contentId || mediaStatus !== 'processing') return;
    let cancelled = false;
    const poll = async () => {
      try {
        const status = await fetchContentMediaStatus(contentId);
        if (cancelled) return;
        if (status.hlsUrl) {
          setMediaHlsUrl(status.hlsUrl);
        }
        if (status.thumbnailUrl) {
          setMediaThumbnailUrl(status.thumbnailUrl);
        }
        if (status.status === 'ready') {
          setMediaStatus('ready');
          setMediaError(null);
        } else if (status.status === 'failed') {
          setMediaStatus('failed');
          setMediaError(status.errorMessage || 'Media processing failed.');
        }
      } catch (error) {
        if (!cancelled) {
          console.warn('Failed to fetch media status', error);
        }
      }
    };
    const intervalId = window.setInterval(poll, 2000);
    poll();
    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [contentId, mediaStatus]);

  useEffect(() => {
    if (!contentId || isEditingContent) return;
    const payload = {
      title: normalizeText(formData.title, MAX_TITLE),
      description: normalizeText(formData.description, MAX_DESCRIPTION),
      contentType: formData.content_type,
      categoryId: formData.category_id || null,
      learningObjective: normalizeText(formData.learning_objective, MAX_OBJECTIVE),
      originExplanation: normalizeText(formData.origin_explanation, MAX_LONG_TEXT),
      definitionLiteral: normalizeText(formData.definition_literal, MAX_LONG_TEXT),
      definitionUsed: normalizeText(formData.definition_used, MAX_LONG_TEXT),
      olderVersionReference: normalizeText(formData.older_version_reference, MAX_OLDER_REFERENCE),
      tags: formData.tags.map(sanitizeTag).filter(Boolean),
    };
    const serialized = JSON.stringify(payload);
    const handler = window.setTimeout(() => {
      if (serialized === lastDraftRef.current) {
        return;
      }
      updateDraftContent(contentId, payload)
        .then(() => {
          lastDraftRef.current = serialized;
        })
        .catch((error) => console.warn('Failed to autosave draft', error));
    }, 500);
    return () => window.clearTimeout(handler);
  }, [contentId, formData, isEditingContent]);

  useEffect(() => {
    const query = tagQuery.trim();
    if (!query) {
      setTagSuggestions([]);
      setIsTagLoading(false);
      return;
    }
    setIsTagLoading(true);
    const handler = window.setTimeout(() => {
      fetchTags(query)
        .then((tags) => {
          const filtered = tags.filter((tag) => !formData.tags.includes(tag));
          setTagSuggestions(filtered);
        })
        .catch((error) => {
          console.warn('Failed to fetch tags', error);
          setTagSuggestions([]);
        })
        .finally(() => setIsTagLoading(false));
    }, 300);
    return () => window.clearTimeout(handler);
  }, [tagQuery, formData.tags]);

  const statusMessages = useMemo(() => {
    if (mediaStatus === 'uploading') {
      return mediaMode === 'link'
        ? ['Downloading media...', 'Preparing media...', 'Getting media ready...']
        : ['Uploading media...', 'Preparing media...', 'Finalizing upload...'];
    }
    if (mediaStatus === 'processing') {
      return ['Getting media ready...', 'Optimizing media...', 'Finalizing media...'];
    }
    return [];
  }, [mediaMode, mediaStatus]);

  useEffect(() => {
    if (statusMessages.length === 0) {
      setStatusIndex(0);
      return;
    }
    setStatusIndex(0);
    const intervalId = window.setInterval(() => {
      setStatusIndex((prev) => (prev + 1) % statusMessages.length);
    }, 2000);
    return () => window.clearInterval(intervalId);
  }, [statusMessages]);

  const isMediaReady = Boolean(contentId) && mediaStatus === 'ready';
  const isBasicsValid = Boolean(formData.title.trim()) && Boolean(formData.description.trim()) && Boolean(formData.category_id);
  const linkIsValid = !mediaLink.trim() || isValidMediaLink(mediaLink);
  const tagsError = formData.tags.length > 0 ? '' : 'At least one tag is required.';
  const isTagsValid = formData.tags.length > 0;
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

  const mediaPreviewUrl = useMemo(() => {
    if (mediaPreview) return mediaPreview;
    if (formData.content_type === 'video') return mediaHlsUrl;
    return mediaThumbnailUrl;
  }, [mediaPreview, mediaHlsUrl, mediaThumbnailUrl, formData.content_type]);
  const isPreviewHls = isHlsUrl(mediaPreviewUrl);

  useHlsVideo({
    videoRef: previewVideoRef,
    src: mediaPreviewUrl,
    enabled: formData.content_type === 'video' && Boolean(mediaPreviewUrl) && isPreviewHls,
  });

  const handleMediaFile = async (file: File) => {
    if (!file.type.startsWith('video/') && !file.type.startsWith('image/')) {
      setMediaError('Only video or image files are supported.');
      setMediaStatus('failed');
      return;
    }
    setMediaError(null);
    setLinkError(null);
    setMediaStatus('uploading');
    setMediaPreview(null);
    setMediaHlsUrl(null);
    setMediaThumbnailUrl(null);

    const reader = new FileReader();
    reader.onloadend = () => {
      setMediaPreview(reader.result as string);
    };
    reader.readAsDataURL(file);

    if (file.type.startsWith('video/')) {
      setFormData((prev) => ({ ...prev, content_type: 'video' }));
    } else if (file.type.startsWith('image/')) {
      setFormData((prev) => ({ ...prev, content_type: 'image' }));
    }

    try {
      const payload = new FormData();
      payload.append('file', file);
      const response = await startContentMediaUpload(payload);
      setContentId(response.contentId);
      setMediaStatus('processing');
    } catch (error) {
      console.warn('Media upload failed', error);
      setMediaStatus('failed');
      setMediaError('Upload failed. Please try again.');
    }
  };

  const handleMediaChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    void handleMediaFile(file);
  };

  const handleDragOver = (e: React.DragEvent<HTMLLabelElement>) => {
    e.preventDefault();
    setIsDragActive(true);
  };

  const handleDragLeave = () => {
    setIsDragActive(false);
  };

  const handleDrop = (e: React.DragEvent<HTMLLabelElement>) => {
    e.preventDefault();
    setIsDragActive(false);
    const file = e.dataTransfer.files?.[0];
    if (file) {
      void handleMediaFile(file);
    }
  };

  const handleLinkSubmit = async () => {
    const trimmed = mediaLink.trim();
    if (!trimmed) return;
    if (!isValidMediaLink(trimmed)) {
      setLinkError('Only TikTok or Instagram Reels links are supported.');
      return;
    }

    setMediaError(null);
    setLinkError(null);
    setMediaStatus('uploading');
    setMediaPreview(null);
    setMediaHlsUrl(null);
    setMediaThumbnailUrl(null);
    setFormData((prev) => ({ ...prev, content_type: 'video' }));

    try {
      const response = await startContentMediaLink(trimmed);
      setContentId(response.contentId);
      setMediaStatus('processing');
    } catch (error) {
      console.warn('Link ingest failed', error);
      setMediaStatus('failed');
      setMediaError('Unable to ingest link. Please try another link.');
    }
  };

  const handleRemoveMedia = () => {
    setMediaPreview(null);
    setMediaHlsUrl(null);
    setMediaThumbnailUrl(null);
    setMediaStatus('idle');
    setMediaError(null);
    setLinkError(null);
    setContentId(null);
  };

  const handleAddTag = (value?: string) => {
    const normalized = sanitizeTag(value ?? tagQuery);
    if (!normalized) return;
    if (formData.tags.includes(normalized)) {
      setTagQuery('');
      return;
    }
    setFormData((prev) => ({ ...prev, tags: [...prev.tags, normalized] }));
    setTagQuery('');
    setTagSuggestions([]);
  };

  const handleRemoveTag = (tag: string) => {
    setFormData((prev) => ({ ...prev, tags: prev.tags.filter((t) => t !== tag) }));
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

  const validateBasics = () => {
    const nextErrors = {
      title: formData.title.trim() ? '' : 'Title is required.',
      description: formData.description.trim() ? '' : 'Description is required.',
      category_id: formData.category_id ? '' : 'Category is required.',
    };
    setFieldErrors(nextErrors);
    return !nextErrors.title && !nextErrors.description && !nextErrors.category_id;
  };

  const handleNext = () => {
    if (currentStep === 0 && !isMediaReady) {
      setMediaError('Please upload media and wait for processing to complete.');
      return;
    }
    if (currentStep === 1 && !validateBasics()) {
      return;
    }
    if (currentStep === 3 && !isTagsValid) {
      setHasAttemptedTags(true);
      return;
    }
    setCurrentStep((prev) => Math.min(prev + 1, steps.length - 1));
  };

  const handleBack = () => {
    setCurrentStep((prev) => Math.max(prev - 1, 0));
  };

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setHasAttemptedQuizSave(true);
    try {
      if (!contentId) {
        setMediaError('Please upload a video or image before submitting.');
        setIsSubmitting(false);
        return;
      }
      if (mediaStatus !== 'ready') {
        setMediaError('Media processing is not complete yet.');
        setIsSubmitting(false);
        return;
      }
      if (!validateBasics()) {
        setCurrentStep(1);
        setIsSubmitting(false);
        return;
      }
      if (!isTagsValid) {
        setCurrentStep(3);
        setHasAttemptedTags(true);
        setIsSubmitting(false);
        return;
      }
      if (isAdminUser && quizValidationError) {
        setQuizError(quizValidationError);
        setIsSubmitting(false);
        return;
      }

      const toNull = (value: string, max: number) => {
        const normalized = normalizeText(value, max);
        return normalized ? normalized : null;
      };

      const payload = {
        title: normalizeText(formData.title, MAX_TITLE),
        description: normalizeText(formData.description, MAX_DESCRIPTION),
        contentType: formData.content_type,
        categoryId: formData.category_id || null,
        learningObjective: toNull(formData.learning_objective, MAX_OBJECTIVE),
        originExplanation: toNull(formData.origin_explanation, MAX_LONG_TEXT),
        definitionLiteral: toNull(formData.definition_literal, MAX_LONG_TEXT),
        definitionUsed: toNull(formData.definition_used, MAX_LONG_TEXT),
        olderVersionReference: toNull(formData.older_version_reference, MAX_OLDER_REFERENCE),
        tags: formData.tags.map(sanitizeTag).filter(Boolean),
      };

      if (isEditingContent) {
        await updateAdminContent(contentId, payload);
        if (isAdminUser && quizDirty) {
          setQuizSaving(true);
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
          await saveAdminContentQuiz(contentId, quizPayload);
          setQuizDirty(false);
          setQuizError(null);
          setQuizSaving(false);
        }
        navigate(returnTo);
        return;
      }

      await submitContent(contentId, payload);
      if (isAdminUser && quizDirty) {
        setQuizSaving(true);
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
        await saveAdminContentQuiz(contentId, quizPayload);
        setQuizDirty(false);
        setQuizError(null);
        setQuizSaving(false);
      }

      navigate('/');
    } catch (error) {
      console.warn('Content creation failed', error);
      setQuizSaving(false);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <MainLayout>
      <div className="container max-w-3xl mx-auto px-4 py-6 md:py-8 pb-safe">
        <div className="flex items-center gap-4 mb-6">
          <Link to={returnTo} className="text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-5 w-5" />
          </Link>
          <h1 className="text-2xl font-bold">{isEditingContent ? 'Edit Content' : 'Create Content'}</h1>
        </div>

        <div className="mb-6">
          <div className="flex items-center justify-between gap-2">
            {steps.map((step, index) => {
              const isActive = index === currentStep;
              const isDone = index < currentStep;
              return (
                <div key={step.id} className="flex-1 flex flex-col items-center">
                  <div
                    className={cn(
                      'h-8 w-8 rounded-full flex items-center justify-center text-xs font-semibold border',
                      isActive && 'border-primary text-primary',
                      isDone && 'bg-primary text-white border-primary',
                      !isActive && !isDone && 'text-muted-foreground border-muted'
                    )}
                  >
                    {isDone ? <Check className="h-4 w-4" /> : index + 1}
                  </div>
                  <span className={cn('text-xs mt-2', isActive ? 'text-foreground' : 'text-muted-foreground')}>
                    {step.label}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        {currentStep === 0 && (
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Media (Required)</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex gap-2 mb-4">
                <Button
                  type="button"
                  variant={mediaMode === 'upload' ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => {
                    setMediaMode('upload');
                    setLinkError(null);
                  }}
                >
                  Upload file
                </Button>
                <Button
                  type="button"
                  variant={mediaMode === 'link' ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => {
                    setMediaMode('link');
                    setLinkError(null);
                  }}
                >
                  Paste link
                </Button>
              </div>

              {mediaMode === 'upload' ? (
                mediaPreviewUrl ? (
                  <div className="relative">
                    {formData.content_type === 'video' ? (
                      <video
                        ref={previewVideoRef}
                        src={isPreviewHls ? undefined : mediaPreviewUrl}
                        className="w-full h-56 object-cover rounded-lg"
                        controls
                      />
                    ) : (
                      <img src={mediaPreviewUrl} alt="Preview" className="w-full h-56 object-cover rounded-lg" />
                    )}
                    <Button
                      type="button"
                      variant="destructive"
                      size="icon"
                      className="absolute top-2 right-2"
                      onClick={handleRemoveMedia}
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                ) : (
                  <label
                    className={cn(
                      'flex flex-col items-center justify-center w-full h-56 border-2 border-dashed border-muted rounded-lg cursor-pointer hover:bg-muted/50 transition-colors',
                      isDragActive && 'border-primary bg-muted/60'
                    )}
                    onDragOver={handleDragOver}
                    onDragEnter={handleDragOver}
                    onDragLeave={handleDragLeave}
                    onDrop={handleDrop}
                  >
                    <Upload className="h-10 w-10 text-muted-foreground mb-2" />
                    <span className="text-sm text-muted-foreground">Upload video or image (drag & drop)</span>
                    <span className="text-xs text-muted-foreground mt-1">Max 200MB</span>
                    <input
                      type="file"
                      accept="video/*,image/*"
                      className="hidden"
                      onChange={handleMediaChange}
                    />
                  </label>
                )
              ) : (
                <div className="space-y-3">
                  <Input
                    placeholder="Paste a TikTok or Instagram Reel link"
                    value={mediaLink}
                    onChange={(e) => {
                      setMediaLink(sanitizeInputValue(e.target.value, MAX_LONG_TEXT));
                      setLinkError(null);
                    }}
                    onBlur={(e) => setMediaLink(normalizeText(e.target.value, MAX_LONG_TEXT))}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    onClick={handleLinkSubmit}
                    disabled={!mediaLink.trim() || !linkIsValid}
                  >
                    <Link2 className="h-4 w-4 mr-2" />
                    Fetch video
                  </Button>
                  {(linkError || (!linkIsValid && mediaLink.trim())) && (
                    <p className="text-sm text-destructive">
                      {linkError ?? 'Only TikTok or Instagram Reels links are supported.'}
                    </p>
                  )}
                </div>
              )}

              <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                {(mediaStatus === 'uploading' || mediaStatus === 'processing') && (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    {statusMessages[statusIndex] ?? 'Processing media...'}
                  </>
                )}
                {mediaStatus === 'ready' && 'Media ready.'}
                {mediaStatus === 'failed' && (mediaError || 'Media processing failed.')}
                {mediaStatus === 'idle' && 'Upload will start immediately after selection.'}
              </div>
              {mediaStatus === 'ready' && formData.content_type === 'video' && !mediaPreview && mediaHlsUrl && (
                <p className="mt-2 text-xs text-muted-foreground">
                  If playback does not start, this browser may not support HLS previews.
                </p>
              )}
              {mediaError && mediaStatus !== 'failed' && (
                <p className="mt-2 text-sm text-destructive">{mediaError}</p>
              )}
            </CardContent>
          </Card>
        )}

        {currentStep === 1 && (
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Basic Information</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="title">Title *</Label>
                <Input
                  id="title"
                  placeholder="e.g., Skibidi Toilet"
                  value={formData.title}
                  maxLength={MAX_TITLE}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, title: sanitizeInputValue(e.target.value, MAX_TITLE) }))
                  }
                  onBlur={(e) =>
                    setFormData((prev) => ({ ...prev, title: normalizeText(e.target.value, MAX_TITLE) }))
                  }
                  required
                />
                {fieldErrors.title && <p className="text-sm text-destructive">{fieldErrors.title}</p>}
              </div>

              <div className="space-y-2">
                <Label htmlFor="description">Description *</Label>
                <Textarea
                  id="description"
                  placeholder="Explain what this term/meme is about..."
                  value={formData.description}
                  maxLength={MAX_DESCRIPTION}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, description: sanitizeInputValue(e.target.value, MAX_DESCRIPTION) }))
                  }
                  onBlur={(e) =>
                    setFormData((prev) => ({ ...prev, description: normalizeText(e.target.value, MAX_DESCRIPTION) }))
                  }
                  rows={3}
                  required
                />
                {fieldErrors.description && <p className="text-sm text-destructive">{fieldErrors.description}</p>}
              </div>

              <div className="space-y-2">
                <Label htmlFor="category">Category *</Label>
                <Select
                  value={formData.category_id}
                  onValueChange={(value) => setFormData((prev) => ({ ...prev, category_id: value }))}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select a category" />
                  </SelectTrigger>
                  <SelectContent>
                    {categories.map((cat) => (
                      <SelectItem key={cat.id} value={cat.id}>
                        {cat.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {fieldErrors.category_id && <p className="text-sm text-destructive">{fieldErrors.category_id}</p>}
              </div>
            </CardContent>
          </Card>
        )}

        {currentStep === 2 && (
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Educational Details (Optional)</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="learning_objective">Learning Objective</Label>
                <Input
                  id="learning_objective"
                  placeholder="e.g., What 'Skibidi' means"
                  value={formData.learning_objective}
                  maxLength={MAX_OBJECTIVE}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      learning_objective: sanitizeInputValue(e.target.value, MAX_OBJECTIVE),
                    }))
                  }
                  onBlur={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      learning_objective: normalizeText(e.target.value, MAX_OBJECTIVE),
                    }))
                  }
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="origin">Origin / Where it came from</Label>
                <Textarea
                  id="origin"
                  placeholder="Explain where this term or meme originated..."
                  value={formData.origin_explanation}
                  maxLength={MAX_LONG_TEXT}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      origin_explanation: sanitizeInputValue(e.target.value, MAX_LONG_TEXT),
                    }))
                  }
                  onBlur={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      origin_explanation: normalizeText(e.target.value, MAX_LONG_TEXT),
                    }))
                  }
                  rows={2}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="definition_literal">Literal Meaning</Label>
                  <Input
                    id="definition_literal"
                    placeholder="What it literally means"
                    value={formData.definition_literal}
                    maxLength={MAX_LONG_TEXT}
                    onChange={(e) =>
                      setFormData((prev) => ({
                        ...prev,
                        definition_literal: sanitizeInputValue(e.target.value, MAX_LONG_TEXT),
                      }))
                    }
                    onBlur={(e) =>
                      setFormData((prev) => ({
                        ...prev,
                        definition_literal: normalizeText(e.target.value, MAX_LONG_TEXT),
                      }))
                    }
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="definition_used">How It Is Used</Label>
                  <Input
                    id="definition_used"
                    placeholder="How people actually use it"
                    value={formData.definition_used}
                    maxLength={MAX_LONG_TEXT}
                    onChange={(e) =>
                      setFormData((prev) => ({
                        ...prev,
                        definition_used: sanitizeInputValue(e.target.value, MAX_LONG_TEXT),
                      }))
                    }
                    onBlur={(e) =>
                      setFormData((prev) => ({
                        ...prev,
                        definition_used: normalizeText(e.target.value, MAX_LONG_TEXT),
                      }))
                    }
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="older_reference">Boomer/Millennial Equivalent</Label>
                <Input
                  id="older_reference"
                  placeholder="e.g., Like saying 'cool' or 'awesome'"
                  value={formData.older_version_reference}
                  maxLength={MAX_OLDER_REFERENCE}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      older_version_reference: sanitizeInputValue(e.target.value, MAX_OLDER_REFERENCE),
                    }))
                  }
                  onBlur={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      older_version_reference: normalizeText(e.target.value, MAX_OLDER_REFERENCE),
                    }))
                  }
                />
              </div>
            </CardContent>
          </Card>
        )}

        {currentStep === 3 && (
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Tags (Required)</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex gap-2">
                <Input
                  placeholder="Search or create a tag"
                  value={tagQuery}
                  maxLength={MAX_TAG}
                  onChange={(e) => setTagQuery(sanitizeInputValue(e.target.value, MAX_TAG))}
                  onBlur={(e) => setTagQuery(normalizeText(e.target.value, MAX_TAG))}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      handleAddTag();
                    }
                  }}
                />
                <Button type="button" variant="outline" onClick={() => handleAddTag()}>
                  <Plus className="h-4 w-4" />
                </Button>
              </div>

              {isTagLoading && (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Searching tags...
                </div>
              )}

              {tagSuggestions.length > 0 && (
                <div className="border border-muted rounded-lg p-2 space-y-1">
                  {tagSuggestions.map((tag) => (
                    <button
                      key={tag}
                      type="button"
                      className="w-full text-left text-sm px-2 py-1 rounded hover:bg-muted"
                      onClick={() => handleAddTag(tag)}
                    >
                      #{tag}
                    </button>
                  ))}
                </div>
              )}

              {tagQuery.trim() && tagSuggestions.length === 0 && !isTagLoading && (
                <p className="text-sm text-muted-foreground">Press plus to add a new tag.</p>
              )}

              <div className="flex flex-wrap gap-2">
                {formData.tags.map((tag) => (
                  <Badge key={tag} variant="secondary" className="gap-1">
                    #{tag}
                    <button type="button" onClick={() => handleRemoveTag(tag)}>
                      <X className="h-3 w-3" />
                    </button>
                  </Badge>
                ))}
              </div>
              {hasAttemptedTags && tagsError && (
                <p className="text-sm text-destructive">{tagsError}</p>
              )}

              {isAdminUser && (
                <div className="pt-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-semibold">Quick Quiz (Optional)</p>
                      <p className="text-xs text-muted-foreground">
                        Add a short multiple choice quiz for this video.
                      </p>
                    </div>
                    <Button type="button" variant="outline" onClick={handleAddQuizQuestion} disabled={quizLoading}>
                      <Plus className="h-4 w-4 mr-2" />
                      Add question
                    </Button>
                  </div>

                  {quizLoading && (
                    <div className="flex items-center gap-2 text-sm text-muted-foreground">
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Loading quiz...
                    </div>
                  )}
                  {quizError && <p className="text-sm text-destructive">{quizError}</p>}

                  {quizQuestions.map((question, index) => (
                    <div key={question.id} className="rounded-lg border border-muted p-4 space-y-3">
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-semibold">Question {index + 1}</span>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          onClick={() => handleRemoveQuizQuestion(question.id)}
                        >
                          <X className="h-4 w-4" />
                        </Button>
                      </div>

                      <div className="space-y-2">
                        <Label>Question text</Label>
                        <Textarea
                          value={question.question_text}
                          maxLength={MAX_LONG_TEXT}
                          rows={2}
                          onChange={(e) =>
                            updateQuizQuestion(question.id, {
                              question_text: sanitizeInputValue(e.target.value, MAX_LONG_TEXT),
                            })
                          }
                        />
                      </div>

                      <div className="space-y-2">
                        <Label>Options</Label>
                        <div className="space-y-2">
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
                        <div className="space-y-2">
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
                        <div className="space-y-2">
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

                      <div className="space-y-2">
                        <Label>Explanation (Optional)</Label>
                        <Textarea
                          value={question.explanation}
                          maxLength={MAX_LONG_TEXT}
                          rows={2}
                          onChange={(e) =>
                            updateQuizQuestion(question.id, {
                              explanation: sanitizeInputValue(e.target.value, MAX_LONG_TEXT),
                            })
                          }
                        />
                      </div>
                    </div>
                  ))}

                  {hasAttemptedQuizSave && quizValidationError && (
                    <p className="text-sm text-destructive">{quizValidationError}</p>
                  )}
                </div>
              )}
            </CardContent>
          </Card>
        )}

        {currentStep === 4 && (
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Review</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="rounded-lg border border-muted p-4 space-y-3">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  {formData.content_type === 'video' ? <Video className="h-4 w-4" /> : <ImageIcon className="h-4 w-4" />}
                  {formData.content_type.toUpperCase()}
                </div>
                {mediaPreviewUrl && formData.content_type === 'video' && (
                  <video
                    ref={previewVideoRef}
                    src={isPreviewHls ? undefined : mediaPreviewUrl}
                    className="w-full h-48 object-cover rounded"
                    controls
                  />
                )}
                {mediaPreviewUrl && formData.content_type === 'image' && (
                  <img src={mediaPreviewUrl} alt="Preview" className="w-full h-48 object-cover rounded" />
                )}
                {!mediaPreviewUrl && <p className="text-sm text-muted-foreground">No media preview available.</p>}
              </div>

              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">Title</p>
                <p className="font-semibold">{formData.title || 'Untitled'}</p>
              </div>

              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">Description</p>
                <p>{formData.description || 'No description provided.'}</p>
              </div>

              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">Category</p>
                <p>
                  {categories.find((cat) => cat.id === formData.category_id)?.name || 'Not selected'}
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div>
                  <p className="text-sm text-muted-foreground">Learning Objective</p>
                  <p>{formData.learning_objective || 'None'}</p>
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Origin</p>
                  <p>{formData.origin_explanation || 'None'}</p>
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Literal Meaning</p>
                  <p>{formData.definition_literal || 'None'}</p>
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">How It Is Used</p>
                  <p>{formData.definition_used || 'None'}</p>
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Older Version Reference</p>
                  <p>{formData.older_version_reference || 'None'}</p>
                </div>
              </div>

              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">Tags</p>
                {formData.tags.length ? (
                  <div className="flex flex-wrap gap-2">
                    {formData.tags.map((tag) => (
                      <Badge key={tag} variant="secondary">#{tag}</Badge>
                    ))}
                  </div>
                ) : (
                  <p>No tags selected.</p>
                )}
              </div>

              {isEditingContent ? (
                <div className="bg-muted rounded-xl p-4 text-sm text-muted-foreground">
                  <p>Your changes will be saved to this video.</p>
                </div>
              ) : (
                <div className="bg-muted rounded-xl p-4 text-sm text-muted-foreground">
                  <p>Your submission will be reviewed by moderators before appearing on the feed.</p>
                </div>
              )}
            </CardContent>
          </Card>
        )}

        <div className="mt-6 flex items-center justify-between">
          <Button type="button" variant="outline" onClick={handleBack} disabled={currentStep === 0}>
            <ChevronLeft className="h-4 w-4 mr-2" />
            Back
          </Button>

          {currentStep < steps.length - 1 ? (
            <Button type="button" onClick={handleNext} disabled={currentStep === 0 && !isMediaReady}>
              Next
              <ChevronRight className="h-4 w-4 ml-2" />
            </Button>
          ) : (
            <Button
              type="button"
              className="gradient-primary border-0"
              disabled={isSubmitting || quizSaving || !isMediaReady || !isBasicsValid || (isAdminUser && quizDirty && !isQuizValid)}
              onClick={handleSubmit}
            >
              {isSubmitting ? (isEditingContent ? 'Saving...' : 'Submitting...') : (isEditingContent ? 'Save Changes' : 'Submit for Review')}
            </Button>
          )}
        </div>
      </div>
    </MainLayout>
  );
};

export default CreateContentPage;
