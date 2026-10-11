import { describe, expect, it } from "vitest";
import TOOL_PARSE from "./toolparse.js?raw";

// toolparse.js wird roh in CLI und App eingefügt – hier genauso auswerten.
const lib = new Function(TOOL_PARSE + "; return { looksLikeServer, verifyTracker, toolProblem };")();

describe("looksLikeServer", () => {
  it("erkennt Befehle, die nicht von selbst enden", () => {
    for (const c of ["npm run dev", "npx vite --port 3000", "node server.js", "python -m http.server 8000", "npm install && npm run dev", "npx serve dist", "tsc --watch"])
      expect(lib.looksLikeServer(c), c).toBe(true);
  });
  it("lässt normale Befehle mit run laufen", () => {
    for (const c of ["npm run build", "vite build", "npm test", "vitest", "next build", "node script.js", "grep -w foo bar.txt"])
      expect(lib.looksLikeServer(c), c).toBe(false);
  });
});

describe("verifyTracker", () => {
  it("verlangt nach Änderungen erst verify, dann check bei Webseiten", () => {
    const t = lib.verifyTracker();
    expect(t.nudge()).toBe("");
    t.note({ name: "write", path: "index.html" }, "OK geschrieben: index.html");
    expect(t.nudge()).toMatch(/verify/);
    expect(t.nudge()).toMatch(/check/);
    expect(t.nudge()).toBe("");
  });
  it("ist zufrieden, wenn geprüft wurde", () => {
    const t = lib.verifyTracker();
    t.note({ name: "edit", path: "src/App.tsx" }, "OK geändert");
    t.note({ name: "verify" }, "ERGEBNIS: ✓ alle Prüfungen bestanden.");
    t.note({ name: "check" }, "ERGEBNIS: ✓ Seite lädt ohne Fehler und zeigt Inhalt.");
    expect(t.nudge()).toBe("");
  });
  it("zählt einen fehlgeschlagenen check nicht als geprüft", () => {
    const t = lib.verifyTracker();
    t.note({ name: "write", path: "app.js" }, "OK geschrieben");
    t.note({ name: "verify" }, "ERGEBNIS: ✓");
    t.note({ name: "check" }, "ERGEBNIS: ✗ Probleme gefunden");
    expect(t.nudge()).toMatch(/check/);
  });
});

describe("toolProblem", () => {
  it("kennt die neuen Prüf-Werkzeuge", () => {
    for (const name of ["start", "logs", "stop", "serve", "check", "verify"]) expect(lib.toolProblem({ name })).toBe("");
  });
});
