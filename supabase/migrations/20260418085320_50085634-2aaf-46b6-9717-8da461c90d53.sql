CREATE TYPE public.boost_mode AS ENUM ('permanent', 'temporary', 'oneshot');

CREATE TABLE public.boost_codes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL UNIQUE,
  mode public.boost_mode NOT NULL DEFAULT 'permanent',
  daily_limit integer NOT NULL DEFAULT 1000,
  duration_days integer,
  bonus_requests integer,
  max_uses integer NOT NULL DEFAULT 1,
  used_count integer NOT NULL DEFAULT 0,
  expires_at timestamptz,
  note text,
  created_by uuid,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.boost_redemptions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code_id uuid NOT NULL REFERENCES public.boost_codes(id) ON DELETE CASCADE,
  user_id uuid NOT NULL,
  mode public.boost_mode NOT NULL,
  daily_limit integer,
  bonus_remaining integer,
  expires_at timestamptz,
  redeemed_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(code_id, user_id)
);

CREATE INDEX idx_boost_redemptions_user ON public.boost_redemptions(user_id);

ALTER TABLE public.boost_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.boost_redemptions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Admins manage boost codes"
ON public.boost_codes FOR ALL
USING (public.has_role(auth.uid(), 'admin'))
WITH CHECK (public.has_role(auth.uid(), 'admin'));

CREATE POLICY "Users see own redemptions"
ON public.boost_redemptions FOR SELECT
USING (auth.uid() = user_id OR public.has_role(auth.uid(), 'admin'));

CREATE POLICY "Admins manage redemptions"
ON public.boost_redemptions FOR ALL
USING (public.has_role(auth.uid(), 'admin'))
WITH CHECK (public.has_role(auth.uid(), 'admin'));