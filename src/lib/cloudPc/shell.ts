// Mythos Cloud-PC: das Terminal. Ein kleiner Befehls-Interpreter über dem Dateisystem (vfs.ts);
// Programme laufen über runtimes.ts. Mensch und Agent benutzen dasselbe Terminal.
import { Vfs, norm, basename, formatSize } from "./vfs";
import { readWeb, runNode, runPython, searchWeb, type Out } from "./runtimes";

export type ShellHooks = {
  /** Datei oder Webseite im Browser-Fenster des PCs öffnen. */
  open: (target: string) => void;
  clear: () => void;
};

export const HELP = `Befehle auf dem Mythos Cloud-PC:
  ls [ordner]            Dateien anzeigen          cd <ordner>        Ordner wechseln
  cat <datei>            Datei anzeigen            tree               alle Dateien als Baum
  mkdir <ordner>         Ordner anlegen            touch <datei>      leere Datei anlegen
  rm [-r] <pfad>         löschen                   mv / cp <von> <nach>
  echo <text> > datei    in Datei schreiben        grep <text> [pfad] Text in Dateien suchen
  head / wc <datei>      Anfang / Zeilen zählen    pwd, clear, date, neofetch
  node <datei.js>        JavaScript ausführen      python <datei.py>  Python 3 ausführen
  python -c "<code>"     Python-Zeile ausführen    open <datei|url>   im Browser des PCs öffnen
  web <url>              Webseite als Text lesen   suche <begriffe>   im Internet suchen
Mehrere Befehle mit && verbinden. Strg+C bricht ein laufendes Programm ab.`;

/** Befehlszeile in Wörter zerlegen (mit "…" und '…'). */
export function tokenize(line: string): string[] {
  const out: string[] = [];
  let cur = "", q: string | null = null, any = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (q) {
      if (c === q) q = null;
      else if (c === "\\" && q === '"' && i + 1 < line.length) cur += line[++i];
      else cur += c;
    } else if (c === '"' || c === "'") { q = c; any = true; }
    else if (/\s/.test(c)) { if (cur || any) out.push(cur); cur = ""; any = false; }
    else if (c === ">" ) {
      if (cur || any) out.push(cur);
      cur = ""; any = false;
      if (line[i + 1] === ">") { out.push(">>"); i++; } else out.push(">");
    } else cur += c;
  }
  if (cur || any) out.push(cur);
  return out;
}

/** "a && b" trennen, aber nicht innerhalb von Anführungszeichen. */
function splitAnd(line: string): string[] {
  const parts: string[] = [];
  let cur = "", q: string | null = null;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (q) { if (c === q) q = null; cur += c; continue; }
    if (c === '"' || c === "'") { q = c; cur += c; continue; }
    if (c === "&" && line[i + 1] === "&") { parts.push(cur); cur = ""; i++; continue; }
    cur += c;
  }
  parts.push(cur);
  return parts.map((p) => p.trim()).filter(Boolean);
}

export class Shell {
  cwd = "/";
  history: string[] = [];

  constructor(public fs: Vfs, private hooks: ShellHooks) {}

  prompt() { return `mythos@cloud-pc:${this.cwd === "/" ? "~" : "~" + this.cwd}$`; }

  private p(path: string) { return norm(path, this.cwd); }

  /** Eine ganze Zeile ausführen. Liefert Exit-Code und gesammelte Ausgabe (für den Agenten). */
  async exec(line: string, out: Out): Promise<{ code: number; output: string }> {
    let output = "";
    const tee: Out = (t, k) => { output += t; out(t, k); };
    const trimmed = line.trim();
    if (!trimmed) return { code: 0, output };
    this.history.push(trimmed);
    if (this.history.length > 200) this.history.shift();
    let code = 0;
    for (const part of splitAnd(trimmed)) {
      code = await this.one(part, tee);
      if (code !== 0) break;
    }
    return { code, output };
  }

  private async one(line: string, out: Out): Promise<number> {
    let words = tokenize(line);
    // Umleitung: befehl > datei / >> datei
    let redirect: { path: string; append: boolean } | null = null;
    const ri = words.findIndex((w) => w === ">" || w === ">>");
    if (ri >= 0) {
      if (!words[ri + 1]) { out("Umleitung ohne Dateiname\n", "err"); return 2; }
      redirect = { path: this.p(words[ri + 1]), append: words[ri] === ">>" };
      words = words.slice(0, ri);
    }
    let captured = "";
    const o: Out = redirect ? (t, k) => { if (k === "err" || k === "info") out(t, k); else captured += t; } : out;
    let code: number;
    try {
      code = await this.run(words, o, line);
    } catch (e) {
      out(`${(e as Error).message}\n`, "err");
      code = 1;
    }
    if (redirect) {
      try {
        const prev = redirect.append && this.fs.isFile(redirect.path) ? this.fs.read(redirect.path) : "";
        this.fs.write(redirect.path, prev + captured);
      } catch (e) { out(`${(e as Error).message}\n`, "err"); return 1; }
    }
    return code;
  }

  private snapshot() { return Object.fromEntries(this.fs.all()); }

  private async run(w: string[], out: Out, line: string): Promise<number> {
    const [cmd, ...a] = w;
    const flags = a.filter((x) => /^-\w/.test(x));
    const args = a.filter((x) => !/^-\w/.test(x));
    switch (cmd) {
      case "help": case "hilfe": out(HELP + "\n"); return 0;
      case "pwd": out(this.cwd + "\n"); return 0;
      case "clear": case "cls": this.hooks.clear(); return 0;
      case "date": out(new Date().toLocaleString("de-DE") + "\n"); return 0;
      case "whoami": out("mythos\n"); return 0;
      case "uname": out("MythosOS 1.0 (läuft in deinem Browser)\n"); return 0;
      case "neofetch": {
        const files = this.fs.all();
        out(`   /\\     mythos@cloud-pc\n  /  \\    OS: MythosOS 1.0\n / /\\ \\   Rechner: dein Browser (WebAssembly)\n/_/  \\_\\  Dateien: ${files.length} (${formatSize(files.reduce((s, [, c]) => s + c.length, 0))})\n          Sprachen: JavaScript, Python 3\n          Kosten: 0 €\n`);
        return 0;
      }
      case "echo": out(args.join(" ") + "\n"); return 0;
      case "cd": {
        const t = this.p(args[0] || "/");
        if (!this.fs.isDir(t)) { out(`cd: ${args[0]}: kein Ordner\n`, "err"); return 1; }
        this.cwd = t;
        return 0;
      }
      case "ls": case "dir": {
        const t = this.p(args[0] || ".");
        if (this.fs.isFile(t)) { out(basename(t) + "\n"); return 0; }
        const list = this.fs.list(t);
        if (!list.length) return 0;
        out(flags.some((f) => f.includes("l"))
          ? list.map((e) => `${e.dir ? "d" : "-"}  ${formatSize(e.size).padStart(9)}  ${e.name}${e.dir ? "/" : ""}`).join("\n") + "\n"
          : list.map((e) => e.name + (e.dir ? "/" : "")).join("  ") + "\n");
        return 0;
      }
      case "tree": {
        const root = this.p(args[0] || ".");
        const lines: string[] = [root === "/" ? "~" : root];
        const walk = (d: string, pre: string) => {
          const list = this.fs.list(d);
          list.forEach((e, i) => {
            const last = i === list.length - 1;
            lines.push(`${pre}${last ? "└── " : "├── "}${e.name}${e.dir ? "/" : ""}`);
            if (e.dir) walk(e.path, pre + (last ? "    " : "│   "));
          });
        };
        walk(root, "");
        out(lines.join("\n") + "\n");
        return 0;
      }
      case "cat": case "type": {
        if (!args.length) { out("cat: Datei fehlt\n", "err"); return 1; }
        for (const f of args) { const c = this.fs.read(this.p(f)); out(c.endsWith("\n") ? c : c + "\n"); }
        return 0;
      }
      case "head": case "tail": {
        const n = Number((flags.find((f) => /^-n?\d+$/.test(f)) || "-10").replace(/\D/g, "")) || 10;
        const lines = this.fs.read(this.p(args[0] || "")).split("\n");
        out((cmd === "head" ? lines.slice(0, n) : lines.slice(-n)).join("\n") + "\n");
        return 0;
      }
      case "wc": {
        const c = this.fs.read(this.p(args[0] || ""));
        out(`${c.split("\n").length} Zeilen  ${c.split(/\s+/).filter(Boolean).length} Wörter  ${c.length} Zeichen  ${args[0]}\n`);
        return 0;
      }
      case "grep": {
        const [needle, where = "."] = args;
        if (!needle) { out("grep: Suchtext fehlt\n", "err"); return 1; }
        const base = this.p(where);
        const ci = flags.some((f) => f.includes("i"));
        const hit = (l: string) => (ci ? l.toLowerCase().includes(needle.toLowerCase()) : l.includes(needle));
        let found = 0;
        for (const [f, c] of this.fs.all()) {
          if (f !== base && !f.startsWith(base === "/" ? "/" : base + "/")) continue;
          c.split("\n").forEach((l, i) => { if (hit(l) && found < 200) { found++; out(`${f}:${i + 1}: ${l.slice(0, 200)}\n`); } });
        }
        return found ? 0 : 1;
      }
      case "mkdir": if (!args.length) { out("mkdir: Name fehlt\n", "err"); return 1; } args.forEach((d) => this.fs.mkdir(this.p(d))); return 0;
      case "touch": args.forEach((f) => { const p = this.p(f); if (!this.fs.isFile(p)) this.fs.write(p, ""); }); return 0;
      case "rm": case "del": case "rmdir": {
        for (const f of args) {
          const p = this.p(f);
          if (this.fs.isDir(p) && cmd === "rm" && !flags.some((x) => x.includes("r"))) { out(`rm: ${f} ist ein Ordner (nimm rm -r)\n`, "err"); return 1; }
          this.fs.remove(p);
        }
        return 0;
      }
      case "mv": case "cp": case "move": case "copy": {
        if (args.length < 2) { out(`${cmd}: Quelle und Ziel angeben\n`, "err"); return 1; }
        this.fs.copy(this.p(args[0]), this.p(args[1]), cmd === "mv" || cmd === "move");
        return 0;
      }
      case "node": {
        if (!args[0]) { out("Interaktives node gibt es hier nicht. Nutze: node datei.js\n", "err"); return 1; }
        if (flags.includes("-e")) {
          const tmp = "/.mythos-eval.js";
          const files = { ...this.snapshot(), [tmp]: args.join(" ") };
          const r = await runNode(files, tmp, this.cwd, [], out);
          this.fs.apply(r.changed);
          return r.code;
        }
        const entry = this.p(args[0]);
        if (!this.fs.isFile(entry)) { out(`node: ${args[0]} nicht gefunden\n`, "err"); return 1; }
        const r = await runNode(this.snapshot(), entry, this.cwd, args.slice(1), out);
        this.fs.apply(r.changed);
        return r.code;
      }
      case "python": case "python3": case "py": {
        if (flags.includes("-c")) {
          const code = line.replace(/^\s*\S+\s+-c\s+/, "").replace(/^(["'])([\s\S]*)\1$/, "$2");
          const r = await runPython(this.snapshot(), null, this.cwd, out, code);
          this.fs.apply(r.changed);
          return r.code;
        }
        if (!args[0]) { out("Interaktives Python gibt es hier nicht. Nutze: python datei.py oder python -c \"print(1)\"\n", "err"); return 1; }
        const entry = this.p(args[0]);
        if (!this.fs.isFile(entry)) { out(`python: ${args[0]} nicht gefunden\n`, "err"); return 1; }
        const r = await runPython(this.snapshot(), entry, this.cwd, out);
        this.fs.apply(r.changed);
        return r.code;
      }
      case "pip": case "npm": case "npx": case "yarn": case "apt": case "apt-get": case "sudo":
        out(cmd === "pip"
          ? "pip ist nicht nötig: Pakete wie numpy, pandas oder matplotlib lädt python automatisch beim import.\n"
          : `${cmd} gibt es auf dem Cloud-PC nicht. Für Websites Bibliotheken per <script src="https://cdn.jsdelivr.net/…"> einbinden.\n`, "err");
        return cmd === "pip" ? 0 : 1;
      case "open": case "start": case "browser": {
        const t = args[0] || "/index.html";
        const target = /^https?:\/\/|^www\./i.test(t) ? t : this.p(t);
        if (!/^https?:|^www\./i.test(target) && !this.fs.isFile(target)) { out(`open: ${t} nicht gefunden\n`, "err"); return 1; }
        this.hooks.open(target);
        out(`Im Browser geöffnet: ${target}\n`, "info");
        return 0;
      }
      case "web": case "curl": case "wget": case "fetch": case "lies": {
        const url = args[0];
        if (!url) { out(`${cmd}: Adresse fehlt\n`, "err"); return 1; }
        out(`Lade ${url} …\n`, "info");
        const text = await readWeb(url);
        out(text.length > 12000 ? text.slice(0, 12000) + "\n… (gekürzt)\n" : text + "\n");
        return 0;
      }
      case "suche": case "search": case "google": {
        const q = args.join(" ");
        if (!q) { out("suche: Suchbegriff fehlt\n", "err"); return 1; }
        out(`Suche nach „${q}“ …\n`, "info");
        const hits = await searchWeb(q);
        if (!hits.length) { out("Keine Treffer.\n"); return 1; }
        out(hits.map((h, i) => `${i + 1}. ${h.title}\n   ${h.url}\n   ${h.snippet}`).join("\n") + "\n");
        return 0;
      }
      default:
        if (/\.(js|mjs|cjs)$/.test(cmd) && this.fs.isFile(this.p(cmd))) return this.run(["node", ...w], out, line);
        if (/\.py$/.test(cmd) && this.fs.isFile(this.p(cmd))) return this.run(["python", ...w], out, line);
        out(`${cmd}: Befehl nicht gefunden. „help“ zeigt alle Befehle.\n`, "err");
        return 127;
    }
  }
}
