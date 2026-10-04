// Chat-Werbung: Free gelegentlich, Light/Plus seltener, Pro nie, Admin 1x pro Tag (Test).
// Wird immer als eigene Nachricht NACH der Antwort angezeigt.
export type AdTier = "free" | "light" | "pro" | "admin";

export const AD_URL = "https://at-feed-vibes.lovable.app/";

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

export const AD_MARKDOWN = (origin: string) =>
  `**📣 Werbung**\n\n![at](${origin}/at-logo.png)\n\n**at** – dein Feed voller Vibes. Jetzt entdecken: [at-feed-vibes.lovable.app](${AD_URL})\n\n[Buche deine eigene Werbung](${origin}/werbung)`;
