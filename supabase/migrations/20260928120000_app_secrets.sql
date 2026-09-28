-- Server-Geheimnisse (z. B. NVIDIA-Schlüssel), die ohne Secrets-Verwaltung gesetzt werden.
-- Nur die Edge Functions (service_role) dürfen lesen/schreiben – Browser/Nutzer nie.
CREATE TABLE IF NOT EXISTS public.app_secrets (
  name text PRIMARY KEY,
  value text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);
REVOKE ALL ON public.app_secrets FROM anon, authenticated;
GRANT ALL ON public.app_secrets TO service_role;
ALTER TABLE public.app_secrets ENABLE ROW LEVEL SECURITY;
