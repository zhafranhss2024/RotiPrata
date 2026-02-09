-- Create enum types
CREATE TYPE public.app_role AS ENUM ('learner', 'contributor', 'admin');
CREATE TYPE public.content_status AS ENUM ('pending', 'approved', 'rejected');
CREATE TYPE public.content_type AS ENUM ('video', 'image', 'text');
CREATE TYPE public.category_type AS ENUM ('slang', 'meme', 'dance_trend', 'social_practice', 'cultural_reference');
CREATE TYPE public.theme_preference AS ENUM ('light', 'dark', 'system');

-- User profiles table
CREATE TABLE public.profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL UNIQUE,
    username TEXT UNIQUE NOT NULL,
    display_name TEXT,
    avatar_url TEXT,
    bio TEXT,
    date_of_birth DATE,
    is_gen_alpha BOOLEAN DEFAULT false,
    theme_preference theme_preference DEFAULT 'system',
    is_verified BOOLEAN DEFAULT false,
    reputation_points INTEGER DEFAULT 0,
    current_streak INTEGER DEFAULT 0,
    longest_streak INTEGER DEFAULT 0,
    last_activity_date DATE,
    total_hours_learned DECIMAL(10,2) DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- User roles table (separate from profiles for security)
CREATE TABLE public.user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    role app_role NOT NULL DEFAULT 'learner',
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    assigned_by UUID REFERENCES auth.users(id),
    UNIQUE (user_id, role)
);

-- Categories table
CREATE TABLE public.categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    type category_type NOT NULL,
    description TEXT,
    icon_url TEXT,
    color TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- Content/Posts table (for the TikTok-style feed)
CREATE TABLE public.content (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    content_type content_type NOT NULL,
    media_url TEXT,
    thumbnail_url TEXT,
    category_id UUID REFERENCES public.categories(id),
    status content_status DEFAULT 'pending',
    learning_objective TEXT,
    origin_explanation TEXT,
    definition_literal TEXT,
    definition_used TEXT,
    older_version_reference TEXT,
    educational_value_votes INTEGER DEFAULT 0,
    view_count INTEGER DEFAULT 0,
    is_featured BOOLEAN DEFAULT false,
    reviewed_by UUID REFERENCES auth.users(id),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_feedback TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- Content tags for additional categorization
CREATE TABLE public.content_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id UUID REFERENCES public.content(id) ON DELETE CASCADE NOT NULL,
    tag TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    UNIQUE (content_id, tag)
);

-- Lessons table
CREATE TABLE public.lessons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by UUID REFERENCES auth.users(id) NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    header_media_url TEXT,
    summary TEXT,
    learning_objectives TEXT[],
    estimated_minutes INTEGER DEFAULT 10,
    xp_reward INTEGER DEFAULT 100,
    badge_name TEXT,
    badge_icon_url TEXT,
    difficulty_level INTEGER DEFAULT 1,
    is_published BOOLEAN DEFAULT false,
    completion_count INTEGER DEFAULT 0,
    origin_content TEXT,
    definition_content TEXT,
    usage_examples TEXT[],
    lore_content TEXT,
    evolution_content TEXT,
    comparison_content TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- Lesson concepts junction table
CREATE TABLE public.lesson_concepts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id UUID REFERENCES public.lessons(id) ON DELETE CASCADE NOT NULL,
    content_id UUID REFERENCES public.content(id) ON DELETE CASCADE NOT NULL,
    order_index INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    UNIQUE (lesson_id, content_id)
);

-- Quizzes table
CREATE TABLE public.quizzes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id UUID REFERENCES public.lessons(id) ON DELETE CASCADE,
    content_id UUID REFERENCES public.content(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    quiz_type TEXT DEFAULT 'multiple_choice',
    time_limit_seconds INTEGER,
    passing_score INTEGER DEFAULT 70,
    created_by UUID REFERENCES auth.users(id) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- Quiz questions table
CREATE TABLE public.quiz_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID REFERENCES public.quizzes(id) ON DELETE CASCADE NOT NULL,
    question_text TEXT NOT NULL,
    question_type TEXT DEFAULT 'multiple_choice',
    media_url TEXT,
    options JSONB,
    correct_answer TEXT NOT NULL,
    explanation TEXT,
    points INTEGER DEFAULT 10,
    order_index INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- User lesson progress
CREATE TABLE public.user_lesson_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    lesson_id UUID REFERENCES public.lessons(id) ON DELETE CASCADE NOT NULL,
    status TEXT DEFAULT 'not_started',
    progress_percentage INTEGER DEFAULT 0,
    current_section TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    last_accessed_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    UNIQUE (user_id, lesson_id)
);

-- User quiz results
CREATE TABLE public.user_quiz_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    quiz_id UUID REFERENCES public.quizzes(id) ON DELETE CASCADE NOT NULL,
    score INTEGER NOT NULL,
    max_score INTEGER NOT NULL,
    percentage DECIMAL(5,2) NOT NULL,
    passed BOOLEAN DEFAULT false,
    answers JSONB,
    time_taken_seconds INTEGER,
    attempted_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- User achievements/badges
CREATE TABLE public.user_achievements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    achievement_name TEXT NOT NULL,
    achievement_type TEXT,
    icon_url TEXT,
    description TEXT,
    earned_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    UNIQUE (user_id, achievement_name)
);

-- Saved/bookmarked content
CREATE TABLE public.saved_content (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    content_id UUID REFERENCES public.content(id) ON DELETE CASCADE,
    lesson_id UUID REFERENCES public.lessons(id) ON DELETE CASCADE,
    saved_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    UNIQUE (user_id, content_id),
    UNIQUE (user_id, lesson_id)
);

-- Browsing history
CREATE TABLE public.browsing_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    content_id UUID REFERENCES public.content(id) ON DELETE CASCADE,
    lesson_id UUID REFERENCES public.lessons(id) ON DELETE CASCADE,
    viewed_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- Content flags/reports
CREATE TABLE public.content_flags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id UUID REFERENCES public.content(id) ON DELETE CASCADE NOT NULL,
    reported_by UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    reason TEXT NOT NULL,
    description TEXT,
    status TEXT DEFAULT 'pending',
    resolved_by UUID REFERENCES auth.users(id),
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    UNIQUE (content_id, reported_by)
);

-- Moderation queue
CREATE TABLE public.moderation_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id UUID REFERENCES public.content(id) ON DELETE CASCADE NOT NULL UNIQUE,
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    priority INTEGER DEFAULT 0,
    assigned_to UUID REFERENCES auth.users(id),
    notes TEXT
);

-- Content engagement (votes, not likes)
CREATE TABLE public.content_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id UUID REFERENCES public.content(id) ON DELETE CASCADE NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    vote_type TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    UNIQUE (content_id, user_id)
);

-- User concepts mastered
CREATE TABLE public.user_concepts_mastered (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    content_id UUID REFERENCES public.content(id) ON DELETE CASCADE NOT NULL,
    mastered_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    UNIQUE (user_id, content_id)
);

-- Security definer function for role checking
CREATE OR REPLACE FUNCTION public.has_role(_user_id UUID, _role app_role)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.user_roles
        WHERE user_id = _user_id
          AND role = _role
    )
$$;

-- Function to check if user is admin
CREATE OR REPLACE FUNCTION public.is_admin(_user_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.has_role(_user_id, 'admin')
$$;

-- Update timestamp trigger function
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public;

-- Apply update triggers
CREATE TRIGGER update_profiles_updated_at BEFORE UPDATE ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_content_updated_at BEFORE UPDATE ON public.content FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_lessons_updated_at BEFORE UPDATE ON public.lessons FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_quizzes_updated_at BEFORE UPDATE ON public.quizzes FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Function to auto-create profile on signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (user_id, username, display_name)
    VALUES (NEW.id, NEW.email, split_part(NEW.email, '@', 1));
    
    INSERT INTO public.user_roles (user_id, role)
    VALUES (NEW.id, 'learner');
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Trigger for new user signup
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Enable RLS on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content_tags ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lessons ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lesson_concepts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.quizzes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.quiz_questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_lesson_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_quiz_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_achievements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saved_content ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.browsing_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content_flags ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.moderation_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_concepts_mastered ENABLE ROW LEVEL SECURITY;

-- RLS Policies for profiles
CREATE POLICY "Profiles are viewable by everyone" ON public.profiles FOR SELECT USING (true);
CREATE POLICY "Users can update own profile" ON public.profiles FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own profile" ON public.profiles FOR INSERT WITH CHECK (auth.uid() = user_id);

-- RLS Policies for user_roles
CREATE POLICY "Users can view own roles" ON public.user_roles FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Admins can view all roles" ON public.user_roles FOR SELECT USING (public.is_admin(auth.uid()));
CREATE POLICY "Admins can manage roles" ON public.user_roles FOR ALL USING (public.is_admin(auth.uid()));

-- RLS Policies for categories
CREATE POLICY "Categories viewable by all" ON public.categories FOR SELECT USING (true);
CREATE POLICY "Admins can manage categories" ON public.categories FOR ALL USING (public.is_admin(auth.uid()));

-- RLS Policies for content
CREATE POLICY "Approved content viewable by all" ON public.content FOR SELECT USING (status = 'approved' OR creator_id = auth.uid() OR public.is_admin(auth.uid()));
CREATE POLICY "Users can create content" ON public.content FOR INSERT WITH CHECK (auth.uid() = creator_id);
CREATE POLICY "Users can update own content" ON public.content FOR UPDATE USING (auth.uid() = creator_id OR public.is_admin(auth.uid()));
CREATE POLICY "Admins can delete content" ON public.content FOR DELETE USING (public.is_admin(auth.uid()));

-- RLS Policies for content_tags
CREATE POLICY "Tags viewable by all" ON public.content_tags FOR SELECT USING (true);
CREATE POLICY "Content creators can manage tags" ON public.content_tags FOR ALL USING (
    EXISTS (SELECT 1 FROM public.content WHERE id = content_id AND creator_id = auth.uid()) OR public.is_admin(auth.uid())
);

-- RLS Policies for lessons
CREATE POLICY "Published lessons viewable by all" ON public.lessons FOR SELECT USING (is_published = true OR public.is_admin(auth.uid()));
CREATE POLICY "Admins can manage lessons" ON public.lessons FOR ALL USING (public.is_admin(auth.uid()));

-- RLS Policies for lesson_concepts
CREATE POLICY "Lesson concepts viewable by all" ON public.lesson_concepts FOR SELECT USING (true);
CREATE POLICY "Admins can manage lesson concepts" ON public.lesson_concepts FOR ALL USING (public.is_admin(auth.uid()));

-- RLS Policies for quizzes
CREATE POLICY "Quizzes viewable by all" ON public.quizzes FOR SELECT USING (true);
CREATE POLICY "Admins can manage quizzes" ON public.quizzes FOR ALL USING (public.is_admin(auth.uid()));

-- RLS Policies for quiz_questions
CREATE POLICY "Quiz questions viewable by all" ON public.quiz_questions FOR SELECT USING (true);
CREATE POLICY "Admins can manage quiz questions" ON public.quiz_questions FOR ALL USING (public.is_admin(auth.uid()));

-- RLS Policies for user progress tables
CREATE POLICY "Users can view own progress" ON public.user_lesson_progress FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can manage own progress" ON public.user_lesson_progress FOR ALL USING (auth.uid() = user_id);

CREATE POLICY "Users can view own quiz results" ON public.user_quiz_results FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own quiz results" ON public.user_quiz_results FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can view own achievements" ON public.user_achievements FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "System can manage achievements" ON public.user_achievements FOR ALL USING (auth.uid() = user_id OR public.is_admin(auth.uid()));

-- RLS Policies for saved content
CREATE POLICY "Users can view own saved" ON public.saved_content FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can manage own saved" ON public.saved_content FOR ALL USING (auth.uid() = user_id);

-- RLS Policies for browsing history
CREATE POLICY "Users can view own history" ON public.browsing_history FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can manage own history" ON public.browsing_history FOR ALL USING (auth.uid() = user_id);

-- RLS Policies for content flags
CREATE POLICY "Users can view own flags" ON public.content_flags FOR SELECT USING (auth.uid() = reported_by OR public.is_admin(auth.uid()));
CREATE POLICY "Users can create flags" ON public.content_flags FOR INSERT WITH CHECK (auth.uid() = reported_by);
CREATE POLICY "Admins can manage flags" ON public.content_flags FOR ALL USING (public.is_admin(auth.uid()));

-- RLS Policies for moderation queue
CREATE POLICY "Admins can view moderation queue" ON public.moderation_queue FOR SELECT USING (public.is_admin(auth.uid()));
CREATE POLICY "Admins can manage moderation queue" ON public.moderation_queue FOR ALL USING (public.is_admin(auth.uid()));

-- RLS Policies for content votes
CREATE POLICY "Votes viewable by all" ON public.content_votes FOR SELECT USING (true);
CREATE POLICY "Users can manage own votes" ON public.content_votes FOR ALL USING (auth.uid() = user_id);

-- RLS Policies for concepts mastered
CREATE POLICY "Users can view own mastered" ON public.user_concepts_mastered FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can manage own mastered" ON public.user_concepts_mastered FOR ALL USING (auth.uid() = user_id);

-- Insert default categories
INSERT INTO public.categories (name, type, description, color) VALUES
('Slang', 'slang', 'Modern slang terms and expressions', '#FF6B6B'),
('Memes', 'meme', 'Internet memes and viral content', '#4ECDC4'),
('Dance Trends', 'dance_trend', 'Popular dance challenges and moves', '#45B7D1'),
('Social Practices', 'social_practice', 'Social behaviors and customs', '#96CEB4'),
('Cultural References', 'cultural_reference', 'Pop culture and media references', '#FFEAA7');

-- Create storage buckets
INSERT INTO storage.buckets (id, name, public) VALUES ('content-media', 'content-media', true);
INSERT INTO storage.buckets (id, name, public) VALUES ('avatars', 'avatars', true);
INSERT INTO storage.buckets (id, name, public) VALUES ('lesson-media', 'lesson-media', true);
INSERT INTO storage.buckets (id, name, public) VALUES ('badges', 'badges', true);

-- Storage policies for content-media
CREATE POLICY "Content media viewable by all" ON storage.objects FOR SELECT USING (bucket_id = 'content-media');
CREATE POLICY "Authenticated users can upload content media" ON storage.objects FOR INSERT WITH CHECK (bucket_id = 'content-media' AND auth.role() = 'authenticated');
CREATE POLICY "Users can update own content media" ON storage.objects FOR UPDATE USING (bucket_id = 'content-media' AND auth.uid()::text = (storage.foldername(name))[1]);
CREATE POLICY "Users can delete own content media" ON storage.objects FOR DELETE USING (bucket_id = 'content-media' AND auth.uid()::text = (storage.foldername(name))[1]);

-- Storage policies for avatars
CREATE POLICY "Avatars viewable by all" ON storage.objects FOR SELECT USING (bucket_id = 'avatars');
CREATE POLICY "Users can upload own avatar" ON storage.objects FOR INSERT WITH CHECK (bucket_id = 'avatars' AND auth.uid()::text = (storage.foldername(name))[1]);
CREATE POLICY "Users can update own avatar" ON storage.objects FOR UPDATE USING (bucket_id = 'avatars' AND auth.uid()::text = (storage.foldername(name))[1]);
CREATE POLICY "Users can delete own avatar" ON storage.objects FOR DELETE USING (bucket_id = 'avatars' AND auth.uid()::text = (storage.foldername(name))[1]);

-- Storage policies for lesson-media
CREATE POLICY "Lesson media viewable by all" ON storage.objects FOR SELECT USING (bucket_id = 'lesson-media');
CREATE POLICY "Admins can manage lesson media" ON storage.objects FOR ALL USING (bucket_id = 'lesson-media' AND public.is_admin(auth.uid()));

-- Storage policies for badges
CREATE POLICY "Badges viewable by all" ON storage.objects FOR SELECT USING (bucket_id = 'badges');
CREATE POLICY "Admins can manage badges" ON storage.objects FOR ALL USING (bucket_id = 'badges' AND public.is_admin(auth.uid()));