import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { ExternalLink, Store, Download } from "lucide-react";
import { toast } from "sonner";
import { openHtmlInTab, safeHtml } from "@/lib/labs";
import PublishDialog from "./PublishDialog";

type Props = { app: { title: string; html: string } | null; onClose: () => void; canPublish?: boolean };

/** Vollbild-Vorschau einer gebauten App. Mobil füllt sie den ganzen Bildschirm. */
export default function AppPreview({ app, onClose, canPublish = true }: Props) {
  const [publish, setPublish] = useState(false);
  const download = () => {
    if (!app) return;
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob([app.html], { type: "text/html" }));
    a.download = (app.title.replace(/[^\w-]+/g, "-").toLowerCase() || "app") + ".html";
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 5000);
  };
  return (
    <>
      <Dialog open={!!app} onOpenChange={(o) => !o && onClose()}>
        <DialogContent className="glass-strong p-0 gap-0 overflow-hidden flex flex-col w-screen max-w-none h-[100dvh] rounded-none sm:w-[95vw] sm:max-w-5xl sm:h-[88vh] sm:rounded-2xl">
          {/* rechts Platz für das Schließen-X des Dialogs lassen */}
          <div className="flex flex-wrap items-center gap-2 border-b border-white/10 py-2.5 pl-4 pr-12 pt-[max(env(safe-area-inset-top),0.625rem)]">
            <DialogTitle className="text-base truncate min-w-0 flex-1">{app?.title}</DialogTitle>
            <DialogDescription className="sr-only">Vorschau der App</DialogDescription>
            <div className="flex gap-1.5">
              <Button size="sm" variant="outline" className="h-9" onClick={() => { try { if (app) openHtmlInTab(app.html, app.title); } catch (e) { toast.error((e as Error).message); } }}>
                <ExternalLink className="h-4 w-4 sm:mr-1.5" /><span className="hidden sm:inline">Neuer Tab</span>
              </Button>
              <Button size="sm" variant="outline" className="h-9" onClick={download} aria-label="Herunterladen">
                <Download className="h-4 w-4 sm:mr-1.5" /><span className="hidden sm:inline">Download</span>
              </Button>
              {canPublish && (
                <Button size="sm" className="h-9 bg-gradient-to-r from-fuchsia-500 to-violet-600 text-white" onClick={() => setPublish(true)}>
                  <Store className="h-4 w-4 sm:mr-1.5" /><span className="hidden sm:inline">Teilen</span>
                </Button>
              )}
            </div>
          </div>
          {app && (
            <iframe title={app.title} srcDoc={safeHtml(app.html)} sandbox="allow-scripts allow-forms allow-modals allow-pointer-lock"
              className="w-full flex-1 bg-white" />
          )}
        </DialogContent>
      </Dialog>
      {app && <PublishDialog open={publish} onOpenChange={setPublish} item={{ type: "app", title: app.title, html: app.html }} />}
    </>
  );
}
