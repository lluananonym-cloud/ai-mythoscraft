
-- 1. Extend profiles with onboarding info
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS age int,
  ADD COLUMN IF NOT EXISTS about text,
  ADD COLUMN IF NOT EXISTS interests text[],
  ADD COLUMN IF NOT EXISTS favorite_block text,
  ADD COLUMN IF NOT EXISTS playstyle text,
  ADD COLUMN IF NOT EXISTS referral text,
  ADD COLUMN IF NOT EXISTS onboarded boolean NOT NULL DEFAULT false;

-- 2. Allow admins to read every profile (admin dashboard)
DROP POLICY IF EXISTS "Admins view all profiles" ON public.profiles;
CREATE POLICY "Admins view all profiles"
ON public.profiles FOR SELECT
TO authenticated
USING (public.has_role(auth.uid(), 'admin'));

-- 3. AI Twin (Pro-only feature) — one twin per user
CREATE TABLE IF NOT EXISTS public.ai_twins (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE,
  twin_name text NOT NULL DEFAULT 'Mein Twin',
  avatar_emoji text DEFAULT '👤',
  style_summary text,
  training_samples text[] NOT NULL DEFAULT '{}',
  vocabulary text[] NOT NULL DEFAULT '{}',
  tone text,
  auto_reply_in_groups boolean NOT NULL DEFAULT false,
  is_trained boolean NOT NULL DEFAULT false,
  last_trained_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.ai_twins TO authenticated;
GRANT ALL ON public.ai_twins TO service_role;

ALTER TABLE public.ai_twins ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users manage own twin"
ON public.ai_twins FOR ALL
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Admins read all twins"
ON public.ai_twins FOR SELECT
TO authenticated
USING (public.has_role(auth.uid(), 'admin'));

CREATE TRIGGER update_ai_twins_updated_at
BEFORE UPDATE ON public.ai_twins
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
