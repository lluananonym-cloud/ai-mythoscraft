import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import TopNav from "@/components/TopNav";
import UpgradeDialog from "@/components/UpgradeDialog";
import { Button } from "@/components/ui/button";
import { Download, Lock, Crown, Check, Monitor, Clock, TerminalSquare } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useSubscription } from "@/hooks/useSubscription";
import { APPS, AppDownload, ReleaseInfo, downloadUrl, fetchReleases } from "@/lib/downloads";

const fmtSize = (b: number) => (b > 1024 * 1024 ? `${(b / 1024 / 1024).toFixed(0)} MB` : `${Math.round(b / 1024)} KB`);
const fmtDate = (s: string) => new Date(s).toLocaleDateString("de-DE", { day: "numeric", month: "short", year: "numeric" });

const AppCard = ({ app, release, releasesLoaded, canDownload, onLocked }: {
  app: AppDownload;
  release?: ReleaseInfo;
  releasesLoaded: boolean;
  canDownload: boolean;
  onLocked: (app: AppDownload) => void;
}) => {
  const asset = release?.assets.find((a) => (app.asset ? a.name === app.asset : true));
  // Solange die GitHub-API nicht geantwortet hat (oder blockiert ist), nehmen wir den festen Link.
  const available = releasesLoaded ? !!asset : !!app.asset;
  const href = asset?.url ?? downloadUrl(app);

  return (
    <div className="glass-strong rounded-3xl overflow-hidden flex flex-col">
      <div className="aspect-[16/10] bg-gradient-cosmic/20 border-b border-border/40 overflow-hidden flex items-center justify-center">
        {app.screenshot ? (
          <img src={app.screenshot} alt={`${app.name} Screenshot`} loading="lazy" className="w-full h-full object-cover object-top"
            onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = "none"; }} />
        ) : (
          app.id === "cli" ? <TerminalSquare className="h-16 w-16 text-primary/70" /> : <Monitor className="h-16 w-16 text-primary/70" />
        )}
      </div>
      <div className="p-6 flex flex-col flex-1">
        <div className="flex items-center justify-between gap-2 mb-1">
          <h3 className="font-display text-xl font-bold">{app.name}</h3>
          <div className="flex items-center gap-1.5">
            <span className={`text-[11px] rounded-full px-2 py-0.5 ${app.free ? "bg-emerald-500/15 text-emerald-400" : "bg-primary/15 text-primary"}`}>
              {app.free ? "Kostenlos" : "Pro"}
            </span>
            <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground glass rounded-full px-2 py-0.5">
              <Monitor className="h-3 w-3" />{app.platform}
            </span>
          </div>
        </div>
        <p className="text-sm text-primary mb-2">{app.tagline}</p>
        <p className="text-sm text-muted-foreground mb-4">{app.description}</p>
        <ul className="space-y-1.5 text-sm mb-6">
          {app.highlights.map((h) => (
            <li key={h} className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-primary shrink-0" />{h}</li>
          ))}
        </ul>
        <div className="mt-auto space-y-2">
          {!available ? (
            <Button disabled variant="outline" className="w-full h-11">
              <Clock className="h-4 w-4 mr-2" />Bald verfügbar
            </Button>
          ) : canDownload || app.free ? (
            <Button asChild className="w-full h-11 bg-gradient-primary text-primary-foreground hover:opacity-90">
              <a href={href} download>
                <Download className="h-4 w-4 mr-2" />Herunterladen
              </a>
            </Button>
          ) : (
            <Button onClick={() => onLocked(app)} variant="outline" className="w-full h-11">
              <Lock className="h-4 w-4 mr-2" />Herunterladen mit Pro
            </Button>
          )}
          {asset && release && (
            <p className="text-[11px] text-center text-muted-foreground">
              {asset.name} · {fmtSize(asset.size)} · aktualisiert {fmtDate(release.publishedAt)}
            </p>
          )}
        </div>
      </div>
    </div>
  );
};

const Downloads = () => {
  const { user } = useAuth();
  const { isPro, loading } = useSubscription();
  const [releases, setReleases] = useState<Record<string, ReleaseInfo>>({});
  const [releasesLoaded, setReleasesLoaded] = useState(false);
  const [locked, setLocked] = useState<AppDownload | null>(null);

  useEffect(() => {
    fetchReleases().then((r) => { setReleases(r); setReleasesLoaded(true); }).catch(() => setReleasesLoaded(false));
  }, []);

  const canDownload = !!user && isPro;

  return (
    <div className="min-h-screen">
      <TopNav />
      <main className="container py-12 sm:py-16">
        <div className="text-center mb-12">
          <div className="inline-flex items-center gap-2 glass rounded-full px-4 py-1.5 mb-5">
            <Crown className="h-3.5 w-3.5 text-primary" />
            <span className="text-xs font-medium text-muted-foreground">Browser &amp; Notch kostenlos · Code &amp; CLI mit Pro</span>
          </div>
          <h1 className="font-display text-4xl md:text-6xl font-bold mb-4">Mythos auf deinem PC</h1>
          <p className="text-muted-foreground max-w-2xl mx-auto">
            Ein Klick, Installer starten, fertig. Die Programme werden automatisch aktuell gehalten, du lädst immer die neueste Version.
          </p>
          {!loading && !canDownload && (
            <div className="mt-6 flex flex-col sm:flex-row gap-3 justify-center">
              {user ? (
                <Button asChild className="bg-gradient-primary text-primary-foreground glow-primary h-11 px-8">
                  <Link to="/dashboard"><Crown className="h-4 w-4 mr-2" />Auf Pro upgraden</Link>
                </Button>
              ) : (
                <Button asChild className="bg-gradient-primary text-primary-foreground glow-primary h-11 px-8">
                  <Link to="/auth">Anmelden</Link>
                </Button>
              )}
            </div>
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 max-w-5xl mx-auto">
          {APPS.map((app) => (
            <AppCard key={app.id} app={app} release={releases[app.tag]} releasesLoaded={releasesLoaded}
              canDownload={canDownload} onLocked={setLocked} />
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
        reason={user ? "Die Desktop-Programme gibt es mit Mythos Pro." : "Melde dich an und hol dir Pro, um die Desktop-Programme zu laden."}
      />
    </div>
  );
};

export default Downloads;
