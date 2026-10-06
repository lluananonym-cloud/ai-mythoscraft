import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import TopNav from "@/components/TopNav";
import UpgradeDialog from "@/components/UpgradeDialog";
import { Button } from "@/components/ui/button";
import {
  Download, Lock, Crown, Check, CheckCircle2, Copy, Monitor, Clock, TerminalSquare, Puzzle, ChevronDown, ShieldCheck, RefreshCw, Sparkles,
} from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { useSubscription } from "@/hooks/useSubscription";
import { APPS, AppDownload, ReleaseInfo, downloadUrl, fetchReleases } from "@/lib/downloads";

const fmtSize = (b: number) => (b > 1024 * 1024 ? `${(b / 1024 / 1024).toFixed(0)} MB` : `${Math.round(b / 1024)} KB`);
const fmtDate = (s: string) => new Date(s).toLocaleDateString("de-DE", { day: "numeric", month: "short", year: "numeric" });
const copy = (text: string, label = "Kopiert") => navigator.clipboard.writeText(text).then(() => toast.success(label)).catch(() => {});

type Filter = "all" | "free" | "pro";

/** Erkennt die installierte Browser-Erweiterung über ihren Heartbeat (bridge.js). */
function useExtensionVersion() {
  const [version, setVersion] = useState<string | null>(null);
  const lastSeen = useRef(0);
  useEffect(() => {
    const onMsg = (e: MessageEvent) => {
      const d = e.data;
      if (d?.source === "mythos-ext" && d.type === "ready") { lastSeen.current = Date.now(); setVersion(d.version || "?"); }
    };
    window.addEventListener("message", onMsg);
    const ping = () => window.postMessage({ source: "mythos-web", type: "ping" }, "*");
    ping();
    const t = setInterval(() => {
      ping();
      if (lastSeen.current && Date.now() - lastSeen.current > 5000) setVersion(null);
    }, 1500);
    return () => { window.removeEventListener("message", onMsg); clearInterval(t); };
  }, []);
  return version;
}

const Preview = ({ app }: { app: AppDownload }) => (
  <div className="relative aspect-[16/10] overflow-hidden border-b border-border/40 bg-[radial-gradient(ellipse_at_top,hsl(var(--primary)/0.25),transparent_70%)] flex items-center justify-center">
    {app.screenshot ? (
      <img
        src={app.screenshot}
        alt={`${app.name} Screenshot`}
        loading="lazy"
        className={app.screenshotFit === "contain"
          ? "h-[88%] w-auto rounded-xl shadow-2xl ring-1 ring-white/10 translate-y-3 transition-transform duration-500 group-hover:translate-y-1"
          : "w-full h-full object-cover object-top transition-transform duration-500 group-hover:scale-[1.03]"}
        onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = "none"; }}
      />
    ) : app.id === "cli" ? (
      <div className="w-[80%] rounded-xl bg-black/70 ring-1 ring-white/10 p-4 font-mono text-[11px] leading-relaxed text-left shadow-2xl">
        <div className="text-muted-foreground">$ mythos</div>
        <div className="text-primary">✦ Mythos Code · bereit</div>
        <div className="text-foreground/80">› Bau mir eine Login-Seite</div>
        <div className="text-muted-foreground">⚙ write src/pages/Login.tsx</div>
        <div className="text-emerald-400">✓ fertig in 38s</div>
      </div>
    ) : (
      <Monitor className="h-16 w-16 text-primary/70" />
    )}
    <span className={`absolute top-3 left-3 text-[11px] font-medium rounded-full px-2.5 py-1 backdrop-blur ${app.free ? "bg-emerald-500/20 text-emerald-300" : "bg-primary/25 text-primary-foreground"}`}>
      {app.free ? "Kostenlos" : <span className="inline-flex items-center gap-1"><Crown className="h-3 w-3" />Pro</span>}
    </span>
  </div>
);

const AppCard = ({ app, release, releasesLoaded, canDownload, onLocked, extVersion }: {
  app: AppDownload;
  release?: ReleaseInfo;
  releasesLoaded: boolean;
  canDownload: boolean;
  onLocked: (app: AppDownload) => void;
  extVersion: string | null;
}) => {
  const [showSteps, setShowSteps] = useState(false);
  const asset = release?.assets.find((a) => (app.asset ? a.name === app.asset : true));
  // Dateien direkt auf der Website sind immer da; sonst zählt das GitHub-Release (bis es geladen ist, der feste Link).
  const available = !!app.directUrl || (releasesLoaded ? !!asset : !!app.asset);
  const href = app.directUrl ?? asset?.url ?? downloadUrl(app);
  const unlocked = canDownload || !!app.free;
  const installedExt = app.id === "extension" && extVersion;
  const Icon = app.id === "cli" ? TerminalSquare : app.id === "extension" ? Puzzle : Monitor;

  return (
    <div className="group glass-strong rounded-3xl overflow-hidden flex flex-col transition-all duration-300 hover:-translate-y-1 hover:border-primary/40">
      <Preview app={app} />
      <div className="p-6 flex flex-col flex-1">
        <div className="flex items-start justify-between gap-3 mb-1">
          <h3 className="font-display text-xl font-bold leading-tight">{app.name}</h3>
          <span className="shrink-0 inline-flex items-center gap-1 text-[11px] text-muted-foreground glass rounded-full px-2 py-0.5 mt-0.5">
            <Icon className="h-3 w-3" />{app.platform}
          </span>
        </div>
        <p className="text-sm text-primary mb-2">{app.tagline}</p>
        <p className="text-sm text-muted-foreground mb-4">{app.description}</p>
        <ul className="space-y-1.5 text-sm mb-5">
          {app.highlights.map((h) => (
            <li key={h} className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-primary shrink-0" />{h}</li>
          ))}
        </ul>

        {app.steps && (
          <div className="mb-4 rounded-2xl border border-border/50 bg-background/40">
            <button type="button" onClick={() => setShowSteps((v) => !v)}
              className="w-full flex items-center justify-between px-4 py-2.5 text-sm font-medium">
              So installierst du sie
              <ChevronDown className={`h-4 w-4 text-muted-foreground transition-transform ${showSteps ? "rotate-180" : ""}`} />
            </button>
            {showSteps && (
              <ol className="px-4 pb-4 space-y-2 text-sm text-muted-foreground">
                {app.steps.map((s, i) => (
                  <li key={s} className="flex gap-3">
                    <span className="h-5 w-5 shrink-0 rounded-full bg-primary/15 text-primary text-[11px] flex items-center justify-center">{i + 1}</span>
                    <span className="flex-1">
                      {s}
                      {s.includes("chrome://extensions") && (
                        <button type="button" onClick={() => copy("chrome://extensions", "Adresse kopiert, im Browser einfügen")}
                          className="ml-2 inline-flex items-center gap-1 text-[11px] text-primary hover:underline">
                          <Copy className="h-3 w-3" />kopieren
                        </button>
                      )}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </div>
        )}

        <div className="mt-auto space-y-2">
          {app.install && available && unlocked && (
            <button type="button" onClick={() => copy(app.install!, "Befehl kopiert")} title="Kopieren"
              className="w-full flex items-center justify-between gap-2 rounded-xl border border-border/60 bg-background/60 px-3 py-2.5 font-mono text-xs text-left hover:border-primary/50 transition-colors">
              <span className="truncate">{app.install}</span>
              <Copy className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
            </button>
          )}
          {installedExt ? (
            <div className="w-full h-11 rounded-md border border-emerald-500/40 bg-emerald-500/10 text-emerald-300 text-sm flex items-center justify-center gap-2">
              <CheckCircle2 className="h-4 w-4" />Installiert und verbunden · v{extVersion}
            </div>
          ) : !available ? (
            <Button disabled variant="outline" className="w-full h-11">
              <Clock className="h-4 w-4 mr-2" />Bald verfügbar
            </Button>
          ) : unlocked ? (
            <Button asChild className="w-full h-11 bg-gradient-primary text-primary-foreground hover:opacity-90">
              <a href={href} download onClick={() => app.steps && setShowSteps(true)}>
                <Download className="h-4 w-4 mr-2" />Herunterladen
              </a>
            </Button>
          ) : (
            <Button onClick={() => onLocked(app)} variant="outline" className="w-full h-11">
              <Lock className="h-4 w-4 mr-2" />Herunterladen mit Pro
            </Button>
          )}
          {asset && release && !app.directUrl && (
            <p className="text-[11px] text-center text-muted-foreground">
              {fmtSize(asset.size)} · aktualisiert {fmtDate(release.publishedAt)}
            </p>
          )}
        </div>
      </div>
    </div>
  );
};

const FILTERS: { key: Filter; label: string }[] = [
  { key: "all", label: "Alle" },
  { key: "free", label: "Kostenlos" },
  { key: "pro", label: "Pro" },
];

const Downloads = () => {
  const { user } = useAuth();
  const { isPro, loading } = useSubscription();
  const [releases, setReleases] = useState<Record<string, ReleaseInfo>>({});
  const [releasesLoaded, setReleasesLoaded] = useState(false);
  const [locked, setLocked] = useState<AppDownload | null>(null);
  const [filter, setFilter] = useState<Filter>("all");
  const extVersion = useExtensionVersion();

  useEffect(() => {
    fetchReleases().then((r) => { setReleases(r); setReleasesLoaded(true); }).catch(() => setReleasesLoaded(false));
  }, []);

  const canDownload = !!user && isPro;
  const shown = APPS.filter((a) => filter === "all" || (filter === "free" ? a.free : !a.free));

  return (
    <div className="min-h-screen relative overflow-hidden">
      <div aria-hidden className="pointer-events-none absolute inset-0 -z-10">
        <div className="absolute -top-40 left-1/2 -translate-x-1/2 h-[520px] w-[820px] rounded-full bg-primary/20 blur-3xl" />
      </div>
      <TopNav />
      <main className="container py-12 sm:py-16">
        <div className="text-center mb-10">
          <div className="inline-flex items-center gap-2 glass rounded-full px-4 py-1.5 mb-5">
            <Sparkles className="h-3.5 w-3.5 text-primary" />
            <span className="text-xs font-medium text-muted-foreground">Browser, Notch &amp; Erweiterung kostenlos · Code &amp; CLI mit Pro</span>
          </div>
          <h1 className="font-display text-4xl md:text-6xl font-bold mb-4">Mythos auf deinem PC</h1>
          <p className="text-muted-foreground max-w-2xl mx-auto">
            Ein Klick, Installer starten, fertig. Alles wird automatisch gebaut, du bekommst immer die neueste Version.
          </p>
          <div className="mt-6 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1.5"><RefreshCw className="h-3.5 w-3.5 text-primary" />Immer aktuell</span>
            <span className="inline-flex items-center gap-1.5"><ShieldCheck className="h-3.5 w-3.5 text-primary" />Kostenlose Apps ohne Anmeldung</span>
            <span className="inline-flex items-center gap-1.5"><Check className="h-3.5 w-3.5 text-primary" />Ein Login für alles</span>
          </div>
          {!loading && !canDownload && (
            <div className="mt-7 flex flex-col sm:flex-row gap-3 justify-center">
              <Button asChild className="bg-gradient-primary text-primary-foreground glow-primary h-11 px-8">
                {user
                  ? <Link to="/dashboard"><Crown className="h-4 w-4 mr-2" />Pro holen für Code &amp; CLI</Link>
                  : <Link to="/auth">Anmelden</Link>}
              </Button>
            </div>
          )}
        </div>

        <div className="flex justify-center mb-8">
          <div className="glass rounded-full p-1 inline-flex gap-1">
            {FILTERS.map((f) => (
              <button key={f.key} type="button" onClick={() => setFilter(f.key)}
                className={`px-4 py-1.5 rounded-full text-sm transition-colors ${filter === f.key ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground"}`}>
                {f.label}
              </button>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6 max-w-6xl mx-auto">
          {shown.map((app) => (
            <AppCard key={app.id} app={app} release={releases[app.tag]} releasesLoaded={releasesLoaded}
              canDownload={canDownload} onLocked={setLocked} extVersion={extVersion} />
          ))}
        </div>

        <p className="text-xs text-center text-muted-foreground mt-10 max-w-xl mx-auto">
          Windows zeigt beim ersten Start eventuell „Der Computer wurde durch Windows geschützt“. Dann auf „Weitere Informationen“ und „Trotzdem ausführen“ klicken.
        </p>
      </main>
      <UpgradeDialog
        open={!!locked}
        onOpenChange={(o) => !o && setLocked(null)}
        feature={locked?.name}
        reason={user ? "Mythos Code und die CLI gibt es mit Mythos Pro." : "Melde dich an und hol dir Pro, um Mythos Code und die CLI zu laden."}
      />
    </div>
  );
};

export default Downloads;
