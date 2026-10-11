-- Mythos Client (Minecraft): Spieler mit Plus und Währung, Sitzungen und Creator-Bewerbungen.
-- Zugriff nur über die Edge Function "mcc" (Service-Rolle); RLS ohne Policies sperrt alles andere.

CREATE TABLE IF NOT EXISTS public.mcc_players (
  uuid text PRIMARY KEY,                       -- Minecraft-UUID ohne Bindestriche, von Mojang bestätigt
  name text NOT NULL,
  plus boolean NOT NULL DEFAULT false,
  coins bigint NOT NULL DEFAULT 0 CHECK (coins >= 0),
  is_owner boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS mcc_players_one_owner ON public.mcc_players (is_owner) WHERE is_owner;

-- Der Owner wird über seinen Minecraft-Namen festgelegt. Beim ersten bestätigten Login mit diesem
-- Namen wird die UUID fest gespeichert; danach zählt nur noch die UUID (Namen können wechseln).
CREATE TABLE IF NOT EXISTS public.mcc_config (
  key text PRIMARY KEY,
  value text NOT NULL
);
INSERT INTO public.mcc_config (key, value) VALUES
  ('owner_name', 'MinePro641'),
  ('owner_start_coins', '1000')
ON CONFLICT (key) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.mcc_sessions (
  token_hash text PRIMARY KEY,
  player_uuid text NOT NULL REFERENCES public.mcc_players(uuid) ON DELETE CASCADE,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS mcc_sessions_player_idx ON public.mcc_sessions (player_uuid);

CREATE TABLE IF NOT EXISTS public.mcc_coin_log (
  id bigserial PRIMARY KEY,
  player_uuid text NOT NULL REFERENCES public.mcc_players(uuid) ON DELETE CASCADE,
  amount bigint NOT NULL,
  reason text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.mcc_creator_applications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  mc_name text NOT NULL,
  mc_uuid text,
  discord text NOT NULL,
  platform text NOT NULL,
  channel_url text NOT NULL,
  followers int NOT NULL DEFAULT 0 CHECK (followers >= 0),
  message text NOT NULL DEFAULT '',
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','rejected')),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS mcc_creator_one_pending
  ON public.mcc_creator_applications (lower(mc_name)) WHERE status = 'pending';

ALTER TABLE public.mcc_players ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mcc_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mcc_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mcc_coin_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mcc_creator_applications ENABLE ROW LEVEL SECURITY;

GRANT ALL ON public.mcc_players, public.mcc_config, public.mcc_sessions, public.mcc_coin_log,
  public.mcc_creator_applications TO service_role;
GRANT USAGE, SELECT ON SEQUENCE public.mcc_coin_log_id_seq TO service_role;

-- Admins der Website dürfen Bewerbungen lesen und bearbeiten.
CREATE POLICY "Admins read creator applications" ON public.mcc_creator_applications
  FOR SELECT TO authenticated USING (public.has_role(auth.uid(),'admin'));
CREATE POLICY "Admins update creator applications" ON public.mcc_creator_applications
  FOR UPDATE TO authenticated USING (public.has_role(auth.uid(),'admin'));
GRANT SELECT, UPDATE ON public.mcc_creator_applications TO authenticated;

-- Währung atomar gutschreiben (kein Lesen-dann-Schreiben in der Edge Function).
CREATE OR REPLACE FUNCTION public.mcc_add_coins(_uuid text, _amount bigint, _reason text)
RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE _new bigint;
BEGIN
  UPDATE public.mcc_players SET coins = coins + _amount, updated_at = now()
    WHERE uuid = _uuid RETURNING coins INTO _new;
  IF _new IS NULL THEN RAISE EXCEPTION 'Spieler nicht gefunden'; END IF;
  INSERT INTO public.mcc_coin_log (player_uuid, amount, reason) VALUES (_uuid, _amount, _reason);
  RETURN _new;
END $$;
REVOKE ALL ON FUNCTION public.mcc_add_coins(text, bigint, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mcc_add_coins(text, bigint, text) TO service_role;
