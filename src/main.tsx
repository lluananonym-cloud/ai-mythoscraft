import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import "./index.css";
import { initPersistentSession } from "./lib/persistentSession";
import { preloadPipeline } from "./lib/transformersLoader";

void initPersistentSession();

// Background-preload the smallest offline model on idle so /sentiment is
// instant and works fully offline after first visit. Skipped on slow/metered
// connections to respect the user's data plan.
const conn = (navigator as any).connection;
if (!conn || (!conn.saveData && !["slow-2g", "2g"].includes(conn.effectiveType))) {
  preloadPipeline({
    task: "sentiment-analysis",
    model: "Xenova/distilbert-base-uncased-finetuned-sst-2-english",
    dtype: "fp32",
  });
}

// Guard service worker registration: never register inside Lovable preview iframe.
const isInIframe = (() => {
  try {
    return window.self !== window.top;
  } catch {
    return true;
  }
})();
const isPreviewHost =
  window.location.hostname.includes("id-preview--") ||
  window.location.hostname.includes("lovableproject.com");

if (isPreviewHost || isInIframe) {
  navigator.serviceWorker?.getRegistrations().then((regs) => regs.forEach((r) => r.unregister()));
} else if ("serviceWorker" in navigator) {
  // Lazy-load the virtual PWA register module only in production-like contexts.
  import("virtual:pwa-register")
    .then(({ registerSW }) => registerSW({ immediate: true }))
    .catch(() => {});
}

createRoot(document.getElementById("root")!).render(<App />);
