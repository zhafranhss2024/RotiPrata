import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ArrowLeft } from 'lucide-react';
import { requestPasswordReset } from '@/lib/api';
import { ApiError } from '@/lib/apiClient';
import { formatRateLimitMessage } from '@/lib/rateLimit';

const ForgotPasswordPage = () => {
  const [email, setEmail] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage('');
    setError('');
    setIsSubmitting(true);

    try {
      const redirectTo = `${window.location.origin}/reset-password`;
      await requestPasswordReset(email, redirectTo);
      setMessage("If an account exists for this email, you'll receive a reset link shortly.");
    } catch (err) {
      console.error('Password reset request failed', err);
      if (err instanceof ApiError && err.code === 'rate_limited') {
        setError('Reset email not sent. Please wait 1 hour before trying again.');
      } else {
        setMessage("If an account exists for this email, you'll receive a reset link shortly.");
      }
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
              <CardTitle className="text-2xl">Reset your password</CardTitle>
              <CardDescription>
                Enter your email and we will send you a reset link.
              </CardDescription>
            </CardHeader>
            <CardContent>
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
                  <Label htmlFor="email">Email</Label>
                  <Input
                    id="email"
                    type="email"
                    placeholder="you@example.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
                <Button type="submit" className="w-full" size="lg" disabled={isSubmitting}>
                  {isSubmitting ? 'Sending...' : 'Send reset link'}
                </Button>
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
    </MainLayout>
  );
};

export default ForgotPasswordPage;
