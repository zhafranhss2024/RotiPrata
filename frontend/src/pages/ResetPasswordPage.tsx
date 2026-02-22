import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ArrowLeft, Eye, EyeOff, Loader2 } from 'lucide-react';
import { resetPassword } from '@/lib/api';
import { PasswordStrength } from '@/components/auth/PasswordStrength';
import { isPasswordCompliant, PASSWORD_POLICY_MESSAGE } from '@/lib/passwordPolicy';
import { ApiError } from '@/lib/apiClient';
import { formatRateLimitMessage } from '@/lib/rateLimit';

const extractAccessToken = () => {
  const hash = window.location.hash.replace(/^#/, '');
  const search = window.location.search.replace(/^\?/, '');
  const params = new URLSearchParams(hash || search);
  return params.get('access_token') || params.get('token');
};

const ResetPasswordPage = () => {
  const navigate = useNavigate();
  const accessToken = useMemo(() => extractAccessToken(), []);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isRedirecting, setIsRedirecting] = useState(false);
  const [redirectDelayMs, setRedirectDelayMs] = useState(2000);

  const passwordsMismatch = password.length > 0 && confirmPassword.length > 0 && password !== confirmPassword;

  const startRedirect = (opts: { error?: string; message?: string; delayMs?: number }) => {
    if (opts.error) {
      setError(opts.error);
    }
    if (opts.message) {
      setMessage(opts.message);
    }
    if (opts.delayMs !== undefined) {
      setRedirectDelayMs(opts.delayMs);
    }
    setIsRedirecting(true);
  };

  useEffect(() => {
    if (!accessToken) {
      startRedirect({
        error: 'Missing or invalid reset token. Redirecting to sign in...',
        delayMs: 2000,
      });
    }
  }, [accessToken]);

  useEffect(() => {
    if (!isRedirecting) return;
    const timeoutId = window.setTimeout(() => {
      navigate('/login', { replace: true });
    }, redirectDelayMs);
    return () => window.clearTimeout(timeoutId);
  }, [isRedirecting, navigate, redirectDelayMs]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage('');
    setError('');

    if (!accessToken) {
      startRedirect({
        error: 'Missing or invalid reset token. Redirecting to sign in...',
        delayMs: 2000,
      });
      return;
    }

    if (!isPasswordCompliant(password)) {
      setError(PASSWORD_POLICY_MESSAGE);
      return;
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setIsSubmitting(true);
    try {
      await resetPassword(accessToken, password);
      startRedirect({
        message: 'Password updated. Redirecting to sign in...',
        delayMs: 1200,
      });
      setPassword('');
      setConfirmPassword('');
    } catch (err) {
      console.error('Reset password failed', err);
      if (err instanceof ApiError) {
        if (err.code === 'rate_limited') {
          setError(formatRateLimitMessage(err.retryAfterSeconds));
          return;
        }
        if (err.fieldErrors?.password) {
          setError(err.fieldErrors.password);
          return;
        }
        if (
          err.status === 401 ||
          err.status === 403 ||
          (err.message && /(token|jwt|expired)/i.test(err.message))
        ) {
          startRedirect({
            error: 'Missing or invalid reset token. Redirecting to sign in...',
            delayMs: 2000,
          });
          return;
        }
        if (err.message) {
          setError(err.message);
          return;
        }
      }
      setError('Unable to reset password. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <MainLayout hideNav>
      <div className="min-h-screen flex items-center justify-center px-4 py-8">
        <div className="w-full max-w-md">
          <Link to="/login" className="inline-flex items-center text-muted-foreground hover:text-foreground mb-6">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Sign In
          </Link>

          <Card>
            <CardHeader className="text-center">
              <CardTitle className="text-2xl">Set a new password</CardTitle>
              <CardDescription>
                Choose a strong password you have not used before.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {!accessToken && (
                <div className="p-3 rounded-lg bg-destructive/10 text-destructive text-sm mb-4">
                  Missing or invalid reset token. Redirecting to sign in...
                </div>
              )}
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
                {isRedirecting && (
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Redirecting to sign in...
                  </div>
                )}
                <div className="space-y-2">
                  <Label htmlFor="password">New Password</Label>
                  <div className="relative">
                    <Input
                      id="password"
                      type={showPassword ? 'text' : 'password'}
                      placeholder="••••••••"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
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
                  <PasswordStrength password={password} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="confirmPassword">Confirm Password</Label>
                  <div className="relative">
                    <Input
                      id="confirmPassword"
                      type={showConfirmPassword ? 'text' : 'password'}
                      placeholder="••••••••"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      required
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="absolute right-0 top-0 h-full"
                      onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    >
                      {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </Button>
                  </div>
                  {passwordsMismatch && (
                    <p className="text-sm text-destructive">Passwords do not match.</p>
                  )}
                </div>
                <Button
                  type="submit"
                  className="w-full"
                  size="lg"
                  disabled={isSubmitting || !accessToken || passwordsMismatch || isRedirecting}
                >
                  {isSubmitting ? 'Updating...' : 'Update password'}
                </Button>
              </form>

              <p className="text-center text-sm text-muted-foreground mt-6">
                Remembered your password?{' '}
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

export default ResetPasswordPage;
