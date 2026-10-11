import { useEffect, useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { ChevronDown, Info, Loader2, Plus, X } from "lucide-react";
import { newId, type McpServer } from "@/lib/chatPrefs";
import { probe, redirectUri, type ProbeResult } from "@/lib/mcpOauth";

type Props = {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  /** Neuer Connector; bei authMode "now" startet der Aufrufer danach sofort den Login. */
  onAdd: (s: McpServer) => void;
};

type AuthMode = "now" | "ondemand" | "none";
type ClientMode = "cimd" | "dcr" | "custom";
type Opt<T extends string> = { value: T; title: string; desc: string };
const AUTH: Opt<"now" | "ondemand" | "none">[] = [
  { value: "now", title: "Jetzt anmelden", desc: "Jeder Benutzer meldet sich über den OAuth-Flow des Servers an, bevor er Tools oder Ressourcen verwenden kann." },
  { value: "ondemand", title: "Bei Bedarf anmelden", desc: "Mythos verbindet sich zunächst ohne Anmeldedaten und fordert zur Anmeldung auf, wenn der Server dies verlangt. Wähle dies für Server, die einige Tools auch ohne Konto anbieten." },
  { value: "none", title: "Keine Anmeldung", desc: "Wähle diese Option für Server mit offenem Zugriff oder für Server, die einen API-Schlüssel statt OAuth verwenden." },
];
const CLIENT: Opt<"cimd" | "dcr" | "custom">[] = [
  { value: "cimd", title: "Mythos' veröffentlichte Identität verwenden", desc: "Der Server liest Mythos' Client-Details von einer öffentlichen URL (CIMD). Keine Einrichtung erforderlich, aber der Server muss dies unterstützen." },
  { value: "dcr", title: "Automatisch registrieren", desc: "Mythos registriert OAuth-Clients beim Server, wenn Benutzer sich verbinden (DCR). Funktioniert mit den meisten Servern, erstellt jedoch viele Client-Registrierungen auf stark frequentierten Servern." },
  { value: "custom", title: "Eigenen OAuth-Client verwenden", desc: "Gib eine Client-ID ein, die du selbst beim Server registriert hast. Lass das Secret leer, es sei denn, dein Autorisierungsserver erfordert eines." },
];

function Options<T extends string>({ opts, value, onChange, found }: { opts: Opt<T>[]; value: T; onChange: (v: T) => void; found?: T }) {
  return (
    <RadioGroup value={value} onValueChange={(v) => onChange(v as T)} className="gap-3">
      {opts.map(o => (
        <label key={o.value} className="flex gap-3 cursor-pointer">
          <RadioGroupItem value={o.value} className="mt-0.5" />
          <div>
            <div className="text-sm font-medium flex items-center gap-2">
              {o.title}
              {found === o.value && <span className="rounded-md bg-white/10 px-1.5 py-0.5 text-[10px] font-normal text-muted-foreground">Erkannt</span>}
            </div>
            <div className="text-xs text-muted-foreground">{o.desc}</div>
          </div>
        </label>
      ))}
    </RadioGroup>
  );
}

const WARN = "Verwende nur Connectors von Entwicklern, denen du vertraust. Mythos hat keine Kontrolle darüber, welche Tools Entwickler bereitstellen, und kann nicht garantieren, dass sie wie vorgesehen funktionieren oder sich nicht verändern.";

/** "Benutzerdefinierten Connector hinzufügen" wie bei Claude: Name + URL, Server prüfen, dann Anmeldung wählen. */
export default function McpConnectorDialog({ open, onOpenChange, onAdd }: Props) {
  const [step, setStep] = useState<1 | 2>(1);
  const [name, setName] = useState("");
  const [url, setUrl] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [found, setFound] = useState<ProbeResult | null>(null);
  const [authMode, setAuthMode] = useState<"now" | "ondemand" | "none">("now");
  const [clientMode, setClientMode] = useState<"cimd" | "dcr" | "custom">("dcr");
  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [headers, setHeaders] = useState<{ k: string; v: string }[]>([]);
  const [advanced, setAdvanced] = useState(false);

  useEffect(() => {
    if (!open) return;
    setStep(1); setName(""); setUrl(""); setErr(""); setFound(null); setBusy(false);
    setClientId(""); setClientSecret(""); setHeaders([]); setAdvanced(false);
  }, [open]);

  const next = async () => {
    const u = url.trim();
    if (!/^https:\/\//i.test(u)) { setErr("Bitte eine HTTPS-Adresse angeben, z. B. https://mcp.example.com/mcp"); return; }
    setBusy(true); setErr("");
    try {
      const r = await probe(u);
      setFound(r); setAuthMode(r.auth); setClientMode(r.client); setStep(2);
    } catch (e) {
      setErr(e instanceof Error ? e.message : "Server nicht erreichbar");
    } finally { setBusy(false); }
  };

  const add = () => {
    if (authMode !== "none" && clientMode === "custom" && !clientId.trim()) { setErr("Bitte eine Client-ID angeben."); return; }
    const u = url.trim();
    const hs = headers.filter(h => h.k.trim() && h.v.trim());
    onAdd({
      id: newId(), name: name.trim() || new URL(u).hostname, url: u, enabled: true,
      authMode, clientMode,
      clientId: clientMode === "custom" ? clientId.trim() : undefined,
      clientSecret: clientMode === "custom" ? clientSecret.trim() || undefined : undefined,
      headers: hs.length ? Object.fromEntries(hs.map(h => [h.k.trim(), h.v.trim()])) : undefined,
    });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="glass-strong max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader><DialogTitle className="text-xl">Benutzerdefinierten Connector hinzufügen</DialogTitle></DialogHeader>
        <p className="text-sm text-muted-foreground -mt-2">Verbinde Mythos mit deinen Daten und Tools.</p>

        {step === 1 ? (
          <div className="space-y-3">
            <div className="space-y-1">
              <Input placeholder="Name" value={name} onChange={e => setName(e.target.value)} autoFocus />
              <p className="text-xs text-muted-foreground">Wird in der Connector-Liste angezeigt.</p>
            </div>
            <div className="space-y-1">
              <Input placeholder="MCP-Server-URL" value={url} onChange={e => setUrl(e.target.value)} onKeyDown={e => e.key === "Enter" && next()} />
              <p className="text-xs text-muted-foreground">Die HTTPS-Adresse, unter der der Server MCP-Anfragen entgegennimmt, zum Beispiel https://mcp.example.com/mcp.</p>
            </div>
            {err && <p className="text-xs text-destructive">{err}</p>}
            <p className="text-xs text-muted-foreground pt-2">{WARN}</p>
            <div className="flex justify-end gap-2 pt-1">
              <Button variant="secondary" onClick={() => onOpenChange(false)}>Abbrechen</Button>
              <Button onClick={next} disabled={busy || !url.trim()}>
                {busy && <Loader2 className="h-4 w-4 animate-spin mr-1" />}{busy ? "Verbinde …" : "Weiter"}
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-5">
            <div className="border-b border-white/10 pb-3">
              <div className="font-medium">{name.trim() || new URL(url.trim()).hostname}</div>
              <div className="font-mono text-xs text-muted-foreground break-all mt-1">{url.trim()}</div>
            </div>

            <div className="space-y-3">
              <div className="flex items-center gap-1.5 font-medium"><span>Authentifizierung</span><Info className="h-3.5 w-3.5 text-muted-foreground" /></div>
              <Options<AuthMode> opts={AUTH} value={authMode} onChange={setAuthMode} found={found?.auth} />
            </div>

            {authMode !== "none" && (
              <div className="space-y-3">
                <div className="flex items-center gap-1.5 font-medium"><span>OAuth-Client</span><Info className="h-3.5 w-3.5 text-muted-foreground" /></div>
                <Options<ClientMode> opts={CLIENT} value={clientMode} onChange={setClientMode} found={found?.oauth ? found.client : undefined} />
                {clientMode === "custom" && (
                  <div className="space-y-2 pl-7">
                    <Input placeholder="Client-ID" value={clientId} onChange={e => setClientId(e.target.value)} />
                    <Input placeholder="Client-Secret (optional)" type="password" value={clientSecret} onChange={e => setClientSecret(e.target.value)} />
                    <p className="text-[11px] text-muted-foreground">Weiterleitungs-URL beim Server eintragen: <span className="font-mono">{redirectUri()}</span></p>
                  </div>
                )}
              </div>
            )}

            <div className="space-y-2">
              <div className="font-medium">Request-Header</div>
              <p className="text-xs text-muted-foreground">Wird mit jeder Anfrage zusammen mit dem OAuth-Bearer-Token gesendet. Werte werden nur in diesem Browser gespeichert. Du kannst bis zu vier hinzufügen.</p>
              {headers.map((h, i) => (
                <div key={i} className="flex gap-2">
                  <Input placeholder="Header-Name" value={h.k} onChange={e => setHeaders(hs => hs.map((x, j) => j === i ? { ...x, k: e.target.value } : x))} />
                  <Input placeholder="Wert" type="password" value={h.v} onChange={e => setHeaders(hs => hs.map((x, j) => j === i ? { ...x, v: e.target.value } : x))} />
                  <Button variant="ghost" size="icon" onClick={() => setHeaders(hs => hs.filter((_, j) => j !== i))} aria-label="Header entfernen"><X className="h-4 w-4" /></Button>
                </div>
              ))}
              {headers.length < 4 && (
                <Button variant="secondary" size="sm" onClick={() => setHeaders(hs => [...hs, { k: "", v: "" }])}><Plus className="h-4 w-4 mr-1" /> Header hinzufügen</Button>
              )}
            </div>

            <div className="border-t border-white/10 pt-3">
              <button className="flex items-center gap-1.5 text-sm" onClick={() => setAdvanced(a => !a)}>
                <ChevronDown className={`h-4 w-4 transition-transform ${advanced ? "" : "-rotate-90"}`} /> Erweiterte
              </button>
              {advanced && (
                <p className="text-xs text-muted-foreground mt-2">Die Erkennung prüft den Server einmal beim Hinzufügen. Anmeldedaten und Header bleiben nur in diesem Browser; Anfragen, die der Server im Browser blockiert, laufen über den Mythos-Server.</p>
              )}
            </div>

            {err && <p className="text-xs text-destructive">{err}</p>}
            <p className="text-xs text-muted-foreground">{WARN}</p>
            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={() => { setErr(""); setStep(1); }}>Zurück</Button>
              <Button onClick={add}>Hinzufügen</Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
