import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Brain, Drama, KeyRound, Loader2, LogOut, Plug, Plus, Trash2, Wrench, Puzzle } from "lucide-react";
import { toast } from "sonner";
import {
  getInstructions, getMcpServers, setInstructions, setMcpServers, type McpServer,
} from "@/lib/chatPrefs";
import { listTools } from "@/lib/mcpClient";
import { login, openLoginPopup } from "@/lib/mcpOauth";
import McpConnectorDialog from "@/components/chat/McpConnectorDialog";

type Props = { open: boolean; onOpenChange: (o: boolean) => void };

/** "Anpassungen" wie bei Claude: Connectoren (MCP), eigene Anweisungen, Personas & Erinnerungen. */
export default function CustomizeDialog({ open, onOpenChange }: Props) {
  const nav = useNavigate();
  const [servers, setServers] = useState<McpServer[]>([]);
  const [tools, setTools] = useState<Record<string, string[] | "err" | "auth" | "load">>({});
  const [adding, setAdding] = useState(false);
  const [instr, setInstr] = useState("");

  useEffect(() => {
    if (!open) return;
    setServers(getMcpServers());
    setInstr(getInstructions());
  }, [open]);

  const save = (next: McpServer[]) => { setServers(next); setMcpServers(next); };
  // Token-Erneuerung schreibt direkt in den Speicher – Liste danach neu lesen.
  const reload = () => setServers(getMcpServers());

  const test = async (s: McpServer) => {
    setTools(t => ({ ...t, [s.id]: "load" }));
    try {
      const list = await listTools(s);
      setTools(t => ({ ...t, [s.id]: list.map(x => x.name) }));
    } catch (e) {
      const auth = !!(e as { auth?: boolean })?.auth;
      setTools(t => ({ ...t, [s.id]: auth ? "auth" : "err" }));
      if (!auth) toast.error(`${s.name}: ${e instanceof Error ? e.message : "nicht erreichbar"}`);
    }
    reload();
  };

  /** OAuth-Login im Popup; das Popup muss direkt im Klick geöffnet werden. */
  const signIn = (s: McpServer) => {
    const popup = openLoginPopup();
    setTools(t => ({ ...t, [s.id]: "load" }));
    login(s, popup).then(oauth => {
      const next = getMcpServers().map(x => x.id === s.id ? { ...x, oauth } : x);
      save(next);
      toast.success(`${s.name} verbunden`);
      test({ ...s, oauth });
    }).catch(e => {
      setTools(t => ({ ...t, [s.id]: "auth" }));
      toast.error(`${s.name}: ${e instanceof Error ? e.message : "Anmeldung fehlgeschlagen"}`);
    });
  };
  const signOut = (s: McpServer) => {
    save(servers.map(x => x.id === s.id ? { ...x, oauth: undefined } : x));
    setTools(t => ({ ...t, [s.id]: "auth" }));
  };

  const add = (s: McpServer) => {
    const next = [...servers, s];
    save(next);
    setAdding(false);
    if (s.authMode === "now") signIn(s); else test(s);
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
                    {t === "auth" && <span className="text-amber-300">Anmeldung nötig</span>}
                  </div>
                  {s.authMode !== "none" && !s.auth && (
                    <div className="flex items-center gap-2">
                      {s.oauth ? (
                        <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => signOut(s)}><LogOut className="h-3 w-3 mr-1" /> Abmelden</Button>
                      ) : (
                        <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => signIn(s)} disabled={t === "load"}><KeyRound className="h-3 w-3 mr-1" /> Anmelden</Button>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
            <Button size="sm" variant="secondary" className="w-full" onClick={() => setAdding(true)}><Plus className="h-4 w-4 mr-1" /> Eigenen Connector hinzufügen</Button>
            <p className="text-[11px] text-muted-foreground">Wird nur in diesem Browser gespeichert.</p>
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
      <McpConnectorDialog open={adding} onOpenChange={setAdding} onAdd={add} />
    </Dialog>
  );
}
