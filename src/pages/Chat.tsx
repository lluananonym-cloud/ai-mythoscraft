import { useState, useEffect, useRef } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import MinecraftAvatar from "@/components/MinecraftAvatar";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  Plus, Send, Trash2, MessageSquare, Loader2, Sparkles, Brain, HelpCircle, Menu,
  Mic, MicOff, Volume2, VolumeX, Paperclip, X as XIcon, Drama, Copy, Download, Lightbulb,
  Image as ImageIcon, Music, Globe, FileText, Languages, UserCog, WifiOff, Smile, AudioLines, Film,
} from "lucide-react";
import { useSubscription } from "@/hooks/useSubscription";
import Paywall from "@/components/Paywall";

const SLASH_COMMANDS = [
  { cmd: "/image",     args: "<beschreibung>",  icon: ImageIcon, desc: "Bild generieren (Nano Banana)" },
  { cmd: "/music",     args: "<stil/vibe>",     icon: Music,     desc: "Echten KI-Song generieren (MusicGen im Browser, kostenlos)" },
  { cmd: "/video",     args: "<szene>",         icon: Film,      desc: "Kurzes KI-Video (Bild + Animation, kostenlos im Browser)" },
  { cmd: "/research",  args: "<thema>",         icon: Globe,     desc: "Deep Research mit Web-Suche" },
  { cmd: "/translate", args: "<sprache> [text]",icon: Languages, desc: "Übersetzen (letzte AI-Antwort wenn ohne Text)" },
  { cmd: "/summarize", args: "",                icon: FileText,  desc: "Konversation zusammenfassen" },
  { cmd: "/identity",  args: "<name>",          icon: UserCog,   desc: "AI-Persona im Chat wechseln" },
  { cmd: "/offline",   args: "<frage>",         icon: WifiOff,   desc: "Offline-Chat im Browser (Qwen2.5-0.5B, ~500MB einmalig)" },
  { cmd: "/offline-summary", args: "<text>",    icon: FileText,  desc: "Offline-Zusammenfassung (DistilBART, ~250MB)" },
  { cmd: "/sentiment", args: "<text>",          icon: Smile,     desc: "Offline-Stimmungsanalyse (~65MB)" },
];
import { toast } from "sonner";
import { useVoiceMode } from "@/hooks/useVoiceMode";
import FunkPlayer, { type FunkPattern } from "@/components/FunkPlayer";
import SongPlayer, { type SongRequest } from "@/components/SongPlayer";
import VideoPlayer, { type VideoRequest } from "@/components/VideoPlayer";
import OfflineAI, { type OfflineTask } from "@/components/OfflineAI";
import { Link } from "react-router-dom";

type Persona = { id: string; name: string; avatar_emoji: string | null };
type Attachment = { url: string; name: string; mime: string };

type Conv = { id: string; title: string; mode: string; updated_at: string };
type Msg = { id?: string; role: "user" | "assistant" | "tool"; content: string; metadata?: any; image?: { url: string; prompt: string }; music?: FunkPattern; song?: SongRequest; video?: VideoRequest; offline?: OfflineTask; attachments?: Attachment[] };

const MODES = [
  { value: "support", label: "Support", icon: HelpCircle, desc: "Mythoscraft Server-Support" },
  { value: "agent", label: "Agent", icon: Brain, desc: "Mit Web-Suche & Tools" },
  { value: "general", label: "General", icon: Sparkles, desc: "Allgemeiner KI-Chat" },
];

const Chat = () => {
  const { user, profile } = useAuth();
  const sub = useSubscription();
  const [paywall, setPaywall] = useState<{ open: boolean; reason?: string }>({ open: false });
  const [convs, setConvs] = useState<Conv[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [mode, setMode] = useState("support");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [voiceMode, setVoiceMode] = useState(false);
  const [personas, setPersonas] = useState<Persona[]>([]);
  const [personaId, setPersonaId] = useState<string>("none");
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [uploading, setUploading] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [slashIndex, setSlashIndex] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const lastSpokenRef = useRef<string>("");
  const sendRef = useRef<(text?: string) => void>(() => {});
  const voice = useVoiceMode({
    lang: "de-DE",
    onTranscript: (t) => { sendRef.current?.(t); },
    onDictation: (t) => {
      setInput(prev => (prev ? prev.trimEnd() + " " + t : t));
    },
  });

  const loadConvs = async () => {
    const { data } = await supabase.from("conversations").select("*").order("updated_at", { ascending: false });
    if (data) setConvs(data as any);
  };

  useEffect(() => { if (user) loadConvs(); }, [user]);

  // Load personas (own + public)
  useEffect(() => {
    if (!user) return;
    supabase.from("ai_personas").select("id,name,avatar_emoji").or(`user_id.eq.${user.id},is_public.eq.true`).then(({ data }) => {
      if (data) setPersonas(data as Persona[]);
    });
  }, [user]);

  const uploadFiles = async (files: FileList | null) => {
    if (!files || !user) return;
    setUploading(true);
    const newAtts: Attachment[] = [];
    for (const file of Array.from(files).slice(0, 5)) {
      if (file.size > 20 * 1024 * 1024) { toast.error(`${file.name}: max 20MB`); continue; }
      const path = `${user.id}/${Date.now()}-${file.name.replace(/[^a-zA-Z0-9._-]/g, "_")}`;
      const { error } = await supabase.storage.from("chat-uploads").upload(path, file);
      if (error) { toast.error(`Upload fehlgeschlagen: ${file.name}`); continue; }
      const { data: pub } = supabase.storage.from("chat-uploads").getPublicUrl(path);
      newAtts.push({ url: pub.publicUrl, name: file.name, mime: file.type });
    }
    setAttachments(prev => [...prev, ...newAtts]);
    setUploading(false);
    if (newAtts.length) toast.success(`${newAtts.length} Datei(en) angehängt`);
  };


  const loadMessages = async (id: string) => {
    setActiveId(id);
    setSidebarOpen(false);
    const { data } = await supabase.from("messages").select("*").eq("conversation_id", id).order("created_at");
    if (data) {
      const enriched = (data as any[]).map(m => ({
        ...m,
        image: m.metadata?.image,
        music: m.metadata?.music,
        song: m.metadata?.song,
        video: m.metadata?.video,
        offline: m.metadata?.offline,
        attachments: m.metadata?.attachments,
      })) as Msg[];
      setMessages(enriched);
    }
    const c = convs.find(c => c.id === id);
    if (c) setMode(c.mode);
  };

  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" }); }, [messages]);

  const newChat = () => {
    setActiveId(null);
    setMessages([]);
    setSidebarOpen(false);
  };

  const deleteChat = async (id: string) => {
    await supabase.from("conversations").delete().eq("id", id);
    if (activeId === id) { setActiveId(null); setMessages([]); }
    loadConvs();
  };

  const send = async (override?: string) => {
    const text = (override ?? input).trim();
    if (!text || sending) return;
    // Pro gating
    if (sub.chatLimitReached) { setPaywall({ open: true, reason: `Du hast dein tägliches Free-Limit (${20} Chats) erreicht.` }); return; }
    if (/^\/image\b/i.test(text) && !sub.canGenerateImage) { setPaywall({ open: true, reason: "Bilder generieren ist eine Pro-Funktion." }); return; }
    if (/^\/music\b/i.test(text) && !sub.canGenerateMusic) { setPaywall({ open: true, reason: "Musik generieren ist eine Pro-Funktion." }); return; }
    if (!override) setInput("");
    setSending(true);

    let convId = activeId;
    if (!convId) {
      const { data, error } = await supabase.from("conversations")
        .insert({ user_id: user!.id, title: text.slice(0, 50), mode })
        .select().single();
      if (error || !data) { toast.error("Chat konnte nicht erstellt werden"); setSending(false); return; }
      convId = data.id;
      setActiveId(convId);
      loadConvs();
    }

    // Intercept /music — generate real AI music in the browser (MusicGen, free)
    const musicMatch = text.match(/^\/music\s+(.+)$/i);
    if (musicMatch) {
      const prompt = musicMatch[1].trim();
      const song: SongRequest = { prompt, title: prompt.slice(0, 60), duration: 10 };
      const userMsg: Msg = { role: "user", content: text };
      const aiMsg: Msg = {
        role: "assistant",
        content: `🎵 **AI-Song wird vorbereitet:** _${prompt}_\n\nKlick unten auf "Generieren". Der erste Song lädt das Modell (~300MB einmalig), dann läuft alles offline im Browser — kostenlos.`,
        song,
      };
      setMessages(prev => [...prev, userMsg, aiMsg]);
      await supabase.from("messages").insert([
        { conversation_id: convId, role: "user", content: text },
        { conversation_id: convId, role: "assistant", content: aiMsg.content, metadata: { song } },
      ]);
      await supabase.from("conversations").update({ updated_at: new Date().toISOString() }).eq("id", convId);
      setSending(false);
      loadConvs();
      return;
    }

    // Intercept /video — generate cinematic clip 100% client-side (image gen + Ken Burns)
    const videoMatch = text.match(/^\/video\s+(.+)$/i);
    if (videoMatch) {
      const prompt = videoMatch[1].trim();
      const video: VideoRequest = { prompt, title: prompt.slice(0, 60), duration: 5, motion: "kenburns" };
      const userMsg: Msg = { role: "user", content: text };
      const aiMsg: Msg = {
        role: "assistant",
        content: `🎬 **AI-Video wird vorbereitet:** _${prompt}_\n\nKlick „Generieren" — die KI erstellt ein Schlüsselbild und animiert es zu einem kurzen Clip. Komplett im Browser, kostenlos.`,
        video,
      };
      setMessages(prev => [...prev, userMsg, aiMsg]);
      await supabase.from("messages").insert([
        { conversation_id: convId, role: "user", content: text },
        { conversation_id: convId, role: "assistant", content: aiMsg.content, metadata: { video } },
      ]);
      await supabase.from("conversations").update({ updated_at: new Date().toISOString() }).eq("id", convId);
      setSending(false);
      loadConvs();
      return;
    }

    // Intercept offline AI commands — run 100% in browser, no server roundtrip
    const offlineMatch =
      text.match(/^\/offline-summary\s+(.+)$/is) ? { kind: "summary" as const, body: text.replace(/^\/offline-summary\s+/i, "") } :
      text.match(/^\/sentiment\s+(.+)$/is)       ? { kind: "sentiment" as const, body: text.replace(/^\/sentiment\s+/i, "") } :
      text.match(/^\/offline\s+(.+)$/is)         ? { kind: "chat" as const, body: text.replace(/^\/offline\s+/i, "") } :
      null;
    if (offlineMatch) {
      const offline: OfflineTask =
        offlineMatch.kind === "chat"      ? { kind: "chat", prompt: offlineMatch.body } :
        offlineMatch.kind === "summary"   ? { kind: "summary", text: offlineMatch.body } :
                                            { kind: "sentiment", text: offlineMatch.body };
      const aiContent =
        offlineMatch.kind === "chat"      ? `🔌 **Offline-Chat** — läuft komplett lokal im Browser. Klick „Starten" (erster Aufruf lädt das Modell einmalig).` :
        offlineMatch.kind === "summary"   ? `🔌 **Offline-Zusammenfassung** wird vorbereitet…` :
                                            `🔌 **Offline-Sentiment** wird analysiert…`;
      const userMsg: Msg = { role: "user", content: text };
      const aiMsg: Msg = { role: "assistant", content: aiContent, offline };
      setMessages(prev => [...prev, userMsg, aiMsg]);
      await supabase.from("messages").insert([
        { conversation_id: convId, role: "user", content: text },
        { conversation_id: convId, role: "assistant", content: aiContent, metadata: { offline } },
      ]);
      await supabase.from("conversations").update({ updated_at: new Date().toISOString() }).eq("id", convId);
      setSending(false);
      loadConvs();
      return;
    }

    const buildContent = (txt: string) => {
      if (!attachments.length) return txt;
      const parts: any[] = [{ type: "text", text: txt }];
      for (const a of attachments) {
        if (a.mime.startsWith("image/")) parts.push({ type: "image_url", image_url: { url: a.url } });
        else parts.push({ type: "text", text: `\n[Anhang: ${a.name} (${a.mime}) — ${a.url}]` });
      }
      return parts;
    };
    const userContentForAI = buildContent(text);
    const displayContent = attachments.length
      ? text + "\n" + attachments.map(a => `📎 ${a.name}`).join("\n")
      : text;

    const userMsg: Msg = { role: "user", content: displayContent };
    setMessages(prev => [...prev, userMsg, { role: "assistant", content: "" }]);
    await supabase.from("messages").insert({
      conversation_id: convId, role: "user", content: displayContent,
      metadata: attachments.length ? { attachments } : null,
    });
    const sentAttachments = attachments;
    setAttachments([]);

    const fnUrl = `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/${mode === "agent" ? "agent" : "chat"}`;
    try {
      const historyForAI = messages.map(m => ({ role: m.role, content: m.content }));
      const resp = await fetch(fnUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY}` },
        body: JSON.stringify({
          conversationId: convId,
          userId: user?.id,
          personaId: personaId !== "none" ? personaId : undefined,
          messages: [...historyForAI, { role: "user", content: userContentForAI }],
          mode,
        }),
      });

      if (!resp.ok || !resp.body) {
        if (resp.status === 429) toast.error("Zu viele Anfragen. Bitte warte einen Moment.");
        else if (resp.status === 402) toast.error("AI-Credits aufgebraucht. Bitte später erneut versuchen.");
        else toast.error("Fehler beim Senden");
        setMessages(prev => prev.slice(0, -1));
        setSending(false);
        return;
      }

      const reader = resp.body.getReader();
      const decoder = new TextDecoder();
      let buf = "", full = "";
      let imageData: { url: string; prompt: string } | undefined;
      let musicData: FunkPattern | undefined;
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += decoder.decode(value, { stream: true });
        let idx;
        while ((idx = buf.indexOf("\n")) !== -1) {
          let line = buf.slice(0, idx); buf = buf.slice(idx + 1);
          if (line.endsWith("\r")) line = line.slice(0, -1);
          if (!line.startsWith("data: ")) continue;
          const json = line.slice(6).trim();
          if (json === "[DONE]") break;
          try {
            const p = JSON.parse(json);
            if (p.tool) {
              setMessages(prev => {
                const next = [...prev];
                const last = next[next.length - 1];
                last.content = (last.content || "") + `\n\n> 🔧 *${p.tool}*\n\n`;
                return next;
              });
              continue;
            }
            if (p.image) {
              imageData = p.image;
              setMessages(prev => {
                const next = [...prev];
                next[next.length - 1] = { ...next[next.length - 1], image: p.image };
                return next;
              });
              continue;
            }
            if (p.music) {
              musicData = p.music;
              setMessages(prev => {
                const next = [...prev];
                next[next.length - 1] = { ...next[next.length - 1], music: p.music };
                return next;
              });
              continue;
            }
            const c = p.choices?.[0]?.delta?.content;
            if (c) {
              full += c;
              setMessages(prev => {
                const next = [...prev];
                next[next.length - 1] = { ...next[next.length - 1], role: "assistant", content: full };
                return next;
              });
            }
          } catch { buf = line + "\n" + buf; break; }
        }
      }

      if (full || imageData || musicData) {
        await supabase.from("messages").insert({
          conversation_id: convId,
          role: "assistant",
          content: full,
          metadata: { image: imageData, music: musicData },
        });
      }
      await supabase.from("conversations").update({ updated_at: new Date().toISOString() }).eq("id", convId);

      // Fire-and-forget: extract durable memories from the user message
      supabase.functions.invoke("extract-memory", { body: { text } }).catch(() => {});

      // Fetch smart follow-up suggestions
      supabase.functions.invoke("suggest", {
        body: { messages: [...historyForAI, { role: "user", content: text }, { role: "assistant", content: full }] },
      }).then(({ data }) => {
        if (data?.items?.length) setSuggestions(data.items);
      }).catch(() => {});
    } catch (e) {
      console.error(e);
      toast.error("Verbindungsfehler");
      setMessages(prev => prev.slice(0, -1));
    } finally {
      setSending(false);
    }
  };

  // Clear suggestions when user starts typing or switches chat
  useEffect(() => { if (input) setSuggestions([]); }, [input]);
  useEffect(() => { setSuggestions([]); }, [activeId]);

  const copyMessage = async (text: string) => {
    try { await navigator.clipboard.writeText(text); toast.success("Kopiert"); } catch { toast.error("Kopieren fehlgeschlagen"); }
  };

  const exportChat = () => {
    if (!messages.length) { toast.error("Nichts zu exportieren"); return; }
    const conv = convs.find(c => c.id === activeId);
    const md = `# ${conv?.title || "Mythos AI Chat"}\n\n_Exportiert: ${new Date().toLocaleString("de-DE")}_\n\n---\n\n` +
      messages.map(m => `## ${m.role === "user" ? "🧑 Du" : "🤖 Mythos AI"}\n\n${m.content}${m.image ? `\n\n![${m.image.prompt}](${m.image.url})` : ""}`).join("\n\n---\n\n");
    const blob = new Blob([md], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url; a.download = `${(conv?.title || "chat").replace(/[^a-z0-9]+/gi, "_")}.md`;
    a.click(); URL.revokeObjectURL(url);
  };


  // Wire send to ref so the voice hook can call it
  useEffect(() => { sendRef.current = send; });

  // Auto-speak last assistant message when voice mode is on and streaming finishes
  useEffect(() => {
    if (!voiceMode || sending) return;
    const last = messages[messages.length - 1];
    if (!last || last.role !== "assistant" || !last.content) return;
    if (lastSpokenRef.current === last.content) return;
    lastSpokenRef.current = last.content;
    voice.speak(last.content);
  }, [voiceMode, sending, messages, voice]);

  // When toggling voice mode ON, start LIVE listening; OFF -> stop everything.
  useEffect(() => {
    if (voiceMode) {
      if (voice.supported) voice.startLive();
    } else {
      voice.stopSpeaking();
      voice.stopListening();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [voiceMode]);

  const ModeIcon = MODES.find(m => m.value === mode)?.icon || HelpCircle;

  const Sidebar = (
    <div className="flex flex-col gap-2 h-full">
      <Button onClick={newChat} className="bg-gradient-primary text-primary-foreground hover:opacity-90 w-full">
        <Plus className="h-4 w-4 mr-1.5" /> Neuer Chat
      </Button>
      <ScrollArea className="flex-1 -mx-1 px-1">
        <div className="space-y-1">
          {convs.map(c => (
            <div
              key={c.id}
              className={`group flex items-center gap-1 rounded-lg px-2 py-2 text-sm cursor-pointer transition-colors ${
                activeId === c.id ? "bg-primary/15 text-foreground" : "hover:bg-secondary/50 text-muted-foreground"
              }`}
              onClick={() => loadMessages(c.id)}
            >
              <MessageSquare className="h-3.5 w-3.5 shrink-0" />
              <span className="truncate flex-1">{c.title}</span>
              <button
                onClick={(e) => { e.stopPropagation(); deleteChat(c.id); }}
                className="opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity p-1"
                aria-label="Chat löschen"
              >
                <Trash2 className="h-3.5 w-3.5 hover:text-destructive" />
              </button>
            </div>
          ))}
          {convs.length === 0 && <p className="text-xs text-muted-foreground p-3 text-center">Noch keine Chats</p>}
        </div>
      </ScrollArea>
    </div>
  );

  return (
    <div className="min-h-screen flex flex-col overflow-x-hidden">
      <TopNav />
      <div className="flex-1 container py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] md:py-4 grid min-h-0 grid-cols-1 md:grid-cols-[260px_1fr] gap-3 md:gap-4 overflow-hidden">
        {/* Desktop sidebar */}
        <aside className="hidden md:flex min-h-0 glass rounded-2xl p-3 flex-col gap-2 h-full max-h-[calc(100dvh-7rem-env(safe-area-inset-top)-env(safe-area-inset-bottom))]">
          {Sidebar}
        </aside>

        {/* Chat area */}
        <main className="glass-strong rounded-2xl flex min-h-0 flex-col h-full max-h-[calc(100dvh-7rem-env(safe-area-inset-top)-env(safe-area-inset-bottom))] overflow-hidden">
          <div className="border-b border-border/50 p-2.5 md:p-3 flex items-center justify-between gap-2">
            <div className="flex items-center gap-2 min-w-0">
              {/* Mobile sidebar trigger */}
              <Sheet open={sidebarOpen} onOpenChange={setSidebarOpen}>
                <SheetTrigger asChild>
                  <Button variant="ghost" size="icon" className="md:hidden h-9 w-9 shrink-0">
                    <Menu className="h-4 w-4" />
                  </Button>
                </SheetTrigger>
                <SheetContent side="left" className="glass-strong w-[280px] p-3 pt-[max(env(safe-area-inset-top),0.75rem)] pb-[max(env(safe-area-inset-bottom),0.75rem)] flex flex-col">
                  <div className="mt-6 flex-1 overflow-hidden">{Sidebar}</div>
                </SheetContent>
              </Sheet>
              <ModeIcon className="h-4 w-4 text-foreground/80 shrink-0" />
              <Link to="/" className="font-display text-base truncate hover:text-foreground/80 transition-colors">Mythos AI</Link>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <Button
                variant="ghost" size="icon"
                className="h-9 w-9"
                onClick={() => { if (sub.canUseVoice) window.location.assign("/voice"); else setPaywall({ open: true, reason: "Live-Sprachchat ist eine Pro-Funktion." }); }}
                title="Live-Sprachchat"
                aria-label="Live-Sprachchat öffnen"
              >
                <AudioLines className="h-4 w-4" />
              </Button>
              {voice.supported && (
                <Button
                  variant={voiceMode ? "default" : "ghost"}
                  size="icon"
                  className={`h-9 w-9 ${voiceMode ? "bg-foreground text-background hover:bg-foreground/90" : ""}`}
                  onClick={() => setVoiceMode(v => !v)}
                  title={voiceMode ? "Voice-Modus aus" : "Voice-Modus an"}
                  aria-label="Voice-Modus umschalten"
                >
                  {voiceMode ? <Volume2 className="h-4 w-4" /> : <VolumeX className="h-4 w-4" />}
                </Button>
              )}
              {messages.length > 0 && (
                <Button
                  variant="ghost" size="icon" className="h-9 w-9"
                  onClick={exportChat}
                  title="Chat als Markdown exportieren"
                  aria-label="Chat exportieren"
                >
                  <Download className="h-4 w-4" />
                </Button>
              )}
              {personas.length > 0 && (
                <Select value={personaId} onValueChange={setPersonaId}>
                  <SelectTrigger className="w-[110px] sm:w-[150px] glass h-9 text-xs border-white/10">
                    <Drama className="h-3.5 w-3.5 mr-1" />
                    <SelectValue placeholder="Persona" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none"><span className="text-muted-foreground">Standard</span></SelectItem>
                    {personas.map(p => (
                      <SelectItem key={p.id} value={p.id}>
                        <span className="flex items-center gap-1.5">{p.avatar_emoji || "🎭"} {p.name}</span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
              <Select value={mode} onValueChange={setMode}>
                <SelectTrigger className="w-[110px] sm:w-[160px] glass h-9 text-xs border-white/10">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {MODES.map(m => (
                    <SelectItem key={m.value} value={m.value}>
                      <div className="flex items-center gap-2">
                        <m.icon className="h-3.5 w-3.5" />
                        <span>{m.label}</span>
                        <span className="hidden sm:inline text-xs text-muted-foreground">— {m.desc}</span>
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div ref={scrollRef} className="flex-1 overflow-y-auto p-3 sm:p-4 md:p-6 space-y-4 md:space-y-6 min-h-0">
            {messages.length === 0 && (
              <div className="h-full flex flex-col items-center justify-center text-center max-w-md mx-auto px-2">
                <div className="h-14 w-14 md:h-16 md:w-16 rounded-2xl bg-gradient-primary flex items-center justify-center glow-primary mb-5 animate-pulse-glow">
                  <Sparkles className="h-7 w-7 md:h-8 md:w-8 text-primary-foreground" />
                </div>
                <h2 className="font-display text-xl md:text-2xl font-bold mb-2">Willkommen bei Mythos AI</h2>
                <p className="text-muted-foreground text-sm mb-6">
                  Frag mich alles über mythoscraft.online — Regeln, Commands, Plugins, Server-Status. Im Agent-Modus kann ich auch im Web suchen.
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 w-full">
                  {[
                    "Wie verbinde ich mich mit dem Server?",
                    "/research aktuelle Minecraft 1.21 Updates",
                    "/image ein epischer Drache über mythoscraft",
                    "/translate english Hallo wie geht's?",
                  ].map(s => (
                    <button
                      key={s}
                      onClick={() => setInput(s)}
                      className="glass rounded-xl p-3 text-xs text-left hover:border-primary/40 transition-colors"
                    >
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            )}
            {messages.map((m, i) => (
              <div key={i} className={`flex gap-2 sm:gap-3 animate-fade-in ${m.role === "user" ? "justify-end" : ""}`}>
                {m.role !== "user" && (
                  <div className="h-8 w-8 shrink-0 mt-0.5 flex items-center justify-center">
                    <img src="/icon.png" alt="" aria-hidden="true" className="h-8 w-8 object-contain" loading="lazy" />
                  </div>
                )}
                <div
                  className={`max-w-[85%] sm:max-w-[80%] rounded-2xl px-3 py-2 sm:px-4 sm:py-3 ${
                    m.role === "user" ? "bg-primary/20 border border-primary/30" : "glass"
                  }`}
                >
                  {m.role === "assistant" ? (
                    <div className="prose-mythos text-sm break-words">
                      {m.content ? (
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{m.content}</ReactMarkdown>
                      ) : !m.image && !m.music && !m.song && !m.video && !m.offline ? (
                        <Loader2 className="h-4 w-4 animate-spin text-primary" />
                      ) : null}
                      {m.image && (
                        <img
                          src={m.image.url}
                          alt={m.image.prompt}
                          className="mt-2 rounded-xl border border-white/10 max-w-full h-auto"
                          loading="lazy"
                        />
                      )}
                      {m.music && <FunkPlayer pattern={m.music} />}
                      {m.song && <SongPlayer request={m.song} />}
                      {m.video && <VideoPlayer request={m.video} />}
                      {m.offline && <OfflineAI task={m.offline} />}
                      {m.role === "assistant" && m.content && !sending && i === messages.length - 1 && (
                        <div className="flex items-center gap-1 mt-2 -mb-1 opacity-60 hover:opacity-100 transition-opacity">
                          <button onClick={() => copyMessage(m.content)} title="Kopieren" className="p-1 hover:text-primary"><Copy className="h-3 w-3" /></button>
                          {voice.supported && (
                            <button
                              onClick={() => voice.status === "speaking" ? voice.stopSpeaking() : voice.speak(m.content)}
                              title={voice.status === "speaking" ? "Stop" : "Vorlesen"}
                              className="p-1 hover:text-primary"
                            >
                              {voice.status === "speaking" ? <VolumeX className="h-3 w-3" /> : <Volume2 className="h-3 w-3" />}
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  ) : (
                    <p className="text-sm whitespace-pre-wrap break-words">{m.content}</p>
                  )}
                </div>
                {m.role === "user" && (
                  <MinecraftAvatar
                    username={profile?.mc_username}
                    fallback={profile?.display_name || user?.email}
                    size={32}
                    className="mt-0.5 shrink-0"
                  />
                )}
              </div>
            ))}
            {suggestions.length > 0 && !sending && (
              <div className="flex flex-wrap gap-1.5 pt-1 animate-fade-in">
                <Lightbulb className="h-3.5 w-3.5 text-primary/70 mt-1.5" />
                {suggestions.map((s, i) => (
                  <button
                    key={i}
                    onClick={() => { setSuggestions([]); send(s); }}
                    className="glass rounded-full px-3 py-1 text-xs hover:border-primary/40 hover:bg-primary/10 transition-all"
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="border-t border-white/5 p-2 sm:p-3">
            {voiceMode && (voice.status === "listening" || voice.status === "speaking") && (
              <div className="flex items-center justify-center gap-1.5 mb-2 text-xs text-foreground/70 animate-fade-in">
                <span className="flex items-end gap-0.5 h-3">
                  {[0, 1, 2, 3].map(i => (
                    <span
                      key={i}
                      className="w-0.5 bg-foreground/80 rounded-full"
                      style={{ height: "100%", animation: `voice-wave 0.9s ease-in-out ${i * 0.12}s infinite` }}
                    />
                  ))}
                </span>
                <span>
                  {voice.status === "speaking"
                    ? "🔊 spricht..."
                    : voice.interim || "👂 höre zu... (sprich einfach drauf los)"}
                </span>
              </div>
            )}
            {attachments.length > 0 && (
              <div className="flex flex-wrap gap-1.5 mb-2">
                {attachments.map((a, i) => (
                  <div key={i} className="flex items-center gap-1.5 glass rounded-lg px-2 py-1 text-xs">
                    {a.mime.startsWith("image/") ? (
                      <img src={a.url} alt={a.name} className="h-5 w-5 rounded object-cover" />
                    ) : <Paperclip className="h-3 w-3" />}
                    <span className="max-w-[120px] truncate">{a.name}</span>
                    <button onClick={() => setAttachments(prev => prev.filter((_, j) => j !== i))} aria-label="Entfernen">
                      <XIcon className="h-3 w-3 hover:text-destructive" />
                    </button>
                  </div>
                ))}
              </div>
            )}
            {input.startsWith("/") && (() => {
              const q = input.slice(1).split(/\s/)[0].toLowerCase();
              const filtered = SLASH_COMMANDS.filter(c => c.cmd.slice(1).startsWith(q));
              if (!filtered.length) return null;
              const pick = (cmd: string, args: string) => {
                setInput(args ? `${cmd} ` : cmd + " ");
                setSlashIndex(0);
              };
              return (
                <div className="glass-strong rounded-xl p-1.5 mb-2 max-h-64 overflow-y-auto animate-fade-in border border-primary/20">
                  <div className="text-[10px] uppercase tracking-wider text-muted-foreground px-2 py-1 flex items-center justify-between">
                    <span>Commands</span>
                    <span className="text-[9px]">↑↓ Tab ↵</span>
                  </div>
                  {filtered.map((c, i) => {
                    const Icon = c.icon;
                    const active = i === Math.min(slashIndex, filtered.length - 1);
                    return (
                      <button
                        key={c.cmd}
                        onMouseEnter={() => setSlashIndex(i)}
                        onClick={() => pick(c.cmd, c.args)}
                        className={`w-full flex items-center gap-2 rounded-lg px-2 py-1.5 text-left text-xs transition-colors ${active ? "bg-primary/15" : "hover:bg-secondary/40"}`}
                      >
                        <Icon className="h-3.5 w-3.5 text-primary shrink-0" />
                        <span className="font-mono font-semibold">{c.cmd}</span>
                        {c.args && <span className="text-muted-foreground font-mono">{c.args}</span>}
                        <span className="text-muted-foreground truncate ml-auto hidden sm:inline">{c.desc}</span>
                      </button>
                    );
                  })}
                </div>
              );
            })()}
            <input
              ref={fileInputRef} type="file" multiple hidden
              accept="image/*,.pdf,.txt,.md,.json,.csv"
              onChange={(e) => { uploadFiles(e.target.files); if (fileInputRef.current) fileInputRef.current.value = ""; }}
            />
            <div className="glass-liquid rounded-2xl flex items-end gap-2 p-2">
              <Button
                type="button" size="icon" variant="ghost"
                className="h-10 w-10 shrink-0 relative z-10"
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading || sending}
                title="Datei anhängen (Bild/PDF)"
              >
                {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Paperclip className="h-4 w-4" />}
              </Button>
              <Textarea
                value={input}
                onChange={(e) => { setInput(e.target.value); setSlashIndex(0); }}
                onKeyDown={(e) => {
                  // Slash command menu navigation
                  if (input.startsWith("/")) {
                    const q = input.slice(1).split(/\s/)[0].toLowerCase();
                    const filtered = SLASH_COMMANDS.filter(c => c.cmd.slice(1).startsWith(q));
                    if (filtered.length && !input.includes(" ")) {
                      if (e.key === "ArrowDown") { e.preventDefault(); setSlashIndex(i => (i + 1) % filtered.length); return; }
                      if (e.key === "ArrowUp")   { e.preventDefault(); setSlashIndex(i => (i - 1 + filtered.length) % filtered.length); return; }
                      if (e.key === "Tab" || (e.key === "Enter" && !e.shiftKey)) {
                        e.preventDefault();
                        const c = filtered[Math.min(slashIndex, filtered.length - 1)];
                        setInput(c.args ? `${c.cmd} ` : c.cmd + " ");
                        setSlashIndex(0);
                        return;
                      }
                    }
                  }
                  if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); }
                }}
                placeholder={
                  voiceMode ? "Tippe oder drücke das Mikro..." :
                  mode === "support" ? "Frage zum Mythoscraft-Server..." :
                  mode === "agent" ? "Was soll der Agent tun?" : "Was möchtest du wissen?"
                }
                className="flex-1 min-h-[44px] max-h-40 resize-none border-0 bg-transparent focus-visible:ring-0 text-sm relative z-10"
                disabled={sending}
              />
              {voice.supported && (
                <Button
                  type="button"
                  onClick={() => {
                    if (voice.status === "listening") voice.stopListening();
                    else voice.startDictation();
                  }}
                  disabled={sending}
                  size="icon"
                  variant="ghost"
                  className={`h-10 w-10 shrink-0 relative z-10 ${
                    voice.status === "listening" && !voiceMode ? "bg-foreground text-background hover:bg-foreground/90 animate-pulse-glow" : ""
                  }`}
                  title={voice.status === "listening" ? "Diktat stoppen" : "Diktieren (ins Eingabefeld)"}
                  aria-label="Mikrofon"
                >
                  {voice.status === "listening" ? <MicOff className="h-4 w-4" /> : <Mic className="h-4 w-4" />}
                </Button>
              )}
              <Button
                onClick={() => send()}
                disabled={!input.trim() || sending}
                size="icon"
                className="bg-foreground text-background hover:bg-foreground/90 h-10 w-10 shrink-0 relative z-10"
              >
                {sending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
              </Button>
            </div>
          </div>
        </main>
      </div>
      <Paywall open={paywall.open} onOpenChange={(o) => setPaywall({ open: o })} reason={paywall.reason} />
    </div>
  );
};
export default Chat;
