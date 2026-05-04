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
  Mic, MicOff, Volume2, VolumeX, Paperclip, X as XIcon, Drama,
} from "lucide-react";
import { toast } from "sonner";
import { useVoiceMode } from "@/hooks/useVoiceMode";
import FunkPlayer, { type FunkPattern } from "@/components/FunkPlayer";

type Persona = { id: string; name: string; avatar_emoji: string | null };
type Attachment = { url: string; name: string; mime: string };

type Conv = { id: string; title: string; mode: string; updated_at: string };
type Msg = { id?: string; role: "user" | "assistant" | "tool"; content: string; metadata?: any; image?: { url: string; prompt: string }; music?: FunkPattern };

const MODES = [
  { value: "support", label: "Support", icon: HelpCircle, desc: "Mythoscraft Server-Support" },
  { value: "agent", label: "Agent", icon: Brain, desc: "Mit Web-Suche & Tools" },
  { value: "general", label: "General", icon: Sparkles, desc: "Allgemeiner KI-Chat" },
];

const Chat = () => {
  const { user, profile } = useAuth();
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

  const loadMessages = async (id: string) => {
    setActiveId(id);
    setSidebarOpen(false);
    const { data } = await supabase.from("messages").select("*").eq("conversation_id", id).order("created_at");
    if (data) {
      const enriched = (data as any[]).map(m => ({
        ...m,
        image: m.metadata?.image,
        music: m.metadata?.music,
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

    const userMsg: Msg = { role: "user", content: text };
    setMessages(prev => [...prev, userMsg, { role: "assistant", content: "" }]);
    await supabase.from("messages").insert({ conversation_id: convId, role: "user", content: text });

    const fnUrl = `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/${mode === "agent" ? "agent" : "chat"}`;
    try {
      const resp = await fetch(fnUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY}` },
        body: JSON.stringify({
          conversationId: convId,
          userId: user?.id,
          messages: [...messages, userMsg].map(m => ({ role: m.role, content: m.content })),
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
    } catch (e) {
      console.error(e);
      toast.error("Verbindungsfehler");
      setMessages(prev => prev.slice(0, -1));
    } finally {
      setSending(false);
    }
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
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <div className="flex-1 container py-3 md:py-4 grid grid-cols-1 md:grid-cols-[260px_1fr] gap-3 md:gap-4 overflow-hidden">
        {/* Desktop sidebar */}
        <aside className="hidden md:flex glass rounded-2xl p-3 flex-col gap-2 h-[calc(100vh-6rem)]">
          {Sidebar}
        </aside>

        {/* Chat area */}
        <main className="glass-strong rounded-2xl flex flex-col h-[calc(100vh-5rem)] md:h-[calc(100vh-6rem)] overflow-hidden">
          <div className="border-b border-border/50 p-2.5 md:p-3 flex items-center justify-between gap-2">
            <div className="flex items-center gap-2 min-w-0">
              {/* Mobile sidebar trigger */}
              <Sheet open={sidebarOpen} onOpenChange={setSidebarOpen}>
                <SheetTrigger asChild>
                  <Button variant="ghost" size="icon" className="md:hidden h-9 w-9 shrink-0">
                    <Menu className="h-4 w-4" />
                  </Button>
                </SheetTrigger>
                <SheetContent side="left" className="glass-strong w-[280px] p-3 flex flex-col">
                  <div className="mt-6 flex-1 overflow-hidden">{Sidebar}</div>
                </SheetContent>
              </Sheet>
              <ModeIcon className="h-4 w-4 text-foreground/80 shrink-0" />
              <span className="font-display text-base truncate">Mythos AI</span>
            </div>
            <div className="flex items-center gap-2 shrink-0">
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
              <Select value={mode} onValueChange={setMode}>
                <SelectTrigger className="w-[120px] sm:w-[180px] glass h-9 text-xs border-white/10">
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

          <div ref={scrollRef} className="flex-1 overflow-y-auto p-3 sm:p-4 md:p-6 space-y-4 md:space-y-6">
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
                    "/music funk sereno style banger",
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
                  <div className="h-8 w-8 rounded-lg bg-gradient-primary flex items-center justify-center shrink-0 mt-0.5">
                    <Sparkles className="h-4 w-4 text-primary-foreground" />
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
                      ) : !m.image && !m.music ? (
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
            <div className="glass-liquid rounded-2xl flex items-end gap-2 p-2">
              <Textarea
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); } }}
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
    </div>
  );
};
export default Chat;
