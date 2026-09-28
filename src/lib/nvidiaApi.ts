/*
 * NVIDIA-Modelle (LLM, Bilder, TTS) über die Supabase-Funktion „nvidia“.
 * Der API-Schlüssel liegt nur als Server-Secret (NVIDIA_API_KEY) auf Supabase – nie im Browser.
 * Die Aufrufe laufen mit der Anmeldung des Nutzers (nur angemeldete Nutzer).
 */
import { supabase } from "@/integrations/supabase/client";

type ChatMessage = { role: string; content: string };

async function callNvidia<T>(body: Record<string, unknown>): Promise<T> {
  const { data, error } = await supabase.functions.invoke("nvidia", { body });
  if (error) throw new Error(`NVIDIA: ${error.message}`);
  if (data?.error) throw new Error(`NVIDIA: ${data.error}`);
  return data as T;
}

/** Chat mit dem Standard-Ausweichmodell. */
export async function nvidiaChat(messages: ChatMessage[]): Promise<string> {
  return (await callNvidia<{ content: string }>({ kind: "chat", messages })).content ?? "";
}

/** Chat mit einem bestimmten NVIDIA-Modell (z. B. meta/llama-3.3-70b-instruct). */
export async function nvidiaLLM(model: string, messages: ChatMessage[]): Promise<string> {
  return (await callNvidia<{ content: string }>({ kind: "chat", model, messages })).content ?? "";
}

/** Bild erzeugen – liefert eine URL (gehostet oder data:-URL). */
export async function nvidiaGenerateImage(prompt: string): Promise<string> {
  return (await callNvidia<{ url: string }>({ kind: "image", prompt })).url;
}

/** Text vorlesen lassen – liefert ein Audio-Blob. */
export async function nvidiaTTS(text: string, model: string = "magpie-tts-multilingual"): Promise<Blob> {
  const { audioBase64 } = await callNvidia<{ audioBase64: string }>({ kind: "tts", model, input: text });
  const binary = Uint8Array.from(atob(audioBase64), (c) => c.charCodeAt(0));
  return new Blob([binary.buffer], { type: "audio/mpeg" });
}
