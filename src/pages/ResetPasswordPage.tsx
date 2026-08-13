import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { Lock, Eye, EyeOff, CircleCheck as CheckCircle, CircleAlert as AlertCircle } from 'lucide-react';

export default function ResetPasswordPage() {
  const { updatePassword } = useAuth();
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [status, setStatus] = useState<'idle' | 'loading' | 'done' | 'error'>('idle');
  const [errorMsg, setErrorMsg] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (password.length < 6) {
      setStatus('error');
      setErrorMsg('Passwort muss mindestens 6 Zeichen lang sein.');
      return;
    }

    if (password !== confirmPassword) {
      setStatus('error');
      setErrorMsg('Die Passwörter stimmen nicht überein.');
      return;
    }

    setStatus('loading');
    setErrorMsg('');

    const { error } = await updatePassword(password);

    if (error) {
      setStatus('error');
      setErrorMsg(error);
      return;
    }

    setStatus('done');
    setTimeout(() => navigate('/'), 2500);
  };

  if (status === 'done') {
    return (
      <div className="app-shell flex items-center justify-center px-6">
        <div className="w-full max-w-sm text-center">
          <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-success-500/15">
            <CheckCircle className="h-7 w-7 text-success-500" />
          </div>
          <h1 className="text-xl font-bold text-text-primary mb-2">Passwort geändert</h1>
          <p className="text-sm text-text-secondary mb-4">
            Dein Passwort wurde erfolgreich aktualisiert. Du wirst gleich weitergeleitet.
          </p>
          <div className="h-1 w-full overflow-hidden rounded-full bg-bg-elevated">
            <div className="h-full animate-pulse rounded-full bg-primary-500" style={{ width: '100%' }} />
          </div>
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
          Zurück zum Login
        </Link>

        <h1 className="text-2xl font-bold text-text-primary mb-2">Neues Passwort</h1>
        <p className="text-sm text-text-secondary mb-6">
          Wähle ein neues Passwort für dein Konto.
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="mb-1.5 block text-xs font-medium text-text-secondary">Neues Passwort</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={6}
                autoComplete="new-password"
                placeholder="Mindestens 6 Zeichen"
                className="input-field pl-10 pr-10"
                disabled={status === 'loading'}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-text-muted hover:text-text-secondary"
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-xs font-medium text-text-secondary">Passwort bestätigen</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
              <input
                type={showPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                minLength={6}
                autoComplete="new-password"
                placeholder="Passwort wiederholen"
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
              'Passwort speichern'
            )}
          </button>
        </form>
      </div>
    </div>
  );
}
