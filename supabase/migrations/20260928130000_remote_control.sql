-- Fernsteuerung der Mythos-Code-App vom Handy aus: kurzlebiger Kopplungscode (wie bei einer Smart-TV-App),
-- danach ein langer geheimer Token für Handy<->Server. Der PC identifiziert sich mit seinem eigenen
-- API-Schlüssel (wie beim normalen Chat). Nur die Edge-Function "remote" (service_role) darf zugreifen.
CREATE TABLE IF NOT EXISTS public.remote_pairs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text UNIQUE,
  pair_secret text,
  user_id uuid NOT NULL,
  api_key_hash text NOT NULL,
  device_name text,
  claimed boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz
);
CREATE TABLE IF NOT EXISTS public.remote_tasks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  pair_id uuid NOT NULL REFERENCES public.remote_pairs(id) ON DELETE CASCADE,
  prompt text NOT NULL,
  status text NOT NULL DEFAULT 'pending',
  result text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS remote_tasks_pair_status_idx ON public.remote_tasks (pair_id, status, created_at);

REVOKE ALL ON public.remote_pairs FROM anon, authenticated;
REVOKE ALL ON public.remote_tasks FROM anon, authenticated;
GRANT ALL ON public.remote_pairs TO service_role;
GRANT ALL ON public.remote_tasks TO service_role;
ALTER TABLE public.remote_pairs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.remote_tasks ENABLE ROW LEVEL SECURITY;
