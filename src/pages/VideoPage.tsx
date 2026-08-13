import { useState, useEffect, useCallback } from 'react';
import { Video, Play, Download, Clock, CircleAlert as AlertCircle, Film, RefreshCw } from 'lucide-react';
import { supabase } from '../lib/supabase';
import { useAuth } from '../lib/auth';
import type { VideoJob } from '../lib/types';

const FUNCTION_URL = `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/video-generate`;

const ASPECT_RATIOS = [
  { label: '16:9', value: '16:9' },
  { label: '9:16', value: '9:16' },
  { label: '1:1', value: '1:1' },
];

const DURATIONS = [
  { label: '5s', value: 5 },
  { label: '10s', value: 10 },
  { label: '15s', value: 15 },
];

export default function VideoPage() {
  const { user } = useAuth();
  const [prompt, setPrompt] = useState('');
  const [aspectRatio, setAspectRatio] = useState('16:9');
  const [duration, setDuration] = useState(5);
  const [jobs, setJobs] = useState<VideoJob[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchJobs = useCallback(async () => {
    if (!user) return;
    const { data, error: err } = await supabase
      .from('video_jobs')
      .select('*')
      .order('created_at', { ascending: false })
      .limit(20);

    if (!err && data) {
      setJobs(data as VideoJob[]);
    }
  }, [user]);

  useEffect(() => {
    fetchJobs();
    const interval = setInterval(fetchJobs, 5000);
    return () => clearInterval(interval);
  }, [fetchJobs]);

  const handleGenerate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!prompt.trim() || loading) return;

    setLoading(true);
    setError(null);

    try {
      const response = await fetch(FUNCTION_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_ANON_KEY}`,
        },
        body: JSON.stringify({
          prompt: prompt.trim(),
          aspect_ratio: aspectRatio,
          duration,
        }),
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData.error || `Request failed (${response.status})`);
      }

      const data = await response.json();

      if (data.error) {
        throw new Error(data.error);
      }

      setPrompt('');
      await fetchJobs();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Ein Fehler ist aufgetreten';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col h-full">
      <div className="chat-scroll px-4 py-6">
        <div className="mx-auto max-w-2xl space-y-6">
          {/* Header */}
          <div className="text-center py-4">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-accent-500/15">
              <Video className="h-7 w-7 text-accent-400" />
            </div>
            <h2 className="text-xl font-semibold text-text-primary">Video Generation</h2>
            <p className="mt-1 text-sm text-text-secondary">
              Erstelle Videos mit SkyReels-V2. Unbegrenzte Länge und Anzahl.
            </p>
          </div>

          {/* Generation form */}
          <form onSubmit={handleGenerate} className="card space-y-4">
            <div>
              <label className="mb-1.5 block text-xs font-medium text-text-secondary">Prompt</label>
              <textarea
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                rows={3}
                placeholder="Beschreibe das Video, das du erstellen möchtest..."
                className="input-field resize-none"
                disabled={loading}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="mb-1.5 block text-xs font-medium text-text-secondary">Format</label>
                <div className="flex gap-1.5">
                  {ASPECT_RATIOS.map((ar) => (
                    <button
                      key={ar.value}
                      type="button"
                      onClick={() => setAspectRatio(ar.value)}
                      className={`flex-1 rounded-lg px-2 py-2 text-xs font-medium transition-colors ${
                        aspectRatio === ar.value
                          ? 'bg-primary-500 text-white'
                          : 'bg-bg-elevated text-text-secondary hover:bg-bg-hover'
                      }`}
                    >
                      {ar.label}
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label className="mb-1.5 block text-xs font-medium text-text-secondary">Dauer</label>
                <div className="flex gap-1.5">
                  {DURATIONS.map((d) => (
                    <button
                      key={d.value}
                      type="button"
                      onClick={() => setDuration(d.value)}
                      className={`flex-1 rounded-lg px-2 py-2 text-xs font-medium transition-colors ${
                        duration === d.value
                          ? 'bg-primary-500 text-white'
                          : 'bg-bg-elevated text-text-secondary hover:bg-bg-hover'
                      }`}
                    >
                      {d.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {error && (
              <div className="flex items-start gap-2 rounded-xl border border-error-500/30 bg-error-500/10 px-4 py-3 text-sm text-error-500">
                <AlertCircle className="h-4 w-4 mt-0.5 flex-shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <button type="submit" disabled={loading || !prompt.trim()} className="btn-primary w-full">
              {loading ? (
                <>
                  <span className="h-4 w-4 animate-spin-slow rounded-full border-2 border-white/30 border-t-white" />
                  Generiere Video...
                </>
              ) : (
                <>
                  <Film className="h-4 w-4" />
                  Video generieren
                </>
              )}
            </button>
          </form>

          {/* Job list */}
          {jobs.length > 0 && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-medium text-text-secondary">Deine Videos</h3>
                <button
                  onClick={fetchJobs}
                  className="btn-ghost text-xs"
                  aria-label="Aktualisieren"
                >
                  <RefreshCw className="h-3.5 w-3.5" />
                </button>
              </div>

              {jobs.map((job) => (
                <div key={job.id} className="card">
                  <div className="flex items-start justify-between gap-3 mb-3">
                    <p className="text-sm text-text-primary line-clamp-2 flex-1">{job.prompt}</p>
                    <div className="flex items-center gap-1.5 text-xs text-text-muted flex-shrink-0">
                      <Clock className="h-3 w-3" />
                      {new Date(job.created_at).toLocaleDateString('de-DE', {
                        day: '2-digit',
                        month: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </div>
                  </div>

                  {job.status === 'pending' || job.status === 'processing' ? (
                    <div className="flex items-center gap-2 rounded-xl bg-bg-elevated px-4 py-6 text-sm text-text-secondary">
                      <span className="h-4 w-4 animate-spin-slow rounded-full border-2 border-primary-500/30 border-t-primary-500" />
                      {job.status === 'pending' ? 'In der Warteschlange...' : 'Video wird generiert...'}
                    </div>
                  ) : job.status === 'failed' ? (
                    <div className="flex items-start gap-2 rounded-xl bg-error-500/10 px-4 py-3 text-sm text-error-500">
                      <AlertCircle className="h-4 w-4 mt-0.5 flex-shrink-0" />
                      <span>{job.error_message || 'Generierung fehlgeschlagen'}</span>
                    </div>
                  ) : job.video_url ? (
                    <div className="space-y-2">
                      <video
                        src={job.video_url}
                        controls
                        playsInline
                        className="w-full rounded-xl bg-black"
                        style={{ aspectRatio: job.aspect_ratio.replace(':', ' / ') }}
                      />
                      <a
                        href={job.video_url}
                        download
                        className="btn-secondary w-full text-xs"
                      >
                        <Download className="h-3.5 w-3.5" />
                        Herunterladen
                      </a>
                    </div>
                  ) : null}

                  <div className="mt-2 flex items-center gap-2 text-[10px] text-text-muted">
                    <span className="rounded bg-bg-elevated px-1.5 py-0.5">SkyReels-V2</span>
                    <span>{job.aspect_ratio}</span>
                    <span>{job.duration}s</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
