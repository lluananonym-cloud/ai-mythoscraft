import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Loader2, Sparkles, WifiOff, Copy } from "lucide-react";
import { toast } from "sonner";
import { loadPipeline } from "@/lib/transformersLoader";

export type OfflineTask =
  | { kind: "chat"; prompt: string }
  | { kind: "summary"; text: string }
  | { kind: "sentiment"; text: string };

const MODELS: Record<OfflineTask["kind"], { task: string; id: string; dtype: string; size: string; label: string }> = {
  chat:      { task: "text-generation",    id: "onnx-community/Qwen2.5-0.5B-Instruct",                  dtype: "q4",   size: "~500MB", label: "Offline-Chat (Qwen2.5-0.5B)" },
  summary:   { task: "summarization",      id: "Xenova/distilbart-cnn-6-6",                             dtype: "fp32", size: "~250MB", label: "Offline-Zusammenfassung" },
  sentiment: { task: "sentiment-analysis", id: "Xenova/distilbert-base-uncased-finetuned-sst-2-english", dtype: "fp32", size: "~65MB",  label: "Offline-Sentiment" },
};

async function getPipe(kind: OfflineTask["kind"], onProgress: (p: number, m: string) => void) {
  const cfg = MODELS[kind];
  return loadPipeline({
    task: cfg.task,
    model: cfg.id,
    dtype: cfg.dtype,
    onProgress: (p) => onProgress(p.pct, p.msg),
  });
}

export default function OfflineAI({ task }: { task: OfflineTask }) {
  const [status, setStatus] = useState<"idle" | "loading" | "running" | "done" | "error">("idle");
  const [progress, setProgress] = useState(0);
  const [progressMsg, setProgressMsg] = useState("");
  const [result, setResult] = useState<string>("");
  const startedRef = useRef(false);
  const cfg = MODELS[task.kind];

  const run = async () => {
    try {
      setStatus("loading");
      setProgressMsg(`Lade ${cfg.label} (${cfg.size}, einmalig)…`);
      const pipe = await getPipe(task.kind, (p, m) => { setProgress(p); setProgressMsg(m); });

      setStatus("running");
      setProgressMsg("Verarbeite offline…");

      let text = "";
      if (task.kind === "chat") {
        const messages = [
          { role: "system", content: "You are a concise, helpful assistant. Reply in the user's language." },
          { role: "user", content: task.prompt },
        ];
        const out: any = await pipe(messages, { max_new_tokens: 256, do_sample: true, temperature: 0.7 });
        const generated = out?.[0]?.generated_text;
        if (Array.isArray(generated)) text = generated[generated.length - 1]?.content ?? "";
        else text = String(generated ?? "");
      } else if (task.kind === "summary") {
        const out: any = await pipe(task.text, { max_new_tokens: 120, min_new_tokens: 30 });
        text = out?.[0]?.summary_text ?? "";
      } else {
        const out: any = await pipe(task.text);
        const r = out?.[0];
        const emoji = r?.label === "POSITIVE" ? "😊" : "😟";
        text = `${emoji} ${r?.label}  (${(r?.score * 100).toFixed(1)}%)`;
      }

      setResult(text.trim() || "(leer)");
      setStatus("done");
    } catch (e: any) {
      console.error("Offline AI error:", e);
      toast.error("Offline-AI fehlgeschlagen: " + (e?.message ?? "unknown"));
      setStatus("error");
    }
  };

  // Auto-run small sentiment model for instant UX
  useEffect(() => {
    if (task.kind === "sentiment" && !startedRef.current) {
      startedRef.current = true;
      run();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const copy = async () => {
    try { await navigator.clipboard.writeText(result); toast.success("Kopiert"); } catch {}
  };

  return (
    <div className="my-3 rounded-2xl border border-white/10 bg-gradient-to-br from-emerald-500/10 via-background/40 to-cyan-500/10 p-4 backdrop-blur-md">
      <div className="flex items-center justify-between gap-3 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          <WifiOff className="h-4 w-4 text-emerald-400 shrink-0" />
          <div className="min-w-0">
            <div className="font-display text-sm truncate">{cfg.label}</div>
            <div className="text-[11px] text-muted-foreground">100% lokal im Browser · keine Server</div>
          </div>
        </div>
        <div className="flex items-center gap-1 shrink-0">
          {status === "done" && (
            <Button size="icon" variant="ghost" onClick={copy} className="h-8 w-8" title="Kopieren">
              <Copy className="h-3.5 w-3.5" />
            </Button>
          )}
          {status === "idle" && (
            <div className="flex gap-2">
              <Button size="sm" onClick={run} className="gap-1.5" aria-label="Offline‑Task starten">
                <Sparkles className="h-4 w-4" /> Starten
              </Button>
              <Button size="sm" variant="outline" onClick={() => setStatus("idle")} className="gap-1.5" aria-label="Später">
                Später
              </Button>
            </div>
          )}
          {(status === "loading" || status === "running") && (
            <Button size="icon" disabled className="h-9 w-9">
              <Loader2 className="h-4 w-4 animate-spin" />
            </Button>
          )}
        </div>
      </div>

      {(status === "loading" || status === "running") && (
        <div className="space-y-1.5">
          <div className="text-xs text-muted-foreground">{progressMsg}</div>
          {status === "loading" && (
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-foreground/10">
              <div className="h-full bg-emerald-400 transition-all" style={{ width: `${progress}%` }} />
            </div>
          )}
        </div>
      )}

      {status === "done" && (
        <div className="mt-1 whitespace-pre-wrap text-sm leading-relaxed">{result}</div>
      )}
      {status === "error" && (
        <div className="mt-1 text-xs text-destructive">Fehler. Versuche ein anderes Modell oder lade die Seite neu.</div>
      )}
    </div>
  );
}
