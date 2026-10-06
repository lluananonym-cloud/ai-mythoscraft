import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Brain, Drama, Loader2, Plug, Plus, Trash2, Wrench, Puzzle } from "lucide-react";
import { toast } from "sonner";
import {
  getInstructions, getMcpServers, newId, setInstructions, setMcpServers, type McpServer,
} from "@/lib/chatPrefs";
import { listTools } from "@/lib/mcpClient";

type Props = { open: boolean; onOpenChange: (o: boolean) => void };

/** "Anpassungen" wie bei Claude: Connectoren (MCP), eigene Anweisungen, Personas & Erinnerungen. */
export default function CustomizeDialog({ open, onOpenChange }: Props) {
  const nav = useNavigate();
  const [servers, setServers] = useState<McpServer[]>([]);
  const [tools, setTools] = useState<Record<string, string[] | "err" | "load">>({});
  const [name, setName] = useState("");
  const [url, setUrl] = useState("");
  const [auth, setAuth] = useState("");
  const [instr, setInstr] = useState("");

  useEffect(() => {
    if (!open) return;
    setServers(getMcpServers());
    setInstr(getInstructions());
  }, [open]);

  const save = (next: McpServer[]) => { setServers(next); setMcpServers(next); };

  const test = async (s: McpServer) => {
    setTools(t => ({ ...t, [s.id]: "load" }));
    try {
      const list = await listTools(s);
      setTools(t => ({ ...t, [s.id]: list.map(x => x.name) }));
    } catch (e) {
      setTools(t => ({ ...t, [s.id]: "err" }));
      toast.error(`${s.name}: ${e instanceof Error ? e.message : "nicht erreichbar"}`);
    }
  };

  const add = () => {
    const u = url.trim();
    if (!/^https?:\/\//i.test(u)) { toast.error("Bitte eine gültige URL angeben (https://…)"); return; }
    const s: McpServer = { id: newId(), name: name.trim() || new URL(u).hostname, url: u, auth: auth.trim() || undefined, enabled: true };
    save([...servers, s]);
    setName(""); setUrl(""); setAuth("");
    test(s);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="glass-strong max-w-xl max-h-[85vh] overflow-y-auto">
        <DialogHeader><DialogTitle>Anpassungen</DialogTitle></DialogHeader>
        <Tabs defaultValue="connectors">
          <TabsList className="grid grid-cols-3 w-full">
            <TabsTrigger value="connectors">Connectoren</TabsTrigger>
            <TabsTrigger value="instructions">Anweisungen</TabsTrigger>
            <TabsTrigger value="more">Mehr</TabsTrigger>
          </TabsList>

          <TabsContent value="connectors" className="space-y-3 pt-2">
            <p className="text-xs text-muted-foreground">
              Verbinde MCP-Server (Streamable HTTP). Mythos kann ihre Werkzeuge dann im Chat und bei <code className="font-mono">/goal</code> selbst benutzen.
            </p>
            {servers.length === 0 && (
              <div className="rounded-xl border border-dashed border-white/10 p-4 text-center text-sm text-muted-foreground">Noch keine Connectoren.</div>
            )}
            {servers.map(s => {
              const t = tools[s.id];
              return (
                <div key={s.id} className="rounded-xl border border-white/10 p-3 space-y-2">
                  <div className="flex items-center gap-2">
                    <Plug className="h-4 w-4 text-violet-300 shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium truncate">{s.name}</div>
                      <div className="text-[11px] text-muted-foreground truncate">{s.url}</div>
                    </div>
                    <Switch checked={s.enabled} onCheckedChange={(v) => save(servers.map(x => x.id === s.id ? { ...x, enabled: v } : x))} />
                    <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => save(servers.filter(x => x.id !== s.id))} aria-label="Entfernen">
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                  <div className="flex items-center gap-2 text-xs">
                    <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => test(s)} disabled={t === "load"}>
                      {t === "load" ? <Loader2 className="h-3 w-3 animate-spin mr-1" /> : <Wrench className="h-3 w-3 mr-1" />} Werkzeuge laden
                    </Button>
                    {Array.isArray(t) && <span className="text-muted-foreground truncate">{t.length} Werkzeuge: {t.slice(0, 6).join(", ")}{t.length > 6 ? " …" : ""}</span>}
                    {t === "err" && <span className="text-destructive">nicht erreichbar</span>}
                  </div>
                </div>
              );
            })}
            <div className="rounded-xl border border-white/10 p-3 space-y-2">
              <div className="text-sm font-medium">Eigenen Connector hinzufügen</div>
              <Input placeholder="Name (z. B. GitHub)" value={name} onChange={e => setName(e.target.value)} />
              <Input placeholder="MCP-URL, z. B. https://example.com/mcp" value={url} onChange={e => setUrl(e.target.value)} />
              <Input placeholder="Authorization (optional), z. B. Bearer …" value={auth} onChange={e => setAuth(e.target.value)} type="password" />
              <Button size="sm" onClick={add}><Plus className="h-4 w-4 mr-1" /> Hinzufügen</Button>
              <p className="text-[11px] text-muted-foreground">Wird nur in diesem Browser gespeichert.</p>
            </div>
          </TabsContent>

          <TabsContent value="instructions" className="space-y-3 pt-2">
            <p className="text-xs text-muted-foreground">Was soll Mythos in jedem Chat über dich wissen oder beachten?</p>
            <Textarea rows={8} value={instr} onChange={e => setInstr(e.target.value)} placeholder="z. B. Antworte kurz. Ich programmiere mit TypeScript und React." />
            <Button size="sm" onClick={() => { setInstructions(instr.trim()); toast.success("Gespeichert"); }}>Speichern</Button>
          </TabsContent>

          <TabsContent value="more" className="grid gap-2 pt-2">
            {[
              { icon: Drama, label: "Personas", desc: "Eigene KI-Charaktere", to: "/personas" },
              { icon: Brain, label: "Erinnerungen", desc: "Was Mythos sich über dich merkt", to: "/memories" },
              { icon: Puzzle, label: "Browser-Erweiterung", desc: "Deinen echten Browser steuern (/browser)", to: "/browser" },
            ].map(x => (
              <button key={x.to} onClick={() => { onOpenChange(false); nav(x.to); }}
                className="flex items-center gap-3 rounded-xl border border-white/10 hover:bg-white/5 p-3 text-left">
                <x.icon className="h-4 w-4 text-violet-300" />
                <div><div className="text-sm font-medium">{x.label}</div><div className="text-xs text-muted-foreground">{x.desc}</div></div>
              </button>
            ))}
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}
