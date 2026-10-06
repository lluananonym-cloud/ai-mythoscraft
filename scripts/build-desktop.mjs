// Baut die Quellordner für Mythos Code App (Electron) und Mythos CLI aus src/lib –
// genau so, wie buildAppZip()/buildCliZip() in src/lib/mythosCli.ts die ZIPs packen.
// GitHub Actions (.github/workflows/build-apps.yml) macht daraus Setup-EXE und npm-Paket.
//
//   node scripts/build-desktop.mjs [ausgabeordner]   (Standard: build/desktop)
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const out = path.resolve(process.argv[2] || path.join(root, "build/desktop"));
const lib = (p) => fs.readFileSync(path.join(root, "src/lib", p), "utf8");

const SITE = process.env.MYTHOS_SITE_URL || "https://ai-mythos.lovable.app";
const env = fs.existsSync(path.join(root, ".env")) ? fs.readFileSync(path.join(root, ".env"), "utf8") : "";
const PROJECT_ID = process.env.VITE_SUPABASE_PROJECT_ID || env.match(/VITE_SUPABASE_PROJECT_ID="?([\w-]+)"?/)?.[1];
if (!PROJECT_ID) throw new Error("VITE_SUPABASE_PROJECT_ID fehlt (.env)");
const FN_BASE = `https://${PROJECT_ID}.supabase.co/functions/v1`;
const API = `${FN_BASE}/v1-messages`;

const cliTs = lib("mythosCli.ts");
const constant = (name) => cliTs.match(new RegExp(`export const ${name} = "([^"]+)"`))?.[1];
const APP_VERSION = constant("APP_VERSION");
const CLI_VERSION = constant("CLI_VERSION");
const CLI_PKG = constant("CLI_PKG");
const APP_PKG = constant("APP_PKG");
if (!APP_VERSION || !CLI_VERSION || !CLI_PKG || !APP_PKG) throw new Error("Versionen in src/lib/mythosCli.ts nicht gefunden");

// Wie fill() in mythosCli.ts: Platzhalter ersetzen, Zeilenenden auf LF.
const fill = (src, vars = {}) =>
  Object.entries(vars).reduce((o, [k, v]) => o.split(k).join(v.replace(/\r\n/g, "\n")), src.replace(/\r\n/g, "\n"));
const gunzipB64 = (b64) => zlib.gunzipSync(Buffer.from(b64.replace(/\s+/g, ""), "base64")).toString("utf8");
const write = (dir, file, content, mode) => {
  const p = path.join(dir, file);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, content);
  if (mode) fs.chmodSync(p, mode);
};

const SHARED_TOOLS = lib("mythos-code-shared/tools.js");
fs.rmSync(out, { recursive: true, force: true });

// ---- Desktop-App ----
const app = path.join(out, "app");
write(app, "package.json", JSON.stringify({
  name: APP_PKG,
  productName: "Mythos Code",
  version: APP_VERSION,
  description: "Mythos Code – KI-Coding-Agent als App (by Mythoscraft)",
  main: "main.js",
  author: "Mythoscraft",
  license: "MIT",
  scripts: { start: "electron .", dist: "electron-builder --win nsis --publish never" },
  devDependencies: { electron: "^31.0.0", "electron-builder": "^24.13.3" },
  build: {
    appId: "online.mythoscraft.mythoscode",
    productName: "Mythos Code",
    files: ["main.js", "preload.js", "index.html", "markdown.js", "mcp.js", "voice.js", "tts-worker.js", "stt-worker.js", "renderer.js", "config.json", "icon.png"],
    win: { target: "nsis", icon: "icon.png" },
    nsis: { oneClick: false, allowToChangeInstallationDirectory: true, createDesktopShortcut: true, shortcutName: "Mythos Code", artifactName: "MythosCode-Setup.exe" },
  },
}, null, 2));
fs.copyFileSync(path.join(root, "public/icon.png"), path.join(app, "icon.png"));
write(app, "main.js", fill(lib("mythos-code-app/main.js"), { "// @@SHARED_TOOLS@@": SHARED_TOOLS }));
for (const f of ["preload.js", "index.html", "markdown.js", "mcp.js", "voice.js"]) write(app, f, fill(lib(`mythos-code-app/${f}`)));
write(app, "tts-worker.js", gunzipB64(lib("mythos-code-app/tts-worker.js.gz.b64")));
write(app, "stt-worker.js", gunzipB64(lib("mythos-code-app/stt-worker.js.gz.b64")));
write(app, "renderer.js", fill(lib("mythos-code-app/renderer.js"), { "__SITE__": SITE, "__FN__": FN_BASE }));
write(app, "config.json", JSON.stringify({ site: SITE }, null, 2));

// ---- CLI (npm-Paket) ----
const cli = path.join(out, "cli");
write(cli, "package.json", JSON.stringify({
  name: CLI_PKG,
  version: CLI_VERSION,
  description: "Mythos Code – KI-Coding-Agent im Terminal (by Mythoscraft)",
  bin: { "mythos": "bin/mythos.js", "mythos-code": "bin/mythos.js" },
  type: "module",
  files: ["bin", "README.md"],
  engines: { node: ">=18" },
  keywords: ["ai", "cli", "agent", "mythos", "coding"],
  license: "MIT",
}, null, 2));
write(cli, "README.md", `# Mythos Code\n\nKI-Coding-Agent im Terminal – by Mythoscraft.\n\n\`\`\`\nnpm install -g ${CLI_PKG}\nmythos login\nmythos\n\`\`\`\n`);
write(cli, "bin/mythos.js", fill(lib("mythos-code-cli/mythos.js"), { "// @@SHARED_TOOLS@@": SHARED_TOOLS, "__API__": API, "__SITE__": SITE }), 0o755);

const summary = { appVersion: APP_VERSION, cliVersion: CLI_VERSION, cliPackage: CLI_PKG, out };
console.log(JSON.stringify(summary));
if (process.env.GITHUB_OUTPUT) {
  fs.appendFileSync(process.env.GITHUB_OUTPUT, `app_version=${APP_VERSION}\ncli_version=${CLI_VERSION}\ncli_package=${CLI_PKG}\n`);
}
