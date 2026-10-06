import { useCallback, useEffect, useRef, useState } from "react";
import { speaker, loadVoice, onLoadState, type LoadState } from "@/lib/mythosVoice";

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
  const startingRef = useRef(false);
  // Fehlversuche in Folge (Live-Modus) – stoppt die Endlos-Neustarts, statt das Mikro flackern zu lassen.
  const failRef = useRef(0);

  useEffect(() => {
    const Ctor = getRecognitionCtor();
    setSupported(!!Ctor);
    if (!Ctor) return;

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
      failRef.current = 0;
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
      // Auto-restart only in LIVE mode (dictation = single shot).
      // Während des Neustarts bleibt der Status "listening" – sonst springt der Mikro-Knopf
      // bei jeder kurzen Pause des Browsers zwischen An und Aus hin und her.
      if (modeRef.current === "live" && !isSpeakingRef.current) {
        if (failRef.current >= 5) {
          modeRef.current = null;
          failRef.current = 0;
          setStatus("idle");
          console.error("[voice] Spracherkennung startet nicht – Live-Modus beendet");
          return;
        }
        if (restartTimerRef.current) window.clearTimeout(restartTimerRef.current);
        restartTimerRef.current = window.setTimeout(() => {
          if (modeRef.current !== "live") return;
          try { rec.start(); } catch { /* already running */ }
        }, 300 + failRef.current * 700);
        return;
      }
      setStatus(s => (s === "listening" ? "idle" : s));
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
      // 'no-speech' ist normal (Stille) – andere Fehler zählen als Fehlversuch.
      if (err !== "no-speech" && err !== "aborted") failRef.current++;
      // Im Live-Modus übernimmt onend den Neustart, der Status bleibt stabil.
      if (modeRef.current !== "live") setStatus("idle");
    };

    recognitionRef.current = rec;
    return () => {
      modeRef.current = null;
      if (restartTimerRef.current) window.clearTimeout(restartTimerRef.current);
      try { rec.stop(); } catch {}
      speaker.stop();
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
    failRef.current = 0;
    modeRef.current = "dictate";
    speaker.stop();
    speaker.unlock();
    isSpeakingRef.current = false;
    safeStart();
  }, [safeStart]);

  const startLive = useCallback(() => {
    const rec = recognitionRef.current;
    if (!rec) return;
    failRef.current = 0;
    modeRef.current = "live";
    speaker.stop();
    speaker.unlock();
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
    const rec = recognitionRef.current;
    isSpeakingRef.current = true;
    // Mikro pausieren, damit es sich nicht selbst hört.
    try { rec?.stop(); } catch { /* läuft nicht */ }
    const finish = () => {
      isSpeakingRef.current = false;
      setStatus(s => (s === "speaking" ? "idle" : s));
      if (modeRef.current === "live") {
        if (restartTimerRef.current) window.clearTimeout(restartTimerRef.current);
        restartTimerRef.current = window.setTimeout(safeStart, 250);
      }
    };
    speaker.speak(text, { lang, onStart: () => setStatus("speaking"), onEnd: finish });
  }, [lang, safeStart]);

  const stopSpeaking = useCallback(() => {
    speaker.stop();
    isSpeakingRef.current = false;
    setStatus(s => (s === "speaking" ? "idle" : s));
  }, []);

  // Ladezustand der Piper-Stimme (für Fortschrittsanzeige beim ersten Mal).
  const [voiceLoad, setVoiceLoad] = useState<LoadState>({ status: "idle", pct: 0 });
  useEffect(() => onLoadState(setVoiceLoad), []);

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
    /** Lautstärke der Mythos-Stimme 0..1 (für den Logo-Orb). */
    speechLevel: () => speaker.level(),
    /** Piper beim Klick entsperren + im Hintergrund laden. */
    prepare: () => { speaker.unlock(); loadVoice()?.catch(() => {}); },
    voiceLoad,
    /** legacy alias = live mode */
    startListening: startLive,
    isLiveListening: modeRef.current === "live",
  };
}
