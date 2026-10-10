// PayPal-Abo abschließen: lädt PayPals Knöpfe erst, wenn jemand wirklich bezahlen will.
// Vorher muss die Zustimmung zu AGB und sofortigem Start bestätigt sein (§ 356 Abs. 4 und 5 BGB).
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";

type Config = { clientId: string; mode: "live" | "sandbox"; plans: { light: string; pro: string } };
type PayPalNS = { Buttons: (o: Record<string, unknown>) => { render: (el: HTMLElement) => Promise<void>; close?: () => void } };
declare global { interface Window { paypal?: PayPalNS } }

let configPromise: Promise<Config> | null = null;
function loadConfig(): Promise<Config> {
  configPromise ??= supabase.functions.invoke("paypal", { body: { action: "config" } }).then(({ data, error }) => {
    if (error || !data?.clientId) {
      configPromise = null;
      throw new Error(data?.error === "not_configured" || /503/.test(String(error?.message)) ? "not_configured" : (data?.error || error?.message || "PayPal nicht erreichbar"));
    }
    return data as Config;
  });
  return configPromise;
}

let sdkPromise: Promise<void> | null = null;
function loadSdk(clientId: string): Promise<void> {
  sdkPromise ??= new Promise<void>((resolve, reject) => {
    const s = document.createElement("script");
    s.src = `https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(clientId)}&vault=true&intent=subscription&currency=EUR&locale=de_DE`;
    s.onload = () => resolve();
    s.onerror = () => { sdkPromise = null; reject(new Error("PayPal konnte nicht geladen werden")); };
    document.head.appendChild(s);
  });
  return sdkPromise;
}

export default function PayPalCheckout({ tier, onDone }: { tier: "light" | "pro"; onDone?: () => void }) {
  const { user } = useAuth();
  const [agreed, setAgreed] = useState(false);
  const [state, setState] = useState<"idle" | "loading" | "ready" | "busy" | "error">("idle");
  const [error, setError] = useState("");
  const box = useRef<HTMLDivElement>(null);
  const doneRef = useRef(onDone);
  doneRef.current = onDone;

  useEffect(() => {
    if (!agreed || !user) return;
    let alive = true;
    let buttons: ReturnType<PayPalNS["Buttons"]> | null = null;
    setState("loading");
    (async () => {
      try {
        const cfg = await loadConfig();
        await loadSdk(cfg.clientId);
        if (!alive || !box.current || !window.paypal) return;
        box.current.innerHTML = "";
        buttons = window.paypal.Buttons({
          style: { shape: "pill", color: "gold", layout: "vertical", label: "subscribe" },
          createSubscription: (_d: unknown, actions: { subscription: { create: (o: unknown) => Promise<string> } }) =>
            actions.subscription.create({
              plan_id: cfg.plans[tier],
              custom_id: user.id,
              application_context: { brand_name: "Mythos AI", locale: "de-DE", shipping_preference: "NO_SHIPPING", user_action: "SUBSCRIBE_NOW" },
            }),
          onApprove: async (data: { subscriptionID?: string }) => {
            setState("busy");
            const { data: res, error: err } = await supabase.functions.invoke("paypal", { body: { action: "activate", subscriptionId: data.subscriptionID } });
            if (err || !res?.ok) {
              setState("error");
              setError(res?.error === "light_needs_migration"
                ? "Zahlung ist durch, aber die Datenbank kennt den Tarif Light noch nicht. Bitte melde dich beim Support, wir schalten dich manuell frei."
                : (res?.error || err?.message || "Freischalten fehlgeschlagen. Melde dich beim Support, die Zahlung ist nicht verloren."));
              return;
            }
            toast.success(`🎉 ${tier === "pro" ? "Pro" : "Light"} ist aktiv. Danke!`);
            doneRef.current?.();
            setTimeout(() => window.location.reload(), 1200);
          },
          onError: (e: unknown) => { setState("error"); setError("PayPal meldet einen Fehler. Versuch es bitte noch einmal."); console.error("[paypal]", e); },
        });
        await buttons.render(box.current);
        if (alive) setState("ready");
      } catch (e) {
        if (!alive) return;
        setState("error");
        setError((e as Error).message === "not_configured"
          ? "Bezahlen mit PayPal ist noch nicht eingerichtet. Frag solange einen Admin nach einem Code."
          : (e as Error).message);
      }
    })();
    return () => { alive = false; try { buttons?.close?.(); } catch { /* schon zu */ } };
  }, [agreed, tier, user]);

  if (!user) return <p className="text-xs text-muted-foreground">Bitte zuerst <Link to="/auth" className="underline">anmelden</Link>.</p>;

  return (
    <div className="space-y-3">
      <label className="flex items-start gap-2 text-xs leading-relaxed text-muted-foreground">
        <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} className="mt-0.5 h-4 w-4 shrink-0 accent-primary" />
        <span>
          Ich akzeptiere die <Link to="/nutzungsbedingungen" target="_blank" className="underline text-foreground">AGB</Link> und
          die <Link to="/datenschutz" target="_blank" className="underline text-foreground">Datenschutzerklärung</Link>. Ich will, dass das Abo sofort startet.
          Widerrufe ich innerhalb von 14 Tagen, zahle ich nur den Teil bis dahin (Wertersatz). Monatlich kündbar.
        </span>
      </label>
      {agreed && (
        <div className="min-h-[52px]">
          {(state === "loading" || state === "busy") && (
            <div className="flex items-center gap-2 text-xs text-muted-foreground"><Loader2 className="h-3.5 w-3.5 animate-spin" /> {state === "busy" ? "Abo wird freigeschaltet…" : "PayPal wird geladen…"}</div>
          )}
          <div ref={box} className={state === "busy" ? "pointer-events-none opacity-40" : ""} />
          {state === "error" && <p className="text-xs text-destructive">{error}</p>}
        </div>
      )}
    </div>
  );
}

/** Kündigt das eigene PayPal-Abo. Es läuft bis zum Ende des bezahlten Monats weiter. */
export async function cancelPayPal(): Promise<{ ok: boolean; expires_at?: string; error?: string }> {
  const { data, error } = await supabase.functions.invoke("paypal", { body: { action: "cancel" } });
  if (error || !data?.ok) return { ok: false, error: data?.error || error?.message };
  return data;
}
