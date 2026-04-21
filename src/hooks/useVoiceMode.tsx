import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Browser-native voice mode using Web Speech API.
 * Live-conversation flavour:
 *  - continuous listening with auto-restart
 *  - automatically pauses mic while TTS is speaking (no echo loop)
 *  - resumes listening after TTS ends
 *  - picks the best available voice for the language
 */

type Status = "idle" | "listening" | "speaking";

const getRecognitionCtor = (): any => {
  if (typeof window === "undefined") return null;
  return (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition || null;
};

const pickBestVoice = (lang: string): SpeechSynthesisVoice | null => {
  if (typeof window === "undefined" || !("speechSynthesis" in window)) return null;
  const voices = window.speechSynthesis.getVoices();
  if (!voices.length) return null;
  const short = lang.toLowerCase().slice(0, 2);
  // Priority: native + premium-sounding names
  const preferredNames = ["google", "microsoft", "natural", "neural", "premium", "anna", "petra", "katharina", "stefan", "markus"];
  const sameLang = voices.filter(v => v.lang?.toLowerCase().startsWith(short));
  for (const name of preferredNames) {
    const hit = sameLang.find(v => v.name.toLowerCase().includes(name));
    if (hit) return hit;
  }
  return sameLang[0] ?? voices[0] ?? null;
};

export function useVoiceMode(opts?: { lang?: string; onTranscript?: (text: string) => void }) {
  const lang = opts?.lang ?? "de-DE";
  const [supported, setSupported] = useState(false);
  const [status, setStatus] = useState<Status>("idle");
  const [interim, setInterim] = useState("");
  const recognitionRef = useRef<any>(null);
  const onTranscriptRef = useRef(opts?.onTranscript);
  onTranscriptRef.current = opts?.onTranscript;
  const wantListeningRef = useRef(false); // user wants live mode on?
  const isSpeakingRef = useRef(false);
  const restartTimerRef = useRef<number | null>(null);
  const voiceRef = useRef<SpeechSynthesisVoice | null>(null);

  useEffect(() => {
    const Ctor = getRecognitionCtor();
    const ttsOk = typeof window !== "undefined" && "speechSynthesis" in window;
    setSupported(!!Ctor && ttsOk);
    if (!Ctor) return;

    // preload voices (Chrome loads them async)
    const loadVoices = () => { voiceRef.current = pickBestVoice(lang); };
    loadVoices();
    if (ttsOk) window.speechSynthesis.onvoiceschanged = loadVoices;

    const rec = new Ctor();
    rec.lang = lang;
    rec.continuous = true;       // keep mic open
    rec.interimResults = true;

    rec.onresult = (e: any) => {
      let interimText = "";
      let finalText = "";
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const t = e.results[i][0].transcript;
        if (e.results[i].isFinal) finalText += t;
        else interimText += t;
      }
      setInterim(interimText);
      if (finalText.trim() && !isSpeakingRef.current) {
        setInterim("");
        onTranscriptRef.current?.(finalText.trim());
      }
    };
    rec.onend = () => {
      setStatus(s => (s === "listening" ? "idle" : s));
      // Auto-restart if user still wants to listen and we're not speaking
      if (wantListeningRef.current && !isSpeakingRef.current) {
        if (restartTimerRef.current) window.clearTimeout(restartTimerRef.current);
        restartTimerRef.current = window.setTimeout(() => {
          try { rec.start(); setStatus("listening"); } catch {}
        }, 250);
      }
    };
    rec.onerror = (e: any) => {
      // 'no-speech' / 'aborted' are normal -> let onend re-arm if needed
      if (e?.error === "not-allowed" || e?.error === "service-not-allowed") {
        wantListeningRef.current = false;
      }
      setStatus("idle");
    };

    recognitionRef.current = rec;
    return () => {
      wantListeningRef.current = false;
      if (restartTimerRef.current) window.clearTimeout(restartTimerRef.current);
      try { rec.stop(); } catch {}
      try { window.speechSynthesis?.cancel(); } catch {}
    };
  }, [lang]);

  const startListening = useCallback(() => {
    const rec = recognitionRef.current;
    if (!rec) return;
    wantListeningRef.current = true;
    try { window.speechSynthesis?.cancel(); } catch {}
    isSpeakingRef.current = false;
    try { rec.start(); setStatus("listening"); } catch {
      // already started - ignore
    }
  }, []);

  const stopListening = useCallback(() => {
    const rec = recognitionRef.current;
    wantListeningRef.current = false;
    if (restartTimerRef.current) window.clearTimeout(restartTimerRef.current);
    try { rec?.stop(); } catch {}
    setStatus("idle");
  }, []);

  const speak = useCallback((text: string) => {
    if (typeof window === "undefined" || !("speechSynthesis" in window)) return;
    const clean = text.replace(/```[\s\S]*?```/g, "").replace(/!\[.*?\]\(.*?\)/g, "").replace(/[#*_`>]/g, "").slice(0, 1500);
    if (!clean.trim()) return;
    try { window.speechSynthesis.cancel(); } catch {}

    // Pause mic while we speak so the AI doesn't hear itself
    const rec = recognitionRef.current;
    isSpeakingRef.current = true;
    try { rec?.stop(); } catch {}

    const u = new SpeechSynthesisUtterance(clean);
    u.lang = lang;
    u.rate = 1.05;
    u.pitch = 1.0;
    const v = voiceRef.current ?? pickBestVoice(lang);
    if (v) { u.voice = v; voiceRef.current = v; }
    u.onstart = () => setStatus("speaking");
    u.onend = () => {
      isSpeakingRef.current = false;
      setStatus(s => (s === "speaking" ? "idle" : s));
      // Resume listening if user is in live mode
      if (wantListeningRef.current && rec) {
        if (restartTimerRef.current) window.clearTimeout(restartTimerRef.current);
        restartTimerRef.current = window.setTimeout(() => {
          try { rec.start(); setStatus("listening"); } catch {}
        }, 200);
      }
    };
    u.onerror = () => {
      isSpeakingRef.current = false;
      setStatus("idle");
    };
    window.speechSynthesis.speak(u);
  }, [lang]);

  const stopSpeaking = useCallback(() => {
    try { window.speechSynthesis?.cancel(); } catch {}
    isSpeakingRef.current = false;
    setStatus(s => (s === "speaking" ? "idle" : s));
  }, []);

  const isLiveListening = wantListeningRef.current;

  return { supported, status, interim, startListening, stopListening, speak, stopSpeaking, isLiveListening };
}
