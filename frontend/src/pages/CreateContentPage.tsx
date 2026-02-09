import React, { useEffect, useState } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { 
  Upload, 
  X, 
  ArrowLeft,
  Video,
  Image as ImageIcon,
  FileText,
  Plus,
} from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import type { ContentType, Category } from '@/types';
import { createContent, fetchCategories, uploadContentMedia } from '@/lib/api';

// Backend: /api/content, /api/content/upload, /api/categories
// Dummy data is returned when mocks are enabled.

const CreateContentPage = () => {
  const navigate = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [categories, setCategories] = useState<Category[]>([]);
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    content_type: 'text' as ContentType,
    category_id: '',
    learning_objective: '',
    origin_explanation: '',
    definition_literal: '',
    definition_used: '',
    older_version_reference: '',
    tags: [] as string[],
  });
  const [mediaFile, setMediaFile] = useState<File | null>(null);
  const [mediaPreview, setMediaPreview] = useState<string | null>(null);
  const [newTag, setNewTag] = useState('');

  useEffect(() => {
    fetchCategories()
      .then(setCategories)
      .catch((error) => console.warn('Failed to load categories', error));
  }, []);

  const handleMediaChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setMediaFile(file);
      
      // Create preview
      const reader = new FileReader();
      reader.onloadend = () => {
        setMediaPreview(reader.result as string);
      };
      reader.readAsDataURL(file);

      // Auto-detect content type
      if (file.type.startsWith('video/')) {
        setFormData(prev => ({ ...prev, content_type: 'video' }));
      } else if (file.type.startsWith('image/')) {
        setFormData(prev => ({ ...prev, content_type: 'image' }));
      }
    }
  };

  const handleAddTag = () => {
    if (newTag.trim() && !formData.tags.includes(newTag.trim())) {
      setFormData(prev => ({ ...prev, tags: [...prev.tags, newTag.trim()] }));
      setNewTag('');
    }
  };

  const handleRemoveTag = (tag: string) => {
    setFormData(prev => ({ ...prev, tags: prev.tags.filter(t => t !== tag) }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);

    try {
      let mediaUrl: string | null = null;
      if (mediaFile) {
        const payload = new FormData();
        payload.append('file', mediaFile);
        const upload = await uploadContentMedia(payload);
        mediaUrl = upload?.url ?? null;
      }

      await createContent({
        ...formData,
        tags: formData.tags.filter(Boolean),
        media_url: mediaUrl,
      });

      navigate('/');
    } catch (error) {
      console.warn('Content creation failed', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <MainLayout>
      <div className="container max-w-2xl mx-auto px-4 py-6 md:py-8 pb-safe">
        {/* Header */}
        <div className="flex items-center gap-4 mb-6">
          <Link to="/" className="text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-5 w-5" />
          </Link>
          <h1 className="text-2xl font-bold">Create Content</h1>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Media Upload */}
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Media (Optional)</CardTitle>
            </CardHeader>
            <CardContent>
              {mediaPreview ? (
                <div className="relative">
                  {formData.content_type === 'video' ? (
                    <video src={mediaPreview} className="w-full h-48 object-cover rounded-lg" controls />
                  ) : (
                    <img src={mediaPreview} alt="Preview" className="w-full h-48 object-cover rounded-lg" />
                  )}
                  <Button
                    type="button"
                    variant="destructive"
                    size="icon"
                    className="absolute top-2 right-2"
                    onClick={() => {
                      setMediaFile(null);
                      setMediaPreview(null);
                    }}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              ) : (
                <label className="flex flex-col items-center justify-center w-full h-48 border-2 border-dashed border-muted rounded-lg cursor-pointer hover:bg-muted/50 transition-colors">
                  <Upload className="h-10 w-10 text-muted-foreground mb-2" />
                  <span className="text-sm text-muted-foreground">Upload video or image</span>
                  <span className="text-xs text-muted-foreground mt-1">Max 50MB</span>
                  <input
                    type="file"
                    accept="video/*,image/*"
                    className="hidden"
                    onChange={handleMediaChange}
                  />
                </label>
              )}

              {/* Content type selection */}
              <div className="flex gap-2 mt-4">
                {(['video', 'image', 'text'] as ContentType[]).map((type) => (
                  <Button
                    key={type}
                    type="button"
                    variant={formData.content_type === type ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => setFormData(prev => ({ ...prev, content_type: type }))}
                  >
                    {type === 'video' && <Video className="h-4 w-4 mr-1" />}
                    {type === 'image' && <ImageIcon className="h-4 w-4 mr-1" />}
                    {type === 'text' && <FileText className="h-4 w-4 mr-1" />}
                    {type.charAt(0).toUpperCase() + type.slice(1)}
                  </Button>
                ))}
              </div>
            </CardContent>
          </Card>

          {/* Basic Info */}
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
                  onChange={(e) => setFormData(prev => ({ ...prev, title: e.target.value }))}
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="description">Description *</Label>
                <Textarea
                  id="description"
                  placeholder="Explain what this term/meme is about..."
                  value={formData.description}
                  onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
                  rows={3}
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="category">Category *</Label>
                <Select
                  value={formData.category_id}
                  onValueChange={(value) => setFormData(prev => ({ ...prev, category_id: value }))}
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
              </div>

              <div className="space-y-2">
                <Label htmlFor="learning_objective">Learning Objective</Label>
                <Input
                  id="learning_objective"
                  placeholder="e.g., What 'Skibidi' means"
                  value={formData.learning_objective}
                  onChange={(e) => setFormData(prev => ({ ...prev, learning_objective: e.target.value }))}
                />
              </div>
            </CardContent>
          </Card>

          {/* Educational Content */}
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Educational Details</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="origin">Origin / Where it came from</Label>
                <Textarea
                  id="origin"
                  placeholder="Explain where this term/meme originated..."
                  value={formData.origin_explanation}
                  onChange={(e) => setFormData(prev => ({ ...prev, origin_explanation: e.target.value }))}
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
                    onChange={(e) => setFormData(prev => ({ ...prev, definition_literal: e.target.value }))}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="definition_used">How It's Used</Label>
                  <Input
                    id="definition_used"
                    placeholder="How people actually use it"
                    value={formData.definition_used}
                    onChange={(e) => setFormData(prev => ({ ...prev, definition_used: e.target.value }))}
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="older_reference">Boomer/Millennial Equivalent</Label>
                <Input
                  id="older_reference"
                  placeholder="e.g., Like saying 'cool' or 'awesome'"
                  value={formData.older_version_reference}
                  onChange={(e) => setFormData(prev => ({ ...prev, older_version_reference: e.target.value }))}
                />
              </div>
            </CardContent>
          </Card>

          {/* Tags */}
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Tags</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex gap-2 mb-3">
                <Input
                  placeholder="Add a tag"
                  value={newTag}
                  onChange={(e) => setNewTag(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), handleAddTag())}
                />
                <Button type="button" variant="outline" onClick={handleAddTag}>
                  <Plus className="h-4 w-4" />
                </Button>
              </div>
              
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
            </CardContent>
          </Card>

          {/* Submit notice */}
          <div className="bg-muted rounded-xl p-4 text-sm text-muted-foreground">
            <p>
              ⚠️ Your submission will be reviewed by our moderators before appearing on the feed.
              Make sure your content is accurate and educational!
            </p>
          </div>

          {/* Submit */}
          <Button
            type="submit"
            className="w-full gradient-primary border-0"
            size="lg"
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Submitting...' : 'Submit for Review'}
          </Button>
        </form>
      </div>
    </MainLayout>
  );
};

export default CreateContentPage;
