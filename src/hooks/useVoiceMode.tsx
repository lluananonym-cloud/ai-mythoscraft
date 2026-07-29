import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Browser-native voice + dictation using the Web Speech API.
 *
 * Two independent modes:
 *  - DICTATION: writes recognised text into a callback (e.g. an input field).
 *               Does NOT auto-send. Single press = listen until silence/stop.
 *  - LIVE VOICE MODE: continuous loop, auto-sends final transcripts, speaks
 *                     the answer back, pauses mic while speaking.
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
  const preferredNames = ["google", "microsoft", "natural", "neural", "premium", "anna", "petra", "katharina", "stefan", "markus"];
  const sameLang = voices.filter(v => v.lang?.toLowerCase().startsWith(short));
  for (const name of preferredNames) {
    const hit = sameLang.find(v => v.name.toLowerCase().includes(name));
    if (hit) return hit;
  }
  return sameLang[0] ?? voices[0] ?? null;
};

export type VoiceMode = "live" | "dictate";

export function useVoiceMode(opts?: {
  lang?: string;
  /** called with finalised transcript chunks while in LIVE mode (auto-send) */
  onTranscript?: (text: string) => void;
  /** called with finalised transcript chunks while in DICTATE mode (write to input) */
  onDictation?: (text: string) => void;
}) {
  const lang = opts?.lang ?? "de-DE";
  const [supported, setSupported] = useState(false);
  const [status, setStatus] = useState<Status>("idle");
  const [interim, setInterim] = useState("");
  const recognitionRef = useRef<any>(null);
  const onTranscriptRef = useRef(opts?.onTranscript);
  const onDictationRef = useRef(opts?.onDictation);
  onTranscriptRef.current = opts?.onTranscript;
  onDictationRef.current = opts?.onDictation;
  const modeRef = useRef<VoiceMode | null>(null); // null = nothing requested
  const isSpeakingRef = useRef(false);
  const restartTimerRef = useRef<number | null>(null);
  const voiceRef = useRef<SpeechSynthesisVoice | null>(null);
  const startingRef = useRef(false);

  useEffect(() => {
    const Ctor = getRecognitionCtor();
    const ttsOk = typeof window !== "undefined" && "speechSynthesis" in window;
    setSupported(!!Ctor);
    if (!Ctor) return;

    if (ttsOk) {
      const loadVoices = () => { voiceRef.current = pickBestVoice(lang); };
      loadVoices();
      window.speechSynthesis.onvoiceschanged = loadVoices;
    }

    const rec = new Ctor();
    rec.lang = lang;
    rec.continuous = true;
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
      const trimmed = finalText.trim();
      if (!trimmed) return;
      setInterim("");
      if (modeRef.current === "live" && !isSpeakingRef.current) {
        onTranscriptRef.current?.(trimmed);
      } else if (modeRef.current === "dictate") {
        onDictationRef.current?.(trimmed);
      }
    };

    rec.onstart = () => {
      startingRef.current = false;
      setStatus("listening");
    };

    rec.onend = () => {
      startingRef.current = false;
      setStatus(s => (s === "listening" ? "idle" : s));
      // Auto-restart only in LIVE mode (dictation = single shot)
      if (modeRef.current === "live" && !isSpeakingRef.current) {
        if (restartTimerRef.current) window.clearTimeout(restartTimerRef.current);
        restartTimerRef.current = window.setTimeout(() => {
          if (modeRef.current !== "live") return;
          try { rec.start(); } catch { /* already running */ }
        }, 300);
      }
    };

    rec.onerror = (e: any) => {
      startingRef.current = false;
      const err = e?.error;
      if (err === "not-allowed" || err === "service-not-allowed") {
        modeRef.current = null;
        setStatus("idle");
        // bubble up via console — UI handles toast separately
        console.error("[voice] microphone permission denied");
        return;
      }
      // 'no-speech', 'aborted', 'audio-capture' etc. -> let onend re-arm
      setStatus("idle");
    };

    recognitionRef.current = rec;
    return () => {
      modeRef.current = null;
      if (restartTimerRef.current) window.clearTimeout(restartTimerRef.current);
      try { rec.stop(); } catch {}
      try { window.speechSynthesis?.cancel(); } catch {}
    };
  }, [lang]);

  const safeStart = useCallback(() => {
    const rec = recognitionRef.current;
    if (!rec || startingRef.current) return;
    startingRef.current = true;
    try {
      rec.start();
    } catch (err: any) {
      // If already started, treat as listening; otherwise reset flag
      startingRef.current = false;
      if (err?.name !== "InvalidStateError") {
        console.error("[voice] start failed", err);
      }
    }
  }, []);

  const startDictation = useCallback(() => {
    const rec = recognitionRef.current;
    if (!rec) return;
    modeRef.current = "dictate";
    try { window.speechSynthesis?.cancel(); } catch {}
    isSpeakingRef.current = false;
    safeStart();
  }, [safeStart]);

  const startLive = useCallback(() => {
    const rec = recognitionRef.current;
    if (!rec) return;
    modeRef.current = "live";
    try { window.speechSynthesis?.cancel(); } catch {}
    isSpeakingRef.current = false;
    safeStart();
  }, [safeStart]);

  const stopListening = useCallback(() => {
    const rec = recognitionRef.current;
    modeRef.current = null;
    if (restartTimerRef.current) window.clearTimeout(restartTimerRef.current);
    try { rec?.stop(); } catch {}
    setStatus("idle");
    setInterim("");
  }, []);

  const speak = useCallback((text: string) => {
    if (typeof window === "undefined" || !("speechSynthesis" in window)) return;
    const clean = text.replace(/```[\s\S]*?```/g, "").replace(/!\[.*?\]\(.*?\)/g, "").replace(/[#*_`>]/g, "").slice(0, 1500);
    if (!clean.trim()) return;
    try { window.speechSynthesis.cancel(); } catch {}

    const rec = recognitionRef.current;
    isSpeakingRef.current = true;
    // pause mic while speaking so it doesn't hear itself
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
      if (modeRef.current === "live") {
        if (restartTimerRef.current) window.clearTimeout(restartTimerRef.current);
        restartTimerRef.current = window.setTimeout(safeStart, 250);
      }
    };
    u.onerror = () => {
      isSpeakingRef.current = false;
      setStatus("idle");
    };
    window.speechSynthesis.speak(u);
  }, [lang, safeStart]);

  const stopSpeaking = useCallback(() => {
    try { window.speechSynthesis?.cancel(); } catch {}
    isSpeakingRef.current = false;
    setStatus(s => (s === "speaking" ? "idle" : s));
  }, []);

  return {
    supported,
    status,
    interim,
    mode: modeRef.current,
    startDictation,
    startLive,
    stopListening,
    speak,
    stopSpeaking,
    /** legacy alias = live mode */
    startListening: startLive,
    isLiveListening: modeRef.current === "live",
  };
}
