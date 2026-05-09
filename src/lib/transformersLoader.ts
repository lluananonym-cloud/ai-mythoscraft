/**
 * Shared loader for @huggingface/transformers pipelines.
 *
 * - Globally deduplicates downloads across components (avoids 2-3x parallel
 *   downloads when multiple OfflineAI / SongPlayer bubbles mount at once).
 * - Stored on `globalThis` so HMR / dynamic re-imports cannot create
 *   duplicate caches.
 * - Progress is broadcast via subscribers so every UI showing the same
 *   model sees one shared progress bar.
 * - Models are cached in the browser's Cache Storage (Transformers.js default),
 *   which is persistent and works fully offline after the first download.
 */

type Progress = { pct: number; msg: string; done: boolean };
type Sub = (p: Progress) => void;

type Entry = {
  promise: Promise<any> | null;
  progress: Progress;
  subs: Set<Sub>;
};

type Store = { pipes: Map<string, Entry>; tf: any };

const g = globalThis as any;
const store: Store = (g.__MYTHOS_TF_STORE__ ||= { pipes: new Map(), tf: null });

async function getTf() {
  if (!store.tf) {
    store.tf = await import("@huggingface/transformers");
    store.tf.env.allowLocalModels = false;
    store.tf.env.useBrowserCache = true;
  }
  return store.tf;
}

function entryFor(key: string): Entry {
  let e = store.pipes.get(key);
  if (!e) {
    e = { promise: null, progress: { pct: 0, msg: "", done: false }, subs: new Set() };
    store.pipes.set(key, e);
  }
  return e;
}

function emit(e: Entry, p: Partial<Progress>) {
  e.progress = { ...e.progress, ...p };
  e.subs.forEach((s) => s(e.progress));
}

export interface LoadOptions {
  task: string;
  model: string;
  dtype?: string;
  /** Called immediately with current progress and on every update. */
  onProgress?: Sub;
}

/** Load (or get the cached) pipeline. Safe to call from many places concurrently. */
export function loadPipeline({ task, model, dtype, onProgress }: LoadOptions): Promise<any> {
  const key = `${task}::${model}::${dtype ?? "default"}`;
  const e = entryFor(key);

  if (onProgress) {
    e.subs.add(onProgress);
    onProgress(e.progress);
  }

  if (!e.promise) {
    e.promise = (async () => {
      const tf = await getTf();
      return await tf.pipeline(task, model, {
        dtype,
        progress_callback: (data: any) => {
          const file: string | undefined = data.file;
          if (data.status === "progress" && (file?.endsWith(".onnx") || file?.endsWith(".bin") || file?.endsWith(".onnx_data"))) {
            emit(e, { pct: Math.round(data.progress ?? 0), msg: `Lade Modell… ${Math.round(data.progress ?? 0)}%` });
          } else if (data.status === "ready") {
            emit(e, { pct: 100, msg: "Modell bereit", done: true });
          }
        },
      });
    })().catch((err) => {
      // Reset so a future retry starts fresh.
      e.promise = null;
      emit(e, { msg: "Fehler beim Laden", done: false });
      throw err;
    });
  }

  // Detach subscriber after the promise settles to avoid leaks.
  if (onProgress) {
    e.promise.finally(() => e.subs.delete(onProgress));
  }
  return e.promise;
}

/** Fire-and-forget background preload (idle). Never throws. */
export function preloadPipeline(opts: LoadOptions) {
  const start = () => {
    loadPipeline(opts).catch(() => {/* ignore, retried on demand */});
  };
  if ("requestIdleCallback" in window) {
    (window as any).requestIdleCallback(start, { timeout: 5000 });
  } else {
    setTimeout(start, 2500);
  }
}
