-- Mythos Cloud: Backend + KI-Gateway für Apps, die Mythos Code für Nutzer baut (wie Lovable Cloud).
-- Die KI verbindet eine App per Werkzeug "cloud" (Edge-Function "app-cloud", action "connect").
-- Jede App bekommt einen öffentlichen App-Schlüssel (mca_…), mit dem sie Daten speichern und die KI
-- nutzen kann. Kosten/Limits laufen über den Besitzer. Nur die Edge-Function (service_role) greift
-- auf Daten und Zähler zu; Besitzer dürfen ihre eigenen Apps sehen.
CREATE TABLE IF NOT EXISTS public.app_cloud_projects (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  name text NOT NULL,
  app_key text NOT NULL UNIQUE,
  ai_daily_limit integer NOT NULL DEFAULT 200,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz,
  UNIQUE (user_id, name)
);

CREATE TABLE IF NOT EXISTS public.app_cloud_rows (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id uuid NOT NULL REFERENCES public.app_cloud_projects(id) ON DELETE CASCADE,
  collection text NOT NULL,
  data jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS app_cloud_rows_project_collection_idx ON public.app_cloud_rows (project_id, collection, created_at);

CREATE TABLE IF NOT EXISTS public.app_cloud_usage (
  id bigserial PRIMARY KEY,
  project_id uuid NOT NULL REFERENCES public.app_cloud_projects(id) ON DELETE CASCADE,
  kind text NOT NULL DEFAULT 'ai',
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS app_cloud_usage_project_idx ON public.app_cloud_usage (project_id, kind, created_at);

REVOKE ALL ON public.app_cloud_projects FROM anon, authenticated;
REVOKE ALL ON public.app_cloud_rows FROM anon, authenticated;
REVOKE ALL ON public.app_cloud_usage FROM anon, authenticated;
GRANT SELECT ON public.app_cloud_projects TO authenticated;
GRANT ALL ON public.app_cloud_projects TO service_role;
GRANT ALL ON public.app_cloud_rows TO service_role;
GRANT ALL ON public.app_cloud_usage TO service_role;
GRANT USAGE, SELECT ON SEQUENCE public.app_cloud_usage_id_seq TO service_role;
ALTER TABLE public.app_cloud_projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_cloud_rows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_cloud_usage ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Owners see their apps" ON public.app_cloud_projects;
CREATE POLICY "Owners see their apps" ON public.app_cloud_projects
  FOR SELECT TO authenticated USING (auth.uid() = user_id);
