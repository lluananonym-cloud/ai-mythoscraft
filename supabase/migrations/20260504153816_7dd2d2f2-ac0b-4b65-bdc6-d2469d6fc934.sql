
-- Auto-Agents: scheduled tasks
CREATE TABLE public.agent_tasks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  title TEXT NOT NULL,
  prompt TEXT NOT NULL,
  mode TEXT NOT NULL DEFAULT 'agent',
  schedule_at TIMESTAMPTZ NOT NULL,
  recurrence TEXT NOT NULL DEFAULT 'once', -- once|daily|weekly
  status TEXT NOT NULL DEFAULT 'pending',  -- pending|running|done|failed
  last_run_at TIMESTAMPTZ,
  last_result TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.agent_tasks ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users manage own agent tasks" ON public.agent_tasks
  FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE INDEX idx_agent_tasks_due ON public.agent_tasks(schedule_at) WHERE status = 'pending';
CREATE TRIGGER update_agent_tasks_updated_at BEFORE UPDATE ON public.agent_tasks
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Friend Groups
CREATE TABLE public.friend_groups (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id UUID NOT NULL,
  name TEXT NOT NULL,
  description TEXT,
  ai_enabled BOOLEAN NOT NULL DEFAULT true,
  ai_persona_id UUID,
  invite_code TEXT NOT NULL UNIQUE DEFAULT lower(substring(md5(random()::text) from 1 for 8)),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.friend_groups ENABLE ROW LEVEL SECURITY;

CREATE TABLE public.group_members (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id UUID NOT NULL REFERENCES public.friend_groups(id) ON DELETE CASCADE,
  user_id UUID NOT NULL,
  role TEXT NOT NULL DEFAULT 'member',
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(group_id, user_id)
);
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;

CREATE TABLE public.group_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id UUID NOT NULL REFERENCES public.friend_groups(id) ON DELETE CASCADE,
  sender_id UUID,
  sender_name TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'user', -- user|assistant
  content TEXT NOT NULL,
  metadata JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.group_messages ENABLE ROW LEVEL SECURITY;

-- Helper: is_group_member (security definer to avoid RLS recursion)
CREATE OR REPLACE FUNCTION public.is_group_member(_group_id UUID, _user_id UUID)
RETURNS BOOLEAN LANGUAGE SQL STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT EXISTS (SELECT 1 FROM public.group_members WHERE group_id = _group_id AND user_id = _user_id)
$$;

CREATE POLICY "Members read group" ON public.friend_groups
  FOR SELECT USING (auth.uid() = owner_id OR public.is_group_member(id, auth.uid()));
CREATE POLICY "Owner manages group" ON public.friend_groups
  FOR ALL USING (auth.uid() = owner_id) WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "Members read members" ON public.group_members
  FOR SELECT USING (public.is_group_member(group_id, auth.uid()));
CREATE POLICY "User joins themself" ON public.group_members
  FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "User leaves themself" ON public.group_members
  FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY "Members read messages" ON public.group_messages
  FOR SELECT USING (public.is_group_member(group_id, auth.uid()));
CREATE POLICY "Members post messages" ON public.group_messages
  FOR INSERT WITH CHECK (public.is_group_member(group_id, auth.uid()) OR sender_id IS NULL);

CREATE TRIGGER update_friend_groups_updated_at BEFORE UPDATE ON public.friend_groups
  FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Persona on conversations
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS persona_id UUID;

-- Storage bucket for chat uploads (private; per-user folder)
INSERT INTO storage.buckets (id, name, public) VALUES ('chat-uploads', 'chat-uploads', true)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "Public read chat uploads" ON storage.objects FOR SELECT
  USING (bucket_id = 'chat-uploads');
CREATE POLICY "Users upload to own folder" ON storage.objects FOR INSERT
  WITH CHECK (bucket_id = 'chat-uploads' AND auth.uid()::text = (storage.foldername(name))[1]);
CREATE POLICY "Users delete own uploads" ON storage.objects FOR DELETE
  USING (bucket_id = 'chat-uploads' AND auth.uid()::text = (storage.foldername(name))[1]);

-- Realtime for groups
ALTER PUBLICATION supabase_realtime ADD TABLE public.group_messages;
ALTER PUBLICATION supabase_realtime ADD TABLE public.group_members;
