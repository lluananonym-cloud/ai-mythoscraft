import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Play, Trash2 } from "lucide-react";
import { deleteMyApp, listMyApps, type MyApp } from "@/lib/labs";

type Props = { source?: MyApp["source"]; refresh?: number; onOpen: (a: MyApp) => void; title?: string };

/** Liste der selbst gebauten Apps (in diesem Browser gespeichert). */
export default function MyApps({ source, refresh, onOpen, title = "Deine Apps" }: Props) {
  const [apps, setApps] = useState<MyApp[]>([]);
  useEffect(() => { setApps(listMyApps().filter((a) => !source || a.source === source)); }, [source, refresh]);
  if (!apps.length) return null;
  return (
    <section>
      <h2 className="font-display text-lg mb-3">{title} ({apps.length})</h2>
      <div className="grid gap-2 sm:grid-cols-2">
        {apps.map((a) => (
          <div key={a.id} className="glass rounded-xl p-3 flex items-center gap-2 min-w-0">
            <div className="flex-1 min-w-0">
              <div className="font-medium truncate">{a.title}</div>
              <div className="text-[11px] text-muted-foreground">{new Date(a.created_at).toLocaleString("de-DE", { dateStyle: "short", timeStyle: "short" })}</div>
            </div>
            <Button size="sm" className="h-10 shrink-0" onClick={() => onOpen(a)}><Play className="h-4 w-4 mr-1" />Öffnen</Button>
            <Button size="icon" variant="ghost" className="h-10 w-10 shrink-0 hover:text-destructive" aria-label="Löschen"
              onClick={() => { if (confirm(`„${a.title}“ löschen?`)) { deleteMyApp(a.id); setApps((x) => x.filter((y) => y.id !== a.id)); } }}>
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        ))}
      </div>
    </section>
  );
}
