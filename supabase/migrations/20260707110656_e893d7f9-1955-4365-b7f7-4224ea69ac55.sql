
CREATE TABLE public.mc_link_codes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL UNIQUE,
  mc_uuid text NOT NULL,
  mc_name text NOT NULL,
  server_id uuid REFERENCES public.mc_servers(id) ON DELETE CASCADE,
  claimed_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  claimed_at timestamptz,
  expires_at timestamptz NOT NULL DEFAULT (now() + interval '30 minutes'),
  created_at timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE ON public.mc_link_codes TO authenticated;
GRANT ALL ON public.mc_link_codes TO service_role;
ALTER TABLE public.mc_link_codes ENABLE ROW LEVEL SECURITY;
CREATE POLICY "auth can read unclaimed or own codes" ON public.mc_link_codes
  FOR SELECT TO authenticated USING (claimed_by IS NULL OR claimed_by = auth.uid());
CREATE POLICY "auth can claim codes" ON public.mc_link_codes
  FOR UPDATE TO authenticated USING (claimed_by IS NULL OR claimed_by = auth.uid())
  WITH CHECK (claimed_by = auth.uid());

CREATE TABLE public.mc_players (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  mc_uuid text NOT NULL UNIQUE,
  mc_name text NOT NULL,
  server_id uuid REFERENCES public.mc_servers(id) ON DELETE SET NULL,
  linked_at timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT, DELETE ON public.mc_players TO authenticated;
GRANT ALL ON public.mc_players TO service_role;
ALTER TABLE public.mc_players ENABLE ROW LEVEL SECURITY;
CREATE POLICY "users see own MC links" ON public.mc_players
  FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY "users can unlink own MC" ON public.mc_players
  FOR DELETE TO authenticated USING (user_id = auth.uid());
