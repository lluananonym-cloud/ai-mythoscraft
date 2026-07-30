import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Loader2, Sparkles } from "lucide-react";
import { nvidiaLLM } from "@/lib/nvidiaApi";
import TopNav from "@/components/TopNav";
import { toast } from "sonner";

const CodeAssist = () => {
  const [prompt, setPrompt] = useState("");
  const [result, setResult] = useState<string>("");
  const [loading, setLoading] = useState(false);

  const run = async () => {
    if (!prompt.trim()) return;
    setLoading(true);
    try {
      const response = await nvidiaLLM("meta/llama-3.3-70b-instruct", [{ role: "user", content: prompt.trim() }]);
      setResult(response);
    } catch (e: any) {
      toast.error(e?.message ?? "Code‑Assist‑Fehler");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <main className="container max-w-2xl py-6 mx-auto">
        <h1 className="font-display text-3xl md:text-4xl font-bold mb-6">Meta Llama Code‑Assist</h1>
        <div className="space-y-4">
          <Textarea
            placeholder="Beschreibe dein Coding‑Problem oder gib Code‑Snippet ein …"
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            className="bg-input/50 min-h-[120px]"
          />
          <Button onClick={run} disabled={loading} className="bg-gradient-primary text-primary-foreground hover:opacity-90">
            {loading ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Sparkles className="h-4 w-4 mr-1" />}
            Ausführen
          </Button>
        </div>
        {result && (
          <div className="mt-6 glass-strong rounded-xl p-4">
            <h2 className="font-display text-lg font-semibold mb-2">Antwort</h2>
            <pre className="whitespace-pre-wrap text-sm">{result}</pre>
          </div>
        )}
      </main>
    </div>
  );
};

export default CodeAssist;
