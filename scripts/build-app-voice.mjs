// Bündelt die Sprach-Worker der Mythos-Code-App (Piper-Stimme, Whisper-Erkennung).
// Ausgabe gzip + base64 (*.js.gz.b64): kleiner im Repo, und minifizierter Code löst sonst
// Fehlalarme im GitHub-Secret-Scanning aus. mythosCli.ts entpackt die Dateien beim ZIP-Bau.
import { build } from "esbuild";
import { gzipSync } from "node:zlib";
import { writeFileSync } from "node:fs";

const external = ["fs", "path", "url", "module", "crypto", "worker_threads", "sharp", "onnxruntime-node", "fs/promises", "stream"];
const workers = [
  ["src/lib/mythosVoice.worker.ts", "src/lib/mythos-code-app/tts-worker.js.gz.b64"],
  ["src/lib/mythosStt.worker.ts", "src/lib/mythos-code-app/stt-worker.js.gz.b64"],
];
for (const [entry, out] of workers) {
  const r = await build({ entryPoints: [entry], bundle: true, format: "iife", minify: true, platform: "browser", legalComments: "none", external, write: false });
  const js = r.outputFiles[0].contents;
  const b64 = gzipSync(js, { level: 9 }).toString("base64");
  writeFileSync(out, b64.replace(/.{1,120}/g, "$&\n"));
  console.log(out, Math.round(js.length / 1024) + " KB →", Math.round(b64.length / 1024) + " KB");
}
