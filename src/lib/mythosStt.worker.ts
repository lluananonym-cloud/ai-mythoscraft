/// <reference lib="webworker" />
// Spracherkennung mit Whisper (Open Source, OpenAI) über Transformers.js – läuft lokal im Hintergrund-Thread.
// Wird für die Mythos-Code-App gebündelt (npm run build:app-voice), weil Electron keine Browser-Spracherkennung hat.
import { pipeline, env } from "@huggingface/transformers";

type Msg =
  | { type: "init"; model: string; ortWasm: string; dtype?: string | Record<string, string>; device?: "webgpu" | "wasm" }
  | { type: "transcribe"; id: number; audio: Float32Array; language?: string };

let asr: ((audio: Float32Array, opts: Record<string, unknown>) => Promise<{ text: string } | { text: string }[]>) | null = null;

self.onmessage = async (e: MessageEvent<Msg>) => {
  const m = e.data;
  try {
    if (m.type === "init") {
      env.allowLocalModels = false;
      const onnx = env.backends.onnx as { wasm?: { wasmPaths?: string } };
      if (onnx.wasm) onnx.wasm.wasmPaths = m.ortWasm;
      asr = (await pipeline("automatic-speech-recognition", m.model, {
        dtype: (m.dtype || { encoder_model: "fp32", decoder_model_merged: "q8" }) as never,
        device: m.device || "wasm",
        progress_callback: (p: { status: string; file?: string; progress?: number }) => {
          if (p.status === "progress" && p.file && /\.onnx/.test(p.file)) self.postMessage({ type: "progress", file: p.file, pct: Math.round(p.progress || 0) });
        },
      })) as unknown as typeof asr;
      // Einmal aufwärmen, damit die erste echte Erkennung schnell ist.
      await (asr as NonNullable<typeof asr>)(new Float32Array(16000), { language: "german", task: "transcribe" });
      self.postMessage({ type: "ready" });
    } else if (m.type === "transcribe") {
      if (!asr) throw new Error("Spracherkennung nicht geladen");
      const out = await asr(m.audio, { language: m.language || "german", task: "transcribe", chunk_length_s: 30, stride_length_s: 5 });
      const text = (Array.isArray(out) ? out.map((o) => o.text).join(" ") : out.text).trim();
      self.postMessage({ type: "text", id: m.id, text });
    }
  } catch (err) {
    self.postMessage({ type: "error", id: m.type === "transcribe" ? m.id : undefined, message: String((err as Error)?.message || err) });
  }
};
