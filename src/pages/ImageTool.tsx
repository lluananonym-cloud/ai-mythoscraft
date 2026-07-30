import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Loader2, Image as ImageIcon, Sparkles } from "lucide-react";
import { nvidiaGenerateImage } from "@/lib/nvidiaApi";
import TopNav from "@/components/TopNav";
import { toast } from "sonner";

const ImageTool = () => {
  const [prompt, setPrompt] = useState("");
  const [imgUrl, setImgUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const generate = async () => {
    if (!prompt.trim()) return;
    setLoading(true);
    try {
      const url = await nvidiaGenerateImage(prompt.trim());
      setImgUrl(url);
    } catch (e: any) {
      toast.error(e?.message ?? "Bild‑Generierung fehlgeschlagen");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <main className="container max-w-2xl py-6 mx-auto">
        <h1 className="font-display text-3xl md:text-4xl font-bold mb-6">NVIDIA Bild‑Generator</h1>
        <div className="space-y-4">
          <Input
            placeholder="Beschreibe das gewünschte Bild …"
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            className="bg-input/50"
          />
          <Button onClick={generate} disabled={loading} className="bg-gradient-primary text-primary-foreground hover:opacity-90">
            {loading ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Sparkles className="h-4 w-4 mr-1" />}
            Bild generieren
          </Button>
        </div>
        {imgUrl && (
          <div className="mt-6 text-center">
            <img src={imgUrl} alt={prompt} className="max-w-full rounded-xl shadow-lg mx-auto" />
          </div>
        )}
      </main>
    </div>
  );
};

export default ImageTool;
