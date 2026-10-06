// Chat-Werbung: Free gelegentlich, Light seltener, Pro nie, Admin 1x pro Tag (Test).
// Wird immer als eigene Nachricht NACH der Antwort angezeigt.
// Gebuchte & freigegebene Werbungen werden nach ihrem Gewicht (KI-Budgetanalyse) ausgewählt.
import { supabase } from "@/integrations/supabase/client";

export type AdTier = "free" | "light" | "pro" | "admin";
export type Ad = { id?: string; product: string; link: string; ad_text: string; weight: number; logo?: string };

export const AD_URL = "https://at-feed-vibes.lovable.app/";
export const HOUSE_AD = (origin: string): Ad => ({ product: "at", link: AD_URL, ad_text: "Dein Feed voller Vibes. Jetzt entdecken!", weight: 3, logo: `${origin}/at-logo.png` });

export function adDue(tier: AdTier): boolean {
  const today = new Date().toDateString();
  if (tier === "admin") {
    if (localStorage.getItem("mythos_ad_admin") === today) return false;
    localStorage.setItem("mythos_ad_admin", today);
    return true;
  }
  const every = tier === "free" ? 4 : tier === "light" ? 10 : 0;
  if (!every) return false;
  const n = (Number(localStorage.getItem("mythos_ad_n")) || 0) + 1;
  localStorage.setItem("mythos_ad_n", String(n));
  return n % every === 0;
}

let cache: { at: number; ads: Ad[] } | null = null;
export async function pickAd(origin: string): Promise<Ad> {
  if (!cache || Date.now() - cache.at > 10 * 60_000) {
    const { data } = await supabase.functions.invoke("ad-review", { body: { action: "list" } }).catch(() => ({ data: null }));
    cache = { at: Date.now(), ads: (data?.ads as Ad[]) || [] };
  }
  const pool = [HOUSE_AD(origin), ...cache.ads];
  let r = Math.random() * pool.reduce((s, a) => s + Math.max(1, a.weight), 0);
  for (const a of pool) { r -= Math.max(1, a.weight); if (r <= 0) return a; }
  return pool[0];
}

const safe = (s: string) => s.replace(/[\[\]()<>*_`]/g, "");
export const adMarkdown = (ad: Ad, origin: string) =>
  `**📣 Werbung**\n\n${ad.logo ? `![${safe(ad.product)}](${ad.logo})\n\n` : ""}**${safe(ad.product)}** – ${safe(ad.ad_text)}\n\n[${safe(ad.link.replace(/^https?:\/\//, "").replace(/\/$/, ""))}](${/^https?:\/\//.test(ad.link) ? ad.link : "https://" + ad.link})\n\n[Buche deine eigene Werbung](${origin}/werbung)`;
