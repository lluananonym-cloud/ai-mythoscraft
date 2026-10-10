-- Mythos Client Cosmetics: Capes (auch animiert), die der Owner im Launcher erstellt.
-- Animierte Capes sind ein senkrechter Streifen aus Einzelbildern (je 64x32 oder ein Vielfaches davon).

CREATE TABLE IF NOT EXISTS public.mcc_cosmetics (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  type text NOT NULL DEFAULT 'cape' CHECK (type IN ('cape')),
  texture_path text NOT NULL,                  -- Pfad im Bucket mcc-cosmetics
  frames int NOT NULL DEFAULT 1 CHECK (frames BETWEEN 1 AND 64),
  frame_time_ms int NOT NULL DEFAULT 100 CHECK (frame_time_ms BETWEEN 20 AND 5000),
  plus_only boolean NOT NULL DEFAULT false,
  active boolean NOT NULL DEFAULT true,
  created_by text REFERENCES public.mcc_players(uuid) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.mcc_players
  ADD COLUMN IF NOT EXISTS equipped_cape uuid REFERENCES public.mcc_cosmetics(id) ON DELETE SET NULL;

ALTER TABLE public.mcc_cosmetics ENABLE ROW LEVEL SECURITY;
GRANT ALL ON public.mcc_cosmetics TO service_role;

-- Öffentlicher Bucket für die Texturen; hochladen darf nur die Edge Function (Service-Rolle).
INSERT INTO storage.buckets (id, name, public)
VALUES ('mcc-cosmetics', 'mcc-cosmetics', true)
ON CONFLICT (id) DO NOTHING;
