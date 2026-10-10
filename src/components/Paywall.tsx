import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useState } from "react";
import { Sparkles, Check, Zap, Crown } from "lucide-react";
import { toast } from "sonner";
import { TIER_LIMITS, useSubscription } from "@/hooks/useSubscription";
import PayPalCheckout, { cancelPayPal } from "@/components/PayPalCheckout";

const TIER_CARDS = [
  {
    key: "light" as const,
    icon: Zap,
    name: "Light",
    price: TIER_LIMITS.light.priceLabel,
    tag: "Mitte",
    features: ["150 Chats / Tag", "Bilder generieren", "Alle Personas", "Keine Werbung"],
    accent: "from-sky-400 to-indigo-500",
  },
  {
    key: "pro" as const,
    icon: Crown,
    name: "Pro",
    price: TIER_LIMITS.pro.priceLabel,
    tag: "Beliebt",
    features: ["Unbegrenzte Chats", "Live-Sprachchat", "Bilder · Musik · Video", "Mythos Code App & CLI", "Priorität bei Updates"],
    accent: "from-fuchsia-500 to-rose-500",
    highlight: true,
  },
];

export default function Paywall({ open, onOpenChange, reason }: { open: boolean; onOpenChange: (o: boolean) => void; reason?: string }) {
  const sub = useSubscription();
  const [picked, setPicked] = useState<"light" | "pro" | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const cancel = async () => {
    setCancelling(true);
    const r = await cancelPayPal();
    setCancelling(false);
    if (r.ok) { toast.success(`Gekündigt. Dein Abo läuft noch bis ${r.expires_at ? new Date(r.expires_at).toLocaleDateString("de-AT") : "Monatsende"}.`); sub.refresh(); }
    else toast.error(r.error || "Kündigen fehlgeschlagen");
  };
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="glass-strong max-w-2xl">
        <DialogHeader>
          <div className="mx-auto h-12 w-12 rounded-full bg-gradient-primary flex items-center justify-center mb-2 glow-primary">
            <Sparkles className="h-6 w-6 text-primary-foreground" />
          </div>
          <DialogTitle className="text-center text-2xl">Mehr aus Mythos rausholen</DialogTitle>
          <DialogDescription className="text-center">{reason || "Wähle einen Plan, der zu dir passt."}</DialogDescription>
        </DialogHeader>

        <div className="grid sm:grid-cols-2 gap-3 py-2">
          {TIER_CARDS.map(t => (
            <div key={t.key}
              className={`relative rounded-2xl p-5 border transition-all ${t.highlight ? "border-primary/50 bg-primary/5" : "border-border/60 bg-card/40"}`}>
              {t.tag && (
                <span className={`absolute -top-2 right-4 text-[10px] uppercase tracking-wider px-2 py-0.5 rounded-full text-white bg-gradient-to-r ${t.accent}`}>
                  {t.tag}
                </span>
              )}
              <div className="flex items-center gap-2 mb-2">
                <div className={`h-9 w-9 rounded-xl bg-gradient-to-br ${t.accent} flex items-center justify-center`}>
                  <t.icon className="h-5 w-5 text-white" />
                </div>
                <div>
                  <div className="font-semibold">{t.name}</div>
                  <div className="text-xs text-muted-foreground">{t.price}</div>
                </div>
              </div>
              <ul className="space-y-1.5 text-sm mt-3">
                {t.features.map(f => (
                  <li key={f} className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-primary shrink-0" />{f}</li>
                ))}
              </ul>
              {sub.tier === t.key && sub.paidViaPayPal ? (
                <div className="mt-4 text-center text-xs text-emerald-400">✓ Dein aktueller Tarif</div>
              ) : (
                <Button onClick={() => setPicked(t.key)} variant={picked === t.key ? "default" : "outline"} className="mt-4 w-full rounded-full">
                  {picked === t.key ? "Ausgewählt" : `${t.name} wählen`}
                </Button>
              )}
            </div>
          ))}
        </div>

        {picked && (
          <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
            <div className="mb-3 text-sm font-medium">{TIER_CARDS.find(t => t.key === picked)!.name} für {TIER_LIMITS[picked].priceLabel} mit PayPal</div>
            <PayPalCheckout tier={picked} onDone={() => onOpenChange(false)} />
          </div>
        )}
        {sub.paidViaPayPal && (
          <p className="text-xs text-center text-muted-foreground">
            Du zahlst per PayPal.{" "}
            <button onClick={cancel} disabled={cancelling} className="underline hover:text-foreground">{cancelling ? "Kündige…" : "Abo kündigen"}</button>
            {" "}(läuft bis Monatsende weiter)
          </p>
        )}
        <p className="text-[11px] text-center text-muted-foreground">Monatlich kündbar. Du hast einen Gutschein? Einlösen unter <a href="/redeem" className="underline">Code einlösen</a>.</p>
        <Button onClick={() => onOpenChange(false)} variant="outline" className="w-full">Später</Button>
      </DialogContent>
    </Dialog>
  );
}
