// Mythos Cloud-PC: Dateisystem des virtuellen Rechners. Läuft komplett im Browser und wird im
// localStorage gespeichert, damit der PC nach dem Neuladen noch dieselben Dateien hat.

const KEY = "mythos.cloudpc.files.v1";
const MAX_FILE = 400_000;

/** Pfad normalisieren: immer absolut, ohne ".", ".." und doppelte Schrägstriche. */
export function norm(path: string, cwd = "/"): string {
  const raw = path.startsWith("/") ? path : `${cwd}/${path}`;
  const out: string[] = [];
  for (const p of raw.split("/")) {
    if (!p || p === ".") continue;
    if (p === "..") out.pop();
    else out.push(p);
  }
  return "/" + out.join("/");
}

export const dirname = (p: string) => norm(p).replace(/\/[^/]*$/, "") || "/";
export const basename = (p: string) => norm(p).split("/").pop() || "/";

export type Entry = { name: string; path: string; dir: boolean; size: number };

export class Vfs {
  private files = new Map<string, string>();
  private dirs = new Set<string>(["/"]);
  private listeners = new Set<() => void>();
  private saveTimer: ReturnType<typeof setTimeout> | null = null;
  version = 0;

  constructor(private persist = true) {
    if (persist) this.load();
  }

  subscribe = (fn: () => void) => {
    this.listeners.add(fn);
    return () => { this.listeners.delete(fn); };
  };
  getVersion = () => this.version;

  private changed() {
    this.version++;
    this.listeners.forEach((fn) => fn());
    if (!this.persist) return;
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => this.save(), 400);
  }

  isFile(p: string) { return this.files.has(norm(p)); }
  isDir(p: string) {
    const n = norm(p);
    if (this.dirs.has(n)) return true;
    const pre = n === "/" ? "/" : n + "/";
    for (const f of this.files.keys()) if (f.startsWith(pre)) return true;
    return false;
  }
  exists(p: string) { return this.isFile(p) || this.isDir(p); }

  read(p: string): string {
    const v = this.files.get(norm(p));
    if (v === undefined) throw new Error(`${p}: ${this.isDir(p) ? "ist ein Ordner" : "Datei nicht gefunden"}`);
    return v;
  }

  write(p: string, content: string) {
    const n = norm(p);
    if (n === "/" || this.isDir(n)) throw new Error(`${p}: ist ein Ordner`);
    if (content.length > MAX_FILE) throw new Error(`${p}: Datei zu groß (max. ${Math.round(MAX_FILE / 1000)} KB)`);
    this.mkdirQuiet(dirname(n));
    this.files.set(n, content);
    this.changed();
  }

  private mkdirQuiet(p: string) {
    let d = norm(p);
    while (d !== "/") {
      if (this.files.has(d)) throw new Error(`${d}: ist eine Datei`);
      this.dirs.add(d);
      d = dirname(d);
    }
  }
  mkdir(p: string) { this.mkdirQuiet(p); this.changed(); }

  remove(p: string) {
    const n = norm(p);
    if (n === "/") throw new Error("Der Hauptordner kann nicht gelöscht werden");
    if (!this.exists(n)) throw new Error(`${p}: nicht gefunden`);
    const pre = n + "/";
    this.files.delete(n);
    for (const f of [...this.files.keys()]) if (f.startsWith(pre)) this.files.delete(f);
    for (const d of [...this.dirs]) if (d === n || d.startsWith(pre)) this.dirs.delete(d);
    this.changed();
  }

  copy(from: string, to: string, move = false) {
    const a = norm(from);
    let b = norm(to);
    if (!this.exists(a)) throw new Error(`${from}: nicht gefunden`);
    if (this.isDir(b)) b = norm(basename(a), b);
    if (this.isFile(a)) {
      this.write(b, this.read(a));
    } else {
      if (b.startsWith(a + "/")) throw new Error("Ein Ordner kann nicht in sich selbst kopiert werden");
      this.mkdirQuiet(b);
      for (const [f, c] of [...this.files]) if (f.startsWith(a + "/")) this.write(b + f.slice(a.length), c);
      for (const d of [...this.dirs]) if (d.startsWith(a + "/")) this.mkdirQuiet(b + d.slice(a.length));
    }
    if (move) this.remove(a);
    else this.changed();
  }

  list(p: string): Entry[] {
    const n = norm(p);
    if (!this.isDir(n)) throw new Error(`${p}: kein Ordner`);
    const pre = n === "/" ? "/" : n + "/";
    const seen = new Map<string, Entry>();
    for (const [f, c] of this.files) {
      if (!f.startsWith(pre)) continue;
      const rest = f.slice(pre.length);
      const name = rest.split("/")[0];
      const dir = rest.includes("/");
      const prev = seen.get(name);
      seen.set(name, { name, path: pre + name, dir, size: (prev?.size || 0) + c.length });
    }
    for (const d of this.dirs) {
      if (d === n || !d.startsWith(pre)) continue;
      const name = d.slice(pre.length).split("/")[0];
      if (!seen.has(name)) seen.set(name, { name, path: pre + name, dir: true, size: 0 });
    }
    return [...seen.values()].sort((x, y) => (x.dir === y.dir ? x.name.localeCompare(y.name) : x.dir ? -1 : 1));
  }

  /** Alle Dateien, sortiert: [[pfad, inhalt], …] */
  all(): [string, string][] {
    return [...this.files].sort(([a], [b]) => a.localeCompare(b));
  }
  allDirs(): string[] { return [...this.dirs].sort(); }

  /** Dateien ersetzen (z. B. nach einem Node- oder Python-Lauf). */
  apply(changed: Record<string, string>) {
    for (const [p, c] of Object.entries(changed)) {
      const n = norm(p);
      this.mkdirQuiet(dirname(n));
      this.files.set(n, c.slice(0, MAX_FILE));
    }
    if (Object.keys(changed).length) this.changed();
  }

  reset(files: Record<string, string> = {}) {
    this.files.clear();
    this.dirs = new Set(["/"]);
    for (const [p, c] of Object.entries(files)) { this.mkdirQuiet(dirname(p)); this.files.set(norm(p), c); }
    this.changed();
  }

  private load() {
    try {
      const raw = localStorage.getItem(KEY);
      if (!raw) return;
      const j = JSON.parse(raw) as { files?: Record<string, string>; dirs?: string[] };
      for (const [p, c] of Object.entries(j.files || {})) this.files.set(norm(p), String(c));
      for (const d of j.dirs || []) this.dirs.add(norm(d));
    } catch { /* kaputter Speicher: leerer PC */ }
  }

  save() {
    try {
      localStorage.setItem(KEY, JSON.stringify({ files: Object.fromEntries(this.files), dirs: [...this.dirs] }));
    } catch { /* Speicher voll oder gesperrt: Dateien bleiben nur in dieser Sitzung */ }
  }
}

export function formatSize(n: number) {
  return n < 1000 ? `${n} B` : n < 1_000_000 ? `${(n / 1000).toFixed(1).replace(".", ",")} KB` : `${(n / 1_000_000).toFixed(1).replace(".", ",")} MB`;
}
