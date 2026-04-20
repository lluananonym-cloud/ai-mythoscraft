import { useEffect, useRef, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogFooter } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Phone, Send, Trash2, Bell, BellOff, Bot, Headphones, Zap, Plus } from "lucide-react";
import { toast } from "sonner";
import { useDesktopNotifications } from "@/hooks/useDesktopNotifications";

type Chat = {
  id: string; phone_number: string; display_name: string | null;
  mode: "support" | "ai" | "auto"; last_message_at: string;
  unread_count: number; ai_identity: string | null;
  last_support_response_at: string | null;
};
type PMsg = {
  id: string; chat_id: string; direction: "inbound" | "outbound";
  channel: string; sender: "user" | "support" | "ai" | "system";
  content: string; created_at: string;
};

const PhoneInbox = () => {
  const [chats, setChats] = useState<Chat[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [msgs, setMsgs] = useState<PMsg[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [simOpen, setSimOpen] = useState(false);
  const [simPhone, setSimPhone] = useState("");
  const [simContent, setSimContent] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);
  const notif = useDesktopNotifications();
  const activeIdRef = useRef<string | null>(null);
  useEffect(() => { activeIdRef.current = activeId; }, [activeId]);

  const loadChats = async () => {
    const { data } = await supabase.from("phone_chats").select("*").order("last_message_at", { ascending: false });
    if (data) setChats(data as any);
  };

  const loadMsgs = async (chatId: string) => {
    const { data } = await supabase.from("phone_messages").select("*").eq("chat_id", chatId).order("created_at");
    if (data) setMsgs(data as any);
    // Reset unread
    await supabase.from("phone_chats").update({ unread_count: 0 }).eq("id", chatId);
    loadChats();
  };

  useEffect(() => { loadChats(); }, []);

  // Realtime: phone_chats + phone_messages
  useEffect(() => {
    const ch = supabase.channel("phone-inbox")
      .on("postgres_changes", { event: "*", schema: "public", table: "phone_chats" }, (payload) => {
        loadChats();
        if (payload.eventType === "INSERT") {
          const c: any = payload.new;
          notif.notify(`Neuer Chat von ${c.display_name || c.phone_number}`, { body: "Inbound auf MythosAI Inbox", onClick: () => { setActiveId(c.id); loadMsgs(c.id); } });
        }
      })
      .on("postgres_changes", { event: "INSERT", schema: "public", table: "phone_messages" }, (payload) => {
        const m: any = payload.new;
        if (m.chat_id === activeIdRef.current) {
          setMsgs(prev => [...prev, m]);
        } else if (m.direction === "inbound") {
          // notify
          supabase.from("phone_chats").select("phone_number,display_name").eq("id", m.chat_id).maybeSingle().then(({ data }) => {
            notif.notify(`Neue Nachricht von ${data?.display_name || data?.phone_number || "Unbekannt"}`, { body: m.content.slice(0, 100), onClick: () => { setActiveId(m.chat_id); loadMsgs(m.chat_id); } });
          });
        } else if (m.sender === "ai") {
          notif.notify("KI hat geantwortet", { body: m.content.slice(0, 100) });
        }
      })
      .subscribe();
    return () => { supabase.removeChannel(ch); };
  }, [notif]);

  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" }); }, [msgs]);

  const setMode = async (chatId: string, mode: Chat["mode"]) => {
    await supabase.from("phone_chats").update({ mode }).eq("id", chatId);
    toast.success(`Modus: ${mode === "ai" ? "Nur KI" : mode === "support" ? "Nur Support" : "Auto (10 Min)"}`);
    notif.notify("Modus geändert", { body: `Chat läuft jetzt im ${mode}-Modus.` });
    loadChats();
  };

  const setIdentity = async (chatId: string, name: string) => {
    await supabase.from("phone_chats").update({ ai_identity: name || null }).eq("id", chatId);
    toast.success(name ? `KI-Identität: ${name}` : "Identität zurückgesetzt");
    loadChats();
  };

  const sendSupport = async () => {
    if (!input.trim() || !activeId || sending) return;
    setSending(true);
    const text = input.trim();
    setInput("");
    await supabase.from("phone_messages").insert({
      chat_id: activeId, direction: "outbound", channel: "whatsapp", sender: "support", content: text,
    });
    await supabase.from("phone_chats").update({
      last_message_at: new Date().toISOString(),
      last_support_response_at: new Date().toISOString(),
    }).eq("id", activeId);
    // TODO: forward via Twilio when configured
    setSending(false);
    loadChats();
  };

  const deleteChat = async (id: string) => {
    if (!confirm("Chat wirklich löschen?")) return;
    await supabase.from("phone_chats").delete().eq("id", id);
    if (activeId === id) { setActiveId(null); setMsgs([]); }
    loadChats();
    toast.success("Chat gelöscht");
  };

  const simulateInbound = async () => {
    if (!simPhone.trim() || !simContent.trim()) return;
    const url = `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/whatsapp-webhook`;
    const r = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY}` },
      body: JSON.stringify({ phone_number: simPhone, content: simContent, channel: "whatsapp" }),
    });
    if (r.ok) {
      toast.success("Inbound simuliert");
      setSimOpen(false); setSimContent("");
    } else {
      toast.error("Simulation fehlgeschlagen");
    }
  };

  const active = chats.find(c => c.id === activeId);

  return (
    <div className="grid grid-cols-1 md:grid-cols-[300px_1fr] gap-3 h-[calc(100vh-14rem)] min-h-[500px]">
      {/* Sidebar */}
      <div className="glass-strong rounded-2xl p-3 flex flex-col gap-2 overflow-hidden">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 text-sm font-medium"><Phone className="h-4 w-4" /> Inbox</div>
          <div className="flex gap-1">
            {!notif.supported ? null : notif.permission === "granted" ? (
              <Bell className="h-4 w-4 text-green-500" />
            ) : (
              <Button size="icon" variant="ghost" className="h-7 w-7" onClick={notif.request} title="Benachrichtigungen aktivieren">
                <BellOff className="h-4 w-4" />
              </Button>
            )}
            <Dialog open={simOpen} onOpenChange={setSimOpen}>
              <DialogTrigger asChild>
                <Button size="icon" variant="ghost" className="h-7 w-7" title="Inbound simulieren"><Plus className="h-4 w-4" /></Button>
              </DialogTrigger>
              <DialogContent className="glass-strong">
                <DialogHeader><DialogTitle>Eingehende Nachricht simulieren</DialogTitle></DialogHeader>
                <div className="space-y-3">
                  <div><Label>Telefonnummer (E.164)</Label><Input value={simPhone} onChange={e => setSimPhone(e.target.value)} placeholder="+4915123456789" className="mt-1.5" /></div>
                  <div><Label>Nachricht</Label><Textarea value={simContent} onChange={e => setSimContent(e.target.value)} className="mt-1.5" /></div>
                  <p className="text-xs text-muted-foreground">Bis Twilio verbunden ist, kannst du hier eine eingehende WhatsApp simulieren. Auto-Modus: KI antwortet nach 10 Min Stille.</p>
                </div>
                <DialogFooter><Button onClick={simulateInbound}>Senden</Button></DialogFooter>
              </DialogContent>
            </Dialog>
          </div>
        </div>
        <ScrollArea className="flex-1 -mx-1 px-1">
          <div className="space-y-1">
            {chats.map(c => (
              <div
                key={c.id}
                onClick={() => { setActiveId(c.id); loadMsgs(c.id); }}
                className={`group flex items-center gap-2 rounded-lg px-2 py-2 text-sm cursor-pointer transition-colors ${
                  activeId === c.id ? "bg-primary/15" : "hover:bg-secondary/50"
                }`}
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5">
                    <span className="truncate text-xs font-medium">{c.display_name || c.phone_number}</span>
                    {c.unread_count > 0 && (
                      <span className="text-[10px] bg-primary text-primary-foreground rounded-full px-1.5 py-0.5">{c.unread_count}</span>
                    )}
                  </div>
                  <div className="text-[10px] text-muted-foreground flex items-center gap-1">
                    {c.mode === "ai" ? <Bot className="h-3 w-3" /> : c.mode === "support" ? <Headphones className="h-3 w-3" /> : <Zap className="h-3 w-3" />}
                    {c.mode}
                  </div>
                </div>
                <button onClick={(e) => { e.stopPropagation(); deleteChat(c.id); }} className="opacity-0 group-hover:opacity-100 transition-opacity p-1">
                  <Trash2 className="h-3.5 w-3.5 hover:text-destructive" />
                </button>
              </div>
            ))}
            {chats.length === 0 && <p className="text-xs text-muted-foreground p-3 text-center">Noch keine Telefon-Chats</p>}
          </div>
        </ScrollArea>
      </div>

      {/* Chat */}
      <div className="glass-strong rounded-2xl flex flex-col overflow-hidden">
        {!active ? (
          <div className="flex-1 flex items-center justify-center text-sm text-muted-foreground">
            Chat auswählen oder eingehende Nachricht simulieren
          </div>
        ) : (
          <>
            <div className="border-b border-border/50 p-3 flex items-center justify-between gap-2 flex-wrap">
              <div className="min-w-0">
                <div className="font-medium text-sm truncate">{active.display_name || active.phone_number}</div>
                <div className="text-xs text-muted-foreground">{active.phone_number}</div>
              </div>
              <div className="flex items-center gap-2 flex-wrap">
                <Input
                  placeholder="KI-Identität (optional)"
                  defaultValue={active.ai_identity || ""}
                  onBlur={(e) => { if (e.target.value !== (active.ai_identity || "")) setIdentity(active.id, e.target.value); }}
                  className="h-8 w-32 text-xs"
                />
                <Select value={active.mode} onValueChange={(v: any) => setMode(active.id, v)}>
                  <SelectTrigger className="h-8 w-32 text-xs"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="auto"><Zap className="h-3 w-3 inline mr-1" />Auto (10 Min)</SelectItem>
                    <SelectItem value="support"><Headphones className="h-3 w-3 inline mr-1" />Nur Support</SelectItem>
                    <SelectItem value="ai"><Bot className="h-3 w-3 inline mr-1" />Nur KI</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div ref={scrollRef} className="flex-1 overflow-y-auto p-3 space-y-2">
              {msgs.map(m => (
                <div key={m.id} className={`flex ${m.direction === "outbound" ? "justify-end" : "justify-start"}`}>
                  <div className={`max-w-[80%] rounded-2xl px-3 py-2 text-sm ${
                    m.sender === "user" ? "glass" :
                    m.sender === "ai" ? "bg-primary/15 border border-primary/30" :
                    m.sender === "support" ? "bg-accent/30 border border-accent/50" : "bg-muted"
                  }`}>
                    <div className="text-[10px] uppercase opacity-60 mb-0.5 flex items-center gap-1">
                      {m.sender === "ai" && <Bot className="h-3 w-3" />}
                      {m.sender === "support" && <Headphones className="h-3 w-3" />}
                      {m.sender}
                    </div>
                    <p className="whitespace-pre-wrap break-words">{m.content}</p>
                  </div>
                </div>
              ))}
              {msgs.length === 0 && <p className="text-center text-xs text-muted-foreground py-8">Keine Nachrichten</p>}
            </div>

            <div className="border-t border-border/50 p-2">
              <div className="glass-liquid rounded-2xl flex items-end gap-2 p-2">
                <Textarea
                  value={input}
                  onChange={e => setInput(e.target.value)}
                  onKeyDown={e => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendSupport(); } }}
                  placeholder="Antwort als Support... (wird an WhatsApp weitergeleitet, sobald Twilio verbunden ist)"
                  className="flex-1 min-h-[40px] max-h-32 resize-none border-0 bg-transparent focus-visible:ring-0 text-sm"
                  disabled={sending}
                />
                <Button onClick={sendSupport} disabled={!input.trim() || sending} size="icon" className="h-9 w-9 shrink-0">
                  <Send className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default PhoneInbox;
