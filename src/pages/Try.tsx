import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import Logo, { LogoMark } from "@/components/Logo";
import { ArrowUp, Sparkles, Lock, ArrowRight, Loader2 } from "lucide-react";

type Msg = { role: "user" | "assistant"; content: string };

const GUEST_LIMIT = 5;
const KEY = "mythos_guest_usage";

function loadUsage(): { day: string; used: number } {
  const day = new Date().toISOString().slice(0, 10);
  try {
    const raw = JSON.parse(localStorage.getItem(KEY) || "null");
    if (raw && raw.day === day) return raw;
  } catch { /* ignore */ }
  return { day, used: 0 };
}

const LOCKED = [
  "Chat-Verlauf & Gedächtnis",
  "Bilder, Musik & Video generieren",
  "Live-Sprachchat",
  "Browser-Agent & Web-Recherche",
  "Personas, Gruppen & Minecraft-Link",
  "Lange Antworten & bessere Modelle",
];

const Try = () => {
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [used, setUsed] = useState(() => loadUsage().used);
  const [error, setError] = useState<string | null>(null);
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: "smooth" }); }, [messages, busy]);

  const remaining = Math.max(0, GUEST_LIMIT - used);
  const limitReached = remaining === 0;

  const send = async () => {
    const text = input.trim();
    if (!text || busy || limitReached) return;
    setError(null);
    setInput("");
    const next = [...messages, { role: "user" as const, content: text }];
    setMessages([...next, { role: "assistant", content: "" }]);
    setBusy(true);

    const day = new Date().toISOString().slice(0, 10);
    const newUsed = used + 1;
    setUsed(newUsed);
    localStorage.setItem(KEY, JSON.stringify({ day, used: newUsed }));

    try {
      const r = await fetch(`${import.meta.env.VITE_SUPABASE_URL}/functions/v1/guest-chat`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY}`,
        },
        body: JSON.stringify({ messages: next }),
      });

      if (!r.ok || !r.body) {
        const j = await r.json().catch(() => ({}));
        throw new Error(
          r.status === 429
            ? "Gast-Limit erreicht — registriere dich kostenlos für mehr."
            : (j as any).error || "Gast-Modus gerade nicht verfügbar.",
        );
      }

      const reader = r.body.getReader();
      const dec = new TextDecoder();
      let buf = "";
      let acc = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += dec.decode(value, { stream: true });
        const lines = buf.split("\n");
        buf = lines.pop() || "";
        for (const line of lines) {
          if (!line.startsWith("data: ")) continue;
          const p = line.slice(6).trim();
          if (p === "[DONE]") continue;
          try {
            const d = JSON.parse(p).choices?.[0]?.delta?.content;
            if (d) {
              acc += d;
              setMessages((prev) => {
                const copy = [...prev];
                copy[copy.length - 1] = { role: "assistant", content: acc };
                return copy;
              });
            }
          } catch { /* ignore */ }
        }
      }
      if (!acc) throw new Error("Keine Antwort erhalten.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Fehler");
      setMessages((prev) => prev.slice(0, -1));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-[100dvh] flex flex-col" style={{ paddingTop: "env(safe-area-inset-top)" }}>
      <header className="border-b border-glass-border/60 px-4 py-3 flex items-center justify-between gap-3">
        <Link to="/"><Logo size="sm" /></Link>
        <div className="flex items-center gap-2">
          <span className="hidden sm:inline-flex items-center gap-1.5 glass rounded-full px-3 py-1 text-[11px] text-muted-foreground">
            <Sparkles className="h-3 w-3 text-primary" /> Gast-Modus · {remaining}/{GUEST_LIMIT} Nachrichten übrig
          </span>
          <Link to="/auth">
            <Button size="sm" className="bg-gradient-primary text-primary-foreground">
              Kostenlos registrieren <ArrowRight className="h-3.5 w-3.5 ml-1" />
            </Button>
          </Link>
        </div>
      </header>

      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto w-full max-w-2xl px-4 py-6 space-y-5">
          {messages.length === 0 && (
            <div className="text-center pt-8 pb-4">
              <LogoMark size="lg" className="mx-auto mb-4" />
              <h1 className="font-display text-2xl sm:text-3xl font-bold mb-2">Mythos AI testen</h1>
              <p className="text-sm text-muted-foreground max-w-md mx-auto">
                Gast-Modus: {GUEST_LIMIT} kurze Nachrichten, kein Login, kein Verlauf — alles wird beim Verlassen verworfen.
              </p>
              <div className="glass rounded-2xl p-4 mt-6 text-left">
                <div className="text-xs font-semibold mb-2 flex items-center gap-1.5">
                  <Lock className="h-3.5 w-3.5 text-muted-foreground" /> Nur mit kostenlosem Account
                </div>
                <ul className="grid sm:grid-cols-2 gap-1.5 text-xs text-muted-foreground">
                  {LOCKED.map((f) => <li key={f}>· {f}</li>)}
                </ul>
              </div>
            </div>
          )}

          {messages.map((m, i) =>
            m.role === "user" ? (
              <div key={i} className="flex justify-end">
                <div className="bg-primary/15 border border-primary/20 rounded-2xl rounded-br-md px-4 py-2.5 text-sm max-w-[85%] whitespace-pre-wrap">
                  {m.content}
                </div>
              </div>
            ) : (
              <div key={i} className="flex gap-3">
                <LogoMark size="sm" className="mt-0.5 h-7 w-7" />
                <div className="text-sm leading-relaxed whitespace-pre-wrap flex-1 pt-1">
                  {m.content || <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />}
                </div>
              </div>
            ),
          )}

          {error && <div className="text-xs text-destructive text-center">{error}</div>}

          {limitReached && (
            <div className="glass-strong rounded-2xl p-5 text-center">
              <h2 className="font-display font-bold text-lg mb-1">Gast-Limit erreicht</h2>
              <p className="text-sm text-muted-foreground mb-4">
                Der Free-Account ist ebenfalls kostenlos und gibt dir 20 Chats pro Tag, Verlauf, Personas und mehr.
              </p>
              <Link to="/auth">
                <Button className="bg-gradient-primary text-primary-foreground">
                  Kostenlos weiterchatten <ArrowRight className="h-4 w-4 ml-1" />
                </Button>
              </Link>
            </div>
          )}
          <div ref={endRef} />
        </div>
      </main>

      <footer
        className="border-t border-glass-border/60 px-4 py-3"
        style={{ paddingBottom: "calc(env(safe-area-inset-bottom) + 0.75rem)" }}
      >
        <div className="mx-auto max-w-2xl">
          <div className="glass rounded-2xl flex items-end gap-2 p-2">
            <Textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); } }}
              placeholder={limitReached ? "Gast-Limit erreicht — bitte registrieren" : "Frag Mythos AI etwas…"}
              disabled={limitReached}
              rows={1}
              className="min-h-[40px] max-h-32 resize-none border-0 bg-transparent focus-visible:ring-0 text-sm"
            />
            <Button size="icon" onClick={send} disabled={busy || limitReached || !input.trim()} className="bg-gradient-primary text-primary-foreground shrink-0">
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowUp className="h-4 w-4" />}
            </Button>
          </div>
          <p className="text-[11px] text-muted-foreground text-center mt-2">
            Gast-Modus · kurze Antworten, keine Tools, kein Verlauf · {remaining}/{GUEST_LIMIT} übrig
          </p>
        </div>
      </footer>
    </div>
  );
};

export default Try;
