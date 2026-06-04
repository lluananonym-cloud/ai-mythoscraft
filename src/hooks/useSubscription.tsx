import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";

export type Tier = "free" | "light" | "pro";

export const TIER_LIMITS: Record<Tier, {
  chatsPerDay: number;
  imageGen: boolean;
  musicGen: boolean;
  videoGen: boolean;
  voice: boolean;
  label: string;
  priceLabel: string;
}> = {
  free:  { chatsPerDay: 20,       imageGen: false, musicGen: false, videoGen: false, voice: false, label: "Free",  priceLabel: "0 €" },
  light: { chatsPerDay: 150,      imageGen: true,  musicGen: false, videoGen: false, voice: false, label: "Light", priceLabel: "2,99 €/Monat" },
  pro:   { chatsPerDay: Infinity, imageGen: true,  musicGen: true,  videoGen: true,  voice: true,  label: "Pro",   priceLabel: "6,99 €/Monat" },
};

export function useSubscription() {
  const { user } = useAuth();
  const [tier, setTier] = useState<Tier>("free");
  const [isAdmin, setIsAdmin] = useState(false);
  const [loading, setLoading] = useState(true);
  const [chatsToday, setChatsToday] = useState(0);

  const refresh = async () => {
    if (!user) { setTier("free"); setIsAdmin(false); setLoading(false); return; }
    const [{ data: sub }, { data: roles }] = await Promise.all([
      supabase.from("subscriptions").select("tier,expires_at").eq("user_id", user.id).maybeSingle(),
      supabase.from("user_roles").select("role").eq("user_id", user.id),
    ]);
    const admin = !!roles?.some(r => r.role === "admin");
    setIsAdmin(admin);
    let resolved: Tier = "free";
    if (admin) resolved = "pro";
    else if (sub?.tier && (!sub.expires_at || new Date(sub.expires_at) > new Date())) {
      if (sub.tier === "pro" || sub.tier === "light") resolved = sub.tier as Tier;
    }
    setTier(resolved);

    const startOfDay = new Date(); startOfDay.setHours(0,0,0,0);
    const { count } = await supabase.from("messages").select("id,conversations!inner(user_id)", { count: "exact", head: true })
      .eq("role", "user").gte("created_at", startOfDay.toISOString())
      .eq("conversations.user_id", user.id);
    setChatsToday(count ?? 0);
    setLoading(false);
  };

  useEffect(() => { refresh(); }, [user?.id]);

  const limits = TIER_LIMITS[tier];
  const isPro = tier === "pro";
  const isLight = tier === "light";

  return {
    tier, isPro, isLight, isAdmin, loading, chatsToday, limits,
    chatLimitReached: chatsToday >= limits.chatsPerDay,
    canUseVoice: limits.voice,
    canGenerateImage: limits.imageGen,
    canGenerateMusic: limits.musicGen,
    canGenerateVideo: limits.videoGen,
    remainingChats: limits.chatsPerDay === Infinity ? Infinity : Math.max(0, limits.chatsPerDay - chatsToday),
    refresh,
  };
}
