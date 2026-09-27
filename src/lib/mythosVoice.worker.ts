/// <reference lib="webworker" />
// Piper läuft hier im Hintergrund-Thread, damit Seite und Logo-Orb beim Berechnen nicht ruckeln.
import { TtsSession } from "@mintplex-labs/piper-tts-web";

type Msg =
  | { type: "init"; voiceId: string; wasmPaths: { onnxWasm: string; piperData: string; piperWasm: string } }
  | { type: "predict"; id: number; text: string };

let session: TtsSession | null = null;

self.onmessage = async (e: MessageEvent<Msg>) => {
  const m = e.data;
  try {
    if (m.type === "init") {
      TtsSession._instance = null;
      session = await TtsSession.create({
        voiceId: m.voiceId,
        wasmPaths: m.wasmPaths,
        progress: (p) => { if (p.total && /\.onnx$/.test(p.url)) self.postMessage({ type: "progress", pct: Math.round((p.loaded / p.total) * 100) }); },
      });
      self.postMessage({ type: "ready" });
    } else if (m.type === "predict") {
      if (!session) throw new Error("Stimme nicht geladen");
      const wav = await (await session.predict(m.text)).arrayBuffer();
      (self as unknown as Worker).postMessage({ type: "audio", id: m.id, wav }, [wav]);
    }
  } catch (err) {
    self.postMessage({ type: "error", id: m.type === "predict" ? m.id : undefined, message: String((err as Error)?.message || err) });
  }
};
