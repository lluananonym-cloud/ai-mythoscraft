import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import type { RealtimeChannel } from "@supabase/supabase-js";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Card } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import {
  Users, Plus, Send, Sparkles, Link2, LogOut as LogOutIcon, Paperclip, FileText, StickyNote,
  Download, Save, MessageSquare, DoorOpen,
} from "lucide-react";
import { toast } from "sonner";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

// Gemeinsame Räume: mehrere Leute arbeiten im selben Chat mit Mythos, sehen sich live
// (wer ist da, Live-Cursor, wer schreibt), teilen Dateien und eine gemeinsame Notiz.
// Alles läuft über die bestehenden Tabellen friend_groups/group_members/group_messages
// plus Supabase Realtime (Presence + Broadcast) – ohne neue Tabelle oder Server-Funktion.

type Group = { id: string; name: string; description: string | null; ai_enabled: boolean; invite_code: string; owner_id: string };
type FileMeta = { name: string; url: string; type?: string; size?: number };
type GMsg = {
  id: string; sender_name: string; role: string; content: string; created_at: string; sender_id: string | null;
  metadata?: { file?: FileMeta } | null;
};
type Peer = { userId: string; name: string; color: string };
type Cursor = { x: number; y: number; name: string; color: string; at: number };

const COLORS = ["#f97316", "#22c55e", "#3b82f6", "#a855f7", "#ec4899", "#eab308", "#14b8a6", "#ef4444"];
const colorFor = (id: string) => { let h = 0; for (const c of id) h = (h * 31 + c.charCodeAt(0)) >>> 0; return COLORS[h % COLORS.length]; };
const AI_RE = /(^|\s)@(ai|mythos)\b/i;
const FILE_RE = /^📎 Datei geteilt: \[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/;
const UUID_CODE_RE = /([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})[.:_-]([a-z0-9]+)/i;
const MAX_FILE = 20 * 1024 * 1024;
const isImage = (f: FileMeta) => (f.type || "").startsWith("image/") || /\.(png|jpe?g|gif|webp|svg)$/i.test(f.name);
const fileOf = (m: GMsg): FileMeta | null => {
  if (m.metadata?.file?.url) return m.metadata.file;
  const x = m.content.match(FILE_RE);
  return x ? { name: x[1], url: x[2] } : null;
};
const fmtSize = (n?: number) => (!n ? "" : n < 1024 * 1024 ? `${Math.max(1, Math.round(n / 1024))} KB` : `${(n / 1024 / 1024).toFixed(1)} MB`);
const noteKey = (id: string) => `mythos-raum-notiz-${id}`;

const Groups = () => {
  const { user, profile } = useAuth();
  const [params, setParams] = useSearchParams();
  const [groups, setGroups] = useState<Group[]>([]);
  const [active, setActive] = useState<Group | null>(null);
  const [msgs, setMsgs] = useState<GMsg[]>([]);
  const [text, setText] = useState("");
  const [newName, setNewName] = useState("");
  const [joinCode, setJoinCode] = useState("");
  const [peers, setPeers] = useState<Peer[]>([]);
  const [cursors, setCursors] = useState<Record<string, Cursor>>({});
  const [typing, setTyping] = useState<Record<string, { name: string; at: number }>>({});
  const [note, setNote] = useState("");
  const [side, setSide] = useState<"notiz" | "dateien">("notiz");
  const [showSide, setShowSide] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [, setTick] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);
  const roomRef = useRef<HTMLDivElement>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const channelRef = useRef<RealtimeChannel | null>(null);
  const noteRef = useRef({ text: "", at: 0 });
  const lastCursor = useRef(0);
  const lastTyping = useRef(0);
  const noteTimer = useRef<number>();

  const myName = profile?.display_name || profile?.mc_username || user?.email?.split("@")[0] || "Gast";
  const myColor = user ? colorFor(user.id) : COLORS[0];

  const loadGroups = async () => {
    const { data } = await supabase.from("friend_groups").select("*").order("updated_at", { ascending: false });
    if (data) setGroups(data as Group[]);
    return (data || []) as Group[];
  };

  // Beim Laden: ?join=<id>.<code> tritt einem Raum bei, ?raum=<id> öffnet ihn direkt.
  useEffect(() => {
    if (!user) return;
    loadGroups().then((list) => {
      const join = params.get("join");
      const raum = params.get("raum");
      if (join) joinWith(join);
      else if (raum) { const g = list.find((x) => x.id === raum); if (g) setActive(g); }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  // Raum öffnen: Verlauf laden, Live-Kanal für Nachrichten, Anwesenheit, Cursor, Tippen und Notiz.
  useEffect(() => {
    if (!active || !user) return;
    setMsgs([]); setPeers([]); setCursors({}); setTyping({});
    let cached = { text: "", at: 0 };
    try { cached = JSON.parse(localStorage.getItem(noteKey(active.id)) || "") || cached; } catch { /* leer */ }
    noteRef.current = cached; setNote(cached.text);

    supabase.from("group_messages").select("*").eq("group_id", active.id).order("created_at").then(({ data }) => {
      if (data) setMsgs(data as GMsg[]);
    });

    const ch = supabase.channel(`raum-${active.id}`, { config: { presence: { key: user.id }, broadcast: { self: false } } });
    ch.on("postgres_changes", { event: "INSERT", schema: "public", table: "group_messages", filter: `group_id=eq.${active.id}` },
      (payload) => {
        const m = payload.new as GMsg;
        setMsgs((prev) => (prev.some((x) => x.id === m.id) ? prev : [...prev, m]));
        if (m.sender_id) setTyping((t) => { const n = { ...t }; delete n[m.sender_id!]; return n; });
      })
      .on("presence", { event: "sync" }, () => {
        const st = ch.presenceState<{ name: string; color: string }>();
        const list = Object.entries(st).map(([userId, metas]) => ({ userId, name: metas[0]?.name || "?", color: metas[0]?.color || colorFor(userId) }));
        setPeers(list);
        setCursors((c) => Object.fromEntries(Object.entries(c).filter(([id]) => st[id])));
      })
      .on("broadcast", { event: "cursor" }, ({ payload }) => {
        setCursors((c) => ({ ...c, [payload.id]: { x: payload.x, y: payload.y, name: payload.name, color: payload.color, at: Date.now() } }));
      })
      .on("broadcast", { event: "typing" }, ({ payload }) => {
        setTyping((t) => ({ ...t, [payload.id]: { name: payload.name, at: Date.now() } }));
      })
      .on("broadcast", { event: "note" }, ({ payload }) => {
        if (typeof payload.text !== "string" || !(payload.at > noteRef.current.at)) return;
        noteRef.current = { text: payload.text, at: payload.at };
        setNote(payload.text);
        try { localStorage.setItem(noteKey(active.id), JSON.stringify(noteRef.current)); } catch { /* voll */ }
      })
      .on("broadcast", { event: "note-req" }, () => {
        if (noteRef.current.at) ch.send({ type: "broadcast", event: "note", payload: noteRef.current });
      })
      .subscribe(async (status) => {
        if (status !== "SUBSCRIBED") return;
        await ch.track({ name: myName, color: myColor });
        ch.send({ type: "broadcast", event: "note-req", payload: {} });
        // eigenen Stand mitschicken, falls er neuer ist als der der anderen
        if (noteRef.current.at) ch.send({ type: "broadcast", event: "note", payload: noteRef.current });
      });
    channelRef.current = ch;
    return () => { channelRef.current = null; supabase.removeChannel(ch); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active?.id, user?.id]);

  // Alte Cursor und „schreibt…“-Hinweise ausblenden
  useEffect(() => {
    const t = window.setInterval(() => setTick((n) => n + 1), 1500);
    return () => window.clearInterval(t);
  }, []);

  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" }); }, [msgs]);

  const broadcast = (event: string, payload: Record<string, unknown>) =>
    channelRef.current?.send({ type: "broadcast", event, payload });

  const onPointerMove = (e: React.PointerEvent) => {
    const now = Date.now();
    if (now - lastCursor.current < 50 || !roomRef.current || !user) return;
    lastCursor.current = now;
    const r = roomRef.current.getBoundingClientRect();
    broadcast("cursor", { id: user.id, name: myName, color: myColor, x: (e.clientX - r.left) / r.width, y: (e.clientY - r.top) / r.height });
  };
  const onPointerLeave = () => { if (user) broadcast("cursor", { id: user.id, name: myName, color: myColor, x: -1, y: -1 }); };

  const selectGroup = (g: Group) => {
    setActive(g); setShowSide(false);
    setParams((p) => { const n = new URLSearchParams(p); n.delete("join"); n.set("raum", g.id); return n; }, { replace: true });
  };

  const createGroup = async () => {
    if (!newName.trim()) return;
    const { data, error } = await supabase.from("friend_groups").insert({ owner_id: user!.id, name: newName.trim() }).select().single();
    if (error) return toast.error(error.message);
    await supabase.from("group_members").insert({ group_id: data.id, user_id: user!.id, role: "owner" });
    setNewName(""); loadGroups(); selectGroup(data as Group);
    toast.success("Raum erstellt! Teile den Einladungslink, damit andere mitmachen.");
  };

  // Beitreten per Einladungslink (enthält Raum-ID und Code) oder per Code.
  const joinWith = async (raw: string) => {
    if (!user || !raw.trim()) return;
    const m = raw.trim().match(UUID_CODE_RE);
    let g: Group | null = null;
    if (m) {
      const [, id, code] = m;
      const { error } = await supabase.from("group_members").insert({ group_id: id, user_id: user.id });
      const fresh = !error;
      if (error && error.code !== "23505") return toast.error("Einladung ungültig");
      const { data } = await supabase.from("friend_groups").select("*").eq("id", id).maybeSingle();
      if (!data || data.invite_code.toLowerCase() !== code.toLowerCase()) {
        if (fresh) await supabase.from("group_members").delete().eq("group_id", id).eq("user_id", user.id);
        return toast.error("Einladung ungültig");
      }
      g = data as Group;
    } else {
      const { data } = await supabase.from("friend_groups").select("*").eq("invite_code", raw.trim().toLowerCase()).maybeSingle();
      if (!data) return toast.error("Code nicht gefunden. Am sichersten klappt es mit dem Einladungslink aus dem Raum.");
      const { error } = await supabase.from("group_members").insert({ group_id: data.id, user_id: user.id });
      if (error && error.code !== "23505") return toast.error(error.message);
      g = data as Group;
    }
    setJoinCode(""); await loadGroups(); selectGroup(g);
    toast.success(`Beigetreten: ${g.name}`);
  };

  const leaveGroup = async () => {
    if (!active || !user) return;
    await supabase.from("group_members").delete().eq("group_id", active.id).eq("user_id", user.id);
    setActive(null); loadGroups();
    setParams((p) => { const n = new URLSearchParams(p); n.delete("raum"); return n; }, { replace: true });
    toast.success("Raum verlassen");
  };

  const askAI = () => {
    if (!active || !user) return;
    if (!active.ai_enabled) return toast.info("Mythos ist in diesem Raum ausgeschaltet.");
    supabase.functions.invoke("group-chat", { body: { groupId: active.id, userId: user.id } }).catch(() => {});
  };

  const post = async (content: string, metadata?: GMsg["metadata"]) => {
    if (!active || !user) return;
    const { data, error } = await supabase.from("group_messages").insert({
      group_id: active.id, sender_id: user.id, sender_name: myName, role: "user", content, metadata: metadata ?? null,
    }).select().single();
    if (error) { toast.error(error.message); return; }
    setMsgs((prev) => (prev.some((x) => x.id === data.id) ? prev : [...prev, data as GMsg]));
    // Mythos antwortet bei @mythos/@ai oder einer Frage
    if (active.ai_enabled && (AI_RE.test(content) || /\?\s*$/.test(content))) askAI();
  };

  const send = async () => {
    if (!text.trim()) return;
    const content = text;
    setText("");
    await post(content);
  };

  const onType = (v: string) => {
    setText(v);
    const now = Date.now();
    if (user && v && now - lastTyping.current > 1500) { lastTyping.current = now; broadcast("typing", { id: user.id, name: myName }); }
  };

  const shareFile = async (file: File) => {
    if (!active || !user) return;
    if (file.size > MAX_FILE) return toast.error(`${file.name} ist zu groß (max. 20 MB).`);
    const safe = file.name.replace(/[^\w.-]+/g, "_").slice(-80) || "datei";
    const path = `${user.id}/raeume/${active.id}/${Date.now()}-${safe}`;
    const { error } = await supabase.storage.from("chat-uploads").upload(path, file, { contentType: file.type || undefined });
    if (error) return toast.error(`Hochladen fehlgeschlagen: ${error.message}`);
    const url = supabase.storage.from("chat-uploads").getPublicUrl(path).data.publicUrl;
    const name = file.name.replace(/[[\]()]/g, "") || safe;
    await post(`📎 Datei geteilt: [${name}](${url})`, { file: { name, url, type: file.type, size: file.size } });
  };
  const shareFiles = async (list: FileList | File[] | null) => {
    const files = Array.from(list || []);
    if (!files.length) return;
    setUploading(true);
    try { for (const f of files) await shareFile(f); } finally { setUploading(false); }
  };

  const onNote = (v: string) => {
    setNote(v);
    noteRef.current = { text: v, at: Date.now() };
    try { if (active) localStorage.setItem(noteKey(active.id), JSON.stringify(noteRef.current)); } catch { /* voll */ }
    window.clearTimeout(noteTimer.current);
    noteTimer.current = window.setTimeout(() => broadcast("note", noteRef.current), 150);
  };
  const saveNote = async () => {
    if (!note.trim()) return toast.info("Die Notiz ist noch leer.");
    const day = new Date().toISOString().slice(0, 10);
    await shareFiles([new File([note], `notiz-${day}.md`, { type: "text/markdown" })]);
    toast.success("Notiz als Datei im Raum gespeichert");
  };
  const noteToMythos = async () => {
    if (!note.trim()) return toast.info("Die Notiz ist noch leer.");
    if (!active?.ai_enabled) return toast.info("Mythos ist in diesem Raum ausgeschaltet.");
    await post(`@mythos Schau dir unsere gemeinsame Notiz an und hilf uns weiter:\n\n${note.slice(0, 4000)}`);
  };

  const toggleAI = async (val: boolean) => {
    if (!active) return;
    const { error } = await supabase.from("friend_groups").update({ ai_enabled: val }).eq("id", active.id);
    if (error) return toast.error(error.message);
    setActive({ ...active, ai_enabled: val });
  };

  const inviteLink = active ? `${window.location.origin}/raeume?join=${active.id}.${active.invite_code}` : "";
  const now = Date.now();
  const liveCursors = Object.entries(cursors).filter(([, c]) => c.x >= 0 && now - c.at < 8000);
  const typers = Object.entries(typing).filter(([id, t]) => id !== user?.id && now - t.at < 3500).map(([, t]) => t.name);
  const sharedFiles = msgs.map((m) => ({ m, f: fileOf(m) })).filter((x): x is { m: GMsg; f: FileMeta } => !!x.f).reverse();
  const isOwner = active?.owner_id === user?.id;

  return (
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <main className="container py-4 grid grid-cols-1 md:grid-cols-[260px_1fr] gap-4 md:h-[calc(100vh-5rem)]">
        <aside className="glass rounded-2xl p-3 flex flex-col gap-3 overflow-hidden">
          <div className="flex items-center gap-2"><Users className="h-4 w-4" /><h2 className="font-semibold text-sm">Gemeinsame Räume</h2></div>
          <p className="text-[11px] text-muted-foreground -mt-2">Zusammen mit Freunden und Mythos chatten, Dateien teilen und an einer Notiz arbeiten.</p>
          <div className="flex gap-1">
            <Input placeholder="Neuer Raum" value={newName} onChange={(e) => setNewName(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") createGroup(); }} className="h-8 text-xs" />
            <Button size="icon" className="h-8 w-8" onClick={createGroup} title="Raum erstellen"><Plus className="h-3.5 w-3.5" /></Button>
          </div>
          <div className="flex gap-1">
            <Input placeholder="Einladungslink oder Code" value={joinCode} onChange={(e) => setJoinCode(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") joinWith(joinCode); }} className="h-8 text-xs" />
            <Button size="icon" variant="outline" className="h-8 w-8" onClick={() => joinWith(joinCode)} title="Beitreten"><LogOutIcon className="h-3.5 w-3.5 rotate-180" /></Button>
          </div>
          <div className="flex-1 overflow-y-auto space-y-1 -mx-1 px-1 max-h-48 md:max-h-none">
            {groups.map((g) => (
              <button key={g.id} onClick={() => selectGroup(g)}
                className={`w-full text-left rounded-lg px-2 py-2 text-sm transition-colors ${active?.id === g.id ? "bg-primary/15" : "hover:bg-secondary/50"}`}>
                <div className="font-medium truncate">{g.name}</div>
                <div className="text-[10px] text-muted-foreground">{g.owner_id === user?.id ? "Dein Raum" : "Mitglied"}</div>
              </button>
            ))}
            {groups.length === 0 && <p className="text-xs text-muted-foreground p-2">Noch keine Räume. Erstell einen oder tritt per Einladungslink bei.</p>}
          </div>
        </aside>

        <Card ref={roomRef} onPointerMove={onPointerMove} onPointerLeave={onPointerLeave}
          onDragOver={(e) => { if (active && e.dataTransfer.types.includes("Files")) { e.preventDefault(); setDragOver(true); } }}
          onDragLeave={(e) => { if (e.currentTarget === e.target) setDragOver(false); }}
          onDrop={(e) => { if (!active) return; e.preventDefault(); setDragOver(false); shareFiles(e.dataTransfer.files); }}
          className="glass-strong rounded-2xl flex flex-col overflow-hidden relative min-h-[70vh] md:min-h-0">
          {!active ? (
            <div className="flex-1 flex items-center justify-center text-muted-foreground text-sm p-6 text-center">Wähle einen Raum oder erstell einen neuen.</div>
          ) : (
            <>
              <div className="border-b border-white/5 p-3 flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <h3 className="font-display text-base truncate">{active.name}</h3>
                  <div className="flex items-center gap-1 mt-1">
                    {peers.map((p) => (
                      <span key={p.userId} title={`${p.name}${p.userId === user?.id ? " (du)" : ""} ist da`}
                        className="h-6 w-6 rounded-full grid place-items-center text-[10px] font-semibold text-white ring-2 ring-background"
                        style={{ background: p.color }}>{p.name.slice(0, 1).toUpperCase()}</span>
                    ))}
                    <span className="text-[10px] text-muted-foreground ml-1">{peers.length} online</span>
                  </div>
                </div>
                <div className="flex items-center gap-1.5 flex-wrap">
                  <Button size="sm" variant="outline" className="h-7 text-xs"
                    onClick={() => { navigator.clipboard.writeText(inviteLink); toast.success("Einladungslink kopiert"); }}>
                    <Link2 className="h-3 w-3 mr-1" />Einladen
                  </Button>
                  <Button size="sm" variant="outline" className="h-7 text-xs lg:hidden" onClick={() => setShowSide((v) => !v)}>
                    {showSide ? <><MessageSquare className="h-3 w-3 mr-1" />Chat</> : <><StickyNote className="h-3 w-3 mr-1" />Notiz & Dateien</>}
                  </Button>
                  <label className="flex items-center gap-1.5 text-xs" title={isOwner ? "Mythos im Raum an/aus" : "Nur der Ersteller kann Mythos ein- oder ausschalten"}>
                    <Sparkles className="h-3.5 w-3.5" /> Mythos
                    <Switch checked={active.ai_enabled} onCheckedChange={toggleAI} disabled={!isOwner} />
                  </label>
                  {!isOwner && (
                    <Button size="icon" variant="ghost" className="h-7 w-7" onClick={leaveGroup} title="Raum verlassen"><DoorOpen className="h-3.5 w-3.5" /></Button>
                  )}
                </div>
              </div>

              <div className="flex-1 min-h-0 grid grid-cols-1 lg:grid-cols-[1fr_300px]">
                <div className={`${showSide ? "hidden lg:flex" : "flex"} flex-col min-h-0`}>
                  <div ref={scrollRef} className="flex-1 overflow-y-auto p-4 space-y-3">
                    {msgs.map((m) => {
                      const f = fileOf(m);
                      const mine = m.sender_id === user?.id;
                      return (
                        <div key={m.id} className={`flex flex-col ${mine ? "items-end" : "items-start"}`}>
                          <span className="text-[10px] text-muted-foreground mb-0.5">
                            {m.role === "assistant" ? "✨ Mythos" : m.sender_name}
                          </span>
                          <div className={`max-w-[80%] rounded-2xl px-3 py-2 text-sm ${
                            m.role === "assistant" ? "glass" : mine ? "bg-primary/20 border border-primary/30" : "bg-secondary/40"
                          }`}>
                            {f ? (
                              <a href={f.url} target="_blank" rel="noreferrer" className="block">
                                {isImage(f) && <img src={f.url} alt={f.name} className="max-h-48 rounded-lg mb-1" loading="lazy" />}
                                <span className="flex items-center gap-1.5"><FileText className="h-3.5 w-3.5 shrink-0" /><span className="underline break-all">{f.name}</span>
                                  {f.size ? <span className="text-[10px] text-muted-foreground">{fmtSize(f.size)}</span> : null}</span>
                              </a>
                            ) : m.role === "assistant" ? (
                              <div className="prose-mythos break-words"><ReactMarkdown remarkPlugins={[remarkGfm]}>{m.content}</ReactMarkdown></div>
                            ) : <p className="whitespace-pre-wrap break-words">{m.content}</p>}
                          </div>
                        </div>
                      );
                    })}
                    {msgs.length === 0 && <p className="text-center text-muted-foreground text-sm">Noch keine Nachrichten. Tipp: schreib „@mythos …“, um Mythos dazuzuholen, oder zieh eine Datei hier rein.</p>}
                  </div>
                  <div className="h-4 px-4 text-[10px] text-muted-foreground">
                    {typers.length > 0 && `${typers.join(", ")} ${typers.length === 1 ? "schreibt" : "schreiben"} …`}
                  </div>
                  <div className="border-t border-white/5 p-2 flex gap-2">
                    <input ref={fileInput} type="file" multiple className="hidden" onChange={(e) => { shareFiles(e.target.files); e.target.value = ""; }} />
                    <Button size="icon" variant="ghost" onClick={() => fileInput.current?.click()} disabled={uploading} title="Datei teilen">
                      <Paperclip className={`h-4 w-4 ${uploading ? "animate-pulse" : ""}`} />
                    </Button>
                    <Input value={text} onChange={(e) => onType(e.target.value)}
                      onKeyDown={(e) => { if (e.key === "Enter") send(); }}
                      placeholder="Nachricht … (@mythos holt Mythos dazu)" className="bg-transparent border-white/10" />
                    <Button onClick={send} size="icon" className="bg-foreground text-background hover:bg-foreground/90">
                      <Send className="h-4 w-4" />
                    </Button>
                  </div>
                </div>

                <div className={`${showSide ? "flex" : "hidden lg:flex"} flex-col min-h-0 border-t lg:border-t-0 lg:border-l border-white/5`}>
                  <div className="flex gap-1 p-2 border-b border-white/5">
                    <Button size="sm" variant={side === "notiz" ? "secondary" : "ghost"} className="h-7 text-xs flex-1" onClick={() => setSide("notiz")}>
                      <StickyNote className="h-3 w-3 mr-1" />Notiz
                    </Button>
                    <Button size="sm" variant={side === "dateien" ? "secondary" : "ghost"} className="h-7 text-xs flex-1" onClick={() => setSide("dateien")}>
                      <FileText className="h-3 w-3 mr-1" />Dateien ({sharedFiles.length})
                    </Button>
                  </div>
                  {side === "notiz" ? (
                    <div className="flex-1 flex flex-col gap-2 p-2 min-h-0">
                      <Textarea value={note} onChange={(e) => onNote(e.target.value)}
                        placeholder="Gemeinsame Notiz: alle im Raum sehen und bearbeiten sie live. Ideen, To-dos, Pläne …"
                        className="flex-1 min-h-[240px] resize-none bg-transparent border-white/10 text-sm" />
                      <div className="flex gap-1">
                        <Button size="sm" variant="outline" className="h-7 text-xs flex-1" onClick={noteToMythos}><Sparkles className="h-3 w-3 mr-1" />Mythos fragen</Button>
                        <Button size="sm" variant="outline" className="h-7 text-xs flex-1" onClick={saveNote}><Save className="h-3 w-3 mr-1" />Als Datei</Button>
                      </div>
                    </div>
                  ) : (
                    <div className="flex-1 overflow-y-auto p-2 space-y-1">
                      <Button size="sm" variant="outline" className="h-7 text-xs w-full mb-1" onClick={() => fileInput.current?.click()} disabled={uploading}>
                        <Paperclip className="h-3 w-3 mr-1" />{uploading ? "Lädt hoch …" : "Datei teilen"}
                      </Button>
                      {sharedFiles.map(({ m, f }) => (
                        <a key={m.id} href={f.url} target="_blank" rel="noreferrer" download
                          className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-xs hover:bg-secondary/50">
                          {isImage(f) ? <img src={f.url} alt="" className="h-8 w-8 rounded object-cover shrink-0" loading="lazy" /> : <FileText className="h-4 w-4 shrink-0" />}
                          <span className="min-w-0 flex-1">
                            <span className="block truncate">{f.name}</span>
                            <span className="block text-[10px] text-muted-foreground">{m.sender_name}{f.size ? ` · ${fmtSize(f.size)}` : ""}</span>
                          </span>
                          <Download className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                        </a>
                      ))}
                      {sharedFiles.length === 0 && <p className="text-xs text-muted-foreground p-2">Noch keine Dateien. Zieh eine Datei in den Raum oder nutz die Büroklammer.</p>}
                    </div>
                  )}
                </div>
              </div>

              {dragOver && (
                <div className="absolute inset-0 z-20 grid place-items-center bg-background/70 backdrop-blur-sm border-2 border-dashed border-primary rounded-2xl pointer-events-none">
                  <p className="text-sm font-medium">Loslassen, um die Datei mit dem Raum zu teilen</p>
                </div>
              )}
              {/* Live-Cursor der anderen */}
              <div className="pointer-events-none absolute inset-0 z-30 overflow-hidden">
                {liveCursors.map(([id, c]) => (
                  <div key={id} className="absolute transition-[left,top] duration-75 ease-linear"
                    style={{ left: `${c.x * 100}%`, top: `${c.y * 100}%` }}>
                    <svg width="16" height="16" viewBox="0 0 16 16" className="drop-shadow">
                      <path d="M1 1 L1 13 L4.5 9.5 L7 15 L9 14 L6.5 8.5 L11.5 8.5 Z" fill={c.color} stroke="white" strokeWidth="1" />
                    </svg>
                    <span className="absolute left-3.5 top-3.5 whitespace-nowrap rounded-full px-1.5 py-0.5 text-[10px] font-medium text-white" style={{ background: c.color }}>
                      {c.name}
                    </span>
                  </div>
                ))}
              </div>
            </>
          )}
        </Card>
      </main>
    </div>
  );
};
export default Groups;
