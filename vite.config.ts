import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import path from "path";
import { componentTagger } from "lovable-tagger";
import { VitePWA } from "vite-plugin-pwa";
import dyadComponentTagger from '@dyad-sh/react-vite-component-tagger';
// nitro import removed – using custom middleware instead

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  build: { minify: true, sourcemap: false },
  server: {
    host: "::",
    port: 8080,
    hmr: {
      overlay: false,
    },
  },
  configureServer(server: any) {
    const jsonParser = (req: any, res: any, next: any) => {
      let data = "";
      req.on("data", (chunk: any) => (data += chunk));
      req.on("end", () => {
        if (data) {
          try {
            req.body = JSON.parse(data);
          } catch (e) {
            req.body = {};
          }
        }
        next();
      });
    };
    const handleProxy = (path: string, handler: (body: any) => Promise<any>) => {
      server.middlewares.use(path, jsonParser, async (req: any, res: any, next: any) => {
        try {
          const result = await handler(req.body);
          res.setHeader("Content-Type", "application/json");
          res.end(JSON.stringify(result));
        } catch (e) {
          res.statusCode = 500;
          res.end(JSON.stringify({ error: (e as Error).message }));
        }
      });
    };
    // /api/nvidia/chat
    handleProxy("/api/nvidia/chat", async (body: any) => {
      const resp = await fetch("https://integrate.api.nvidia.com/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${process.env.NV_API_KEY || ""}`,
        },
        body: JSON.stringify({
          model: "gpt-oss-120gb",
          messages: body.messages,
          temperature: 0.7,
        }),
      });
      const data: any = await resp.json();
      return { content: data.choices?.[0]?.message?.content ?? "" };
    });
    // /api/nvidia/llm
    handleProxy("/api/nvidia/llm", async (body: any) => {
      const resp = await fetch("https://integrate.api.nvidia.com/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${process.env.NV_API_KEY || ""}`,
        },
        body: JSON.stringify({
          model: body.model,
          messages: body.messages,
          temperature: 0.7,
        }),
      });
      const data: any = await resp.json();
      return { content: data.choices?.[0]?.message?.content ?? "" };
    });
    // /api/nvidia/image
    handleProxy("/api/nvidia/image", async (body: any) => {
      const resp = await fetch("https://integrate.api.nvidia.com/v1/images/generations", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${process.env.NV_API_KEY || ""}`,
        },
        body: JSON.stringify({
          model: "qwen-image-edit-nvpcb-ovsl2sl",
          prompt: body.prompt,
          n: 1,
        }),
      });
      const data: any = await resp.json();
      const img = data.data?.[0];
      return { url: img?.url ?? `data:image/png;base64,${img?.b64_json}` };
    });
    // /api/nvidia/tts
    handleProxy("/api/nvidia/tts", async (body: any) => {
      const resp = await fetch("https://integrate.api.nvidia.com/v1/audio/speech", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${process.env.NV_API_KEY || ""}`,
        },
        body: JSON.stringify({
          model: body.model || "magpie-tts-multilingual",
          input: body.text,
        }),
      });
      const data: any = await resp.json();
      return { audioBase64: data.audioBase64 };
    });
  },
  plugins: [dyadComponentTagger(),
      react(),
    mode === "development" && componentTagger(),
    VitePWA({
      registerType: "autoUpdate",
      devOptions: { enabled: false },
      includeAssets: ["icon.png", "apple-touch-icon.png"],
      manifest: {
        name: "Mythos AI",
        short_name: "Mythos",
        description: "Mythos AI – dein KI-Begleiter für Minecraft & mehr.",
        theme_color: "#000000",
        background_color: "#000000",
        display: "standalone",
        start_url: "/",
        scope: "/",
        icons: [
          { src: "/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
          { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
          { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
        ],
      },
      workbox: {
        maximumFileSizeToCacheInBytes: 30 * 1024 * 1024,
        globIgnores: ["**/ort-wasm*", "**/*.wasm"],
        navigateFallbackDenylist: [/^\/~oauth/, /^\/api/, /^\/supabase/],
        cleanupOutdatedCaches: true,
        clientsClaim: true,
        skipWaiting: true,
        runtimeCaching: [
          {
            // Always go to network for HTML so a redeploy never traps users on a stale shell.
            urlPattern: ({ request }) => request.mode === "navigate",
            handler: "NetworkFirst",
            options: {
              cacheName: "html",
              networkTimeoutSeconds: 3,
              expiration: { maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 },
            },
          },
          {
            urlPattern: ({ request }) =>
              ["style", "script", "worker", "image", "font"].includes(request.destination),
            handler: "StaleWhileRevalidate",
            options: {
              cacheName: "assets",
              expiration: { maxEntries: 200, maxAgeSeconds: 60 * 60 * 24 * 30 },
            },
          },
          {
            // Local WASM runtime for Transformers.js — cache forever after first hit.
            urlPattern: /\.wasm$/,
            handler: "CacheFirst",
            options: {
              cacheName: "wasm",
              expiration: { maxEntries: 20, maxAgeSeconds: 60 * 60 * 24 * 365 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            // Hugging Face model weights — large, immutable, cache forever.
            urlPattern: ({ url }) =>
              url.hostname === "huggingface.co" || url.hostname.endsWith(".hf.co"),
            handler: "CacheFirst",
            options: {
              cacheName: "hf-models",
              expiration: { maxEntries: 100, maxAgeSeconds: 60 * 60 * 24 * 365 },
              cacheableResponse: { statuses: [0, 200] },
              rangeRequests: true,
            },
          },
        ],
      },
    }),
  ].filter(Boolean),
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
    dedupe: ["react", "react-dom", "react/jsx-runtime", "react/jsx-dev-runtime", "@tanstack/react-query", "@tanstack/query-core"],
  },
}));
