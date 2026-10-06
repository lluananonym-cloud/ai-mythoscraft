// Desktop-Programme, die GitHub Actions automatisch baut und als Release
// im öffentlichen Repo veröffentlicht. Die Website verlinkt nur noch hierauf.
export const RELEASE_REPO = "lluananonym-cloud/ai-mythoscraft";

export type AppDownload = {
  id: string;
  name: string;
  tagline: string;
  description: string;
  /** Release-Tag im RELEASE_REPO, z. B. "code-latest" */
  tag: string;
  /** Dateiname im Release; null = erstes Asset nehmen */
  asset: string | null;
  platform: string;
  /** true = für alle kostenlos, sonst nur mit Pro */
  free?: boolean;
  /** Optionaler Installationsbefehl zum Kopieren (z. B. für die CLI) */
  install?: string;
  screenshot?: string;
  highlights: string[];
};

export const APPS: AppDownload[] = [
  {
    id: "code",
    name: "Mythos Code",
    tagline: "KI-Coding-Agent für deinen PC",
    description: "Projekt wählen, sagen was gebaut werden soll, fertig. Mythos liest, schreibt und testet deinen Code selbstständig, auch per Sprache.",
    tag: "code-latest",
    asset: "MythosCode-Setup.exe",
    platform: "Windows",
    screenshot: "/screenshots/mythos-code.webp",
    highlights: ["Projekte, Chats und Aufgabenlisten", "Live-Vorschau, Dateibaum und Terminal", "Sprachmodus: einfach mit Mythos reden"],
  },
  {
    id: "browser",
    name: "Mythos Browser",
    tagline: "Schnell, privat, clean",
    description: "Chromium-Browser mit eingebautem Werbe- und Trackerblocker, Mythos Search und Chrome-Erweiterungen.",
    tag: "browser-latest",
    asset: "MythosBrowser-Setup.exe",
    platform: "Windows",
    free: true,
    screenshot: "/screenshots/mythos-browser.webp",
    highlights: ["Werbeblocker eingebaut", "Chrome-Erweiterungen", "Mythos Search als Standard"],
  },
  {
    id: "notch",
    name: "Mythos Notch",
    tagline: "Dein KI-Assistent oben am Bildschirm",
    description: "Mythos lebt als kleine Insel oben am Bildschirm. Ein Klick und du fragst, ohne ein Fenster zu wechseln.",
    tag: "notch-latest",
    asset: "MythosNotch-Setup.exe",
    platform: "Windows",
    free: true,
    highlights: ["Immer griffbereit", "Sprach- und Textchat", "Steuert auf Wunsch deinen PC"],
  },
  {
    id: "cli",
    name: "Mythos CLI",
    tagline: "Mythos im Terminal",
    description: "Für alle, die lieber im Terminal arbeiten. Gleicher Agent wie in Mythos Code.",
    tag: "cli-latest",
    asset: "mythos-code.tgz",
    platform: "Windows · macOS · Linux",
    install: "npm install -g mythos-code",
    highlights: ["Login per Browser", "Gleiche Limits wie dein Plan", "Braucht Node.js ab Version 18"],
  },
];

export const downloadUrl = (app: AppDownload, assetName?: string) =>
  `https://github.com/${RELEASE_REPO}/releases/download/${app.tag}/${encodeURIComponent(assetName ?? app.asset ?? "")}`;

export type ReleaseInfo = {
  tag: string;
  publishedAt: string;
  assets: { name: string; size: number; url: string }[];
};

/** Liest alle Releases einmal (öffentliche GitHub-API, kein Token nötig). */
export async function fetchReleases(): Promise<Record<string, ReleaseInfo>> {
  const r = await fetch(`https://api.github.com/repos/${RELEASE_REPO}/releases?per_page=50`, {
    headers: { Accept: "application/vnd.github+json" },
  });
  if (!r.ok) throw new Error(`GitHub ${r.status}`);
  const list = (await r.json()) as any[];
  const out: Record<string, ReleaseInfo> = {};
  for (const rel of list) {
    out[rel.tag_name] = {
      tag: rel.tag_name,
      publishedAt: rel.published_at,
      assets: (rel.assets ?? []).map((a: any) => ({ name: a.name, size: a.size, url: a.browser_download_url })),
    };
  }
  return out;
}
