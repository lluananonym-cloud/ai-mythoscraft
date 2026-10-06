CREATE TABLE public.ad_campaigns (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product text NOT NULL,
  link text NOT NULL,
  ad_text text NOT NULL,
  budget text NOT NULL,
  contact text NOT NULL,
  status text NOT NULL DEFAULT 'pending',
  weight integer NOT NULL DEFAULT 1,
  ai_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  approved_at timestamptz
);
GRANT INSERT ON public.ad_campaigns TO anon, authenticated;
GRANT SELECT, UPDATE, DELETE ON public.ad_campaigns TO authenticated;
GRANT ALL ON public.ad_campaigns TO service_role;
ALTER TABLE public.ad_campaigns ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone can request an ad" ON public.ad_campaigns FOR INSERT TO anon, authenticated
  WITH CHECK (status = 'pending' AND weight = 1 AND approved_at IS NULL AND char_length(product) <= 200 AND char_length(link) <= 300 AND char_length(ad_text) <= 500 AND char_length(budget) <= 300 AND char_length(contact) <= 255);
CREATE POLICY "Admins manage ads" ON public.ad_campaigns FOR ALL TO authenticated
  USING (public.has_role(auth.uid(), 'admin')) WITH CHECK (public.has_role(auth.uid(), 'admin'));

-- Öffentliche, sichere Ansicht nur der freigegebenen Werbungen (ohne Kontaktdaten)
CREATE OR REPLACE FUNCTION public.active_ads()
RETURNS TABLE(id uuid, product text, link text, ad_text text, weight integer)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT id, product, link, ad_text, weight FROM public.ad_campaigns WHERE status = 'approved'
$$;
GRANT EXECUTE ON FUNCTION public.active_ads() TO anon, authenticated;