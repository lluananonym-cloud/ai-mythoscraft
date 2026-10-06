-- Pro-Einladungen: Pro-Nutzer laden Freunde per Link ein.
-- Eingeladene bekommen 7 Tage Light, der Einlader 7 Tage Pro gratis pro Registrierung.

-- Light als Tier erlauben (falls die alte Prüfung nur free/pro kennt).
ALTER TABLE public.subscriptions DROP CONSTRAINT IF EXISTS subscriptions_tier_check;
ALTER TABLE public.subscriptions ADD CONSTRAINT subscriptions_tier_check CHECK (tier IN ('free','light','pro'));

CREATE TABLE IF NOT EXISTS public.invite_codes (
  user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  code text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.referrals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  inviter_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  invitee_id uuid NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
  bonus_days int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (inviter_id <> invitee_id)
);
CREATE INDEX IF NOT EXISTS referrals_inviter_idx ON public.referrals (inviter_id, created_at);

GRANT SELECT ON public.invite_codes TO authenticated;
GRANT SELECT ON public.referrals TO authenticated;
GRANT ALL ON public.invite_codes, public.referrals TO service_role;

ALTER TABLE public.invite_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.referrals ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Own invite code" ON public.invite_codes
  FOR SELECT TO authenticated USING (auth.uid() = user_id OR public.has_role(auth.uid(),'admin'));
CREATE POLICY "Own referrals" ON public.referrals
  FOR SELECT TO authenticated USING (auth.uid() = inviter_id OR auth.uid() = invitee_id OR public.has_role(auth.uid(),'admin'));

-- Liefert den Einladungscode des eingeloggten Pro-Nutzers (legt ihn beim ersten Mal an).
CREATE OR REPLACE FUNCTION public.get_invite_code()
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  _uid uuid := auth.uid();
  _code text;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'Nicht eingeloggt'; END IF;
  IF NOT public.is_pro(_uid) THEN RAISE EXCEPTION 'Einladen geht nur mit Pro'; END IF;
  SELECT code INTO _code FROM public.invite_codes WHERE user_id = _uid;
  IF _code IS NOT NULL THEN RETURN _code; END IF;
  LOOP
    _code := upper(substr(md5(gen_random_uuid()::text), 1, 8));
    BEGIN
      INSERT INTO public.invite_codes (user_id, code) VALUES (_uid, _code);
      RETURN _code;
    EXCEPTION WHEN unique_violation THEN
      -- Kollision: neuen Code würfeln (oder Code wurde parallel angelegt)
      SELECT code INTO _code FROM public.invite_codes WHERE user_id = _uid;
      IF _code IS NOT NULL THEN RETURN _code; END IF;
    END;
  END LOOP;
END;
$$;

-- Löst eine Einladung für das eingeloggte (neue) Konto ein.
-- Schutz: nur neue Konten (max. 7 Tage alt), nur einmal pro Konto, keine Selbsteinladung,
-- Einlader muss Pro sein, Bonus für den Einlader höchstens 10x in 30 Tagen.
CREATE OR REPLACE FUNCTION public.redeem_invite(_code text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  _uid uuid := auth.uid();
  _inviter uuid;
  _created timestamptz;
  _sub public.subscriptions%ROWTYPE;
  _recent int;
  _bonus int := 0;
BEGIN
  IF _uid IS NULL THEN RAISE EXCEPTION 'Nicht eingeloggt'; END IF;

  SELECT user_id INTO _inviter FROM public.invite_codes WHERE code = upper(trim(_code));
  IF _inviter IS NULL THEN RETURN jsonb_build_object('ok', false, 'error', 'Einladungslink ungültig'); END IF;
  IF _inviter = _uid THEN RETURN jsonb_build_object('ok', false, 'error', 'Du kannst dich nicht selbst einladen'); END IF;
  IF EXISTS (SELECT 1 FROM public.referrals WHERE invitee_id = _uid) THEN
    RETURN jsonb_build_object('ok', false, 'error', 'Du hast schon eine Einladung eingelöst');
  END IF;

  SELECT created_at INTO _created FROM auth.users WHERE id = _uid;
  IF _created IS NULL OR _created < now() - interval '7 days' THEN
    RETURN jsonb_build_object('ok', false, 'error', 'Einladungen gelten nur für neue Konten');
  END IF;
  IF NOT public.is_pro(_inviter) THEN
    RETURN jsonb_build_object('ok', false, 'error', 'Diese Einladung ist nicht mehr aktiv');
  END IF;

  -- Eingeladener: 7 Tage Light (nur wenn er nicht schon etwas Besseres hat)
  SELECT * INTO _sub FROM public.subscriptions WHERE user_id = _uid;
  IF _sub.id IS NULL THEN
    INSERT INTO public.subscriptions (user_id, tier, source, expires_at, granted_by, note)
    VALUES (_uid, 'light', 'gift', now() + interval '7 days', _inviter, 'Einladung');
  ELSIF NOT (_sub.tier IN ('light','pro') AND (_sub.expires_at IS NULL OR _sub.expires_at > now() + interval '7 days')) THEN
    UPDATE public.subscriptions
      SET tier = CASE WHEN _sub.tier = 'pro' AND (_sub.expires_at IS NULL OR _sub.expires_at > now()) THEN 'pro' ELSE 'light' END,
          source = 'gift', granted_by = _inviter, note = 'Einladung',
          expires_at = now() + interval '7 days'
      WHERE user_id = _uid;
  END IF;

  -- Einlader: 7 Tage Pro gratis (max. 10 Boni in 30 Tagen)
  SELECT count(*) INTO _recent FROM public.referrals
    WHERE inviter_id = _inviter AND bonus_days > 0 AND created_at > now() - interval '30 days';
  IF _recent < 10 THEN
    _bonus := 7;
    SELECT * INTO _sub FROM public.subscriptions WHERE user_id = _inviter;
    IF _sub.id IS NULL THEN
      -- z. B. Admin ohne Abo-Zeile: Pro ist ohnehin unbegrenzt, nichts zu tun
      NULL;
    ELSIF _sub.tier = 'pro' AND _sub.expires_at IS NOT NULL THEN
      UPDATE public.subscriptions
        SET expires_at = greatest(_sub.expires_at, now()) + make_interval(days => _bonus)
        WHERE user_id = _inviter;
    END IF;
  END IF;

  INSERT INTO public.referrals (inviter_id, invitee_id, bonus_days) VALUES (_inviter, _uid, _bonus);
  RETURN jsonb_build_object('ok', true, 'light_days', 7, 'inviter_bonus_days', _bonus);
END;
$$;

REVOKE ALL ON FUNCTION public.get_invite_code() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.redeem_invite(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_invite_code() TO authenticated;
GRANT EXECUTE ON FUNCTION public.redeem_invite(text) TO authenticated;
