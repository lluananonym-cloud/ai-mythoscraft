import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";

export const FREE_LIMITS = {
  chatsPerDay: 20,
  voice: false,
  imageGen: false,
  musicGen: false,
};

export function useSubscription() {
  const { user } = useAuth();
  const [isPro, setIsPro] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  const [loading, setLoading] = useState(true);
  const [chatsToday, setChatsToday] = useState(0);

  const refresh = async () => {
    if (!user) { setIsPro(false); setIsAdmin(false); setLoading(false); return; }
    const [{ data: sub }, { data: roles }] = await Promise.all([
      supabase.from("subscriptions").select("tier,expires_at").eq("user_id", user.id).maybeSingle(),
      supabase.from("user_roles").select("role").eq("user_id", user.id),
    ]);
    const admin = !!roles?.some(r => r.role === "admin");
    const pro = admin || (sub?.tier === "pro" && (!sub.expires_at || new Date(sub.expires_at) > new Date()));
    setIsAdmin(admin);
    setIsPro(pro);

    const startOfDay = new Date(); startOfDay.setHours(0,0,0,0);
    const { count } = await supabase.from("messages").select("id,conversations!inner(user_id)", { count: "exact", head: true })
      .eq("role", "user").gte("created_at", startOfDay.toISOString())
      .eq("conversations.user_id", user.id);
    setChatsToday(count ?? 0);
    setLoading(false);
  };

  useEffect(() => { refresh(); }, [user?.id]);

  return {
    isPro, isAdmin, loading, chatsToday,
    chatLimitReached: !isPro && chatsToday >= FREE_LIMITS.chatsPerDay,
    canUseVoice: isPro,
    canGenerateImage: isPro,
    canGenerateMusic: isPro,
    remainingChats: isPro ? Infinity : Math.max(0, FREE_LIMITS.chatsPerDay - chatsToday),
    refresh,
  };
}
