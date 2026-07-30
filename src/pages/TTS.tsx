import { useState, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Loader2, Play, Pause, Sparkles } from "lucide-react";
import { nvidiaTTS } from "@/lib/nvidiaApi";
import TopNav from "@/components/TopNav";
import { toast } from "sonner";

const TTS = () => {
  const [text, setText] = useState("");
  const [model, setModel] = useState("magpie-tts-multilingual");
  const [loading, setLoading] = useState(false);
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const audioRef = useRef<HTMLAudioElement>(null);

  const speak = async () => {
    if (!text.trim()) return;
    setLoading(true);
    try {
      const blob = await nvidiaTTS(text.trim(), model);
      const url = URL.createObjectURL(blob);
      setAudioUrl(url);
    } catch (e: any) {
      toast.error(e?.message ?? "Sprachausgabe fehlgeschlagen");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <main className="container max-w-2xl py-6 mx-auto">
        <h1 className="font-display text-3xl md:text-4xl font-bold mb-6">NVIDIA Text‑to‑Speech</h1>
        <div className="space-y-4">
          <Textarea
            placeholder="Gib den Text ein, der vorgelesen werden soll …"
            value={text}
            onChange={(e) => setText(e.target.value)}
            className="bg-input/50 min-h-[120px]"
          />
          <div className="flex gap-2 items-center">
            <Button onClick={speak} disabled={loading} className="bg-gradient-primary text-primary-foreground hover:opacity-90">
              {loading ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Sparkles className="h-4 w-4 mr-1" />}
              Vorlesen
            </Button>
            {audioUrl && (
              <Button onClick={() => audioRef.current?.play()} className="flex items-center">
                <Play className="h-4 w-4 mr-1" /> Abspielen
              </Button>
            )}
          </div>
        </div>
        {audioUrl && <audio ref={audioRef} src={audioUrl} controls className="mt-4 w-full" />}
      </main>
    </div>
  );
};

export default TTS;
