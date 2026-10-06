import { supabase } from "@/integrations/supabase/client";
import { toast } from "sonner";

const KEY = "mythos_invite_ref";

export const inviteLink = (code: string) => `${window.location.origin}/auth?ref=${encodeURIComponent(code)}`;

/** Merkt sich ?ref=CODE aus der URL, bis der Nutzer eingeloggt ist. */
export function captureInviteRef() {
  try {
    const ref = new URLSearchParams(window.location.search).get("ref");
    if (ref && /^[A-Za-z0-9]{4,32}$/.test(ref)) localStorage.setItem(KEY, ref.toUpperCase());
  } catch { /* Speicher blockiert: Einladung geht dann verloren */ }
}

export function pendingInviteRef(): string | null {
  try { return localStorage.getItem(KEY); } catch { return null; }
}

/** Löst eine gemerkte Einladung für den eingeloggten Nutzer ein (einmalig). */
export async function redeemPendingInvite() {
  const code = pendingInviteRef();
  if (!code) return;
  try { localStorage.removeItem(KEY); } catch { /* egal */ }
  const { data, error } = await (supabase.rpc as any)("redeem_invite", { _code: code });
  if (error) return;
  const res = data as { ok: boolean; error?: string } | null;
  if (res?.ok) toast.success("Einladung eingelöst: 7 Tage Light gratis!");
  else if (res?.error) toast.message(res.error);
}
