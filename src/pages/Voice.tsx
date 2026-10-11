import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { useSubscription } from "@/hooks/useSubscription";
import Paywall from "@/components/Paywall";
import { ArrowLeft, Mic, MicOff, Camera, MonitorUp, Phone, PhoneOff, MessageSquareText, X, MessageCircle } from "lucide-react";
import LogoOrb from "@/components/LogoOrb";
import { VOICES, getVoice, setVoice, onLoadState, type LoadState } from "@/lib/mythosVoice";
import { DEFAULT_MYTHOS_ID } from "@/lib/mythosModels";
import { LiveCall, type CallState, type AskFn } from "@/lib/liveCall";
import { toast } from "sonner";

/**
 * Live-Anruf mit Mythos: wie ein Telefonat. Mythos antwortet Satz für Satz, man kann ihm ins Wort fallen,
 * optional sieht er Kamera oder Bildschirm. Das Gespräch landet automatisch als Chat im Verlauf
 * (oder im Chat, aus dem man angerufen hat: /voice?c=<id>).
 */
const fmt = (ms: number) => { const s = Math.max(0, Math.floor(ms / 1000)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`; };
const BARGE_KEY = "mythos.call.bargeIn";

function CtrlBtn({ on, onClick, label, children, danger, disabled }: { on?: boolean; onClick: () => void; label: string; children: React.ReactNode; danger?: boolean; disabled?: boolean }) {
  return (
    <button onClick={onClick} disabled={disabled} className="flex flex-col items-center gap-1.5 disabled:opacity-40" aria-label={label} aria-pressed={on}>
      <span className={`flex items-center justify-center rounded-full transition-colors ${danger ? "h-16 w-16 bg-red-500 hover:bg-red-500/90 text-white" : `h-14 w-14 ${on ? "bg-white text-black" : "bg-white/10 hover:bg-white/15 text-white"}`}`}>
      {children}
    </span>
      <span className="text-[11px] text-white/60">{label}</span>
    </button>
  );
}

export default function Voice() {
  const { user } = useAuth();
  const nav = useNavigate();
  const [params] = useSearchParams();
  const fromChat = params.get("c");
  const { isPro, loading } = useSubscription();
  const [showPaywall, setShowPaywall] = useState(false);
  const [voiceId, setVoiceId] = useState(getVoice);
  const [voiceLoad, setVoiceLoad] = useState<LoadState>({ status: "idle", pct: 0 });
  useEffect(() => onLoadState(setVoiceLoad), []);
  const [st, setSt] = useState<CallState | null>(null);
  const [now, setNow] = useState(Date.now());
  const [endedAt, setEndedAt] = useState<number | null>(null);
  const [showLog, setShowLog] = useState(false);
  const [bargeIn, setBargeIn] = useState(() => { try { return localStorage.getItem(BARGE_KEY) !== "0"; } catch { return true; } });
  const [savedId, setSavedId] = useState<string | null>(null);
  const [chatTitle, setChatTitle] = useState<string>("");
  const contextRef = useRef<{ role: "user" | "assistant"; content: string }[]>([]);
  const modeRef = useRef("support");
  const convRef = useRef<string | null>(fromChat);
  const saveChain = useRef<Promise<unknown>>(Promise.resolve());
  const logEnd = useRef<HTMLDivElement>(null);

  // ---------- Kamera / Bildschirm: Mythos sieht pro Frage ein Standbild ----------
  const camRef = useRef<HTMLVideoElement>(null);
  const scrRef = useRef<HTMLVideoElement>(null);
  const [cam, setCam] = useState<MediaStream | null>(null);
  const [scr, setScr] = useState<MediaStream | null>(null);
  const seeRef = useRef<{ cam: MediaStream | null; scr: MediaStream | null }>({ cam: null, scr: null });
  seeRef.current = { cam, scr };
  const grabFrame = (v: HTMLVideoElement | null, maxSide: number) => {
    if (!v || !v.videoWidth) return null;
    const k = Math.min(1, maxSide / Math.max(v.videoWidth, v.videoHeight));
    const c = document.createElement("canvas");
    c.width = Math.round(v.videoWidth * k); c.height = Math.round(v.videoHeight * k);
    c.getContext("2d")?.drawImage(v, 0, 0, c.width, c.height);
    return c.toDataURL("image/jpeg", 0.82);
  };
  const stopSee = (kind: "cam" | "scr") => {
    seeRef.current[kind]?.getTracks().forEach(t => t.stop());
    (kind === "cam" ? setCam : setScr)(null);
  };
  const toggleSee = async (kind: "cam" | "scr") => {
    if (seeRef.current[kind]) return stopSee(kind);
    try {
      const s = kind === "cam"
        ? await navigator.mediaDevices.getUserMedia({ video: { width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false })
        : await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false });
      s.getVideoTracks()[0].onended = () => (kind === "cam" ? setCam : setScr)(null);
      (kind === "cam" ? setCam : setScr)(s);
    } catch { toast.error(kind === "cam" ? "Kamera nicht verfügbar" : "Bildschirm teilen abgebrochen"); }
  };
  useEffect(() => { if (camRef.current) camRef.current.srcObject = cam; }, [cam]);
  useEffect(() => { if (scrRef.current) scrRef.current.srcObject = scr; }, [scr]);

  // ---------- Kontext aus dem Chat, aus dem angerufen wurde ----------
  useEffect(() => {
    if (!fromChat || !user) return;
    (async () => {
      const { data: conv } = await supabase.from("conversations").select("id,title,mode").eq("id", fromChat).maybeSingle();
      if (!conv) { convRef.current = null; return; }
      setChatTitle((conv as { title?: string }).title || "");
      modeRef.current = (conv as { mode?: string }).mode || "support";
      const { data } = await supabase.from("messages").select("role,content").eq("conversation_id", fromChat)
        .order("created_at", { ascending: false }).limit(20);
      contextRef.current = ((data as { role: string; content: string }[] | null) ?? [])
        .filter(m => (m.role === "user" || m.role === "assistant") && m.content)
        .reverse().map(m => ({ role: m.role as "user" | "assistant", content: m.content.slice(0, 4000) }));
    })();
  }, [fromChat, user]);

  // ---------- Frage an Mythos (bestehende chat-Function, Antwort als Stream) ----------
  const ask: AskFn = async (history, text, signal, onText) => {
    const imgs = [grabFrame(camRef.current, 1280), grabFrame(scrRef.current, 1568)].filter((x): x is string => !!x);
    const content = imgs.length ? [{ type: "text", text }, ...imgs.map(url => ({ type: "image_url", image_url: { url } }))] : text;
    const url = `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/chat`;
    const key = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY;
    const resp = await fetch(url, {
      method: "POST", signal,
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}`, apikey: key },
      body: JSON.stringify({
        userId: user?.id,
        mode: modeRef.current,
        mythos: (typeof window !== "undefined" && localStorage.getItem("mythos.model")) || DEFAULT_MYTHOS_ID,
        voice: true,
        messages: [...history, { role: "user", content }],
      }),
    });
    if (!resp.ok || !resp.body) {
      console.error("[call] chat error", resp.status, await resp.text().catch(() => ""));
      throw new Error(resp.status === 429 ? "Gerade zu viele Anfragen – sag es gleich nochmal." : "Mythos ist gerade nicht erreichbar.");
    }
    const reader = resp.body.getReader();
    const dec = new TextDecoder();
    let buf = "", full = "";
    for (;;) {
      const { done, value } = await reader.read(); if (done) break;
      buf += dec.decode(value, { stream: true });
      let i;
      while ((i = buf.indexOf("\n")) !== -1) {
        const line = buf.slice(0, i).replace(/\r$/, ""); buf = buf.slice(i + 1);
        if (!line.startsWith("data: ")) continue;
        const j = line.slice(6).trim(); if (j === "[DONE]") continue;
        try { const c = JSON.parse(j).choices?.[0]?.delta?.content; if (c) { full += c; onText(full); } } catch { /* unvollständig */ }
      }
    }
    return full;
  };

  // ---------- Gespräch automatisch im Chat-Verlauf speichern ----------
  const saveExchange = (q: string, a: string) => {
    if (!user) return;
    saveChain.current = saveChain.current.then(async () => {
      let id = convRef.current;
      if (!id) {
        const { data, error } = await supabase.from("conversations")
          .insert({ user_id: user.id, title: `📞 ${q}`.slice(0, 50), mode: modeRef.current }).select("id").single();
        if (error || !data) { console.error("[call] Chat konnte nicht angelegt werden", error); return; }
        id = convRef.current = (data as { id: string }).id;
      }
      await supabase.from("messages").insert([
        { conversation_id: id, role: "user", content: q, metadata: { call: true } },
        { conversation_id: id, role: "assistant", content: a, metadata: { call: true } },
      ]);
      await supabase.from("conversations").update({ updated_at: new Date().toISOString() }).eq("id", id);
      setSavedId(id);
    }).catch(e => console.error("[call] Speichern fehlgeschlagen", e));
  };
  const saveRef = useRef(saveExchange); saveRef.current = saveExchange;
  const askRef = useRef(ask); askRef.current = ask;

  const call = useMemo(() => new LiveCall({
    lang: "de-DE",
    ask: (...a) => askRef.current(...a),
    onState: setSt,
    onExchange: (q, a) => saveRef.current(q, a),
    get context() { return contextRef.current; },
  }), []);
  useEffect(() => { call.bargeIn = bargeIn; try { localStorage.setItem(BARGE_KEY, bargeIn ? "1" : "0"); } catch { /* egal */ } }, [call, bargeIn]);

  const phase = st?.phase ?? "idle";
  const inCall = phase !== "idle" && phase !== "ended";

  // Uhr, Bildschirm wach halten, Fehler anzeigen, beim Verlassen auflegen.
  useEffect(() => {
    if (!inCall) return;
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    let lock: { release: () => Promise<void> } | null = null;
    (navigator as any).wakeLock?.request("screen").then((l: typeof lock) => { lock = l; }).catch(() => {});
    return () => { window.clearInterval(t); lock?.release().catch(() => {}); };
  }, [inCall]);
  useEffect(() => { if (phase === "ended") { setEndedAt(Date.now()); stopSee("cam"); stopSee("scr"); } }, [phase]);
  useEffect(() => { if (st?.error) { toast.error(st.error); call.clearError(); } }, [st?.error, call]);
  useEffect(() => () => { call.hangUp(); seeRef.current.cam?.getTracks().forEach(t => t.stop()); seeRef.current.scr?.getTracks().forEach(t => t.stop()); }, [call]);
  useEffect(() => { if (showLog) logEnd.current?.scrollIntoView({ block: "end" }); }, [showLog, st?.turns]);

  useEffect(() => { if (!loading && !isPro) setShowPaywall(true); }, [loading, isPro]);

  const startCall = () => {
    if (!isPro) { setShowPaywall(true); return; }
    setEndedAt(null); setNow(Date.now());
    call.start();
  };

  const turns = st?.turns ?? [];
  const lastUser = [...turns].reverse().find(t => t.role === "user");
  const lastAi = turns[turns.length - 1]?.role === "assistant" ? turns[turns.length - 1] : null;
  const duration = st?.startedAt ? (endedAt ?? now) - st.startedAt : 0;
  const statusText =
    phase === "connecting" ? "Verbinde …" :
    st?.muted ? "Stummgeschaltet" :
    phase === "listening" ? "Hört zu" :
    phase === "thinking" ? "Denkt nach …" :
    phase === "speaking" ? "Spricht" : "";
  const orbStatus = phase === "listening" || phase === "speaking" || phase === "thinking" ? phase : "idle";
  const backTo = savedId ? `/app/c/${savedId}` : fromChat ? `/app/c/${fromChat}` : "/app";


  return (
    <div className="fixed inset-0 bg-black flex flex-col text-white">
      {/* Kopfzeile */}
      <div className="flex items-center justify-between gap-3 p-4" style={{ paddingTop: "max(1rem, env(safe-area-inset-top))" }}>
        <Link to={backTo} onClick={() => call.hangUp()} aria-label="Zurück"><ArrowLeft className="h-6 w-6 text-white/80" /></Link>
        <div className="text-center min-w-0">
          <div className="text-sm font-medium">Mythos</div>
          <div className="text-xs text-white/50 tabular-nums truncate">
            {inCall ? <>{statusText}{st?.startedAt ? ` · ${fmt(duration)}` : ""}</> : phase === "ended" ? `Anruf beendet · ${fmt(duration)}` : chatTitle ? `Anruf im Chat „${chatTitle.slice(0, 30)}“` : "Live-Anruf"}
          </div>
        </div>
        <select
          value={voiceId}
          onChange={(e) => { setVoice(e.target.value); setVoiceId(e.target.value); }}
          className="bg-white/5 border border-white/10 rounded-full px-3 py-1.5 text-xs text-white/80 outline-none max-w-[9.5rem]"
          aria-label="Stimme"
        >
          {VOICES.map((v) => <option key={v.id} value={v.id} className="bg-neutral-900">{v.label}</option>)}
        </select>
      </div>

      <div className="flex-1 relative min-h-0">
        {(cam || scr) && (
          <div className="absolute top-2 right-4 z-10 flex gap-2">
            <video ref={camRef} autoPlay muted playsInline className={cam ? "h-24 rounded-xl border border-white/15 bg-black" : "hidden"} />
            <video ref={scrRef} autoPlay muted playsInline className={scr ? "h-24 rounded-xl border border-white/15 bg-black" : "hidden"} />
            <span className="absolute left-2 bottom-1 text-[10px] text-red-300 drop-shadow">● Mythos sieht mit</span>
          </div>
        )}
        {!(cam || scr) && <><video ref={camRef} className="hidden" muted /><video ref={scrRef} className="hidden" muted /></>}

        {phase === "ended" ? (
          <div className="absolute inset-0 overflow-y-auto px-5 py-4">
            <div className="max-w-xl mx-auto space-y-3">
              {turns.filter(t => t.content).length === 0 && <p className="text-center text-white/50 text-sm mt-10">Es wurde nichts gesagt.</p>}
              {turns.filter(t => t.content).map((t, i) => (
                <div key={i} className={`rounded-2xl px-4 py-2.5 text-sm max-w-[85%] ${t.role === "user" ? "ml-auto bg-white/10" : "bg-violet-500/15 border border-violet-400/20"}`}>
                  {t.content}{t.cut && <span className="text-white/40"> …</span>}
                </div>
              ))}
              {savedId && <p className="text-center text-xs text-white/40 pt-2">Gespräch ist im Chat-Verlauf gespeichert.</p>}
            </div>
          </div>
        ) : (
          <>
            <div onClick={() => call.interrupt()} className="absolute inset-0" title={phase === "speaking" ? "Tippen: Mythos unterbrechen" : undefined}>
              <LogoOrb status={orbStatus} getLevel={() => (phase === "thinking" ? 0.15 : call.level())} />
            </div>
            <div className="absolute bottom-6 left-0 right-0 px-6 text-center pointer-events-none">
              {voiceLoad.status === "loading" && inCall && (
                <p className="text-white/40 text-xs mb-2">Natürliche Stimme lädt … {voiceLoad.pct}% (nur beim ersten Mal)</p>
              )}
              {st?.sttLoading != null && (
                <p className="text-white/40 text-xs mb-2">Spracherkennung lädt … {st.sttLoading}% (nur beim ersten Mal)</p>
              )}
              {inCall ? (
                <>
                  {(st?.interim || lastUser) && (
                    <p className="text-white/45 text-sm mb-1.5 line-clamp-2">{st?.interim || lastUser?.content}</p>
                  )}
                  <p className="text-white/85 text-base min-h-[3rem] line-clamp-3">
                    {lastAi?.content ? (lastAi.content.length > 220 ? "…" + lastAi.content.slice(-220) : lastAi.content)
                      : phase === "listening" && !st?.muted ? (turns.length ? "" : "Sprich einfach los – wie am Telefon.") : ""}
                  </p>
                </>
              ) : (
                <p className="text-white/60 text-sm">
                  {isPro || loading ? "Ruf Mythos an und rede einfach los. Du kannst ihn jederzeit unterbrechen." : "Live-Anrufe sind eine Pro-Funktion."}
                </p>
              )}
            </div>
          </>
        )}

        {showLog && inCall && (
          <div className="absolute inset-x-3 top-0 bottom-0 z-20 rounded-2xl bg-neutral-950/95 border border-white/10 flex flex-col">
            <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/10">
              <span className="text-sm font-medium">Verlauf</span>
              <button onClick={() => setShowLog(false)} aria-label="Verlauf schließen"><X className="h-5 w-5 text-white/70" /></button>
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-3">
              {turns.filter(t => t.content).map((t, i) => (
                <div key={i} className={`rounded-2xl px-4 py-2.5 text-sm max-w-[85%] ${t.role === "user" ? "ml-auto bg-white/10" : "bg-violet-500/15 border border-violet-400/20"}`}>
                  {t.content}{t.cut && <span className="text-white/40"> …</span>}
                </div>
              ))}
              <div ref={logEnd} />
            </div>
          </div>
        )}
      </div>

      {/* Bedienleiste wie beim Telefon */}
      <div className="px-6 pt-4 flex flex-col items-center gap-4" style={{ paddingBottom: "max(1.75rem, env(safe-area-inset-bottom))" }}>
        {inCall ? (
          <div className="flex items-end justify-center gap-5 sm:gap-7">
            <CtrlBtn on={st?.muted} onClick={() => call.setMuted(!st?.muted)} label={st?.muted ? "Stumm" : "Mikro"}>
              {st?.muted ? <MicOff className="h-6 w-6" /> : <Mic className="h-6 w-6" />}
            </CtrlBtn>
            <CtrlBtn on={!!cam} onClick={() => toggleSee("cam")} label="Kamera"><Camera className="h-6 w-6" /></CtrlBtn>
            <CtrlBtn danger onClick={() => call.hangUp()} label="Auflegen"><PhoneOff className="h-7 w-7" /></CtrlBtn>
            <CtrlBtn on={!!scr} onClick={() => toggleSee("scr")} label="Bildschirm"><MonitorUp className="h-6 w-6" /></CtrlBtn>
            <CtrlBtn on={showLog} onClick={() => setShowLog(v => !v)} label="Verlauf"><MessageSquareText className="h-6 w-6" /></CtrlBtn>
          </div>
        ) : (
          <div className="flex items-end justify-center gap-6">
            {phase === "ended" && (
              <CtrlBtn onClick={() => nav(backTo)} label="Zum Chat"><MessageCircle className="h-6 w-6" /></CtrlBtn>
            )}
            <button onClick={startCall} disabled={loading} className="flex flex-col items-center gap-1.5 disabled:opacity-40" aria-label="Anrufen">
              <span className="h-16 w-16 rounded-full bg-emerald-500 hover:bg-emerald-500/90 flex items-center justify-center"><Phone className="h-7 w-7" /></span>
              <span className="text-[11px] text-white/60">{phase === "ended" ? "Nochmal anrufen" : "Anrufen"}</span>
            </button>
          </div>
        )}
        <label className="flex items-center gap-2 text-[11px] text-white/45 select-none cursor-pointer">
          <input type="checkbox" checked={bargeIn} onChange={(e) => setBargeIn(e.target.checked)} className="accent-violet-500" />
          Dazwischenreden erlaubt (bei Lautsprechern ohne Kopfhörer ggf. ausschalten)
        </label>
      </div>

      <Paywall open={showPaywall} onOpenChange={(o) => { setShowPaywall(o); if (!o && !isPro) nav(backTo); }} reason="Live-Anrufe mit Mythos sind eine Pro-Funktion." />
    </div>
  );
}
