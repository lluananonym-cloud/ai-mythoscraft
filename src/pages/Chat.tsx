import { useState, useEffect, useRef } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ScrollArea } from "@/components/ui/scroll-area";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Plus, Send, Trash2, MessageSquare, Loader2, Sparkles, Brain, HelpCircle, Wrench } from "lucide-react";
import { toast } from "sonner";

type Conv = { id: string; title: string; mode: string; updated_at: string };
type Msg = { id?: string; role: "user" | "assistant" | "tool"; content: string; metadata?: any };

const MODES = [
  { value: "support", label: "Support", icon: HelpCircle, desc: "Mythoscraft Server-Support" },
  { value: "agent", label: "Agent", icon: Brain, desc: "Mit Web-Suche & Tools" },
  { value: "general", label: "General", icon: Sparkles, desc: "Allgemeiner KI-Chat" },
];

const Chat = () => {
  const { user } = useAuth();
  const [convs, setConvs] = useState<Conv[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [mode, setMode] = useState("support");
  const scrollRef = useRef<HTMLDivElement>(null);

  const loadConvs = async () => {
    const { data } = await supabase.from("conversations").select("*").order("updated_at", { ascending: false });
    if (data) setConvs(data as any);
  };

  useEffect(() => { if (user) loadConvs(); }, [user]);

  const loadMessages = async (id: string) => {
    setActiveId(id);
    const { data } = await supabase.from("messages").select("*").eq("conversation_id", id).order("created_at");
    if (data) setMessages(data as any);
    const c = convs.find(c => c.id === id);
    if (c) setMode(c.mode);
  };

  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" }); }, [messages]);

  const newChat = async () => {
    setActiveId(null);
    setMessages([]);
  };

  const deleteChat = async (id: string) => {
    await supabase.from("conversations").delete().eq("id", id);
    if (activeId === id) { setActiveId(null); setMessages([]); }
    loadConvs();
  };

  const send = async () => {
    if (!input.trim() || sending) return;
    const text = input.trim();
    setInput("");
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
            // Agent tool events
            if (p.tool) {
              setMessages(prev => {
                const next = [...prev];
                const last = next[next.length - 1];
                last.content = (last.content || "") + `\n\n> 🔧 *${p.tool}*\n\n`;
                return next;
              });
              continue;
            }
            const c = p.choices?.[0]?.delta?.content;
            if (c) {
              full += c;
              setMessages(prev => {
                const next = [...prev];
                next[next.length - 1] = { role: "assistant", content: full };
                return next;
              });
            }
          } catch { buf = line + "\n" + buf; break; }
        }
      }

      if (full) await supabase.from("messages").insert({ conversation_id: convId, role: "assistant", content: full });
      await supabase.from("conversations").update({ updated_at: new Date().toISOString() }).eq("id", convId);
    } catch (e) {
      console.error(e);
      toast.error("Verbindungsfehler");
      setMessages(prev => prev.slice(0, -1));
    } finally {
      setSending(false);
    }
  };

  const ModeIcon = MODES.find(m => m.value === mode)?.icon || HelpCircle;

  return (
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <div className="flex-1 container py-4 grid grid-cols-1 md:grid-cols-[260px_1fr] gap-4 overflow-hidden">
        {/* Sidebar */}
        <aside className="glass rounded-2xl p-3 flex flex-col gap-2 h-[calc(100vh-6rem)]">
          <Button onClick={newChat} className="bg-gradient-primary text-primary-foreground hover:opacity-90 w-full">
            <Plus className="h-4 w-4 mr-1.5" /> Neuer Chat
          </Button>
          <ScrollArea className="flex-1 -mx-1 px-1">
            <div className="space-y-1">
              {convs.map(c => (
                <div key={c.id} className={`group flex items-center gap-1 rounded-lg px-2 py-2 text-sm cursor-pointer transition-colors ${activeId === c.id ? "bg-primary/15 text-foreground" : "hover:bg-secondary/50 text-muted-foreground"}`}
                  onClick={() => loadMessages(c.id)}>
                  <MessageSquare className="h-3.5 w-3.5 shrink-0" />
                  <span className="truncate flex-1">{c.title}</span>
                  <button onClick={(e) => { e.stopPropagation(); deleteChat(c.id); }} className="opacity-0 group-hover:opacity-100 transition-opacity">
                    <Trash2 className="h-3.5 w-3.5 hover:text-destructive" />
                  </button>
                </div>
              ))}
              {convs.length === 0 && <p className="text-xs text-muted-foreground p-3 text-center">Noch keine Chats</p>}
            </div>
          </ScrollArea>
        </aside>

        {/* Chat area */}
        <main className="glass-strong rounded-2xl flex flex-col h-[calc(100vh-6rem)] overflow-hidden">
          <div className="border-b border-border/50 p-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <ModeIcon className="h-4 w-4 text-primary" />
              <span className="font-medium text-sm">Mythos AI</span>
            </div>
            <Select value={mode} onValueChange={setMode}>
              <SelectTrigger className="w-[180px] bg-secondary/50 h-9 text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                {MODES.map(m => (
                  <SelectItem key={m.value} value={m.value}>
                    <div className="flex items-center gap-2"><m.icon className="h-3.5 w-3.5" /><span>{m.label}</span><span className="text-xs text-muted-foreground">— {m.desc}</span></div>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div ref={scrollRef} className="flex-1 overflow-y-auto p-6 space-y-6">
            {messages.length === 0 && (
              <div className="h-full flex flex-col items-center justify-center text-center max-w-md mx-auto">
                <div className="h-16 w-16 rounded-2xl bg-gradient-primary flex items-center justify-center glow-primary mb-5 animate-pulse-glow">
                  <Sparkles className="h-8 w-8 text-primary-foreground" />
                </div>
                <h2 className="font-display text-2xl font-bold mb-2">Willkommen bei Mythos AI</h2>
                <p className="text-muted-foreground text-sm mb-6">Frag mich alles über mythoscraft.online — Regeln, Commands, Plugins, Server-Status. Im Agent-Modus kann ich auch im Web suchen.</p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 w-full">
                  {["Wie verbinde ich mich mit dem Server?", "Welche Commands gibt es?", "Ist der Server gerade online?", "Was sind die Regeln?"].map(s => (
                    <button key={s} onClick={() => setInput(s)} className="glass rounded-xl p-3 text-xs text-left hover:border-primary/40 transition-colors">{s}</button>
                  ))}
                </div>
              </div>
            )}
            {messages.map((m, i) => (
              <div key={i} className={`flex gap-3 animate-fade-in ${m.role === "user" ? "justify-end" : ""}`}>
                {m.role !== "user" && (
                  <div className="h-8 w-8 rounded-lg bg-gradient-primary flex items-center justify-center shrink-0 mt-0.5">
                    <Sparkles className="h-4 w-4 text-primary-foreground" />
                  </div>
                )}
                <div className={`max-w-[80%] rounded-2xl px-4 py-3 ${m.role === "user" ? "bg-primary/20 border border-primary/30" : "glass"}`}>
                  {m.role === "assistant" ? (
                    <div className="prose-mythos text-sm">
                      {m.content ? <ReactMarkdown remarkPlugins={[remarkGfm]}>{m.content}</ReactMarkdown> : <Loader2 className="h-4 w-4 animate-spin text-primary" />}
                    </div>
                  ) : (
                    <p className="text-sm whitespace-pre-wrap">{m.content}</p>
                  )}
                </div>
              </div>
            ))}
          </div>

          <div className="border-t border-border/50 p-3">
            <div className="glass rounded-xl flex items-end gap-2 p-2">
              <Textarea
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); } }}
                placeholder={mode === "support" ? "Frage zum Mythoscraft-Server..." : mode === "agent" ? "Was soll der Agent tun?" : "Was möchtest du wissen?"}
                className="flex-1 min-h-[44px] max-h-40 resize-none border-0 bg-transparent focus-visible:ring-0 text-sm"
                disabled={sending}
              />
              <Button onClick={send} disabled={!input.trim() || sending} size="icon" className="bg-gradient-primary text-primary-foreground hover:opacity-90 h-10 w-10 shrink-0">
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
