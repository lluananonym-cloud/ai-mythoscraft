// Bausteine für den Chat im Stil von Lovable: aufklappbarer Denk-Block, Schritt-Zeitleiste,
// Werkzeug-Zeilen, Tages-Trenner, relative Zeit und die Vorschlags-Leiste über dem Eingabefeld.
import { useEffect, useState, type ReactNode } from "react";
import { ChevronDown, FileText, Image as ImageIcon, Music, Globe, RefreshCw, SquareTerminal, Wrench, X } from "lucide-react";

/** "gerade eben", "vor 5 Minuten", "vor 13 Stunden", "vor 2 Tagen". */
export function relTime(iso?: string, now = Date.now()): string {
  if (!iso) return "gerade eben";
  const s = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  if (s < 60) return "gerade eben";
  const m = Math.round(s / 60);
  if (m < 60) return m === 1 ? "vor 1 Minute" : `vor ${m} Minuten`;
  const h = Math.round(m / 60);
  if (h < 24) return h === 1 ? "vor 1 Stunde" : `vor ${h} Stunden`;
  const d = Math.round(h / 24);
  return d === 1 ? "vor 1 Tag" : `vor ${d} Tagen`;
}

/** Tages-Schlüssel für die Trenner zwischen Nachrichten. */
export function dayKey(iso?: string): string {
  const d = iso ? new Date(iso) : new Date();
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

export function dayLabel(iso?: string): string {
  const d = iso ? new Date(iso) : new Date();
  const today = new Date();
  const y = new Date(); y.setDate(today.getDate() - 1);
  if (dayKey(d.toISOString()) === dayKey(today.toISOString())) return "Heute";
  if (dayKey(d.toISOString()) === dayKey(y.toISOString())) return "Gestern";
  return d.toLocaleDateString("de-AT", { day: "numeric", month: "long", year: d.getFullYear() === today.getFullYear() ? undefined : "numeric" });
}

export function DayDivider({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-4 py-2 text-xs text-muted-foreground">
      <div className="h-px flex-1 bg-white/10" />
      <span>{label}</span>
      <div className="h-px flex-1 bg-white/10" />
    </div>
  );
}

/** Aktualisiert relative Zeitangaben einmal pro Minute. */
export function useMinuteTick() {
  const [, set] = useState(0);
  useEffect(() => { const t = setInterval(() => set((x) => x + 1), 60_000); return () => clearInterval(t); }, []);
}

function thinkLabel(ms?: number, live?: boolean): string {
  if (live) return "Denkt nach…";
  if (!ms || ms < 1000) return "Nachgedacht für weniger als eine Sekunde";
  const s = Math.round(ms / 1000);
  if (s < 60) return `Nachgedacht für ${s} ${s === 1 ? "Sekunde" : "Sekunden"}`;
  const m = Math.round(s / 60);
  return `Nachgedacht für ${m} ${m === 1 ? "Minute" : "Minuten"}`;
}

/** Denk-Block wie bei Lovable: Zeile mit Pfeil, aufgeklappt mit grauer Linie links. */
export function ThinkingBlock({ text, ms, live }: { text: string; ms?: number; live?: boolean }) {
  const [open, setOpen] = useState(!!live);
  useEffect(() => { if (!live) setOpen(false); }, [live]);
  return (
    <div className="not-prose mb-3">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-2 text-[14px] text-muted-foreground hover:text-foreground/80 transition-colors"
      >
        <ChevronDown className={`h-4 w-4 transition-transform ${open ? "" : "-rotate-90"}`} />
        <span className={live ? "mythos-shimmer" : ""}>{thinkLabel(ms, live)}</span>
      </button>
      {open && (
        <div className="mt-2 ml-2 max-h-80 overflow-y-auto border-l-2 border-white/15 pl-4 text-[14px] leading-relaxed text-foreground/70 whitespace-pre-wrap">
          {text}
        </div>
      )}
    </div>
  );
}

function stepIcon(label: string) {
  if (/bild|image|foto/i.test(label)) return ImageIcon;
  if (/song|musik|music/i.test(label)) return Music;
  if (/such|search|web|internet/i.test(label)) return Globe;
  if (/lies|read|datei|file/i.test(label)) return FileText;
  if (/prüf|test|befehl|command|run/i.test(label)) return SquareTerminal;
  return Wrench;
}

/** Eine Zeile in der Zeitleiste (Symbol links, Text, optional aufklappbarer Inhalt). */
export function TimelineRow({ label, detail, children }: { label: ReactNode; detail?: string; children?: ReactNode }) {
  const [open, setOpen] = useState(false);
  const Icon = stepIcon(typeof label === "string" ? label : detail ?? "");
  const expandable = !!children;
  return (
    <div className="relative pl-8">
      <span className="absolute left-[9px] top-0 bottom-0 w-px bg-white/10" aria-hidden />
      <span className="absolute left-0 top-1.5 flex h-5 w-5 items-center justify-center rounded-md bg-background text-muted-foreground">
        <Icon className="h-4 w-4" />
      </span>
      <button
        type="button"
        disabled={!expandable}
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-start gap-2 py-1.5 text-left text-[14px] text-foreground/90 disabled:cursor-default"
      >
        <span className="flex-1 min-w-0">
          {label}
          {detail && <span className="ml-2 rounded-md bg-white/[0.06] px-1.5 py-0.5 font-mono text-[12px] text-muted-foreground">{detail}</span>}
        </span>
        {expandable && <ChevronDown className={`mt-0.5 h-4 w-4 shrink-0 text-muted-foreground transition-transform ${open ? "rotate-180" : ""}`} />}
      </button>
      {open && children && <div className="mb-2 rounded-xl border border-white/10 bg-white/[0.03] p-3 text-xs">{children}</div>}
    </div>
  );
}

/** Schritte, die der Server während einer Antwort meldet (Bild erzeugen, Suche …). */
export function StepsTimeline({ steps }: { steps: string[] }) {
  if (!steps.length) return null;
  return (
    <div className="not-prose mb-3">
      {steps.map((s, i) => <TimelineRow key={i} label={s} />)}
    </div>
  );
}

/** Vorschläge als waagrecht scrollbare Chips über dem Eingabefeld, mit Neu laden und Schließen. */
export function SuggestionBar({ items, onPick, onRefresh, onClose, refreshing }: {
  items: string[];
  onPick: (s: string) => void;
  onRefresh?: () => void;
  onClose: () => void;
  refreshing?: boolean;
}) {
  if (!items.length) return null;
  return (
    <div className="mb-2 flex items-center gap-2 animate-fade-in">
      <div className="relative min-w-0 flex-1">
        <div className="flex gap-2 overflow-x-auto pb-0.5 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          {items.map((s) => (
            <button
              key={s}
              onClick={() => onPick(s)}
              className="shrink-0 rounded-full border border-white/15 bg-white/[0.03] px-4 py-2 text-[14px] text-foreground/90 hover:bg-white/[0.08] transition-colors"
            >
              {s}
            </button>
          ))}
        </div>
        <div className="pointer-events-none absolute inset-y-0 right-0 w-10 bg-gradient-to-l from-background to-transparent" />
      </div>
      {onRefresh && (
        <button onClick={onRefresh} aria-label="Neue Vorschläge" className="shrink-0 rounded-full p-2 text-muted-foreground hover:bg-white/5 hover:text-foreground">
          <RefreshCw className={`h-4 w-4 ${refreshing ? "animate-spin" : ""}`} />
        </button>
      )}
      <button onClick={onClose} aria-label="Vorschläge ausblenden" className="shrink-0 rounded-full p-2 text-muted-foreground hover:bg-white/5 hover:text-foreground">
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
