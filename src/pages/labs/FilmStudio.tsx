import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import LabsShell from "@/components/labs/LabsShell";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import {
  Clapperboard, Play, Pause, Download, Loader2, CheckCircle2, Circle, ImagePlus, RefreshCw, Trash2, Copy,
  ChevronLeft, ChevronRight, Plus, Music, Mic, Film as FilmIcon, Sparkles, Upload, FolderOpen, Square, Wand2,
} from "lucide-react";
import { toast } from "sonner";
import { makeImage, shrinkImage } from "@/lib/labs";
import { VOICES, getVoice, setVoice, onLoadState, type LoadState } from "@/lib/mythosVoice";
import {
  CAMERAS, LENGTHS, STYLES, deleteFilm, imagePrompt, listFilms, saveFilm, uid, writeNextScene, writeScript,
  type Film, type Scene,
} from "@/lib/filmStudio/script";
import {
  H, W, buildTimeline, drawFrame, fmtTime, loadPicture, makeMusic, mixSoundtrack, narrate, recordFilm, videoMime,
  type FrameData, type Picture, type Timeline,
} from "@/lib/filmStudio/render";

const EXAMPLES = [
  "Ein Vulkan bricht auf einer einsamen Insel aus, und ein junger Forscher muss sein Dorf warnen.",
  "Ein kleiner Roboter findet im Müll eine Blume und will sie retten.",
  "Die Geschichte des Kaffees, vom äthiopischen Hirten bis zum Café um die Ecke.",
  "Zwei Astronauten entdecken auf dem Mars eine Tür im Felsen.",
];

type Step = { label: string; done: number; total: number };

/** Mehrere Aufgaben mit begrenzter Parallelität abarbeiten. */
async function pool<T>(items: T[], n: number, fn: (item: T) => Promise<void>) {
  const queue = [...items];
  await Promise.all(Array.from({ length: Math.min(n, queue.length) }, async () => {
    for (let it = queue.shift(); it !== undefined; it = queue.shift()) await fn(it);
  }));
}

/** Mythos Filmstudio: aus einer Idee ein fertiger Kurzfilm mit Drehbuch, Bildern, Sprecher, Musik und Schnitt. */
export default function FilmStudio() {
  const [idea, setIdea] = useState("");
  const [length, setLength] = useState(60);
  const [styleId, setStyleId] = useState(STYLES[0].id);
  const [film, setFilm] = useState<Film | null>(null);
  const [films, setFilms] = useState<Film[]>(() => listFilms());
  const [steps, setSteps] = useState<Step[] | null>(null);
  const [selected, setSelected] = useState(0);
  const [wish, setWish] = useState("");
  const [addingScene, setAddingScene] = useState(false);
  const [imgBusy, setImgBusy] = useState<Set<string>>(new Set());
  const [voiceId, setVoiceId] = useState(getVoice());
  const [voiceLoad, setVoiceLoad] = useState<LoadState>({ status: "idle", pct: 0 });
  const [musicSeed, setMusicSeed] = useState(7);
  const [customMusic, setCustomMusic] = useState<{ name: string; buf: AudioBuffer } | null>(null);

  // Fertig vorbereitete Daten für Vorschau und Export
  const [tl, setTl] = useState<Timeline | null>(null);
  const [soundtrack, setSoundtrack] = useState<AudioBuffer | null>(null);
  const [preparing, setPreparing] = useState(false);
  const picsRef = useRef(new Map<string, Picture | null>());
  const [picsVersion, setPicsVersion] = useState(0);

  // Abspielen
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(false);
  const playRef = useRef<{ ac: AudioContext; src: AudioBufferSourceNode; t0: number; from: number; raf: number } | null>(null);

  // Export
  const [exporting, setExporting] = useState<number | null>(null);
  const exportStop = useRef({ stop: false });
  const [video, setVideo] = useState<{ url: string; name: string } | null>(null);

  const busy = !!steps;
  const scene = film?.scenes[selected];

  useEffect(() => onLoadState(setVoiceLoad), []);

  const update = useCallback((fn: (f: Film) => Film) => setFilm((f) => (f ? fn(f) : f)), []);
  const updateScene = useCallback((id: string, patch: Partial<Scene>) =>
    update((f) => ({ ...f, scenes: f.scenes.map((s) => (s.id === id ? { ...s, ...patch } : s)) })), [update]);

  // Speichern (kurz verzögert)
  useEffect(() => {
    if (!film || busy) return;
    const t = setTimeout(() => {
      if (!saveFilm(film)) toast.error("Browser-Speicher ist voll, der Film wurde nicht gespeichert.");
      setFilms(listFilms());
    }, 800);
    return () => clearTimeout(t);
  }, [film, busy]);

  // ---------- Bilder ----------
  const genImage = useCallback(async (f: Film, s: Scene) => {
    setImgBusy((b) => new Set(b).add(s.id));
    try {
      const prompt = imagePrompt(f, s);
      let url = await makeImage(prompt, { width: 1280, height: 720 });
      let pic = await loadPicture(url);
      if (!pic) {
        // Bild ließ sich nicht in den Film laden (z. B. gesperrte Adresse): direkt im Browser erzeugen.
        url = `https://image.pollinations.ai/prompt/${encodeURIComponent(prompt.slice(0, 800))}?model=flux&width=1280&height=720&nologo=true&referrer=mythoscraft&seed=${Math.floor(Math.random() * 1e6)}`;
        pic = await loadPicture(url);
      }
      if (!pic) throw new Error("Bild konnte nicht geladen werden");
      picsRef.current.set(s.id, pic);
      updateScene(s.id, { image: url });
      setPicsVersion((v) => v + 1);
    } catch (e) {
      toast.error(`Bild für „${s.heading}“: ${(e as Error).message}`);
    } finally {
      setImgBusy((b) => { const n = new Set(b); n.delete(s.id); return n; });
    }
  }, [updateScene]);

  // Bilder zu vorhandenen URLs laden (z. B. nach dem Öffnen eines gespeicherten Films)
  useEffect(() => {
    if (!film) return;
    let changed = false;
    void Promise.all(film.scenes.map(async (s) => {
      if (!s.image || picsRef.current.has(s.id + "|" + s.image)) return;
      picsRef.current.set(s.id + "|" + s.image, null);
      const pic = await loadPicture(s.image);
      picsRef.current.set(s.id, pic);
      changed = true;
    })).then(() => { if (changed) setPicsVersion((v) => v + 1); });
  }, [film]);

  // ---------- Ton und Zeitplan vorbereiten ----------
  const prepKey = film && !busy
    ? JSON.stringify([film.scenes.map((s) => [s.id, s.narration, s.duration]), film.music, film.musicVolume, film.voiceVolume, voiceId, musicSeed, customMusic?.name])
    : "";
  useEffect(() => {
    if (!film || !prepKey) return;
    let cancelled = false;
    const t = setTimeout(async () => {
      setPreparing(true);
      try {
        const narration = new Map<string, AudioBuffer>();
        for (const s of film.scenes) {
          const buf = await narrate(s.narration, voiceId);
          if (cancelled) return;
          if (buf) narration.set(s.id, buf);
        }
        const music = customMusic?.buf ?? await makeMusic(film.music, musicSeed).catch(() => null);
        const timeline = buildTimeline(film.scenes, (s) => narration.get(s.id)?.duration);
        const mix = await mixSoundtrack(film, timeline, narration, music);
        if (cancelled) return;
        setTl(timeline);
        setSoundtrack(mix);
      } catch (e) {
        console.error("[film] Vorbereitung", e);
      } finally {
        if (!cancelled) setPreparing(false);
      }
    }, 500);
    return () => { cancelled = true; clearTimeout(t); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [prepKey]);

  // Zeitplan sofort (geschätzt) für die Timeline, auch bevor der Ton fertig ist
  const liveTl = useMemo(() => {
    if (!film) return null;
    if (tl && tl.slots.length === film.scenes.length && tl.slots.every((s, i) => s.scene.id === film.scenes[i].id)) {
      return { ...tl, slots: tl.slots.map((s, i) => ({ ...s, scene: film.scenes[i] })) };
    }
    return buildTimeline(film.scenes, () => undefined);
  }, [film, tl]);

  const frameData = useMemo<FrameData | null>(() => (film && liveTl ? { film, tl: liveTl, pics: picsRef.current } : null),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [film, liveTl, picsVersion]);

  // ---------- Abspielen ----------
  const draw = useCallback((t: number) => {
    const ctx = canvasRef.current?.getContext("2d");
    if (ctx && frameData) drawFrame(ctx, frameData, t);
  }, [frameData]);

  useEffect(() => { if (!playing) draw(time); }, [draw, time, playing]);

  const stopPlayback = useCallback(() => {
    const p = playRef.current;
    if (!p) return;
    cancelAnimationFrame(p.raf);
    try { p.src.stop(); } catch { /* schon gestoppt */ }
    void p.ac.close();
    playRef.current = null;
    setPlaying(false);
  }, []);

  const play = useCallback(async (from = time) => {
    if (!soundtrack || !frameData) return;
    stopPlayback();
    const start = from >= frameData.tl.total - 0.1 ? 0 : from;
    const ac = new AudioContext();
    await ac.resume();
    const src = ac.createBufferSource();
    src.buffer = soundtrack;
    src.connect(ac.destination);
    const t0 = ac.currentTime + 0.05;
    src.start(t0, start);
    const p = { ac, src, t0, from: start, raf: 0 };
    const tick = () => {
      const t = start + Math.max(0, ac.currentTime - t0);
      if (t >= frameData.tl.total) { stopPlayback(); setTime(0); return; }
      draw(t);
      setTime(t);
      p.raf = requestAnimationFrame(tick);
    };
    p.raf = requestAnimationFrame(tick);
    playRef.current = p;
    setPlaying(true);
  }, [soundtrack, frameData, time, draw, stopPlayback]);

  // Bei Änderungen am Film anhalten, damit Bild und Ton nicht auseinanderlaufen
  useEffect(() => { stopPlayback(); }, [soundtrack, stopPlayback]);
  useEffect(() => () => stopPlayback(), [stopPlayback]);

  const seek = (t: number) => {
    setTime(t);
    if (playing) void play(t);
    const slot = liveTl?.slots.find((s) => t >= s.start && t < s.end);
    if (slot) setSelected(slot.index);
  };

  // ---------- Film drehen ----------
  const produce = async (text = idea) => {
    const what = text.trim();
    if (!what || busy) return;
    stopPlayback();
    setVideo(null);
    const style = STYLES.find((s) => s.id === styleId) ?? STYLES[0];
    const st: Step[] = [
      { label: "Drehbuch schreiben", done: 0, total: 1 },
      { label: "Szenen drehen (Bilder)", done: 0, total: 1 },
      { label: "Sprecher vertonen", done: 0, total: 1 },
      { label: "Filmmusik komponieren", done: 0, total: 1 },
      { label: "Schnitt und Tonmischung", done: 0, total: 1 },
    ];
    const set = (i: number, done: number, total?: number) => {
      st[i] = { ...st[i], done, total: total ?? st[i].total };
      setSteps(st.map((s) => ({ ...s })));
    };
    setSteps(st.map((s) => ({ ...s })));
    try {
      const f = await writeScript(what, length, style.prompt);
      set(0, 1);
      setFilm(f);
      setSelected(0);
      setTime(0);
      picsRef.current = new Map();
      setTl(null); setSoundtrack(null); setCustomMusic(null);

      const n = f.scenes.length;
      set(1, 0, n); set(2, 0, n);
      let imgs = 0, voices = 0;
      const images = pool(f.scenes, 3, async (s) => { await genImage(f, s); set(1, ++imgs, n); });
      const narration = (async () => {
        for (const s of f.scenes) { await narrate(s.narration, voiceId); set(2, ++voices, n); }
      })();
      const music = makeMusic(f.music, musicSeed).then(() => set(3, 1), () => set(3, 1));
      await Promise.all([images, narration, music]);
      set(4, 1);
      toast.success("Dein Film ist fertig. Drück auf Abspielen.");
    } catch (e) {
      toast.error((e as Error).message || "Der Film konnte nicht gedreht werden.");
    } finally {
      setSteps(null);
    }
  };

  // Idee aus dem Chat (/film <idee>) oder der Code-App direkt drehen
  const [params, setParams] = useSearchParams();
  const autoIdea = useRef(params.get("idee"));
  useEffect(() => {
    const t = autoIdea.current?.trim();
    if (!t) return;
    autoIdea.current = null;
    setParams({}, { replace: true });
    setIdea(t);
    void produce(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openFilm = (f: Film) => {
    stopPlayback();
    picsRef.current = new Map();
    setTl(null); setSoundtrack(null); setCustomMusic(null); setVideo(null);
    setFilm(f); setSelected(0); setTime(0);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  // ---------- Szenen bearbeiten ----------
  const moveScene = (dir: -1 | 1) => update((f) => {
    const to = selected + dir;
    if (to < 0 || to >= f.scenes.length) return f;
    const scenes = [...f.scenes];
    [scenes[selected], scenes[to]] = [scenes[to], scenes[selected]];
    setSelected(to);
    return { ...f, scenes };
  });
  const duplicateScene = () => update((f) => {
    const s = f.scenes[selected];
    const copy = { ...s, id: uid(), heading: s.heading + " (Kopie)" };
    if (picsRef.current.has(s.id)) picsRef.current.set(copy.id, picsRef.current.get(s.id)!);
    const scenes = [...f.scenes];
    scenes.splice(selected + 1, 0, copy);
    setSelected(selected + 1);
    return { ...f, scenes };
  });
  const removeScene = () => update((f) => {
    if (f.scenes.length <= 1) { toast.error("Ein Film braucht mindestens eine Szene."); return f; }
    setSelected(Math.max(0, selected - 1));
    return { ...f, scenes: f.scenes.filter((_, i) => i !== selected) };
  });
  const addScene = async () => {
    if (!film) return;
    setAddingScene(true);
    try {
      const s = await writeNextScene(film, wish);
      const f = { ...film, scenes: [...film.scenes, s] };
      setFilm(f);
      setSelected(f.scenes.length - 1);
      setWish("");
      void genImage(f, s);
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setAddingScene(false);
    }
  };
  const uploadImage = async (s: Scene, file?: File) => {
    if (!file) return;
    try {
      const url = await shrinkImage(file, 1280);
      picsRef.current.set(s.id, await loadPicture(url));
      updateScene(s.id, { image: url });
      setPicsVersion((v) => v + 1);
    } catch (e) {
      toast.error((e as Error).message);
    }
  };
  const uploadMusic = async (file?: File) => {
    if (!file) return;
    try {
      const ac = new AudioContext();
      const buf = await ac.decodeAudioData(await file.arrayBuffer());
      void ac.close();
      setCustomMusic({ name: file.name, buf });
      toast.success("Deine Musik liegt jetzt unter dem Film.");
    } catch {
      toast.error("Die Datei konnte nicht als Musik gelesen werden.");
    }
  };

  // ---------- Export ----------
  const exportVideo = async () => {
    if (!frameData || !soundtrack) return;
    stopPlayback();
    exportStop.current = { stop: false };
    setExporting(0);
    setVideo(null);
    try {
      const blob = await recordFilm(frameData, soundtrack, setExporting, exportStop.current);
      const ext = videoMime().startsWith("video/mp4") ? "mp4" : "webm";
      const name = `${(film?.title || "film").replace(/[^\wäöüÄÖÜß -]+/g, "").trim() || "film"}.${ext}`;
      setVideo({ url: URL.createObjectURL(blob), name });
      toast.success("Video ist fertig.");
    } catch (e) {
      if ((e as Error).message !== "abgebrochen") toast.error((e as Error).message);
    } finally {
      setExporting(null);
    }
  };
  useEffect(() => () => { if (video) URL.revokeObjectURL(video.url); }, [video]);

  const total = liveTl?.total ?? 0;
  const pct = (t: number) => `${total ? (t / total) * 100 : 0}%`;

  return (
    <LabsShell icon={Clapperboard} title="Mythos Filmstudio" wide
      subtitle="Du hast eine Idee, Mythos dreht den Film: Drehbuch, Szenen, Sprecher, Musik und Schnitt. Danach schneidest du selbst weiter.">
      {/* Idee */}
      <section className="glass rounded-2xl p-3 md:p-4 space-y-3">
        <Textarea value={idea} onChange={(e) => setIdea(e.target.value)} rows={2} disabled={busy}
          onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); void produce(); } }}
          placeholder="Worum soll dein Film gehen? Zum Beispiel: Ein Vulkanausbruch auf einer einsamen Insel." className="resize-none text-base" />
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-muted-foreground w-12">Länge</span>
          {LENGTHS.map((l) => (
            <Button key={l.sec} size="sm" variant={length === l.sec ? "default" : "outline"} disabled={busy} onClick={() => setLength(l.sec)}>{l.label}</Button>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-muted-foreground w-12">Stil</span>
          {STYLES.map((s) => (
            <Button key={s.id} size="sm" variant={styleId === s.id ? "default" : "outline"} disabled={busy} onClick={() => setStyleId(s.id)}>{s.label}</Button>
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => produce()} disabled={!idea.trim() || busy} className="min-h-[44px]">
            {busy ? <Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> : <Clapperboard className="h-4 w-4 mr-1.5" />}Film drehen
          </Button>
          {!busy && !film && EXAMPLES.map((ex) => (
            <Button key={ex} variant="outline" className="min-h-[44px] max-w-full" onClick={() => setIdea(ex)}>
              <Sparkles className="h-4 w-4 mr-1.5 shrink-0 text-fuchsia-300" /><span className="truncate">{ex.split(",")[0].slice(0, 42)}</span>
            </Button>
          ))}
        </div>
      </section>

      {/* Fortschritt beim Drehen */}
      {steps && (
        <section className="glass rounded-2xl p-4 space-y-2">
          {steps.map((s) => {
            const done = s.done >= s.total;
            const active = !done && steps.findIndex((x) => x.done < x.total) === steps.indexOf(s);
            return (
              <div key={s.label} className="flex items-center gap-2 text-sm">
                {done ? <CheckCircle2 className="h-4 w-4 text-emerald-400" /> : active || s.done > 0 ? <Loader2 className="h-4 w-4 animate-spin text-fuchsia-300" /> : <Circle className="h-4 w-4 text-muted-foreground" />}
                <span className={done ? "" : "text-muted-foreground"}>{s.label}</span>
                {s.total > 1 && <span className="text-xs text-muted-foreground">{s.done} von {s.total}</span>}
              </div>
            );
          })}
          {voiceLoad.status === "loading" && <p className="text-xs text-muted-foreground">Die Mythos-Stimme wird beim ersten Mal geladen ({voiceLoad.pct} %), danach geht es schneller.</p>}
        </section>
      )}

      {film && (
        <>
          {/* Player */}
          <section className="glass rounded-2xl p-3 md:p-4 space-y-3">
            <div className="relative overflow-hidden rounded-xl bg-black">
              <canvas ref={canvasRef} width={W} height={H} className="block w-full h-auto aspect-video" />
              {exporting !== null && (
                <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black/70 text-center p-4">
                  <Loader2 className="h-8 w-8 animate-spin text-fuchsia-300" />
                  <p className="text-sm">Video wird aufgenommen: {fmtTime(exporting)} von {fmtTime(total)}</p>
                  <p className="text-xs text-muted-foreground">Bitte diesen Tab geöffnet lassen, die Aufnahme läuft in Echtzeit.</p>
                  <Button size="sm" variant="secondary" onClick={() => { exportStop.current.stop = true; }}><Square className="h-4 w-4 mr-1.5" />Abbrechen</Button>
                </div>
              )}
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button onClick={() => (playing ? stopPlayback() : play())} disabled={!soundtrack || exporting !== null} className="min-h-[44px]">
                {playing ? <><Pause className="h-4 w-4 mr-1.5" />Pause</> : <><Play className="h-4 w-4 mr-1.5" />Abspielen</>}
              </Button>
              <span className="text-sm tabular-nums text-muted-foreground">{fmtTime(time)} / {fmtTime(total)}</span>
              {(preparing || busy) && <span className="flex items-center gap-1 text-xs text-muted-foreground"><Loader2 className="h-3 w-3 animate-spin" />Ton wird vorbereitet</span>}
              <div className="ml-auto flex flex-wrap gap-2">
                <Button variant="secondary" onClick={exportVideo} disabled={!soundtrack || preparing || busy || exporting !== null} className="min-h-[44px]">
                  <FilmIcon className="h-4 w-4 mr-1.5" />Video erstellen
                </Button>
                {video && (
                  <Button asChild className="min-h-[44px]">
                    <a href={video.url} download={video.name}><Download className="h-4 w-4 mr-1.5" />Herunterladen</a>
                  </Button>
                )}
              </div>
            </div>

            {/* Timeline */}
            <div className="space-y-1.5 select-none">
              <div className="relative h-6 cursor-pointer" onClick={(e) => {
                const r = e.currentTarget.getBoundingClientRect();
                seek(Math.max(0, Math.min(total - 0.01, ((e.clientX - r.left) / r.width) * total)));
              }}>
                <div className="absolute inset-x-0 top-1/2 h-1 -translate-y-1/2 rounded bg-white/10" />
                <div className="absolute top-1/2 h-1 -translate-y-1/2 rounded bg-fuchsia-400" style={{ width: pct(time) }} />
                <div className="absolute top-0 h-6 w-0.5 bg-white" style={{ left: pct(time) }} />
              </div>
              <div className="relative">
                <div className="flex gap-1 overflow-x-auto pb-1">
                  {liveTl?.slots.map((s) => (
                    <button key={s.scene.id} onClick={() => { setSelected(s.index); seek(s.start + 0.01); }}
                      style={{ flexGrow: s.end - s.start, flexBasis: 0, minWidth: 72 }}
                      className={`relative h-16 overflow-hidden rounded-lg border text-left ${s.index === selected ? "border-fuchsia-400 ring-2 ring-fuchsia-400/40" : "border-white/10"}`}>
                      {s.scene.image
                        ? <img src={s.scene.image} alt="" className="absolute inset-0 h-full w-full object-cover" loading="lazy" />
                        : <div className="absolute inset-0 bg-gradient-to-br from-indigo-900 to-fuchsia-900" />}
                      {imgBusy.has(s.scene.id) && <Loader2 className="absolute right-1 top-1 h-4 w-4 animate-spin text-white" />}
                      <span className="absolute inset-x-0 bottom-0 bg-black/60 px-1.5 py-0.5 text-[10px] leading-tight truncate">
                        {s.index + 1}. {s.scene.heading} · {Math.round(s.end - s.start)}s
                      </span>
                    </button>
                  ))}
                  <div style={{ flexGrow: 3.5, flexBasis: 0, minWidth: 40 }} className="h-16 rounded-lg border border-white/10 bg-black flex items-center justify-center text-[10px] text-muted-foreground">Abspann</div>
                </div>
                <div className="relative mt-1 h-4 rounded bg-white/5" title="Sprecher">
                  <Mic className="absolute left-1 top-0.5 h-3 w-3 text-muted-foreground" />
                  {liveTl?.slots.filter((s) => s.voiceDur).map((s) => (
                    <div key={s.scene.id} className="absolute top-0.5 h-3 rounded bg-sky-400/60" style={{ left: pct(s.voiceStart), width: pct(s.voiceDur) }} />
                  ))}
                </div>
                <div className="relative mt-1 h-4 rounded bg-white/5" title="Musik">
                  <Music className="absolute left-1 top-0.5 h-3 w-3 text-muted-foreground" />
                  {film.musicVolume > 0 && <div className="absolute left-5 right-0 top-0.5 h-3 rounded bg-gradient-to-r from-emerald-400/20 via-emerald-400/50 to-emerald-400/10" />}
                </div>
              </div>
            </div>
          </section>

          <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
            {/* Szene bearbeiten */}
            {scene && (
              <section className="glass rounded-2xl p-3 md:p-4 space-y-3 min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-display">Szene {selected + 1} von {film.scenes.length}</span>
                  <div className="ml-auto flex gap-1">
                    <Button size="icon" variant="ghost" onClick={() => moveScene(-1)} disabled={selected === 0} aria-label="Nach vorne"><ChevronLeft className="h-4 w-4" /></Button>
                    <Button size="icon" variant="ghost" onClick={() => moveScene(1)} disabled={selected === film.scenes.length - 1} aria-label="Nach hinten"><ChevronRight className="h-4 w-4" /></Button>
                    <Button size="icon" variant="ghost" onClick={duplicateScene} aria-label="Duplizieren"><Copy className="h-4 w-4" /></Button>
                    <Button size="icon" variant="ghost" onClick={removeScene} aria-label="Löschen"><Trash2 className="h-4 w-4" /></Button>
                  </div>
                </div>
                <Input value={scene.heading} onChange={(e) => updateScene(scene.id, { heading: e.target.value })} placeholder="Szenentitel" />
                <label className="block space-y-1">
                  <span className="text-xs text-muted-foreground">Sprechertext</span>
                  <Textarea value={scene.narration} onChange={(e) => updateScene(scene.id, { narration: e.target.value })} rows={3} className="text-sm" />
                </label>
                <label className="block space-y-1">
                  <span className="text-xs text-muted-foreground">Was im Bild zu sehen ist</span>
                  <Textarea value={scene.prompt} onChange={(e) => updateScene(scene.id, { prompt: e.target.value })} rows={2} className="text-sm" />
                </label>
                <div className="flex flex-wrap gap-2">
                  <Button variant="secondary" size="sm" disabled={imgBusy.has(scene.id)} onClick={() => genImage(film, scene)}>
                    {imgBusy.has(scene.id) ? <Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> : <RefreshCw className="h-4 w-4 mr-1.5" />}Neues Bild
                  </Button>
                  <Button variant="outline" size="sm" asChild>
                    <label className="cursor-pointer"><ImagePlus className="h-4 w-4 mr-1.5" />Eigenes Bild
                      <input type="file" accept="image/*" className="hidden" onChange={(e) => { void uploadImage(scene, e.target.files?.[0]); e.target.value = ""; }} />
                    </label>
                  </Button>
                </div>
                <div className="space-y-1">
                  <span className="text-xs text-muted-foreground">Kamera</span>
                  <div className="flex flex-wrap gap-1.5">
                    {CAMERAS.map((c) => (
                      <Button key={c.id} size="sm" variant={scene.camera === c.id ? "default" : "outline"} onClick={() => updateScene(scene.id, { camera: c.id })}>{c.label}</Button>
                    ))}
                  </div>
                </div>
                <label className="block space-y-1">
                  <span className="text-xs text-muted-foreground">Mindestdauer: {scene.duration} Sekunden (länger, wenn der Sprecher mehr Zeit braucht)</span>
                  <input type="range" min={3} max={30} value={scene.duration} onChange={(e) => updateScene(scene.id, { duration: Number(e.target.value) })} className="w-full accent-fuchsia-400" />
                </label>
              </section>
            )}

            {/* Film-Einstellungen */}
            <section className="glass rounded-2xl p-3 md:p-4 space-y-3">
              <Input value={film.title} onChange={(e) => update((f) => ({ ...f, title: e.target.value }))} className="font-display" aria-label="Filmtitel" />
              <label className="block space-y-1">
                <span className="text-xs text-muted-foreground">Sprecher</span>
                <select value={voiceId} onChange={(e) => { setVoice(e.target.value); setVoiceId(e.target.value); }}
                  className="w-full rounded-md border border-white/10 bg-background px-3 py-2 text-sm">
                  {VOICES.map((v) => <option key={v.id} value={v.id}>{v.id === "browser" ? "Ohne Sprecher (nur Untertitel)" : v.label}</option>)}
                </select>
                {voiceLoad.status === "loading" && <span className="text-xs text-muted-foreground">Stimme wird geladen: {voiceLoad.pct} %</span>}
                {voiceLoad.status === "error" && <span className="text-xs text-red-400">Die Stimme konnte nicht geladen werden, der Film läuft mit Untertiteln.</span>}
              </label>
              <label className="block space-y-1">
                <span className="text-xs text-muted-foreground">Lautstärke Sprecher</span>
                <input type="range" min={0} max={1.5} step={0.05} value={film.voiceVolume} onChange={(e) => update((f) => ({ ...f, voiceVolume: Number(e.target.value) }))} className="w-full accent-sky-400" />
              </label>
              <label className="block space-y-1">
                <span className="text-xs text-muted-foreground">Lautstärke Musik</span>
                <input type="range" min={0} max={1} step={0.05} value={film.musicVolume} onChange={(e) => update((f) => ({ ...f, musicVolume: Number(e.target.value) }))} className="w-full accent-emerald-400" />
              </label>
              <div className="flex flex-wrap gap-2">
                <Button size="sm" variant="secondary" onClick={() => { setCustomMusic(null); setMusicSeed((s) => s + 1); }}><Wand2 className="h-4 w-4 mr-1.5" />Neue Musik</Button>
                <Button size="sm" variant="outline" asChild>
                  <label className="cursor-pointer"><Upload className="h-4 w-4 mr-1.5" />Eigene Musik
                    <input type="file" accept="audio/*" className="hidden" onChange={(e) => { void uploadMusic(e.target.files?.[0]); e.target.value = ""; }} />
                  </label>
                </Button>
              </div>
              {customMusic && <p className="text-xs text-muted-foreground truncate">Musik: {customMusic.name} (nur in dieser Sitzung)</p>}
              <label className="flex items-center justify-between gap-2 text-sm">
                Untertitel
                <Switch checked={film.subtitles} onCheckedChange={(v) => update((f) => ({ ...f, subtitles: v }))} />
              </label>
              <div className="space-y-2 border-t border-white/10 pt-3">
                <span className="text-xs text-muted-foreground">Szene hinzufügen</span>
                <Input value={wish} onChange={(e) => setWish(e.target.value)} placeholder="Was soll passieren? (optional)"
                  onKeyDown={(e) => { if (e.key === "Enter") void addScene(); }} />
                <Button size="sm" className="w-full" onClick={addScene} disabled={addingScene || busy}>
                  {addingScene ? <Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> : <Plus className="h-4 w-4 mr-1.5" />}Mythos schreibt die nächste Szene
                </Button>
              </div>
            </section>
          </div>
        </>
      )}

      {/* Gespeicherte Filme */}
      {films.length > 0 && (
        <section className="glass rounded-2xl p-3 md:p-4 space-y-2">
          <div className="flex items-center gap-2 font-display"><FolderOpen className="h-4 w-4" />Meine Filme</div>
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {films.map((f) => (
              <div key={f.id} className={`flex items-center gap-2 rounded-xl border p-2 ${film?.id === f.id ? "border-fuchsia-400/50" : "border-white/10"}`}>
                <button className="flex min-w-0 flex-1 items-center gap-2 text-left" onClick={() => openFilm(f)} disabled={busy}>
                  {f.scenes[0]?.image
                    ? <img src={f.scenes[0].image} alt="" className="h-10 w-16 shrink-0 rounded object-cover" loading="lazy" />
                    : <div className="h-10 w-16 shrink-0 rounded bg-gradient-to-br from-indigo-900 to-fuchsia-900" />}
                  <span className="min-w-0">
                    <span className="block truncate text-sm">{f.title}</span>
                    <span className="block text-xs text-muted-foreground">{f.scenes.length} Szenen · {new Date(f.updated_at).toLocaleDateString("de-DE")}</span>
                  </span>
                </button>
                <Button size="icon" variant="ghost" aria-label="Film löschen" disabled={busy} onClick={() => {
                  deleteFilm(f.id); setFilms(listFilms());
                  if (film?.id === f.id) { stopPlayback(); setFilm(null); }
                }}><Trash2 className="h-4 w-4" /></Button>
              </div>
            ))}
          </div>
        </section>
      )}
    </LabsShell>
  );
}
