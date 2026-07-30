import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import VideoPlayer, { VideoRequest } from "@/components/VideoPlayer";
import TopNav from "@/components/TopNav";
import { Sparkles } from "lucide-react";

const VideoTool = () => {
  const [request, setRequest] = useState<VideoRequest | null>(null);
  const [prompt, setPrompt] = useState("");
  const [title, setTitle] = useState("");

  const start = () => {
    if (!prompt.trim()) return;
    setRequest({ prompt, title: title || undefined });
  };

  const reset = () => {
    setRequest(null);
    setPrompt("");
    setTitle("");
  };

  return (
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <main className="container max-w-2xl py-6 mx-auto">
        <h1 className="font-display text-3xl md:text-4xl font-bold mb-6">AI Video‑Generator</h1>
        {request ? (
          <div className="space-y-4">
            <VideoPlayer request={request} />
            <Button onClick={reset} variant="outline" className="mt-2">
              <Sparkles className="h-4 w-4 mr-1" /> Neuer Durchlauf
            </Button>
          </div>
        ) : (
          <div className="space-y-4">
            <label className="block text-sm font-medium text-foreground">Prompt (Beschreibung für das Video)</label>
            <Textarea
              value={prompt}
              onChange={e => setPrompt(e.target.value)}
              placeholder="z. B. Ein futuristisches Stadtbild bei Sonnenuntergang …"
              className="bg-input/50"
              rows={3}
            />
            <label className="block text-sm font-medium text-foreground mt-4">Titel (optional)</label>
            <Input
              value={title}
              onChange={e => setTitle(e.target.value)}
              placeholder="Mein KI‑Video"
              className="bg-input/50"
            />
            <Button onClick={start} className="mt-2 bg-gradient-primary text-primary-foreground hover:opacity-90">
              <Sparkles className="h-4 w-4 mr-1" /> Video generieren
            </Button>
          </div>
        )}
      </main>
    </div>
  );
};

export default VideoTool;
