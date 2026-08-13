import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { Sparkles, Mail, Lock, Eye, EyeOff } from 'lucide-react';

export default function AuthPage() {
  const { signIn, signUp } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState<'signin' | 'signup'>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    const fn = mode === 'signin' ? signIn : signUp;
    const { error: err } = await fn(email, password);

    if (err) {
      setError(err);
      setLoading(false);
      return;
    }

    if (mode === 'signup') {
      // After signup, Supabase auto-signs in (email confirmation is off)
      navigate('/');
    } else {
      navigate('/');
    }
  };

  return (
    <div className="app-shell flex items-center justify-center px-6">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-500 shadow-lg shadow-primary-500/20">
            <Sparkles className="h-7 w-7 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-text-primary">AI Studio</h1>
          <p className="mt-1 text-sm text-text-secondary">
            {mode === 'signin' ? 'Willkommen zurück' : 'Erstelle dein Konto'}
          </p>
        </div>

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
              />
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-xs font-medium text-text-secondary">Passwort</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={6}
                autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
                placeholder="Mindestens 6 Zeichen"
                className="input-field pl-10 pr-10"
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

          {error && (
            <div className="rounded-xl border border-error-500/30 bg-error-500/10 px-4 py-3 text-sm text-error-500">
              {error}
            </div>
          )}

          <button type="submit" disabled={loading} className="btn-primary w-full">
            {loading ? (
              <span className="h-4 w-4 animate-spin-slow rounded-full border-2 border-white/30 border-t-white" />
            ) : mode === 'signin' ? (
              'Anmelden'
            ) : (
              'Konto erstellen'
            )}
          </button>
        </form>

        <div className="mt-6 space-y-3 text-center">
          {mode === 'signin' && (
            <Link
              to="/forgot-password"
              className="text-xs text-text-secondary hover:text-primary-400 transition-colors"
            >
              Passwort vergessen?
            </Link>
          )}
          <div className="text-sm text-text-secondary">
            {mode === 'signin' ? 'Noch kein Konto?' : 'Schon ein Konto?'}{' '}
            <button
              onClick={() => {
                setMode(mode === 'signin' ? 'signup' : 'signin');
                setError(null);
              }}
              className="font-medium text-primary-400 hover:text-primary-300"
            >
              {mode === 'signin' ? 'Registrieren' : 'Anmelden'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
