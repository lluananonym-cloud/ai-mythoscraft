import { useState, useEffect, useRef } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import MinecraftAvatar from "@/components/MinecraftAvatar";
import Logo from "@/components/Logo";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem,
  DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  Plus, Send, Trash2, MessageSquare, Loader2, Sparkles, Brain, HelpCircle, Menu,
  Mic, MicOff, Volume2, VolumeX, Paperclip, X as XIcon, Drama, Copy, Download, Lightbulb,
  Image as ImageIcon, Music, Globe, FileText, Languages, UserCog, WifiOff, Smile, AudioLines, Film,
  PanelLeftClose, PanelLeft, LogOut, Key, Shield, Bot, Users, BarChart3,
  Crown, Gamepad2, Server, Ticket, User as UserIcon, Search, Puzzle,
} from "lucide-react";
import { useSubscription } from "@/hooks/useSubscription";
import Paywall from "@/components/Paywall";
import { toast } from "sonner";
import { useVoiceMode } from "@/hooks/useVoiceMode";
import FunkPlayer, { type FunkPattern } from "@/components/FunkPlayer";
import SongPlayer, { type SongRequest } from "@/components/SongPlayer";
import VideoPlayer, { type VideoRequest } from "@/components/VideoPlayer";
import AgentBrowser from "@/components/AgentBrowser";
import BrowserExtensionPanel from "@/components/BrowserExtensionPanel";
import { Link, useNavigate } from "react-router-dom";
import { isPuterModel, getPuterLabel } from "@/lib/puterAi";

import { NV_API_KEY, nvidiaLLM } from "@/lib/nvidiaApi";

const SLASH_COMMANDS = [
  { cmd: "/image",     args: "<beschreibung>",  icon: ImageIcon, desc: "Bild generieren (Nano Banana)" },
  { cmd: "/music",     args: "<stil/vibe>",     icon: Music,     desc: "Echten KI-Song generieren (MusicGen im Browser, kostenlos)" },
  { cmd: "/video",     args: "<szene>",         icon: Film,      desc: "Kurzes KI-Video (Bild + Animation, kostenlos im Browser)" },
  { cmd: "/agent",     args: "<aufgabe>",       icon: Globe,     desc: "Browser-Agent: KI surft live — du siehst jeden Klick (Pro)" },
  { cmd: "/browser",   args: "[anweisung]",     icon: Puzzle,    desc: "Deinen echten Browser steuern per Mythos-Erweiterung (Pro)" },
  { cmd: "/research",  args: "<thema>",         icon: Globe,     desc: "Deep Research mit Web-Suche" },
  { cmd: "/translate", args: "<sprache> [text]",icon: Languages, desc: "Übersetzen (letzte AI-Antwort wenn ohne Text)" },
  { cmd: "/summarize", args: "",                icon: FileText,  desc: "Konversation zusammenfassen" },
  { cmd: "/identity",  args: "<name>",          icon: UserCog,   desc: "AI-Persona im Chat wechseln" },
  { cmd: "/code",       args: "<prompt>",       icon: Bot,       desc: "Code‑Assist via Meta Llama (Kostenlos)" },
  { cmd: "/offline",   args: "<frage>",         icon: WifiOff,   desc: "Offline-Chat im Browser (Qwen2.5-0.5B, ~500MB einmalig)" },
  { cmd: "/offline-summary", args: "<text>",    icon: FileText,  desc: "Offline-Zusammenfassung (DistilBART, ~250MB)" },
  { cmd: "/sentiment", args: "<text>",          icon: Smile,     desc: "Offline-Stimmungsanalyse (~65MB)" },
];

type Persona = { id: string; name: string; avatar_emoji: string | null };
type Attachment = { url: string; name: string; mime: string };
type Conv = { id: string; title: string; mode: string; updated_at: string };
type Msg = { id?: string; role: "user" | "assistant" | "tool"; content: string; metadata?: any; image?: { url: string; prompt: string }; music?: FunkPattern; song?: SongRequest; video?: VideoRequest; agent?: { task: string }; ext?: { task: string }; attachments?: Attachment[] };

const MODES = [
  { value: "support", label: "Support", icon: HelpCircle, desc: "Mythoscraft Server-Support" },
  { value: "agent", label: "Agent", icon: Brain, desc: "Mit Web-Suche & Tools" },
  { value: "general", label: "General", icon: Sparkles, desc: "Allgemeiner KI-Chat" },
];

const SIDEBAR_KEY = "mythos.sidebar.collapsed";

const Chat = () => {
  const { user, profile, isAdmin, signOut } = useAuth();
  const nav = useNavigate();
  const sub = useSubscription();
  const [paywall, setPaywall] = useState<{ open: boolean; reason?: string }>({ open: false });
  const [convs, setConvs] = useState<Conv[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [mode, setMode] = useState("support");
  const [mobileSidebar, setMobileSidebar] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(() => {
    if (typeof window === "undefined") return false;
    return localStorage.getItem(SIDEBAR_KEY) === "1";
  });
  const [voiceMode, setVoiceMode] = useState(false);
  const [personas, setPersonas] = useState<Persona[]>([]);
  const [personaId, setPersonaId] = useState<string>("none");
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [uploading, setUploading] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [slashIndex, setSlashIndex] = useState(0);
  const [convSearch, setConvSearch] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const scrollRef = useRef<HTMLDivElement>(null);
    const lastSpokenRef = useRef<string>("");
    const sendRef = useRef<(text?: string) => void>(() => {});
  const voice = useVoiceMode({
    lang: "de-DE",
    onTranscript: (t) => { sendRef.current?.(t); },
    onDictation: (t) => { setInput(prev => (prev ? prev.trimEnd() + " " + t : t)); },
  });

  useEffect(() => { localStorage.setItem(SIDEBAR_KEY, sidebarCollapsed ? "1" : "0"); }, [sidebarCollapsed]);

  const loadConvs = async () => {
    const { data } = await supabase.from("conversations").select("*").order("updated_at", { ascending: false });
    if (data) setConvs(data as any);
  };

  useEffect(() => { if (user) loadConvs(); }, [user]);

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
    setMobileSidebar(false);
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
    setMobileSidebar(false);
  };

  const deleteChat = async (id: string) => {
    await supabase.from("conversations").delete().eq("id", id);
    if (activeId === id) { setActiveId(null); setMessages([]); }
    loadConvs();
  };

  const send = async (override?: string) => {
    let text = (override ?? input).trim();
    if (!text || sending) return;
    // Auto-detect generation commands
    const lowerText = text.toLowerCase();
    if (lowerText.includes("generiere mir ein bild von")) {
      const match = lowerText.match(/generiere mir ein bild von (.+)/i);
      if (match && match[1]) {
        text = `/image ${match[1].trim()}`;
      }
    } else if (lowerText.includes("generiere mir ein video von")) {
      const match = lowerText.match(/generiere mir ein video von (.+)/i);
      if (match && match[1]) {
        text = `/video ${match[1].trim()}`;
      }
    } else if (lowerText.includes("generiere mir musik von")) {
      const match = lowerText.match(/generiere mir musik von (.+)/i);
      if (match && match[1]) {
        text = `/music ${match[1].trim()}`;
      }
    } else if (/generiere (mir )?(das|dies) als bild/i.test(lowerText) || /mach (das|dies) als bild/i.test(lowerText) || /erstelle (das|dies) als bild/i.test(lowerText)) {
      // Find context from previous messages
      let prompt = "";
      for (let i = messages.length - 1; i >= 0; i--) {
        const msg = messages[i];
        if (msg.content && !msg.content.startsWith("/")) {
          prompt = msg.content.replace(/^([🤖👤]|AI-Song|AI-Video|🎬|🎵).*/g, "").trim().slice(0, 250);
          if (prompt) break;
        }
      }
      if (!prompt) {
        prompt = text.replace(/generiere|mir|das|dies|als|bild|mach|erstelle/gi, "").trim() || "Ein schönes Bild";
      }
      text = `/image ${prompt}`;
    }
    if (sub.chatLimitReached) { setPaywall({ open: true, reason: `Du hast dein tägliches Free-Limit (${20} Chats) erreicht.` }); return; }
    if (/^\/image\b/i.test(text) && !sub.canGenerateImage) { setPaywall({ open: true, reason: "Bilder generieren ist eine Pro-Funktion." }); return; }
    if (/^\/music\b/i.test(text) && !sub.canGenerateMusic) { setPaywall({ open: true, reason: "Musik generieren ist eine Pro-Funktion." }); return; }
    if (/^\/agent\b/i.test(text) && !sub.isPro) { setPaywall({ open: true, reason: "Der Browser-Agent ist eine Pro-Funktion." }); return; }
    if (/^\/browser\b/i.test(text) && !sub.isPro) { setPaywall({ open: true, reason: "Browser-Steuerung ist eine Pro-Funktion." }); return; }
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

    // /browser — echte Browser-Steuerung über die Mythos-Erweiterung
    const extMatch = text.match(/^\/browser\b\s*(.*)$/i);
    if (extMatch) {
      const t = extMatch[1].trim();
      const userMsg: Msg = { role: "user", content: text };
      const aiMsg: Msg = {
        role: "assistant",
        content: "🧩 **Browser Control** — ich steuere deinen echten Browser: klicken, tippen, Seiten öffnen. Beim ersten Mal einmalig die Erweiterung installieren, danach reicht `/browser <anweisung>`.",
        ext: { task: t },
      };
      setMessages(prev => [...prev, userMsg, aiMsg]);
      await supabase.from("messages").insert([
        { conversation_id: convId, role: "user", content: text },
        { conversation_id: convId, role: "assistant", content: aiMsg.content, metadata: { ext: { task: t } } },
      ]);
      await supabase.from("conversations").update({ updated_at: new Date().toISOString() }).eq("id", convId);
      setSending(false); loadConvs(); return;
    }

    // /agent — Browser-Agent live im Chat
    const agentMatch = text.match(/^\/agent\s+(.+)$/i);
    if (agentMatch) {
      const task = agentMatch[1].trim();
      const userMsg: Msg = { role: "user", content: text };
      const aiMsg: Msg = { role: "assistant", content: "", agent: { task } };
      setMessages(prev => [...prev, userMsg, aiMsg]);
      await supabase.from("messages").insert([
        { conversation_id: convId, role: "user", content: text },
      ]);
      await supabase.from("conversations").update({ updated_at: new Date().toISOString() }).eq("id", convId);
      setSending(false); loadConvs(); return;
    }


    const musicMatch = text.match(/^\/music\s+(.+)$/i);
    if (musicMatch) {
      const prompt = musicMatch[1].trim();
      const song: SongRequest = { prompt, title: prompt.slice(0, 60), duration: 10 };
      const userMsg: Msg = { role: "user", content: text };
      const aiMsg: Msg = {
        role: "assistant",
        content: `🎵 **AI-Song wird vorbereitet:** _${prompt}_\n\nKlick unten auf „Generieren". Der erste Song lädt das Modell (~300MB einmalig), dann läuft alles offline im Browser — kostenlos.`,
        song,
      };
      setMessages(prev => [...prev, userMsg, aiMsg]);
      await supabase.from("messages").insert([
        { conversation_id: convId, role: "user", content: text },
        { conversation_id: convId, role: "assistant", content: aiMsg.content, metadata: { song } },
      ]);
      await supabase.from("conversations").update({ updated_at: new Date().toISOString() }).eq("id", convId);
      setSending(false); loadConvs(); return;
    }

    const videoMatch = text.match(/^\/video\s+(.+)$/i);
    if (videoMatch) {
      const prompt = videoMatch[1].trim();
      const video: VideoRequest = { prompt, title: prompt.slice(0, 60), duration: 8, motion: "kenburns" };
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
      setSending(false); loadConvs(); return;
    }

    // /code command – Meta Llama coding assistance
    const codeMatch = text.match(/^\/code\s+(.+)$/i);
    if (codeMatch) {
      const prompt = codeMatch[1].trim();
      const aiContent = await nvidiaLLM("meta/llama-3.3-70b-instruct", [{ role: "user", content: prompt }]);
      const userMsg: Msg = { role: "user", content: text };
      const aiMsg: Msg = { role: "assistant", content: aiContent };
      setMessages(prev => [...prev, userMsg, aiMsg]);
      await supabase.from("messages").insert([
        { conversation_id: convId, role: "user", content: text },
        { conversation_id: convId, role: "assistant", content: aiContent },
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
    setAttachments([]);

    const chosenModel = (profile as any)?.ai_model as string | undefined;
    const modelForCall = isPuterModel(chosenModel) ? chosenModel : undefined;

    const fnUrl = `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/${mode === "agent" ? "agent" : "chat"}`;
    try {
      const historyForAI = messages.map(m => ({ role: m.role, content: m.content }));
      const doFetch = () => fetch(fnUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY}` },
        body: JSON.stringify({
          conversationId: convId,
          userId: user?.id,
          personaId: personaId !== "none" ? personaId : undefined,
          model: modelForCall,
          messages: [...historyForAI, { role: "user", content: userContentForAI }],
          mode,
        }),
      });
      // Auto-retry on 429 with backoff (1s, 3s) — fixes "zu viele Anfragen"
      let resp = await doFetch();
      for (let attempt = 0; resp.status === 429 && attempt < 2; attempt++) {
        const wait = attempt === 0 ? 1000 : 3000;
        toast.info(`Kurz Pause… (${wait / 1000}s)`);
        await new Promise(r => setTimeout(r, wait));
        resp = await doFetch();
      }

      if (!resp.ok || !resp.body) {
        if (resp.status === 429) {
          toast.error("Zu viele Anfragen. Warte kurz und probier's nochmal.");
        } else if (resp.status === 402) {
          // Fallback to NVIDIA OSS model when credits are exhausted
          try {
            const nvidiaResp = await fetch("https://integrate.api.nvidia.com/v1/chat/completions", {
              method: "POST",
              headers: {
                "Content-Type": "application/json",
                Authorization: `Bearer ${NV_API_KEY}`,
              },
              body: JSON.stringify({
                model: "gpt-oss-120gb",
                messages: [...historyForAI, { role: "user", content: userContentForAI }],
                temperature: 0.7,
              }),
            });
            if (!nvidiaResp.ok) throw new Error(`NVIDIA API error ${nvidiaResp.status}`);
            const nvidiaData = await nvidiaResp.json();
            const content = nvidiaData.choices?.[0]?.message?.content ?? "";
            // replace the placeholder assistant message with the actual content
            setMessages(prev => {
              const updated = [...prev];
              const lastIdx = updated.length - 1;
              updated[lastIdx] = { role: "assistant", content } as any;
              return updated;
            });
            await supabase.from("messages").insert({
              conversation_id: convId,
              role: "assistant",
              content,
            });
            await supabase.from("conversations").update({ updated_at: new Date().toISOString() }).eq("id", convId);
            setSending(false);
            loadConvs();
            return;
          } catch (e) {
            console.error("Fallback error:", e);
            toast.error(`NVIDIA fallback failed: ${e?.message || "unknown"}`);
          }
        } else {
          toast.error("Fehler beim Senden");
        }
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

      supabase.functions.invoke("extract-memory", { body: { text } }).catch(() => {});
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

  useEffect(() => { sendRef.current = send; });

  useEffect(() => {
    if (!voiceMode || sending) return;
    const last = messages[messages.length - 1];
    if (!last || last.role !== "assistant" || !last.content) return;
    if (lastSpokenRef.current === last.content) return;
    lastSpokenRef.current = last.content;
    voice.speak(last.content);
  }, [voiceMode, sending, messages, voice]);

  useEffect(() => {
      if (voiceMode) { if (voice.supported) voice.startLive(); }
      else { voice.stopSpeaking(); voice.stopListening(); }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [voiceMode]);
  
    useEffect(() => {
      if (textareaRef.current) {
        textareaRef.current.style.height = 'auto';
        textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 200) + 'px';
      }
    }, [input]);

  const filteredConvs = convSearch
    ? convs.filter(c => c.title.toLowerCase().includes(convSearch.toLowerCase()))
    : convs;

  const displayName = profile?.display_name || user?.email?.split("@")[0] || "Account";

  const SidebarContentBlock = (
    <div className="flex flex-col h-full min-h-0">
      {/* Header row */}
      <div className="flex items-center justify-between gap-1 px-2 pt-2 pb-3">
        <Link to="/" className="flex items-center gap-2 px-1 rounded-lg hover:bg-white/5 py-1" aria-label="Startseite">
          <Logo size="sm" />
        </Link>
        <div className="flex items-center gap-1">
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" className="h-8 w-8 hidden md:inline-flex" onClick={() => setSidebarCollapsed(true)}>
                <PanelLeftClose className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent side="right">Sidebar einklappen</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" className="h-8 w-8" onClick={newChat} aria-label="Neuer Chat">
                <Plus className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent side="bottom">Neuer Chat</TooltipContent>
          </Tooltip>
        </div>
      </div>

      {/* New chat CTA */}
      <div className="px-2">
        <button
          onClick={newChat}
          className="w-full flex items-center gap-2 rounded-xl border border-white/10 hover:border-white/20 hover:bg-white/5 px-3 py-2.5 text-sm text-left transition-colors"
        >
          <Plus className="h-4 w-4" />
          <span>Neuer Chat</span>
        </button>
      </div>

      {/* Search */}
      {convs.length > 4 && (
        <div className="px-2 pt-2">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
            <input
              value={convSearch}
              onChange={(e) => setConvSearch(e.target.value)}
              placeholder="Chats suchen…"
              className="w-full bg-white/5 border border-white/5 rounded-lg pl-8 pr-2 py-1.5 text-xs placeholder:text-muted-foreground focus:outline-none focus:border-white/20"
            />
          </div>
        </div>
      )}

      {/* Conversation list */}
      <ScrollArea className="flex-1 mt-2 px-2">
        <div className="space-y-0.5 pb-2">
          {filteredConvs.length === 0 && (
            <p className="text-xs text-muted-foreground p-3 text-center">
              {convSearch ? "Keine Treffer" : "Noch keine Chats"}
            </p>
          )}
          {filteredConvs.map(c => (
            <div
              key={c.id}
              className={`group flex items-center gap-2 rounded-lg pl-3 pr-1 py-2 text-sm cursor-pointer transition-colors ${
                activeId === c.id ? "bg-white/10 text-foreground" : "hover:bg-white/5 text-foreground/80"
              }`}
              onClick={() => loadMessages(c.id)}
            >
              <span className="truncate flex-1">{c.title}</span>
              <button
                onClick={(e) => { e.stopPropagation(); deleteChat(c.id); }}
                className="opacity-0 group-hover:opacity-100 focus:opacity-100 transition-opacity p-1"
                aria-label="Chat löschen"
              >
                <Trash2 className="h-3.5 w-3.5 hover:text-destructive" />
              </button>
            </div>
          ))}
        </div>
      </ScrollArea>

      {/* User footer */}
      <div className="border-t border-white/5 p-2">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className="w-full flex items-center gap-2 rounded-lg hover:bg-white/5 p-2 transition-colors">
              <MinecraftAvatar username={profile?.mc_username} fallback={displayName} size={32} />
              <div className="flex-1 min-w-0 text-left">
                <div className="text-sm font-medium truncate">{profile?.mc_username || displayName}</div>
                <div className="text-[11px] text-muted-foreground truncate">
                  {sub.tier === "pro" ? "✨ Pro" : sub.tier === "light" ? "· Light" : "Free"}
                </div>
              </div>
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" side="top" className="glass-strong w-64">
            <DropdownMenuLabel className="text-xs text-muted-foreground truncate">{user?.email}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => nav("/dashboard")}><Key className="h-4 w-4 mr-2" /> Dashboard</DropdownMenuItem>
            <DropdownMenuItem onClick={() => nav("/dashboard?tab=profile")}><UserIcon className="h-4 w-4 mr-2" /> Profil & Skin</DropdownMenuItem>
            <DropdownMenuItem onClick={() => nav("/memories")}><Brain className="h-4 w-4 mr-2" /> Memories</DropdownMenuItem>
            <DropdownMenuItem onClick={() => nav("/personas")}><Drama className="h-4 w-4 mr-2" /> Personas</DropdownMenuItem>
            <DropdownMenuItem onClick={() => nav("/twin")}><Crown className="h-4 w-4 mr-2 text-fuchsia-400" /> AI Twin <span className="ml-auto text-[9px] uppercase text-fuchsia-400">Pro</span></DropdownMenuItem>
            <DropdownMenuItem onClick={() => nav("/games")}><Gamepad2 className="h-4 w-4 mr-2 text-fuchsia-400" /> Game Coder <span className="ml-auto text-[9px] uppercase text-fuchsia-400">Pro</span></DropdownMenuItem>
            <DropdownMenuItem onClick={() => nav("/agents")}><Bot className="h-4 w-4 mr-2" /> Auto-Agents</DropdownMenuItem>
            <DropdownMenuItem onClick={() => nav("/groups")}><Users className="h-4 w-4 mr-2" /> Freunde-Chats</DropdownMenuItem>
            <DropdownMenuItem onClick={() => nav("/analytics")}><BarChart3 className="h-4 w-4 mr-2" /> Analytics</DropdownMenuItem>
            <DropdownMenuItem onClick={() => nav("/mc-servers")}><Server className="h-4 w-4 mr-2" /> Minecraft-Server</DropdownMenuItem>
            <DropdownMenuItem onClick={() => nav("/redeem")}><Ticket className="h-4 w-4 mr-2" /> Boost Code einlösen</DropdownMenuItem>
            {isAdmin && <DropdownMenuItem onClick={() => nav("/admin")}><Shield className="h-4 w-4 mr-2" /> Admin</DropdownMenuItem>}
            <DropdownMenuSeparator />
            {sub.tier === "free" && (
              <DropdownMenuItem onClick={() => setPaywall({ open: true, reason: "Upgrade auf Pro für alle Features." })}>
                <Crown className="h-4 w-4 mr-2 text-fuchsia-400" /> Auf Pro upgraden
              </DropdownMenuItem>
            )}
            <DropdownMenuItem onClick={async () => { await signOut(); nav("/"); }}>
              <LogOut className="h-4 w-4 mr-2" /> Logout
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  );

  return (
    <TooltipProvider delayDuration={200}>
      <div className="h-[100dvh] flex overflow-hidden bg-background">
        {/* Desktop sidebar */}
        <aside
          className={`hidden md:flex shrink-0 flex-col h-full transition-[width] duration-200 ease-out border-r border-white/5 bg-[hsl(0_0%_5%)] ${
            sidebarCollapsed ? "w-0" : "w-[260px]"
          } overflow-hidden`}
        >
          <div className="w-[260px] h-full">{SidebarContentBlock}</div>
        </aside>

        {/* Mobile sidebar */}
        <Sheet open={mobileSidebar} onOpenChange={setMobileSidebar}>
          <SheetContent
            side="left"
            className="w-[280px] p-0 bg-[hsl(0_0%_5%)] border-r border-white/5 pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)]"
          >
            {SidebarContentBlock}
          </SheetContent>
        </Sheet>

        {/* Main column */}
        <main className="flex-1 min-w-0 flex flex-col h-full">
          {/* Top bar */}
          <header className="shrink-0 flex items-center gap-1 sm:gap-2 px-2 sm:px-3 h-14 border-b border-white/5 pt-[env(safe-area-inset-top)]">
            {/* Sidebar toggle */}
            <Button variant="ghost" size="icon" className="md:hidden h-9 w-9" onClick={() => setMobileSidebar(true)} aria-label="Sidebar öffnen">
              <Menu className="h-5 w-5" />
            </Button>
            {sidebarCollapsed && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button variant="ghost" size="icon" className="hidden md:inline-flex h-9 w-9" onClick={() => setSidebarCollapsed(false)} aria-label="Sidebar öffnen">
                    <PanelLeft className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="bottom">Sidebar öffnen</TooltipContent>
              </Tooltip>
            )}
            {sidebarCollapsed && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button variant="ghost" size="icon" className="hidden md:inline-flex h-9 w-9" onClick={newChat} aria-label="Neuer Chat">
                    <Plus className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="bottom">Neuer Chat</TooltipContent>
              </Tooltip>
            )}

            {/* Mode selector as title */}
            <Select value={mode} onValueChange={setMode}>
              <SelectTrigger className="h-9 w-auto min-w-0 border-0 bg-transparent hover:bg-white/5 px-2 gap-1.5 text-sm font-medium focus:ring-0">
                <SelectValue />
              </SelectTrigger>
              <SelectContent align="start">
                {MODES.map(m => (
                  <SelectItem key={m.value} value={m.value}>
                    <div className="flex items-center gap-2">
                      <m.icon className="h-3.5 w-3.5" />
                      <span>Mythos AI · {m.label}</span>
                      <span className="hidden sm:inline text-xs text-muted-foreground">— {m.desc}</span>
                    </div>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <div className="flex-1" />

            {personas.length > 0 && (
              <Select value={personaId} onValueChange={setPersonaId}>
                <SelectTrigger className="w-[130px] sm:w-[160px] h-9 text-xs border-white/10 bg-white/5">
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

            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost" size="icon" className="h-9 w-9"
                  onClick={() => { if (sub.canUseVoice) nav("/voice"); else setPaywall({ open: true, reason: "Live-Sprachchat ist eine Pro-Funktion." }); }}
                  aria-label="Live-Sprachchat"
                >
                  <AudioLines className="h-4 w-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent side="bottom">Live-Sprachchat</TooltipContent>
            </Tooltip>

            {voice.supported && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost" size="icon"
                    className={`h-9 w-9 ${voiceMode ? "text-foreground bg-white/10" : ""}`}
                    onClick={() => setVoiceMode(v => !v)}
                    aria-label="Voice-Modus umschalten"
                  >
                    {voiceMode ? <Volume2 className="h-4 w-4" /> : <VolumeX className="h-4 w-4" />}
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="bottom">{voiceMode ? "Voice-Modus aus" : "Voice-Modus an"}</TooltipContent>
              </Tooltip>
            )}

            {messages.length > 0 && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button variant="ghost" size="icon" className="h-9 w-9" onClick={exportChat} aria-label="Chat exportieren">
                    <Download className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="bottom">Als Markdown exportieren</TooltipContent>
              </Tooltip>
            )}
          </header>

          {/* Messages */}
          <div ref={scrollRef} className="flex-1 overflow-y-auto min-h-0">
            {messages.length === 0 ? (
              <div className="h-full flex flex-col items-center justify-center px-4">
                <div className="mb-6"><Logo size="lg" /></div>
                <h1 className="font-display text-3xl md:text-4xl text-center mb-2 gradient-text">Womit kann ich helfen?</h1>
                <p className="text-muted-foreground text-sm text-center max-w-md mb-8">
                  Frag mich alles. Tippe <code className="font-mono bg-white/5 px-1.5 py-0.5 rounded text-[11px]">/</code> für alle Commands.
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 w-full max-w-2xl">
                  {[
                    { icon: HelpCircle, label: "Wie verbinde ich mich mit dem Server?" },
                    { icon: Film,       label: "/video epische Drohnenaufnahme über einer Burg" },
                    { icon: ImageIcon,  label: "/image ein epischer Drache über mythoscraft" },
                    { icon: Music,      label: "/music chill lofi hip hop beat" },
                  ].map(({ icon: Icon, label }) => (
                    <button
                      key={label}
                      onClick={() => setInput(label)}
                      className="border border-white/10 hover:border-white/20 hover:bg-white/5 rounded-2xl p-4 text-sm text-left transition-colors flex items-start gap-3"
                    >
                      <Icon className="h-4 w-4 text-foreground/60 mt-0.5 shrink-0" />
                      <span className="text-foreground/90">{label}</span>
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div className="max-w-3xl mx-auto px-3 sm:px-6 py-6 space-y-6">
                {messages.map((m, i) => (
                  <div key={i} className="animate-fade-in">
                    {m.role === "user" ? (
                      <div className="flex justify-end">
                        <div className="max-w-[85%] rounded-3xl px-4 py-2.5 bg-white/10 text-foreground">
                          <p className="text-[15px] whitespace-pre-wrap break-words leading-relaxed">{m.content}</p>
                        </div>
                      </div>
                    ) : (
                      <div className="flex gap-3">
                        <div className="h-7 w-7 shrink-0 mt-1 flex items-center justify-center">
                          <img src="/icon.png" alt="" aria-hidden="true" className="h-7 w-7 object-contain" loading="lazy" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="prose-mythos text-[15px] break-words">
                            {m.content ? (
                              <ReactMarkdown remarkPlugins={[remarkGfm]}>{m.content}</ReactMarkdown>
                            ) : !m.image && !m.music && !m.song && !m.video && !m.agent && !m.ext ? (
                              <div className="flex items-center gap-2 text-muted-foreground text-sm">
                                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                                <span>Denke nach…</span>
                              </div>
                            ) : null}
                            {m.image && (
                              <img
                                src={m.image.url}
                                alt={m.image.prompt}
                                className="mt-2 rounded-2xl border border-white/10 max-w-full h-auto"
                                loading="lazy"
                              />
                            )}
                            {m.music && <FunkPlayer pattern={m.music} />}
                            {m.song && <SongPlayer request={m.song} />}
                            {m.video && <VideoPlayer request={m.video} />}
                            {m.ext && <BrowserExtensionPanel initialTask={m.ext.task || undefined} />}
                            {m.agent && (
                              <AgentBrowser
                                task={m.agent.task}
                                onDone={(ans) => {
                                  if (!ans) return;
                                  supabase.from("messages").insert({
                                    conversation_id: activeId,
                                    role: "assistant",
                                    content: ans,
                                    metadata: { agentAnswer: true },
                                  });
                                }}
                              />
                            )}
                          </div>
                          {m.content && !sending && (
                            <div className="flex items-center gap-0.5 mt-2 -ml-1.5 opacity-40 hover:opacity-100 transition-opacity">
                              <button onClick={() => copyMessage(m.content)} title="Kopieren" className="p-1.5 rounded-md hover:bg-white/5">
                                <Copy className="h-3.5 w-3.5" />
                              </button>
                              {voice.supported && (
                                <button
                                  onClick={() => voice.status === "speaking" ? voice.stopSpeaking() : voice.speak(m.content)}
                                  title={voice.status === "speaking" ? "Stop" : "Vorlesen"}
                                  className="p-1.5 rounded-md hover:bg-white/5"
                                >
                                  {voice.status === "speaking" ? <VolumeX className="h-3.5 w-3.5" /> : <Volume2 className="h-3.5 w-3.5" />}
                                </button>
                              )}
                            </div>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                ))}
                {suggestions.length > 0 && !sending && (
                  <div className="flex flex-wrap gap-1.5 pt-1 pl-10 animate-fade-in">
                    <Lightbulb className="h-3.5 w-3.5 text-foreground/50 mt-1.5" />
                    {suggestions.map((s, i) => (
                      <button
                        key={i}
                        onClick={() => { setSuggestions([]); send(s); }}
                        className="border border-white/10 rounded-full px-3 py-1 text-xs hover:border-white/30 hover:bg-white/5 transition-all"
                      >
                        {s}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Composer */}
          <div className="shrink-0 px-3 sm:px-6 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-2">
            <div className="max-w-3xl mx-auto">
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
                    {voice.status === "speaking" ? "🔊 spricht..." : voice.interim || "👂 höre zu... (sprich einfach drauf los)"}
                  </span>
                </div>
              )}

              {attachments.length > 0 && (
                <div className="flex flex-wrap gap-1.5 mb-2">
                  {attachments.map((a, i) => (
                    <div key={i} className="flex items-center gap-1.5 bg-white/5 border border-white/10 rounded-lg px-2 py-1 text-xs">
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
                  <div className="glass-strong rounded-2xl p-1.5 mb-2 max-h-64 overflow-y-auto animate-fade-in">
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
                          className={`w-full flex items-center gap-2 rounded-xl px-2 py-1.5 text-left text-xs transition-colors ${active ? "bg-white/10" : "hover:bg-white/5"}`}
                        >
                          <Icon className="h-3.5 w-3.5 text-foreground/80 shrink-0" />
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

              <div className="relative rounded-3xl border border-white/10 bg-[hsl(0_0%_10%)] focus-within:border-white/25 transition-colors shadow-[0_8px_32px_hsl(0_0%_0%/0.4)]">
                <Textarea ref={textareaRef}
                  value={input}
                  onChange={(e) => { setInput(e.target.value); setSlashIndex(0); }}
                  onKeyDown={(e) => {
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
                    mode === "support" ? "Frage Mythos AI…" :
                    mode === "agent" ? "Was soll der Agent tun?" : "Frag mich alles…"
                  }
                  className="min-h-[56px] max-h-[200px] overflow-hidden resize-none border-0 bg-transparent focus-visible:ring-0 text-[15px] px-4 pt-4 pb-14 shadow-none"
                                    style={{ height: 'auto' }}
                                    disabled={sending}
                />
                {/* Action row inside composer */}
                <div className="absolute left-2 bottom-2 flex items-center gap-1">
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button
                        type="button" size="icon" variant="ghost"
                        className="h-9 w-9 rounded-full"
                        onClick={() => fileInputRef.current?.click()}
                        disabled={uploading || sending}
                        aria-label="Anhang"
                      >
                        {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Paperclip className="h-4 w-4" />}
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent side="top">Datei anhängen</TooltipContent>
                  </Tooltip>
                </div>

                <div className="absolute right-2 bottom-2 flex items-center gap-1">
                  {voice.supported && (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          type="button"
                          onClick={() => { if (voice.status === "listening") voice.stopListening(); else voice.startDictation(); }}
                          disabled={sending}
                          size="icon"
                          variant="ghost"
                          className={`h-9 w-9 rounded-full ${
                            voice.status === "listening" && !voiceMode ? "bg-foreground text-background hover:bg-foreground/90" : ""
                          }`}
                          aria-label="Diktieren"
                        >
                          {voice.status === "listening" ? <MicOff className="h-4 w-4" /> : <Mic className="h-4 w-4" />}
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent side="top">{voice.status === "listening" ? "Diktat stoppen" : "Diktieren"}</TooltipContent>
                    </Tooltip>
                  )}
                  <Button
                    onClick={() => send()}
                    disabled={!input.trim() || sending}
                    size="icon"
                    className="h-9 w-9 rounded-full bg-foreground text-background hover:bg-foreground/90 disabled:opacity-30"
                    aria-label="Senden"
                  >
                    {sending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
                  </Button>
                </div>
              </div>
              <p className="text-[10px] text-muted-foreground text-center mt-2 hidden sm:block">
                Mythos AI kann Fehler machen. Wichtige Infos prüfen. <code className="font-mono">/</code> für Commands.
              </p>
            </div>
          </div>
        </main>

        <Paywall open={paywall.open} onOpenChange={(o) => setPaywall({ open: o })} reason={paywall.reason} />
      </div>
    </TooltipProvider>
  );
};

export default Chat;
