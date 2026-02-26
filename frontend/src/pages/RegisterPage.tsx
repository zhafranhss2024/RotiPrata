import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Checkbox } from '@/components/ui/checkbox';
import { Eye, EyeOff, ArrowLeft } from 'lucide-react';
import { useAuthContext } from '@/contexts/AuthContext';
import { buildGoogleOAuthUrl, checkDisplayNameAvailability } from '@/lib/api';
import { PasswordStrength } from '@/components/auth/PasswordStrength';
import { isPasswordCompliant, PASSWORD_POLICY_MESSAGE } from '@/lib/passwordPolicy';
import {
  isDisplayNameFormatValid,
  DISPLAY_NAME_POLICY_MESSAGE,
} from '@/lib/displayNamePolicy';

const RegisterPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { register, isLoading, isAuthenticated } = useAuthContext();
  const from = (location.state as { from?: string } | null)?.from || '/';
  const [formData, setFormData] = useState({
    email: '',
    displayName: '',
    password: '',
    confirmPassword: '',
    isGenAlpha: false,
    acceptTerms: false,
  });
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [displayNameStatus, setDisplayNameStatus] = useState<'idle' | 'checking' | 'available' | 'taken' | 'invalid' | 'error'>('idle');

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      navigate(from, { replace: true });
    }
  }, [from, isAuthenticated, isLoading, navigate]);

  useEffect(() => {
    const raw = formData.displayName;
    if (!raw.trim()) {
      setDisplayNameStatus('idle');
      return;
    }
    if (!isDisplayNameFormatValid(raw)) {
      setDisplayNameStatus('invalid');
      return;
    }
    setDisplayNameStatus('checking');
    const timeout = window.setTimeout(async () => {
      try {
        const result = await checkDisplayNameAvailability(raw);
        setDisplayNameStatus(result.available ? 'available' : 'taken');
      } catch (err) {
        console.error('Display name check failed', err);
        setDisplayNameStatus('error');
      }
    }, 500);
    return () => window.clearTimeout(timeout);
  }, [formData.displayName]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setMessage('');

    if (!isDisplayNameFormatValid(formData.displayName)) {
      setError(DISPLAY_NAME_POLICY_MESSAGE);
      return;
    }

    if (displayNameStatus === 'checking') {
      setError('Checking display name availability. Please wait.');
      return;
    }

    if (displayNameStatus === 'taken') {
      setError('Display name already in use.');
      return;
    }

    if (displayNameStatus === 'error') {
      setError('Unable to verify display name availability. Please try again.');
      return;
    }

    // Validation
    if (formData.password !== formData.confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    if (!formData.acceptTerms) {
      setError('Please accept the terms and conditions');
      return;
    }

    if (!isPasswordCompliant(formData.password)) {
      setError(PASSWORD_POLICY_MESSAGE);
      return;
    }
    
    const result = await register(
      formData.email,
      formData.password,
      formData.displayName,
      formData.isGenAlpha
    );
    if (result.success) {
      if (result.requiresVerification) {
        setMessage(result.message || 'Check your email to confirm your account.');
        return;
      }
      navigate(from, { replace: true });
      return;
    }
    setError(result.error || 'Registration failed');
  };

  const handleGoogleRegister = () => {
    const redirectTo = `${window.location.origin}/auth/callback`;
    window.location.href = buildGoogleOAuthUrl(redirectTo);
  };

  return (
    <MainLayout hideNav>
      <div className="min-h-screen flex items-center justify-center px-4 py-8">
        <div className="w-full max-w-md">
          {/* Back button */}
          <Link to="/" className="inline-flex items-center text-muted-foreground hover:text-foreground mb-6">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Feed
          </Link>

          <Card>
            <CardHeader className="text-center">
              <div className="w-16 h-16 mx-auto mb-4 gradient-primary rounded-2xl flex items-center justify-center">
                <span className="text-3xl">🥞</span>
              </div>
              <CardTitle className="text-2xl">Create Account</CardTitle>
              <CardDescription>
                Join Rotiprata and start your brain rot journey
              </CardDescription>
            </CardHeader>
            
            <CardContent>
              {/* OAuth buttons */}
              <Button
                variant="outline"
                className="w-full mb-4"
                onClick={handleGoogleRegister}
                type="button"
              >
                <svg className="h-5 w-5 mr-2" viewBox="0 0 24 24">
                  <path
                    fill="currentColor"
                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                  />
                  <path
                    fill="currentColor"
                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  />
                  <path
                    fill="currentColor"
                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                  />
                  <path
                    fill="currentColor"
                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                  />
                </svg>
                Continue with Google
              </Button>

              <div className="relative my-6">
                <Separator />
                <span className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 bg-card px-2 text-xs text-muted-foreground">
                  or register with email
                </span>
              </div>

              {/* Registration form */}
              <form onSubmit={handleSubmit} className="space-y-4">
                {message && (
                  <div className="p-3 rounded-lg bg-success/10 text-success text-sm">
                    {message}
                  </div>
                )}
                {error && (
                  <div className="p-3 rounded-lg bg-destructive/10 text-destructive text-sm">
                    {error}
                  </div>
                )}
                
                <div className="space-y-2">
                  <Label htmlFor="displayName">Display name</Label>
                  <Input
                    id="displayName"
                    type="text"
                    placeholder="cooluser123"
                    value={formData.displayName}
                    onChange={(e) => setFormData({ ...formData, displayName: e.target.value })}
                    required
                  />
                  {formData.displayName && (
                    <p className="text-xs text-muted-foreground">
                      {displayNameStatus === 'checking' && 'Checking availability...'}
                      {displayNameStatus === 'available' && 'Display name is available.'}
                      {displayNameStatus === 'taken' && 'Display name is already taken.'}
                      {displayNameStatus === 'invalid' && DISPLAY_NAME_POLICY_MESSAGE}
                      {displayNameStatus === 'error' && 'Unable to check display name right now.'}
                    </p>
                  )}
                </div>
                
                <div className="space-y-2">
                  <Label htmlFor="email">Email</Label>
                  <Input
                    id="email"
                    type="email"
                    placeholder="you@example.com"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    required
                  />
                </div>
                
                <div className="space-y-2">
                  <Label htmlFor="password">Password</Label>
                  <div className="relative">
                    <Input
                      id="password"
                      type={showPassword ? 'text' : 'password'}
                      placeholder="••••••••"
                      value={formData.password}
                      onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                      required
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="absolute right-0 top-0 h-full"
                      onClick={() => setShowPassword(!showPassword)}
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </Button>
                  </div>
                  <PasswordStrength password={formData.password} />
                </div>
                
                <div className="space-y-2">
                  <Label htmlFor="confirmPassword">Confirm Password</Label>
                  <Input
                    id="confirmPassword"
                    type="password"
                    placeholder="••••••••"
                    value={formData.confirmPassword}
                    onChange={(e) => setFormData({ ...formData, confirmPassword: e.target.value })}
                    required
                  />
                </div>

                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="isGenAlpha"
                    checked={formData.isGenAlpha}
                    onCheckedChange={(checked) => setFormData({ ...formData, isGenAlpha: !!checked })}
                  />
                  <Label htmlFor="isGenAlpha" className="text-sm">
                    I was born in 2010 or later (Gen Alpha)
                  </Label>
                </div>

                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="acceptTerms"
                    checked={formData.acceptTerms}
                    onCheckedChange={(checked) => setFormData({ ...formData, acceptTerms: !!checked })}
                    required
                  />
                  <Label htmlFor="acceptTerms" className="text-sm">
                    I accept the{' '}
                    <Link to="/terms" className="text-primary hover:underline">
                      Terms of Service
                    </Link>{' '}
                    and{' '}
                    <Link to="/privacy" className="text-primary hover:underline">
                      Privacy Policy
                    </Link>
                  </Label>
                </div>

                <Button type="submit" className="w-full gradient-primary border-0" size="lg" disabled={isLoading}>
                  {isLoading ? 'Creating account...' : 'Create Account'}
                </Button>
              </form>

              <p className="text-center text-sm text-muted-foreground mt-6">
                Already have an account?{' '}
                <Link to="/login" className="text-primary hover:underline font-medium">
                  Sign in
                </Link>
              </p>
            </CardContent>
          </Card>
        </div>
      </div>
    </MainLayout>
  );
};

export default RegisterPage;
