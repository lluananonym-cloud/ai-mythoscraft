import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Sparkles, Check, Zap, Crown } from "lucide-react";
import { TIER_LIMITS } from "@/hooks/useSubscription";

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
    features: ["Unbegrenzte Chats", "Live-Sprachchat", "Bilder · Musik · Video", "Priorität bei Updates"],
    accent: "from-fuchsia-500 to-rose-500",
    highlight: true,
  },
];

export default function Paywall({ open, onOpenChange, reason }: { open: boolean; onOpenChange: (o: boolean) => void; reason?: string }) {
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
            </div>
          ))}
        </div>

        <p className="text-xs text-center text-muted-foreground">
          Checkout läuft gerade über Paddle. Frag solange einen Admin nach einem Gratis-Code.
        </p>
        <Button onClick={() => onOpenChange(false)} variant="outline" className="w-full">Später</Button>
      </DialogContent>
    </Dialog>
  );
}
