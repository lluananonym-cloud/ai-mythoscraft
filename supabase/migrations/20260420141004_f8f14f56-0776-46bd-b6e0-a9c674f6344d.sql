-- Phone chats (one row per phone number conversation)
CREATE TABLE public.phone_chats (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_number TEXT NOT NULL UNIQUE,
  display_name TEXT,
  mode TEXT NOT NULL DEFAULT 'auto' CHECK (mode IN ('support','ai','auto')),
  last_support_response_at TIMESTAMPTZ,
  last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  unread_count INTEGER NOT NULL DEFAULT 0,
  ai_identity TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.phone_chats ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Admins manage phone chats"
ON public.phone_chats FOR ALL
USING (public.has_role(auth.uid(), 'admin'))
WITH CHECK (public.has_role(auth.uid(), 'admin'));

CREATE TRIGGER update_phone_chats_updated_at
BEFORE UPDATE ON public.phone_chats
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Phone messages
CREATE TABLE public.phone_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  chat_id UUID NOT NULL REFERENCES public.phone_chats(id) ON DELETE CASCADE,
  direction TEXT NOT NULL CHECK (direction IN ('inbound','outbound')),
  channel TEXT NOT NULL DEFAULT 'whatsapp' CHECK (channel IN ('whatsapp','sms','web')),
  sender TEXT NOT NULL CHECK (sender IN ('user','support','ai','system')),
  content TEXT NOT NULL,
  metadata JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.phone_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Admins manage phone messages"
ON public.phone_messages FOR ALL
USING (public.has_role(auth.uid(), 'admin'))
WITH CHECK (public.has_role(auth.uid(), 'admin'));

CREATE INDEX idx_phone_messages_chat ON public.phone_messages(chat_id, created_at DESC);
CREATE INDEX idx_phone_chats_last_msg ON public.phone_chats(last_message_at DESC);

-- Identity override on conversations (per-chat temporary persona)
ALTER TABLE public.conversations
ADD COLUMN IF NOT EXISTS identity_override TEXT;

-- Enable realtime
ALTER TABLE public.phone_chats REPLICA IDENTITY FULL;
ALTER TABLE public.phone_messages REPLICA IDENTITY FULL;
ALTER PUBLICATION supabase_realtime ADD TABLE public.phone_chats;
ALTER PUBLICATION supabase_realtime ADD TABLE public.phone_messages;