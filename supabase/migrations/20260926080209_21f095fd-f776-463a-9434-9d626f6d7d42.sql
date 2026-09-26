CREATE TABLE public.app_settings (key text PRIMARY KEY, value text, updated_at timestamptz NOT NULL DEFAULT now());
GRANT SELECT ON public.app_settings TO anon, authenticated;
GRANT INSERT, UPDATE, DELETE ON public.app_settings TO authenticated;
GRANT ALL ON public.app_settings TO service_role;
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone reads settings" ON public.app_settings FOR SELECT USING (true);
CREATE POLICY "Admins manage settings" ON public.app_settings FOR ALL TO authenticated USING (public.has_role(auth.uid(),'admin')) WITH CHECK (public.has_role(auth.uid(),'admin'));

CREATE TABLE public.cli_device_auth (
  code text PRIMARY KEY,
  poll_secret text NOT NULL,
  user_id uuid,
  api_key text,
  created_at timestamptz NOT NULL DEFAULT now()
);
GRANT ALL ON public.cli_device_auth TO service_role;
ALTER TABLE public.cli_device_auth ENABLE ROW LEVEL SECURITY;