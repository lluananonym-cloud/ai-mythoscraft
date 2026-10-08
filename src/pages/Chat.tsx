import { useState, useEffect, useRef } from "react";
import ImageGeneration, { browserImageUrl, type GeneratedImage } from "@/components/chat/ImageGeneration";
import { isImageRequest } from "@/lib/imageIntent";
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
import MarkdownMessage from "@/components/MarkdownMessage";
import { adDue, pickAd, adMarkdown } from "@/lib/mythosAds";
import LogoOrb from "@/components/LogoOrb";
import {
  Plus, Send, Trash2, MessageSquare, Loader2, Sparkles, Brain, HelpCircle, Menu,
  Mic, MicOff, Volume2, VolumeX, Paperclip, X as XIcon, Drama, Copy, Download, Lightbulb,
  Image as ImageIcon, Music, Globe, FileText, Languages, UserCog, WifiOff, Smile, AudioLines, Film,
  PanelLeftClose, PanelLeft, LogOut, Key, Shield, Bot, Users, BarChart3,
  Crown, Gamepad2, Server, Ticket, User as UserIcon, Search, Puzzle,
  ArrowLeft, ArrowRight, FolderOpen, Shapes, Clock, Briefcase, Target, Square, Code2, Wrench, ChevronRight,
  Globe2, Compass,
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
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { isPuterModel, getPuterLabel } from "@/lib/puterAi";
import ModelPicker from "@/components/ModelPicker";
import { DEFAULT_MYTHOS_ID, isAllowed, mythosLabel } from "@/lib/mythosModels";

import { nvidiaChat, nvidiaLLM } from "@/lib/nvidiaApi";
import CustomizeDialog from "@/components/chat/CustomizeDialog";
import ArtifactsDialog from "@/components/chat/ArtifactsDialog";
import ProjectsDialog from "@/components/chat/ProjectsDialog";
import { getInstructions, getMcpServers, getProjects, projectOfChat, setProjects, type Project } from "@/lib/chatPrefs";
import { callTool, collectTools } from "@/lib/mcpClient";

const SLASH_COMMANDS = [
  { cmd: "/goal",      args: "<ziel>",          icon: Target,    desc: "Ziel setzen – Mythos arbeitet selbstständig Schritt für Schritt, bis es erreicht ist" },
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
  { cmd: "/koppeln",   args: "[code]",          icon: Code2,     desc: "Mit Mythos Code auf dem PC koppeln und ihn von hier steuern" },
  { cmd: "/codeprogramm", args: "",             icon: Download,  desc: "Mythos Code als Windows-App herunterladen" },
  { cmd: "/handyapp", args: "",                 icon: Download,  desc: "Mythos als iPhone-App herunterladen (Chat + Code-Fernsteuerung)" },
  { cmd: "/handyappadmin", args: "",            icon: Shield,    desc: "Admin: iOS-Projekt-ZIP für den GitHub-Build erzeugen", admin: true },
  { cmd: "/handyappupload", args: "<drive-link>", icon: Shield,  desc: "Admin: .ipa-Link veröffentlichen", admin: true },
  { cmd: "/codeprogrammadmin", args: "",        icon: Shield,    desc: "Admin: App-ZIP für den Setup-Build erzeugen", admin: true },
  { cmd: "/codeprogrammadminupload", args: "<drive-link>", icon: Shield, desc: "Admin: Setup-EXE-Link veröffentlichen", admin: true },
  { cmd: "/apikeyadmin", args: "[2] <AIza…>", icon: Shield, desc: "Admin: gratis Google-AI-Ausweichschlüssel speichern (wenn Lovable-Credits leer sind)", admin: true },
  { cmd: "/nvidiakeyadmin", args: "[bild] <nvapi-…>", icon: Shield, desc: "Admin: NVIDIA-Schlüssel sicher auf dem Server speichern", admin: true },
  { cmd: "/exeupdateadmin", args: "<drive-link> [version]", icon: Shield, desc: "Admin: Update-EXE für Mythos Code veröffentlichen", admin: true },
];

type Persona = { id: string; name: string; avatar_emoji: string | null };
type Attachment = { url: string; name: string; mime: string };
type Conv = { id: string; title: string; mode: string; updated_at: string };
type Msg = { id?: string; role: "user" | "assistant" | "tool"; content: string; metadata?: any; image?: GeneratedImage; imagePending?: { prompt: string }; music?: FunkPattern; song?: SongRequest; video?: VideoRequest; agent?: { task: string }; search?: string; ext?: { task: string }; attachments?: Attachment[]; agents?: AgentStatus[]; thinking?: string };
/** Status eines Teil-Agenten im Multi-Agent-Modus (vom agent-Endpunkt gestreamt). */
type AgentStatus = { i: number; title: string; status: "läuft" | "fertig" | "fehler"; phase?: string };

const MODES = [
  { value: "support", label: "Support", icon: HelpCircle, desc: "Mythoscraft Server-Support" },
  { value: "agent", label: "Agent", icon: Brain, desc: "Mit Web-Suche & Tools" },
  { value: "general", label: "General", icon: Sparkles, desc: "Allgemeiner KI-Chat" },
];

const SIDEBAR_KEY = "mythos.sidebar.collapsed";

// /goal: so viele Arbeitsschritte darf Mythos höchstens machen, bevor es von selbst anhält.
const GOAL_MAX_STEPS = 20;
// Obergrenze für alle Antworten einer Runde (Schritte + Werkzeug-Aufrufe).
const MAX_TURNS = 60;
const GOAL_DONE = /\[ZIEL ERREICHT\]/i;
const TOOL_CALL = /<tool_call>\s*([\s\S]*?)\s*<\/tool_call>/i;

type ApiMsg = { role: string; content: any };
type GoalState = { text: string; step: number; status: "läuft" | "erreicht" | "gestoppt" | "limit" };
type StreamResult = { full: string; image?: GeneratedImage; music?: FunkPattern; agents?: AgentStatus[]; thinking: string; aborted: boolean };

/** Nachricht aus dem Verlauf so umwandeln, wie das Modell sie bekommt. */
const toApi = (m: Msg): ApiMsg =>
  m.role === "tool"
    ? { role: "user", content: `Ergebnis von Werkzeug ${m.metadata?.tool || ""}:\n${m.content}` }
    : { role: m.role, content: m.content };

function parseToolCall(text: string): { name: string; arguments: unknown } | null {
  const m = text.match(TOOL_CALL);
  if (!m) return null;
  try {
    const j = JSON.parse(m[1].replace(/^```(?:json)?|```$/g, "").trim());
    return typeof j?.name === "string" ? { name: j.name, arguments: j.arguments ?? j.args ?? {} } : null;
  } catch { return null; }
}

/** Technische Markierungen aus einer Antwort entfernen, bevor sie angezeigt wird. */
const forDisplay = (text: string) =>
  text.replace(/<tool_call>[\s\S]*?(<\/tool_call>|$)/gi, "").replace(GOAL_DONE, "").trimEnd();

const Chat = () => {
  const { user, profile, isAdmin, signOut } = useAuth();
  const nav = useNavigate();
  // Eine Route (/app/*) für alle Chats, damit die Seite beim Wechsel nicht neu startet.
  const splat = useParams()["*"] || "";
  const chatId = splat.match(/^c\/([^/]+)/)?.[1];
  const [searchParams, setSearchParams] = useSearchParams();
  // Chat, der gerade beim Senden neu angelegt wurde – dessen URL-Wechsel darf die laufende Antwort nicht neu laden.
  const createdIdRef = useRef<string | null>(null);
  const sub = useSubscription();
  const [paywall, setPaywall] = useState<{ open: boolean; reason?: string }>({ open: false });
  const [convs, setConvs] = useState<Conv[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [mode, setMode] = useState("support");
  const [mythosId, setMythosId] = useState<string>(() => {
    if (typeof window === "undefined") return DEFAULT_MYTHOS_ID;
    return localStorage.getItem("mythos.model") || DEFAULT_MYTHOS_ID;
  });
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
  // Chats, in deren Nachrichten der Suchtext vorkommt (nicht nur im Titel).
  const [contentHits, setContentHits] = useState<Set<string>>(new Set());
  // Nachrichten, die während einer laufenden Antwort eingegeben wurden – werden danach der Reihe nach gesendet.
  const [queue, setQueue] = useState<string[]>([]);
  const [dragging, setDragging] = useState(false);
  // Seitenleiste wie bei Claude
  const [projectId, setProjectId] = useState<string | null>(null);
  const [projects, setProjectsState] = useState<Project[]>(() => getProjects());
  const [dialog, setDialog] = useState<null | "projects" | "artifacts" | "customize">(null);
  const searchRef = useRef<HTMLInputElement>(null);
  // /goal
  const [goal, setGoal] = useState<GoalState | null>(null);
  const stopRef = useRef(false);
  const abortRef = useRef<AbortController | null>(null);
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
  useEffect(() => { localStorage.setItem("mythos.model", mythosId); }, [mythosId]);
  useEffect(() => {
    const sync = () => setProjectsState(getProjects());
    window.addEventListener("mythos-prefs", sync);
    return () => window.removeEventListener("mythos-prefs", sync);
  }, []);
  // downgrade silently if the stored choice is not covered by the current plan
  useEffect(() => {
    if (!sub.loading && !isAllowed(mythosId, sub.tier)) setMythosId(DEFAULT_MYTHOS_ID);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sub.loading, sub.tier]);

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
    setMessages([]);
    const { data: conv } = await supabase.from("conversations").select("id,mode").eq("id", id).maybeSingle();
    if (!conv) { toast.error("Chat nicht gefunden"); nav("/app", { replace: true }); return; }
    setMode((conv as { mode: string }).mode);
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
        ext: m.metadata?.ext,
        agents: m.metadata?.agents,
        thinking: m.metadata?.thinking,
      })) as Msg[];
      setMessages(enriched);
    }
  };

  // Die URL bestimmt den offenen Chat: /app = neuer Chat, /app/c/<id> = genau dieser Chat.
  useEffect(() => {
    if (!user) return;
    if (chatId) {
      if (createdIdRef.current === chatId) { createdIdRef.current = null; return; }
      if (sending) stop();
      setGoal(null);
      loadMessages(chatId);
    } else {
      if (sending) stop();
      setGoal(null);
      setActiveId(null);
      setMessages([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chatId, user]);

  const openChat = (id: string) => {
    setMobileSidebar(false);
    if (id !== chatId) nav(`/app/c/${id}`);
  };

  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" }); }, [messages]);

  const newChat = () => {
    setActiveId(null);
    setMessages([]);
    setMobileSidebar(false);
    if (chatId) nav("/app");
  };

  const deleteChat = async (id: string) => {
    await supabase.from("conversations").delete().eq("id", id);
    if (activeId === id) { setActiveId(null); setMessages([]); nav("/app", { replace: true }); }
    loadConvs();
  };

  const send = async (override?: string) => {
    let text = (override ?? input).trim();
    if (!text || sending) return;
    // Auto-detect generation commands
    const lowerText = text.toLowerCase();
    if (lowerText.includes("generiere mir ein video von")) {
      const match = lowerText.match(/generiere mir ein video von (.+)/i);
      if (match && match[1]) {
        text = `/video ${match[1].trim()}`;
      }
    } else if (lowerText.includes("generiere mir musik von")) {
      const match = lowerText.match(/generiere mir musik von (.+)/i);
      if (match && match[1]) {
        text = `/music ${match[1].trim()}`;
      }
    }
    // Bild-Wünsche in normaler Sprache ("generiere mir das als Bild") erkennt der Server selbst
    // und löst "das" über den Chatverlauf auf. Hier nur für Paywall und Routing.
    const wantsImage = /^\/image\b/i.test(text) || (!attachments.length && isImageRequest(text));
    const localReply = (content: string) => setMessages(prev => [...prev, { role: "user", content: text }, { role: "assistant", content }]);
    const downloadBlob = (blob: Blob, name: string) => {
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob); a.download = name; a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 10_000);
    };
    if (/^\/codeadmin\b/i.test(text)) {
      if (!override) setInput("");
      if (!isAdmin) { toast.error("Nur für Admins."); return; }
      const { buildCliZip, ADMIN_GUIDE, CLI_PKG, CLI_VERSION } = await import("@/lib/mythosCli");
      if (localStorage.getItem("mythos_cli_built") === CLI_VERSION) {
        localReply(`Das Mythos-Code-Paket v${CLI_VERSION} wurde bereits erstellt. **/codeadmin** geht nur einmal pro Version.`);
        return;
      }
      downloadBlob(await buildCliZip(window.location.origin), `${CLI_PKG}.zip`);
      localStorage.setItem("mythos_cli_built", CLI_VERSION);
      localReply(ADMIN_GUIDE);
      return;
    }
    if (/^\/apikeyadmin\b/i.test(text)) {
      if (!override) setInput("");
      if (!isAdmin) { toast.error("Nur für Admins."); return; }
      const parts = text.replace(/^\/apikeyadmin\b/i, "").trim().split(/\s+/).filter(Boolean);
      const which = /^(2|zweit|backup)$/i.test(parts[0] ?? "") ? "google2" : "google";
      const value = which === "google2" ? parts[1] : parts[0];
      // Den Schlüssel nie im Chat anzeigen – nur maskiert.
      const reply = (content: string) => setMessages(prev => [...prev, { role: "user", content: `/apikeyadmin ${which === "google2" ? "2 " : ""}${value ? "AIza••••" : ""}` }, { role: "assistant", content }]);
      if (!value) { reply("So geht's:\n```\n/apikeyadmin AIza…      (Google-AI-Ausweichschlüssel, gratis)\n/apikeyadmin 2 AIza…    (zweiter, falls der erste ausgeschöpft ist)\n```\nGratis-Schlüssel: aistudio.google.com → „Get API key“ → „Create API key“.\nDer Schlüssel wird nur geschützt auf dem Server gespeichert und nie wieder angezeigt."); return; }
      const { data, error } = await supabase.functions.invoke("nvidia", { body: { kind: "setkey", which, value } });
      if (error || data?.error) { toast.error("Speichern fehlgeschlagen: " + (data?.error || error?.message)); reply("✗ " + (data?.error || error?.message)); return; }
      reply(`✓ Google-AI-Ausweichschlüssel${which === "google2" ? " #2" : ""} sicher auf dem Server gespeichert. Wird automatisch genutzt, wenn Lovable-Credits leer sind.`);
      return;
    }
    if (/^\/nvidiakeyadmin\b/i.test(text)) {
      if (!override) setInput("");
      if (!isAdmin) { toast.error("Nur für Admins."); return; }
      const parts = text.replace(/^\/nvidiakeyadmin\b/i, "").trim().split(/\s+/).filter(Boolean);
      const which = /^(bild|image)$/i.test(parts[0] ?? "") ? "image" : "main";
      const value = which === "image" ? parts[1] : parts[0];
      // Den Schlüssel nie im Chat anzeigen – nur maskiert.
      const reply = (content: string) => setMessages(prev => [...prev, { role: "user", content: `/nvidiakeyadmin ${which === "image" ? "bild " : ""}${value ? "nvapi-••••" : ""}` }, { role: "assistant", content }]);
      if (!value) { reply("So geht's:\n```\n/nvidiakeyadmin nvapi-…        (Chat & Vorlesen, auch Bilder)\n/nvidiakeyadmin bild nvapi-…   (nur Bilder)\n```\nDer Schlüssel wird nur geschützt auf dem Server gespeichert und nie wieder angezeigt."); return; }
      const { data, error } = await supabase.functions.invoke("nvidia", { body: { kind: "setkey", which, value } });
      if (error || data?.error) { toast.error("Speichern fehlgeschlagen: " + (data?.error || error?.message)); reply("✗ " + (data?.error || error?.message)); return; }
      reply(`✓ NVIDIA-Schlüssel${which === "image" ? " für Bilder" : ""} sicher auf dem Server gespeichert.`);
      return;
    }
    if (/^\/exeupdateadmin\b/i.test(text)) {
      if (!override) setInput("");
      if (!isAdmin) { toast.error("Nur für Admins."); return; }
      const { toDirectDownloadUrl, APP_DOWNLOAD_SETTING, APP_UPDATE_SETTING, APP_VERSION } = await import("@/lib/mythosCli");
      const [rawLink = "", rawVersion] = text.replace(/^\/exeupdateadmin\b/i, "").trim().split(/\s+/);
      const url = toDirectDownloadUrl(rawLink);
      const version = rawVersion || APP_VERSION;
      if (!url || !/^\d+\.\d+\.\d+$/.test(version)) {
        localReply("So geht's:\n```\n/exeupdateadmin https://drive.google.com/file/d/…/view 1.3.0\n```\nOhne Version wird die Version der aktuellen ZIP (" + APP_VERSION + ") genommen.");
        return;
      }
      const now = new Date().toISOString();
      const { error } = await supabase.from("app_settings").upsert([
        { key: APP_UPDATE_SETTING, value: JSON.stringify({ version, url }), updated_at: now },
        { key: APP_DOWNLOAD_SETTING, value: url, updated_at: now },
      ]);
      if (error) { toast.error("Speichern fehlgeschlagen: " + error.message); return; }
      localReply(`✓ Update **v${version}** veröffentlicht. Neue Downloads über **/codeprogramm** bekommen ab jetzt diese Version.\n\nDirekter Link: ${url}`);
      return;
    }
    if (/^\/handyappupload\b/i.test(text)) {
      if (!override) setInput("");
      if (!isAdmin) { toast.error("Nur für Admins."); return; }
      const { toDirectDownloadUrl, HANDY_DOWNLOAD_SETTING } = await import("@/lib/mythosCli");
      const url = toDirectDownloadUrl(text.replace(/^\/handyappupload\b/i, ""));
      if (!url) { localReply("Bitte einen gültigen https-Link zur .ipa angeben:\n```\n/handyappupload https://drive.google.com/file/d/…/view\n```"); return; }
      const { error } = await supabase.from("app_settings")
        .upsert({ key: HANDY_DOWNLOAD_SETTING, value: url, updated_at: new Date().toISOString() });
      if (error) { toast.error("Speichern fehlgeschlagen: " + error.message); return; }
      localReply(`✓ Download-Link gespeichert. Ab jetzt kann jeder mit **/handyapp** die Installationsanleitung bekommen.\n\nDirekter Link: ${url}`);
      return;
    }
    if (/^\/handyappadmin\b/i.test(text)) {
      if (!override) setInput("");
      if (!isAdmin) { toast.error("Nur für Admins."); return; }
      const { buildHandyZip, HANDY_ADMIN_GUIDE, HANDY_PKG } = await import("@/lib/mythosCli");
      downloadBlob(await buildHandyZip(window.location.origin), `${HANDY_PKG}.zip`);
      localReply(HANDY_ADMIN_GUIDE);
      return;
    }
    if (/^\/handyapp\b/i.test(text)) {
      if (!override) setInput("");
      const { HANDY_DOWNLOAD_SETTING, HANDY_USER_GUIDE } = await import("@/lib/mythosCli");
      const { data } = await supabase.from("app_settings").select("value").eq("key", HANDY_DOWNLOAD_SETTING).maybeSingle();
      if (!data?.value) { localReply("Die Mythos-Handy-App ist noch nicht verfügbar – schau bald wieder vorbei."); return; }
      localReply(HANDY_USER_GUIDE(data.value));
      return;
    }
    if (/^\/codeprogrammadminupload\b/i.test(text)) {
      if (!override) setInput("");
      if (!isAdmin) { toast.error("Nur für Admins."); return; }
      const { toDirectDownloadUrl, APP_DOWNLOAD_SETTING } = await import("@/lib/mythosCli");
      const url = toDirectDownloadUrl(text.replace(/^\/codeprogrammadminupload\b/i, ""));
      if (!url) { localReply("Bitte einen gültigen https-Link angeben:\n```\n/codeprogrammadminupload https://drive.google.com/file/d/…/view\n```"); return; }
      const { error } = await supabase.from("app_settings")
        .upsert({ key: APP_DOWNLOAD_SETTING, value: url, updated_at: new Date().toISOString() });
      if (error) { toast.error("Speichern fehlgeschlagen: " + error.message); return; }
      localReply(`✓ Download-Link gespeichert. Ab jetzt kann jeder mit **/codeprogramm** die Setup-EXE herunterladen.\n\nDirekter Link: ${url}`);
      return;
    }
    if (/^\/codeprogrammadmin\b/i.test(text)) {
      if (!override) setInput("");
      if (!isAdmin) { toast.error("Nur für Admins."); return; }
      const { buildAppZip, APP_ADMIN_GUIDE, APP_PKG } = await import("@/lib/mythosCli");
      downloadBlob(await buildAppZip(window.location.origin), `${APP_PKG}.zip`);
      localReply(APP_ADMIN_GUIDE);
      return;
    }
    if (/^\/koppeln\b/i.test(text)) {
      if (!override) setInput("");
      // Den Code zeigt die Mythos-Code-App auf dem PC nach /koppeln an – hier wird er eingegeben.
      const code = text.replace(/^\/koppeln\b/i, "").replace(/\D/g, "").slice(0, 6);
      nav(code ? `/code?code=${code}` : "/code");
      return;
    }
    if (/^\/codeprogramm\b/i.test(text)) {
      if (!override) setInput("");
      // Die Setup-EXE wird automatisch von GitHub Actions gebaut (build-apps.yml) und liegt immer aktuell im Release.
      const { APP_USER_GUIDE } = await import("@/lib/mythosCli");
      const { APPS, downloadUrl } = await import("@/lib/downloads");
      const url = downloadUrl(APPS.find((x) => x.id === "code")!);
      const a = document.createElement("a");
      a.href = url; a.target = "_blank"; a.rel = "noopener"; a.click();
      localReply(APP_USER_GUIDE(url));
      return;
    }
    if (/^\/code\s*$/i.test(text)) {
      if (!override) setInput("");
      if (!sub.isPro) { setPaywall({ open: true, reason: "Mythos Code (CLI) ist eine Pro-Funktion." }); return; }
      const { USER_GUIDE } = await import("@/lib/mythosCli");
      setMessages(prev => [...prev, { role: "user", content: text }, { role: "assistant", content: USER_GUIDE }]);
      return;
    }
    // /goal <ziel> — Mythos arbeitet selbstständig in mehreren Schritten, bis das Ziel erreicht ist.
    let goalText: string | undefined;
    const goalMatch = text.match(/^\/goal\b\s*([\s\S]*)$/i);
    if (goalMatch) {
      goalText = goalMatch[1].trim();
      if (!goalText) {
        if (!override) setInput("");
        localReply("So geht's:\n```\n/goal <dein Ziel>\n```\nBeispiel: `/goal Schreib mir einen kompletten Businessplan für einen Minecraft-Server`\n\nMythos macht dann einen Plan und arbeitet Schritt für Schritt selbstständig weiter, bis das Ziel erreicht ist. Oben siehst du das Ziel, mit **Stopp** hältst du es jederzeit an.");
        return;
      }
      text = `🎯 Ziel: ${goalText}`;
    }
    if (sub.chatLimitReached) { setPaywall({ open: true, reason: `Du hast dein tägliches Free-Limit (${20} Chats) erreicht.` }); return; }
    if (wantsImage && !sub.canGenerateImage) { setPaywall({ open: true, reason: "Bilder generieren ist eine Pro-Funktion." }); return; }
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
      createdIdRef.current = convId;
      // Im geöffneten Projekt angelegt -> gehört zu diesem Projekt.
      if (projectId) setProjects(getProjects().map(p => p.id === projectId ? { ...p, chatIds: [convId!, ...p.chatIds] } : p));
      nav(`/app/c/${convId}`, { replace: true });
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
    // Nicht auf das Speichern warten: die KI-Anfrage startet sofort (spart eine Server-Runde).
    supabase.from("messages").insert({
      conversation_id: convId, role: "user", content: displayContent,
      metadata: attachments.length ? { attachments } : null,
    }).then(({ error }) => { if (error) console.warn("Nachricht nicht gespeichert", error.message); });
    setAttachments([]);

    if (goalText) setGoal({ text: goalText, step: 1, status: "läuft" });
    const history: ApiMsg[] = messages.filter(m => !(m as any).ad).map(toApi);
    history.push({ role: "user", content: userContentForAI });
    try {
      await runAgent(convId!, history, text, goalText);
    } catch (e) {
      console.error(e);
      toast.error("Verbindungsfehler");
    } finally {
      abortRef.current = null;
      setSending(false);
    }
  };

  /** Ein Modell-Aufruf mit Streaming in die letzte (leere) Assistent-Nachricht. null = fehlgeschlagen. */
  const streamOnce = async (apiMessages: ApiMsg[], convId: string): Promise<StreamResult | null> => {
    const chosenModel = (profile as any)?.ai_model as string | undefined;
    const modelForCall = isPuterModel(chosenModel) ? chosenModel : undefined;
    // Bild-Wünsche laufen immer über die Chat-Funktion (dort sitzen Erkennung und Backup-Kette).
    const lastUser = [...apiMessages].reverse().find(m => m.role === "user");
    const lastUserText = typeof lastUser?.content === "string" ? lastUser.content
      : Array.isArray(lastUser?.content) ? (lastUser!.content as { type?: string; text?: string }[]).find(p => p.type === "text")?.text ?? "" : "";
    const toChat = /^\/image\b/i.test(lastUserText) || isImageRequest(lastUserText);
    const fnUrl = `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/${mode === "agent" && !toChat ? "agent" : "chat"}`;
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    const setLast = (patch: Partial<Msg>) => setMessages(prev => {
      const next = [...prev];
      next[next.length - 1] = { ...next[next.length - 1], role: "assistant", ...patch };
      return next;
    });
    const doFetch = () => fetch(fnUrl, {
      method: "POST",
      signal: ctrl.signal,
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY}` },
      body: JSON.stringify({
        conversationId: convId,
        userId: user?.id,
        personaId: personaId !== "none" ? personaId : undefined,
        model: modelForCall,
        mythos: mythosId,
        messages: apiMessages,
        clientImageFallback: true,
        mode,
      }),
    });

    let full = "", thinking = "";
    let imageData: GeneratedImage | undefined;
    let musicData: FunkPattern | undefined;
    let agentsData: AgentStatus[] | undefined;
    try {
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
            // Über den Server – der NVIDIA-Schlüssel ist nie im Browser.
            const asText = (c: unknown) => typeof c === "string" ? c : Array.isArray(c) ? c.map((p: { text?: string }) => p.text || "").join("\n") : "";
            const content = await nvidiaChat(apiMessages.map(m => ({ role: m.role, content: asText(m.content) })));
            setLast({ content });
            return { full: content, thinking: "", aborted: false };
          } catch (e) {
            console.error("Fallback error:", e);
            toast.error(`NVIDIA fallback failed: ${(e as Error)?.message || "unknown"}`);
          }
        } else {
          toast.error("Fehler beim Senden");
        }
        setMessages(prev => prev.slice(0, -1));
        return null;
      }

      const reader = resp.body.getReader();
      const decoder = new TextDecoder();
      let buf = "";
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
            if (p.imagePending) { setLast({ imagePending: p.imagePending }); continue; }
            if (p.imageFailed) {
              // Server-Kette erschöpft: direkt aus dem Browser bei Pollinations versuchen (eigene IP, eigenes Limit).
              const { prompt, reasons } = p.imageFailed as { prompt: string; reasons?: string[] };
              imageData = { url: browserImageUrl(prompt), prompt, fallback: true, reasons };
              setLast({ image: imageData, imagePending: undefined });
              continue;
            }
            if (p.tool) {
              setMessages(prev => {
                const next = [...prev];
                const last = next[next.length - 1];
                last.content = (last.content || "") + `\n\n> 🔧 *${p.tool}*\n\n`;
                return next;
              });
              continue;
            }
            if (p.agents) { agentsData = p.agents; setLast({ agents: p.agents }); continue; }
            if (p.search) { setLast({ search: p.search }); continue; }
            if (p.image) { imageData = p.image; setLast({ image: p.image, imagePending: undefined }); continue; }
            if (p.music) { musicData = p.music; setLast({ music: p.music }); continue; }
            // Gedanken des Modells (je nach Anbieter unterschiedlich benannt) live mitschreiben.
            const d = p.choices?.[0]?.delta;
            const r = typeof d?.reasoning === "string" ? d.reasoning
              : typeof d?.reasoning_content === "string" ? d.reasoning_content
              : Array.isArray(d?.reasoning_details) ? d.reasoning_details.map((x: any) => x?.text || x?.summary || "").join("") : "";
            if (r) { thinking += r; setLast({ thinking }); }
            const c = d?.content;
            if (c) { full += c; setLast({ content: full }); }
          } catch { buf = line + "\n" + buf; break; }
        }
      }
      return { full, image: imageData, music: musicData, agents: agentsData, thinking, aborted: false };
    } catch (e) {
      if ((e as Error)?.name === "AbortError") {
        // Gestoppt: was schon da ist, bleibt stehen.
        if (!full && !imageData) { setMessages(prev => prev.slice(0, -1)); return null; }
        return { full, image: imageData, music: musicData, agents: agentsData, thinking, aborted: true };
      }
      setMessages(prev => prev.slice(0, -1));
      throw e;
    }
  };

  /** Zusätzliche Hinweise fürs Modell: eigene Anweisungen, Projekt, Werkzeuge und /goal. */
  const buildHints = (
    tools: Awaited<ReturnType<typeof collectTools>>,
    goalNow: { text: string; step: number } | null,
    convId: string,
  ) => {
    const parts: string[] = [];
    const own = getInstructions();
    if (own) parts.push(`## Anweisungen des Nutzers\n${own}`);
    const proj = projectOfChat(convId);
    if (proj?.instructions) parts.push(`## Projekt „${proj.name}"\n${proj.instructions}`);
    if (tools.length) {
      parts.push(
        "## Werkzeuge (Connectoren)\n" +
        "Du kannst diese Werkzeuge selbst benutzen, wenn sie helfen. Für einen Aufruf schreibst du genau einen Block:\n" +
        '<tool_call>{"name": "werkzeug_name", "arguments": { ... }}</tool_call>\n' +
        "und hörst danach sofort auf zu schreiben. Du bekommst dann das Ergebnis und machst weiter. Höchstens ein Aufruf pro Antwort. " +
        "Erfinde keine Ergebnisse.\n\n" +
        tools.map(t => `- ${t.key}: ${(t.tool.description || "").slice(0, 300)}\n  Parameter: ${JSON.stringify(t.tool.inputSchema ?? {}).slice(0, 600)}`).join("\n"),
      );
    }
    if (goalNow) {
      parts.push(
        `## /goal-Modus – Schritt ${goalNow.step} von höchstens ${GOAL_MAX_STEPS}\n` +
        `Ziel: ${goalNow.text}\n\n` +
        "Du arbeitest selbstständig wie ein Agent, bis das Ziel vollständig erreicht ist. Regeln:\n" +
        (goalNow.step === 1 ? "- Beginne mit einem kurzen Plan als Checkliste (- [ ] …) und erledige dann direkt den ersten Punkt.\n" : "- Mach mit dem nächsten offenen Punkt deines Plans weiter. Wiederhole nichts, was schon erledigt ist.\n") +
        "- Jede Antwort ist ein echter Arbeitsschritt mit konkretem Ergebnis (Text, Code, Recherche, Werkzeug-Aufruf), nicht nur Ankündigungen.\n" +
        "- Frag nicht nach Erlaubnis und warte nicht auf den Nutzer. Triff sinnvolle Annahmen und nenne sie kurz.\n" +
        "- Ist noch etwas offen, beende die Antwort mit einer Zeile „Als Nächstes: …\".\n" +
        "- Erst wenn das Ziel komplett erreicht ist: kurze Zusammenfassung des Ergebnisses und als allerletzte Zeile genau [ZIEL ERREICHT]",
      );
    }
    return parts.join("\n\n");
  };

  /** Arbeitet so lange weiter, wie Werkzeug-Aufrufe oder ein /goal es verlangen. */
  const runAgent = async (convId: string, history: ApiMsg[], userText: string, goalText?: string) => {
    stopRef.current = false;
    const servers = getMcpServers().filter(s => s.enabled);
    const tools = servers.length ? await collectTools(servers) : [];
    let step = 1;
    let final = "";
    for (let turn = 0; turn < MAX_TURNS; turn++) {
      const hints = buildHints(tools, goalText ? { text: goalText, step } : null, convId);
      // Hinweise als eigene System-Nachricht direkt vor der letzten Nutzer-Nachricht – so bleiben Slash-Befehle unverändert.
      const apiMessages = hints && !/^\//.test(userText)
        ? [...history.slice(0, -1), { role: "system", content: hints }, history[history.length - 1]]
        : history;
      const res = await streamOnce(apiMessages, convId);
      if (!res) { if (goalText) setGoal(g => g && { ...g, status: "gestoppt" }); break; }
      if (res.full || res.image || res.music) {
        await supabase.from("messages").insert({
          conversation_id: convId, role: "assistant", content: res.full,
          metadata: { image: res.image, music: res.music, agents: res.agents, thinking: res.thinking || undefined, goal: goalText ? step : undefined },
        });
      }
      history.push({ role: "assistant", content: res.full });
      final = res.full;
      if (res.aborted || stopRef.current) { if (goalText) setGoal(g => g && { ...g, status: "gestoppt" }); break; }

      const call = tools.length ? parseToolCall(res.full) : null;
      if (call) {
        const entry = tools.find(t => t.key === call.name);
        let result: string;
        try {
          result = entry ? await callTool(entry.server, entry.tool.name, call.arguments) : `FEHLER: Werkzeug „${call.name}" gibt es nicht.`;
        } catch (e) { result = "FEHLER: " + ((e as Error)?.message || "unbekannt"); }
        result = result.slice(0, 12000) || "(leer)";
        const meta = { tool: call.name, args: call.arguments as any };
        setMessages(prev => [...prev, { role: "tool", content: result, metadata: meta }, { role: "assistant", content: "" }]);
        await supabase.from("messages").insert({ conversation_id: convId, role: "tool", content: result, metadata: meta });
        history.push({ role: "user", content: `Ergebnis von Werkzeug ${call.name}:\n${result}` });
        if (stopRef.current) { setMessages(prev => prev.slice(0, -1)); break; }
        continue;
      }

      if (goalText) {
        if (GOAL_DONE.test(res.full)) { setGoal(g => g && { ...g, status: "erreicht" }); toast.success("🎯 Ziel erreicht"); break; }
        if (step >= GOAL_MAX_STEPS) { setGoal(g => g && { ...g, status: "limit" }); break; }
        step++;
        setGoal(g => g && { ...g, step });
        const cont = `Weiter mit Schritt ${step}.`;
        const meta = { auto: true, step };
        setMessages(prev => [...prev, { role: "user", content: cont, metadata: meta }, { role: "assistant", content: "" }]);
        await supabase.from("messages").insert({ conversation_id: convId, role: "user", content: cont, metadata: meta });
        history.push({ role: "user", content: cont });
        continue;
      }
      break;
    }
    await supabase.from("conversations").update({ updated_at: new Date().toISOString() }).eq("id", convId);
    loadConvs();
    if (!final) return;
    if (adDue(isAdmin ? "admin" : sub.tier)) { const o = window.location.origin; pickAd(o).then(ad => setMessages(prev => [...prev, { role: "assistant", content: adMarkdown(ad, o), ad: true } as any])); }
    supabase.functions.invoke("extract-memory", { body: { text: userText } }).catch(() => {});
    supabase.functions.invoke("suggest", {
      body: { messages: [...history.slice(-6).map(m => ({ role: m.role, content: typeof m.content === "string" ? m.content : userText })), { role: "assistant", content: final }] },
    }).then(({ data }) => {
      if (data?.items?.length && !goalText) setSuggestions(data.items);
    }).catch(() => {});
  };

  const stop = () => {
    stopRef.current = true;
    abortRef.current?.abort();
    setQueue([]);
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

  // Von der Startseite: /app?q=... schickt die Nachricht direkt in einem neuen Chat ab.
  const startedFromHomeRef = useRef(false);
  useEffect(() => {
    const q = searchParams.get("q");
    if (!q || startedFromHomeRef.current || !user || sub.loading || chatId) return;
    startedFromHomeRef.current = true;
    const m = searchParams.get("mode");
    if (m && MODES.some(x => x.value === m)) setMode(m);
    setSearchParams({}, { replace: true });
    setTimeout(() => sendRef.current?.(q), 0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, user, sub.loading, chatId]);

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

  useEffect(() => {
    const q = convSearch.trim();
    if (q.length < 2 || !user) { setContentHits(new Set()); return; }
    const t = setTimeout(async () => {
      const { data } = await supabase.from("messages").select("conversation_id")
        .ilike("content", `%${q.replace(/[\\%_]/g, (m) => "\\" + m)}%`).limit(300);
      setContentHits(new Set((data || []).map((m: { conversation_id: string }) => m.conversation_id)));
    }, 300);
    return () => clearTimeout(t);
  }, [convSearch, user]);

  const filteredConvs = convSearch
    ? convs.filter(c => c.title.toLowerCase().includes(convSearch.toLowerCase()) || contentHits.has(c.id))
    : convs;

  // Warteschlange abarbeiten, sobald die aktuelle Antwort fertig ist.
  useEffect(() => {
    if (sending || !queue.length) return;
    const [next, ...rest] = queue;
    setQueue(rest);
    send(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sending, queue]);

  const submitOrQueue = () => {
    const text = input.trim();
    if (!text) return;
    if (sending) { setQueue(q => [...q, text]); setInput(""); return; }
    send();
  };

  const displayName = profile?.display_name || user?.email?.split("@")[0] || "Account";

  const activeProject = projects.find(p => p.id === projectId) || null;
  const sidebarConvs = activeProject ? filteredConvs.filter(c => activeProject.chatIds.includes(c.id)) : filteredConvs;

  const NavItem = ({ icon: Icon, label, onClick, badge, active }: { icon: typeof Plus; label: string; onClick: () => void; badge?: string; active?: boolean }) => (
    <button
      onClick={onClick}
      className={`w-full flex items-center gap-3 rounded-lg px-2.5 py-2 text-[15px] text-left transition-colors ${active ? "bg-white/10" : "hover:bg-white/5"} text-foreground/90`}
    >
      <Icon className="h-[18px] w-[18px] shrink-0 text-foreground/80" />
      <span className="truncate">{label}</span>
      {badge && <span className="rounded-md bg-white/10 px-1.5 py-0.5 text-[11px] text-foreground/70">{badge}</span>}
    </button>
  );

  const SidebarContentBlock = (
    <div className="flex flex-col h-full min-h-0">
      {/* Kopfzeile wie bei Claude: einklappen, zurück/vor, Chat/Code */}
      <div className="flex items-center gap-0.5 pl-2 pr-10 md:pr-2 pt-2 pb-2">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="ghost" size="icon" className="h-8 w-8 hidden md:inline-flex" onClick={() => setSidebarCollapsed(true)} aria-label="Seitenleiste einklappen">
              <PanelLeftClose className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent side="bottom">Seitenleiste einklappen</TooltipContent>
        </Tooltip>
        <Link to="/" className="md:hidden flex items-center px-1" aria-label="Startseite"><Logo size="sm" /></Link>
        <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => nav(-1)} aria-label="Zurück"><ArrowLeft className="h-4 w-4" /></Button>
        <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => nav(1)} aria-label="Vor"><ArrowRight className="h-4 w-4 opacity-60" /></Button>
        <div className="ml-auto flex items-center rounded-xl bg-white/5 p-0.5">
          <Tooltip>
            <TooltipTrigger asChild>
              <span className="flex h-7 w-8 items-center justify-center rounded-lg bg-white/10"><MessageSquare className="h-4 w-4" /></span>
            </TooltipTrigger>
            <TooltipContent side="bottom">Chat</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <button onClick={() => nav("/code")} className="flex h-7 w-8 items-center justify-center rounded-lg text-foreground/60 hover:text-foreground" aria-label="Code">
                <Code2 className="h-4 w-4" />
              </button>
            </TooltipTrigger>
            <TooltipContent side="bottom">Mythos Code</TooltipContent>
          </Tooltip>
        </div>
      </div>

      {/* Suchen */}
      <div className="px-2 pb-2">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <input
            ref={searchRef}
            value={convSearch}
            onChange={(e) => setConvSearch(e.target.value)}
            placeholder="Suchen"
            className="w-full bg-white/[0.04] border border-white/10 rounded-xl pl-9 pr-2 py-2 text-[15px] placeholder:text-muted-foreground focus:outline-none focus:border-white/25"
          />
        </div>
      </div>

      {/* Hauptmenü */}
      <div className="px-2 space-y-0.5">
        <button onClick={() => { setProjectId(null); newChat(); }} className="w-full flex items-center gap-3 rounded-lg px-2.5 py-2 text-[15px] text-left hover:bg-white/5">
          <span className="flex h-[22px] w-[22px] -ml-0.5 items-center justify-center rounded-full bg-white/10"><Plus className="h-3.5 w-3.5" /></span>
          <span>Neu</span>
        </button>
        <NavItem icon={FolderOpen} label="Projekte" onClick={() => setDialog("projects")} active={!!activeProject} />
        <NavItem icon={Shapes} label="Artifacts" onClick={() => setDialog("artifacts")} />
        <NavItem icon={Clock} label="Routinen" onClick={() => nav("/agents")} />
        <NavItem icon={Briefcase} label="Anpassungen" onClick={() => setDialog("customize")} />
        <NavItem icon={Globe2} label="Welt-Modus" onClick={() => nav("/welt")} />
        <NavItem icon={Compass} label="Entdecken" onClick={() => nav("/entdecken")} badge="Neu" />
      </div>

      {/* Chats */}
      <div className="px-4 pt-5 pb-1 flex items-center gap-1 text-[13px] text-muted-foreground">
        {activeProject ? (
          <>
            <button onClick={() => setProjectId(null)} className="hover:text-foreground">Chats</button>
            <ChevronRight className="h-3.5 w-3.5" />
            <span className="truncate text-foreground/80">{activeProject.name}</span>
          </>
        ) : <span>Chats</span>}
      </div>
      <ScrollArea className="flex-1 px-2">
        <div className="space-y-0.5 pb-2">
          {sidebarConvs.length === 0 && (
            <p className="text-xs text-muted-foreground p-3 text-center">
              {convSearch ? "Keine Treffer" : activeProject ? "Noch keine Chats in diesem Projekt – klick auf „Neu“." : "Noch keine Chats"}
            </p>
          )}
          {sidebarConvs.map(c => (
            <div
              key={c.id}
              className={`group flex items-center gap-2 rounded-lg pl-3 pr-1 py-2 text-sm cursor-pointer transition-colors ${
                activeId === c.id ? "bg-white/10 text-foreground" : "hover:bg-white/5 text-foreground/80"
              }`}
              onClick={() => openChat(c.id)}
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
        <main
          className="relative flex-1 min-w-0 flex flex-col h-full"
          onDragOver={(e) => { if (e.dataTransfer.types.includes("Files")) { e.preventDefault(); setDragging(true); } }}
          onDragLeave={(e) => { if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false); }}
          onDrop={(e) => {
            if (!e.dataTransfer.files.length) return;
            e.preventDefault(); setDragging(false);
            uploadFiles(e.dataTransfer.files);
          }}
        >
          {dragging && (
            <div className="pointer-events-none absolute inset-2 z-50 flex items-center justify-center rounded-3xl border-2 border-dashed border-violet-400/70 bg-background/70 backdrop-blur-sm">
              <div className="glass-strong rounded-2xl px-6 py-4 text-sm">⬇ Dateien oder Bilder hier ablegen</div>
            </div>
          )}
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

            {/* Model + effort in one */}
            <ModelPicker
              value={mythosId}
              tier={sub.tier}
              onChange={setMythosId}
              onLocked={(reason) => setPaywall({ open: true, reason })}
            />

            <Select value={mode} onValueChange={setMode}>
              <SelectTrigger className="h-9 w-auto min-w-0 border-0 bg-transparent hover:bg-white/5 px-2 gap-1.5 text-xs text-muted-foreground focus:ring-0">
                <SelectValue />
              </SelectTrigger>
              <SelectContent align="start">
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
                    onClick={() => { voice.prepare(); setVoiceMode(v => !v); }}
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

          {goal && (
            <div className="shrink-0 border-b border-violet-400/15 bg-violet-500/[0.06] animate-fade-in">
              <div className="max-w-3xl mx-auto flex items-center gap-2 px-3 sm:px-6 py-2 text-sm">
                <Target className="h-4 w-4 text-violet-300 shrink-0" />
                <span className="truncate flex-1"><span className="text-muted-foreground">Ziel: </span>{goal.text}</span>
                <span className="text-xs text-muted-foreground shrink-0 flex items-center gap-1.5">
                  {goal.status === "läuft" && <><Loader2 className="h-3 w-3 animate-spin" /> Schritt {goal.step}</>}
                  {goal.status === "erreicht" && <span className="text-emerald-400">✓ Erreicht nach {goal.step} {goal.step === 1 ? "Schritt" : "Schritten"}</span>}
                  {goal.status === "gestoppt" && "Gestoppt"}
                  {goal.status === "limit" && `Pause nach ${GOAL_MAX_STEPS} Schritten`}
                </span>
                {goal.status === "läuft" ? (
                  <Button size="sm" variant="outline" className="h-7 px-2.5 text-xs border-white/15" onClick={stop}>
                    <Square className="h-3 w-3 mr-1 fill-current" /> Stopp
                  </Button>
                ) : (
                  <>
                    {(goal.status === "gestoppt" || goal.status === "limit") && !sending && (
                      <Button size="sm" variant="outline" className="h-7 px-2.5 text-xs border-white/15" onClick={() => send(`/goal ${goal.text}`)}>Weitermachen</Button>
                    )}
                    <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => setGoal(null)} aria-label="Ziel ausblenden"><XIcon className="h-3.5 w-3.5" /></Button>
                  </>
                )}
              </div>
            </div>
          )}

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
                    { icon: Target,     label: "/goal Erstelle mir einen kompletten Lernplan für die Matheprüfung" },
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
                    {m.role === "user" && m.metadata?.auto ? (
                      <div className="flex items-center gap-3 text-[11px] uppercase tracking-wider text-muted-foreground">
                        <div className="h-px flex-1 bg-white/10" />
                        <span className="flex items-center gap-1.5"><Target className="h-3 w-3 text-violet-300" /> Schritt {m.metadata.step}</span>
                        <div className="h-px flex-1 bg-white/10" />
                      </div>
                    ) : m.role === "tool" ? (
                      <details className="ml-10 rounded-xl border border-white/10 bg-white/[0.03] px-3 py-2 text-xs">
                        <summary className="cursor-pointer select-none text-muted-foreground flex items-center gap-1.5">
                          <Wrench className="h-3 w-3 text-violet-300" /> Werkzeug benutzt: <span className="font-mono text-foreground/80">{m.metadata?.tool}</span>
                        </summary>
                        {m.metadata?.args && <pre className="mt-2 whitespace-pre-wrap break-all text-muted-foreground">{JSON.stringify(m.metadata.args, null, 2)}</pre>}
                        <div className="mt-2 max-h-60 overflow-y-auto whitespace-pre-wrap break-words text-foreground/80">{m.content}</div>
                      </details>
                    ) : m.role === "user" ? (
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
                            {m.agents && m.agents.length > 0 && (
                              <div className="not-prose mb-3 rounded-xl border border-violet-400/20 bg-violet-500/5 px-3 py-2 text-xs">
                                <div className="mb-1 font-semibold text-violet-300">🧩 Multi-Agent: {m.agents.length} Agenten parallel</div>
                                {m.agents.map(a => (
                                  <div key={a.i} className="flex items-center gap-2 py-0.5">
                                    <span className="w-4 text-center">{a.status === "fertig" ? "✓" : a.status === "fehler" ? "⚠" : <Loader2 className="inline h-3 w-3 animate-spin" />}</span>
                                    <span className="flex-1 truncate text-foreground/90">Agent {a.i + 1}: {a.title}</span>
                                    {a.phase && <span className="max-w-[45%] truncate text-muted-foreground">{a.phase}</span>}
                                  </div>
                                ))}
                              </div>
                            )}
                            {m.thinking && (
                              <details className="not-prose mb-3 rounded-xl border border-white/10 bg-white/[0.03] px-3 py-2 text-xs">
                                <summary className="cursor-pointer select-none text-muted-foreground">
                                  {m.content ? "💭 Gedanken" : <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3 w-3 animate-spin" /> Denkt nach…</span>}
                                </summary>
                                <div className="mt-2 max-h-72 overflow-y-auto whitespace-pre-wrap leading-relaxed text-muted-foreground">{m.thinking}</div>
                              </details>
                            )}
                            {m.search && m.content && (
                              <div className="not-prose mb-2 inline-flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[11px] text-muted-foreground">
                                <Globe className="h-3 w-3" /> Im Internet gesucht: <span className="text-foreground/80">{m.search}</span>
                              </div>
                            )}
                            {m.content ? (
                              <>
                                <MarkdownMessage content={forDisplay(m.content)} />
                                {GOAL_DONE.test(m.content) && (
                                  <div className="not-prose mt-3 inline-flex items-center gap-1.5 rounded-full border border-emerald-400/30 bg-emerald-500/10 px-2.5 py-1 text-xs text-emerald-300">
                                    <Target className="h-3 w-3" /> Ziel erreicht
                                  </div>
                                )}
                              </>
                            ) : !m.image && !m.imagePending && !m.music && !m.song && !m.video && !m.agent && !m.ext ? (
                              <div className="flex items-center gap-2 text-muted-foreground text-sm">
                                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                                {m.search ? (
                                  <span className="flex items-center gap-1.5">
                                    <Globe className="h-3.5 w-3.5 animate-pulse" />
                                    Suche im Internet nach <span className="text-foreground/80">„{m.search}"</span>…
                                  </span>
                                ) : (
                                  <span>Denke nach…</span>
                                )}
                              </div>
                            ) : null}
                            {(m.image || m.imagePending) && <ImageGeneration image={m.image} pending={m.imagePending} />}
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
                          {forDisplay(m.content) && !sending && (
                            <div className="flex items-center gap-0.5 mt-2 -ml-1.5 opacity-40 hover:opacity-100 transition-opacity">
                              <button onClick={() => copyMessage(forDisplay(m.content))} title="Kopieren" className="p-1.5 rounded-md hover:bg-white/5">
                                <Copy className="h-3.5 w-3.5" />
                              </button>
                              {voice.supported && (
                                <button
                                  onClick={() => { if (voice.status === "speaking") voice.stopSpeaking(); else { voice.prepare(); voice.speak(m.content); } }}
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
                <div className="flex items-center justify-center gap-2 mb-2 text-xs text-foreground/70 animate-fade-in">
                  <span className="relative h-12 w-12 shrink-0">
                    <LogoOrb status={voice.status} getLevel={() => (voice.status === "speaking" ? voice.speechLevel() : 0.25)} />
                  </span>
                  <span>
                    {voice.status === "speaking" ? "🔊 spricht..." : voice.interim || "👂 höre zu... (sprich einfach drauf los)"}
                  </span>
                </div>
              )}

              {queue.length > 0 && (
                <div className="flex flex-wrap items-center gap-1.5 mb-2 text-xs text-muted-foreground">
                  <span>⏳ Warteschlange:</span>
                  {queue.map((q, i) => (
                    <div key={i} className="flex items-center gap-1.5 bg-white/5 border border-white/10 rounded-lg px-2 py-1 text-foreground/90">
                      <span className="max-w-[180px] truncate">{q}</span>
                      <button onClick={() => setQueue(prev => prev.filter((_, j) => j !== i))} aria-label="Aus Warteschlange entfernen">
                        <XIcon className="h-3 w-3 hover:text-destructive" />
                      </button>
                    </div>
                  ))}
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
                const filtered = SLASH_COMMANDS.filter(c => c.cmd.slice(1).startsWith(q) && (!("admin" in c) || isAdmin));
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
                      const filtered = SLASH_COMMANDS.filter(c => c.cmd.slice(1).startsWith(q) && (!("admin" in c) || isAdmin));
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
                    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); submitOrQueue(); }
                  }}
                  onPaste={(e) => {
                    // Bilder/Dateien aus der Zwischenablage (Strg+V) direkt anhängen.
                    if (!e.clipboardData.files.length) return;
                    e.preventDefault();
                    uploadFiles(e.clipboardData.files);
                  }}
                  placeholder={
                    sending ? "Nächste Nachricht? Enter reiht sie in die Warteschlange ein" :
                    voiceMode ? "Tippe oder drücke das Mikro..." :
                    mode === "support" ? "Frage Mythos AI…" :
                    mode === "agent" ? "Was soll der Agent tun?" : "Frag mich alles…"
                  }
                  className="min-h-[56px] max-h-[200px] overflow-hidden resize-none border-0 bg-transparent focus-visible:ring-0 text-[15px] px-4 pt-4 pb-14 shadow-none"
                                    style={{ height: 'auto' }}
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
                  {!input.trim() && (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          type="button"
                          size="icon"
                          variant="ghost"
                          className="h-9 w-9 rounded-full"
                          onClick={() => { if (sub.canUseVoice) nav("/voice"); else setPaywall({ open: true, reason: "Live-Sprachchat ist eine Pro-Funktion." }); }}
                          aria-label="Live-Sprachchat"
                        >
                          <AudioLines className="h-4 w-4" />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent side="top">Live-Sprachchat</TooltipContent>
                    </Tooltip>
                  )}
                  {sending && !input.trim() ? (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          onClick={stop}
                          size="icon"
                          className="h-9 w-9 rounded-full bg-foreground text-background hover:bg-foreground/90"
                          aria-label="Stopp"
                        >
                          <Square className="h-3.5 w-3.5 fill-current" />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent side="top">Stopp</TooltipContent>
                    </Tooltip>
                  ) : (
                  <Button
                    onClick={submitOrQueue}
                    disabled={!input.trim() && !sending}
                    size="icon"
                    className="h-9 w-9 rounded-full bg-foreground text-background hover:bg-foreground/90 disabled:opacity-30"
                    aria-label={sending ? "In die Warteschlange" : "Senden"}
                  >
                    <Send className="h-4 w-4" />
                  </Button>
                  )}
                </div>
              </div>
              <p className="text-[10px] text-muted-foreground text-center mt-2 hidden sm:block">
                Mythos AI kann Fehler machen. Wichtige Infos prüfen. <code className="font-mono">/</code> für Commands.
              </p>
            </div>
          </div>
        </main>

        <ProjectsDialog
          open={dialog === "projects"}
          onOpenChange={(o) => setDialog(o ? "projects" : null)}
          onOpenProject={(id) => { setProjectId(id); newChat(); }}
        />
        <ArtifactsDialog open={dialog === "artifacts"} onOpenChange={(o) => setDialog(o ? "artifacts" : null)} onOpenChat={openChat} />
        <CustomizeDialog open={dialog === "customize"} onOpenChange={(o) => setDialog(o ? "customize" : null)} />
        <Paywall open={paywall.open} onOpenChange={(o) => setPaywall({ open: o })} reason={paywall.reason} />
      </div>
    </TooltipProvider>
  );
};

export default Chat;
