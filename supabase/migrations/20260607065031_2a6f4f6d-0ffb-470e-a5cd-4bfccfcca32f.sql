
CREATE TABLE public.pro_games (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL,
  title TEXT NOT NULL,
  prompt TEXT NOT NULL,
  genre TEXT,
  html TEXT NOT NULL,
  play_count INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.pro_games TO authenticated;
GRANT ALL ON public.pro_games TO service_role;

ALTER TABLE public.pro_games ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users manage own games"
  ON public.pro_games FOR ALL TO authenticated
  USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Admins view all games"
  ON public.pro_games FOR SELECT TO authenticated
  USING (public.has_role(auth.uid(), 'admin'));

CREATE TRIGGER update_pro_games_updated_at
  BEFORE UPDATE ON public.pro_games
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
