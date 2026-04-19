import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Browser-native voice mode using Web Speech API.
 * - SpeechRecognition for STT (mic input -> text)
 * - speechSynthesis for TTS (text -> spoken output)
 * Free, runs entirely in the browser. No external API keys.
 *
 * Browser support: Chrome/Edge/Safari (via webkit prefix). Firefox: limited.
 */

type Status = "idle" | "listening" | "speaking";

const getRecognitionCtor = (): any => {
  if (typeof window === "undefined") return null;
  return (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition || null;
};

export function useVoiceMode(opts?: { lang?: string; onTranscript?: (text: string) => void }) {
  const lang = opts?.lang ?? "de-DE";
  const [supported, setSupported] = useState(false);
  const [status, setStatus] = useState<Status>("idle");
  const [interim, setInterim] = useState("");
  const recognitionRef = useRef<any>(null);
  const utteranceRef = useRef<SpeechSynthesisUtterance | null>(null);
  const onTranscriptRef = useRef(opts?.onTranscript);
  onTranscriptRef.current = opts?.onTranscript;

  useEffect(() => {
    const Ctor = getRecognitionCtor();
    const ttsOk = typeof window !== "undefined" && "speechSynthesis" in window;
    setSupported(!!Ctor && ttsOk);
    if (!Ctor) return;

    const rec = new Ctor();
    rec.lang = lang;
    rec.continuous = false;
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
      if (finalText.trim()) {
        setInterim("");
        onTranscriptRef.current?.(finalText.trim());
      }
    };
    rec.onend = () => setStatus((s) => (s === "listening" ? "idle" : s));
    rec.onerror = () => setStatus("idle");

    recognitionRef.current = rec;
    return () => { try { rec.stop(); } catch {} };
  }, [lang]);

  const startListening = useCallback(() => {
    const rec = recognitionRef.current;
    if (!rec) return;
    try { window.speechSynthesis?.cancel(); } catch {}
    try { rec.start(); setStatus("listening"); } catch {}
  }, []);

  const stopListening = useCallback(() => {
    const rec = recognitionRef.current;
    if (!rec) return;
    try { rec.stop(); } catch {}
    setStatus("idle");
  }, []);

  const speak = useCallback((text: string) => {
    if (typeof window === "undefined" || !("speechSynthesis" in window)) return;
    const clean = text.replace(/```[\s\S]*?```/g, "").replace(/[#*_`>]/g, "").slice(0, 1500);
    if (!clean.trim()) return;
    try { window.speechSynthesis.cancel(); } catch {}
    const u = new SpeechSynthesisUtterance(clean);
    u.lang = lang;
    u.rate = 1.05;
    u.pitch = 1.0;
    // Pick best voice for the language if available
    const voices = window.speechSynthesis.getVoices();
    const match = voices.find(v => v.lang?.toLowerCase().startsWith(lang.toLowerCase().slice(0, 2)));
    if (match) u.voice = match;
    u.onstart = () => setStatus("speaking");
    u.onend = () => setStatus((s) => (s === "speaking" ? "idle" : s));
    u.onerror = () => setStatus("idle");
    utteranceRef.current = u;
    window.speechSynthesis.speak(u);
  }, [lang]);

  const stopSpeaking = useCallback(() => {
    try { window.speechSynthesis?.cancel(); } catch {}
    setStatus((s) => (s === "speaking" ? "idle" : s));
  }, []);

  return { supported, status, interim, startListening, stopListening, speak, stopSpeaking };
}
