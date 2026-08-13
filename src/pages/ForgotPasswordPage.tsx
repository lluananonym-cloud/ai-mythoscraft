import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { Mail, ArrowLeft, CircleCheck as CheckCircle, CircleAlert as AlertCircle } from 'lucide-react';

export default function ForgotPasswordPage() {
  const { resetPassword } = useAuth();
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState<'idle' | 'loading' | 'sent' | 'error'>('idle');
  const [errorMsg, setErrorMsg] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setStatus('loading');
    setErrorMsg('');

    const { error } = await resetPassword(email);

    if (error) {
      setStatus('error');
      setErrorMsg(error);
      return;
    }

    setStatus('sent');
  };

  if (status === 'sent') {
    return (
      <div className="app-shell flex items-center justify-center px-6">
        <div className="w-full max-w-sm text-center">
          <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-success-500/15">
            <CheckCircle className="h-7 w-7 text-success-500" />
          </div>
          <h1 className="text-xl font-bold text-text-primary mb-2">E-Mail gesendet</h1>
          <p className="text-sm text-text-secondary mb-6 leading-relaxed">
            Wir haben einen Link zum Zurücksetzen deines Passworts an{' '}
            <span className="font-medium text-text-primary">{email}</span> geschickt.
            Bitte prüfe dein Postfach und auch den Spam-Ordner.
          </p>
          <Link to="/auth" className="btn-primary w-full">
            Zurück zum Login
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="app-shell flex items-center justify-center px-6">
      <div className="w-full max-w-sm">
        <Link
          to="/auth"
          className="mb-6 inline-flex items-center gap-1.5 text-sm text-text-secondary hover:text-text-primary transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          Zurück
        </Link>

        <h1 className="text-2xl font-bold text-text-primary mb-2">Passwort vergessen</h1>
        <p className="text-sm text-text-secondary mb-6">
          Kein Problem. Gib deine E-Mail-Adresse ein und wir senden dir einen Link, um dein Passwort zurückzusetzen.
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="mb-1.5 block text-xs font-medium text-text-secondary">E-Mail</label>
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
                placeholder="dein@email.com"
                className="input-field pl-10"
                disabled={status === 'loading'}
              />
            </div>
          </div>

          {status === 'error' && (
            <div className="flex items-start gap-2 rounded-xl border border-error-500/30 bg-error-500/10 px-4 py-3 text-sm text-error-500">
              <AlertCircle className="h-4 w-4 mt-0.5 flex-shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          <button type="submit" disabled={status === 'loading'} className="btn-primary w-full">
            {status === 'loading' ? (
              <span className="h-4 w-4 animate-spin-slow rounded-full border-2 border-white/30 border-t-white" />
            ) : (
              'Link senden'
            )}
          </button>
        </form>
      </div>
    </div>
  );
}
