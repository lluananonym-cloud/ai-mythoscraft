// Fernsteuerung der Mythos-Code-App vom Handy, ohne eigenen Server: Nachrichten laufen Ende-zu-Ende
// verschlüsselt (AES-GCM) über ntfy.sh. Der Schlüssel kommt aus dem Link, den der PC bei /koppeln
// schickt (…/code#k=…). Gleiche Verfahren wie in src/lib/mythos-code-app/renderer.js (relayKeys usw.).
export const RELAY = "https://ntfy.sh/";

export const b64u = (buf: ArrayBuffer | Uint8Array) =>
  btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
export const unb64u = (s: string) =>
  Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4)), (c) => c.charCodeAt(0));

export type RelayKeys = { aes: CryptoKey; inbox: string; outbox: string };

/** inbox = Aufgaben an den PC, outbox = Antworten ans Handy. */
export async function relayKeys(key: string): Promise<RelayKeys> {
  const raw = unb64u(key);
  if (raw.length !== 32) throw new Error("Ungültiger Kopplungs-Link");
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode("mythos-relay:" + key));
  const h = Array.from(new Uint8Array(digest)).map((x) => x.toString(16).padStart(2, "0")).join("").slice(0, 40);
  return {
    aes: await crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt", "decrypt"]),
    inbox: "mythos-" + h + "-pc",
    outbox: "mythos-" + h + "-handy",
  };
}

export async function seal(aes: CryptoKey, obj: unknown): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, aes, new TextEncoder().encode(JSON.stringify(obj))));
  const all = new Uint8Array(12 + ct.length);
  all.set(iv); all.set(ct, 12);
  return b64u(all);
}

export async function open<T = unknown>(aes: CryptoKey, text: string): Promise<T> {
  const all = unb64u(String(text || "").trim());
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: all.slice(0, 12) }, aes, all.slice(12));
  return JSON.parse(new TextDecoder().decode(plain)) as T;
}

export async function send(keys: RelayKeys, obj: unknown) {
  const r = await fetch(RELAY + keys.inbox, { method: "POST", body: await seal(keys.aes, obj) });
  if (!r.ok) throw new Error(r.status === 429 ? "Zu viele Nachrichten, bitte kurz warten." : "Senden fehlgeschlagen (" + r.status + ")");
}
