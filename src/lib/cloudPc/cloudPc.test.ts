import { describe, expect, it, vi } from "vitest";
vi.mock("@/lib/labs", () => ({ askAI: vi.fn() }));
import { Vfs, norm } from "./vfs";
import { Shell, tokenize } from "./shell";
import { parseReply } from "./agent";
import { buildPage, makeZip } from "./project";

const setup = () => {
  const fs = new Vfs(false);
  const opened: string[] = [];
  const sh = new Shell(fs, { open: (t) => opened.push(t), clear: () => {} });
  const run = async (line: string) => { let o = ""; const r = await sh.exec(line, (t) => { o += t; }); return { ...r, o }; };
  return { fs, sh, run, opened };
};

describe("Cloud-PC Dateisystem", () => {
  it("normalisiert Pfade", () => {
    expect(norm("a/../b/./c", "/x")).toBe("/x/b/c");
    expect(norm("/../..")).toBe("/");
  });
  it("legt Ordner an, kopiert und löscht", () => {
    const fs = new Vfs(false);
    fs.write("/web/index.html", "<h1>Hi</h1>");
    expect(fs.isDir("/web")).toBe(true);
    fs.copy("/web", "/kopie");
    expect(fs.read("/kopie/index.html")).toBe("<h1>Hi</h1>");
    fs.remove("/web");
    expect(fs.exists("/web/index.html")).toBe(false);
    expect(fs.list("/").map((e) => e.name)).toEqual(["kopie"]);
  });
});

describe("Cloud-PC Terminal", () => {
  it("zerlegt Befehle mit Anführungszeichen und Umleitung", () => {
    expect(tokenize(`echo "hallo welt">a.txt`)).toEqual(["echo", "hallo welt", ">", "a.txt"]);
  });
  it("schreibt per Umleitung, verkettet mit && und stoppt bei Fehlern", async () => {
    const { fs, run } = setup();
    expect((await run(`mkdir projekt && cd projekt && echo "a" > x.txt && echo b >> x.txt`)).code).toBe(0);
    expect(fs.read("/projekt/x.txt")).toBe("a\nb\n");
    const r = await run("cat fehlt.txt && echo nie");
    expect(r.code).toBe(1);
    expect(r.o).not.toContain("nie");
  });
  it("zeigt Baum, sucht Text und öffnet Dateien im Browser", async () => {
    const { fs, run, opened } = setup();
    fs.write("/index.html", "<p>Mondbohne</p>");
    expect((await run("tree")).o).toContain("index.html");
    expect((await run("grep Mondbohne")).o).toContain("/index.html:1");
    await run("open index.html");
    expect(opened).toEqual(["/index.html"]);
    expect((await run("gibtsnicht")).code).toBe(127);
  });
});

describe("Cloud-PC Agent", () => {
  it("liest Plan und Aktionen", () => {
    const r = parseReply(`<plan>Ich baue die Seite.</plan>
<aktion typ="schreiben" pfad="/index.html">
\`\`\`html
<h1>Hallo</h1>
\`\`\`
</aktion>
<aktion typ="befehl">ls</aktion>
<aktion typ="öffnen">/index.html</aktion>`);
    expect(r.plan).toBe("Ich baue die Seite.");
    expect(r.actions).toEqual([
      { type: "write", path: "/index.html", content: "<h1>Hallo</h1>", cut: false },
      { type: "run", cmd: "ls" },
      { type: "open", target: "/index.html" },
    ]);
  });
  it("erkennt abgeschnittene Dateien", () => {
    const r = parseReply(`<aktion typ="schreiben" pfad="/a.js">console.log(1`);
    expect(r.actions[0]).toMatchObject({ type: "write", cut: true });
  });
});

describe("Cloud-PC Projekt", () => {
  it("setzt CSS und JS in die Seite ein", () => {
    const fs = new Vfs(false);
    fs.write("/index.html", `<html><head><link rel="stylesheet" href="style.css"></head><body><script src="./app.js"></script></body></html>`);
    fs.write("/style.css", "body{color:red}");
    fs.write("/app.js", "console.log(1)");
    const html = buildPage(fs, "/index.html");
    expect(html).toContain("body{color:red}");
    expect(html).toContain("console.log(1)");
    expect(html).not.toContain('src="./app.js"');
  });
  it("packt ein ZIP mit der richtigen Größe", () => {
    const zip = makeZip([["/a.txt", "hallo"]]);
    expect(zip.type).toBe("application/zip");
    expect(zip.size).toBe(30 + 5 + 5 + 46 + 5 + 22);
  });
});
