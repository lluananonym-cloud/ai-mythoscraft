-- ============ USER MEMORIES ============
CREATE TABLE public.user_memories (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL,
  content TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'general',
  source TEXT NOT NULL DEFAULT 'auto',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.user_memories ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users manage own memories" ON public.user_memories
  FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

CREATE TRIGGER update_user_memories_updated_at
  BEFORE UPDATE ON public.user_memories
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE INDEX idx_user_memories_user ON public.user_memories(user_id, created_at DESC);

-- ============ MC SERVERS ============
CREATE TABLE public.mc_servers (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL,
  name TEXT NOT NULL,
  key_hash TEXT NOT NULL UNIQUE,
  key_prefix TEXT NOT NULL,
  ingame_chat_enabled BOOLEAN NOT NULL DEFAULT true,
  events_enabled BOOLEAN NOT NULL DEFAULT true,
  greet_on_join BOOLEAN NOT NULL DEFAULT true,
  comment_on_death BOOLEAN NOT NULL DEFAULT true,
  chat_trigger TEXT NOT NULL DEFAULT '!ai',
  ai_persona_id UUID,
  last_seen_at TIMESTAMPTZ,
  total_events BIGINT NOT NULL DEFAULT 0,
  revoked BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.mc_servers ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users manage own mc servers" ON public.mc_servers
  FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Admins see all mc servers" ON public.mc_servers
  FOR SELECT USING (has_role(auth.uid(), 'admin'::app_role));

CREATE TRIGGER update_mc_servers_updated_at
  BEFORE UPDATE ON public.mc_servers
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- ============ MC EVENTS ============
CREATE TABLE public.mc_events (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  server_id UUID NOT NULL REFERENCES public.mc_servers(id) ON DELETE CASCADE,
  event_type TEXT NOT NULL,
  player_name TEXT,
  player_uuid TEXT,
  content TEXT,
  ai_response TEXT,
  metadata JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.mc_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users see own server events" ON public.mc_events
  FOR SELECT USING (
    EXISTS (SELECT 1 FROM public.mc_servers s WHERE s.id = mc_events.server_id AND s.user_id = auth.uid())
  );
CREATE POLICY "Admins see all events" ON public.mc_events
  FOR SELECT USING (has_role(auth.uid(), 'admin'::app_role));

CREATE INDEX idx_mc_events_server ON public.mc_events(server_id, created_at DESC);

-- ============ AI PERSONAS ============
CREATE TABLE public.ai_personas (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL,
  name TEXT NOT NULL,
  description TEXT,
  system_prompt TEXT NOT NULL,
  avatar_emoji TEXT DEFAULT '🤖',
  is_public BOOLEAN NOT NULL DEFAULT false,
  use_count BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.ai_personas ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users manage own personas" ON public.ai_personas
  FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Anyone reads public personas" ON public.ai_personas
  FOR SELECT USING (is_public = true OR auth.uid() = user_id OR has_role(auth.uid(), 'admin'::app_role));

CREATE TRIGGER update_ai_personas_updated_at
  BEFORE UPDATE ON public.ai_personas
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE INDEX idx_ai_personas_user ON public.ai_personas(user_id);
CREATE INDEX idx_ai_personas_public ON public.ai_personas(is_public) WHERE is_public = true;