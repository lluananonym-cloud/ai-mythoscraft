import { useEffect, useRef, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Users, Plus, Send, Sparkles, Copy, LogOut as LogOutIcon } from "lucide-react";
import { toast } from "sonner";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

type Group = { id: string; name: string; description: string | null; ai_enabled: boolean; invite_code: string; owner_id: string };
type GMsg = { id: string; sender_name: string; role: string; content: string; created_at: string; sender_id: string | null };

const Groups = () => {
  const { user, profile } = useAuth();
  const [groups, setGroups] = useState<Group[]>([]);
  const [active, setActive] = useState<Group | null>(null);
  const [msgs, setMsgs] = useState<GMsg[]>([]);
  const [text, setText] = useState("");
  const [newName, setNewName] = useState("");
  const [joinCode, setJoinCode] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  const loadGroups = async () => {
    const { data } = await supabase.from("friend_groups").select("*").order("updated_at", { ascending: false });
    if (data) setGroups(data as Group[]);
  };
  useEffect(() => { if (user) loadGroups(); }, [user]);

  useEffect(() => {
    if (!active) return;
    supabase.from("group_messages").select("*").eq("group_id", active.id).order("created_at").then(({ data }) => {
      if (data) setMsgs(data as GMsg[]);
    });
    const ch = supabase.channel(`group-${active.id}`)
      .on("postgres_changes", { event: "INSERT", schema: "public", table: "group_messages", filter: `group_id=eq.${active.id}` },
        (payload) => setMsgs(prev => [...prev, payload.new as GMsg]))
      .subscribe();
    return () => { supabase.removeChannel(ch); };
  }, [active]);

  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" }); }, [msgs]);

  const createGroup = async () => {
    if (!newName.trim()) return;
    const { data, error } = await supabase.from("friend_groups").insert({ owner_id: user!.id, name: newName }).select().single();
    if (error) return toast.error(error.message);
    await supabase.from("group_members").insert({ group_id: data.id, user_id: user!.id, role: "owner" });
    setNewName(""); loadGroups(); setActive(data as Group);
    toast.success("Gruppe erstellt!");
  };

  const joinGroup = async () => {
    if (!joinCode.trim()) return;
    const { data: g, error } = await supabase.from("friend_groups").select("*").eq("invite_code", joinCode.trim().toLowerCase()).maybeSingle();
    if (error || !g) return toast.error("Code nicht gefunden");
    const { error: e2 } = await supabase.from("group_members").insert({ group_id: g.id, user_id: user!.id });
    if (e2 && !e2.message.includes("duplicate")) return toast.error(e2.message);
    setJoinCode(""); loadGroups(); setActive(g as Group);
    toast.success(`Beigetreten: ${g.name}`);
  };

  const send = async () => {
    if (!text.trim() || !active) return;
    const senderName = profile?.display_name || profile?.mc_username || user!.email!.split("@")[0];
    const content = text;
    setText("");
    await supabase.from("group_messages").insert({
      group_id: active.id, sender_id: user!.id, sender_name: senderName, role: "user", content,
    });
    // trigger AI if mentioned with @ai or AI is enabled and message ends with ?
    if (active.ai_enabled && (/\b@ai\b/i.test(content) || /\?\s*$/.test(content))) {
      supabase.functions.invoke("group-chat", { body: { groupId: active.id, userId: user!.id } }).catch(() => {});
    }
  };

  const toggleAI = async (val: boolean) => {
    if (!active) return;
    await supabase.from("friend_groups").update({ ai_enabled: val }).eq("id", active.id);
    setActive({ ...active, ai_enabled: val });
  };

  return (
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <main className="container py-4 grid grid-cols-1 md:grid-cols-[280px_1fr] gap-4 h-[calc(100vh-5rem)]">
        <aside className="glass rounded-2xl p-3 flex flex-col gap-3 overflow-hidden">
          <div className="flex items-center gap-2"><Users className="h-4 w-4" /><h2 className="font-semibold text-sm">Gruppen</h2></div>
          <div className="flex gap-1">
            <Input placeholder="Neue Gruppe" value={newName} onChange={e => setNewName(e.target.value)} className="h-8 text-xs" />
            <Button size="icon" className="h-8 w-8" onClick={createGroup}><Plus className="h-3.5 w-3.5" /></Button>
          </div>
          <div className="flex gap-1">
            <Input placeholder="Invite-Code" value={joinCode} onChange={e => setJoinCode(e.target.value)} className="h-8 text-xs" />
            <Button size="icon" variant="outline" className="h-8 w-8" onClick={joinGroup}><LogOutIcon className="h-3.5 w-3.5 rotate-180" /></Button>
          </div>
          <div className="flex-1 overflow-y-auto space-y-1 -mx-1 px-1">
            {groups.map(g => (
              <button key={g.id} onClick={() => setActive(g)}
                className={`w-full text-left rounded-lg px-2 py-2 text-sm transition-colors ${active?.id === g.id ? "bg-primary/15" : "hover:bg-secondary/50"}`}>
                <div className="font-medium truncate">{g.name}</div>
                <div className="text-[10px] text-muted-foreground">Code: {g.invite_code}</div>
              </button>
            ))}
            {groups.length === 0 && <p className="text-xs text-muted-foreground p-2">Noch keine Gruppen</p>}
          </div>
        </aside>

        <Card className="glass-strong rounded-2xl flex flex-col overflow-hidden">
          {!active ? (
            <div className="flex-1 flex items-center justify-center text-muted-foreground text-sm">Wähle eine Gruppe.</div>
          ) : (
            <>
              <div className="border-b border-white/5 p-3 flex items-center justify-between gap-2">
                <div>
                  <h3 className="font-display text-base">{active.name}</h3>
                  <button className="text-[10px] text-muted-foreground hover:text-foreground"
                    onClick={() => { navigator.clipboard.writeText(active.invite_code); toast.success("Code kopiert"); }}>
                    <Copy className="h-2.5 w-2.5 inline mr-1" />Invite-Code: {active.invite_code}
                  </button>
                </div>
                <label className="flex items-center gap-2 text-xs">
                  <Sparkles className="h-3.5 w-3.5" /> AI
                  <Switch checked={active.ai_enabled} onCheckedChange={toggleAI} />
                </label>
              </div>
              <div ref={scrollRef} className="flex-1 overflow-y-auto p-4 space-y-3">
                {msgs.map(m => (
                  <div key={m.id} className={`flex flex-col ${m.sender_id === user?.id ? "items-end" : "items-start"}`}>
                    <span className="text-[10px] text-muted-foreground mb-0.5">
                      {m.role === "assistant" ? "✨ Mythos AI" : m.sender_name}
                    </span>
                    <div className={`max-w-[80%] rounded-2xl px-3 py-2 text-sm ${
                      m.role === "assistant" ? "glass" :
                      m.sender_id === user?.id ? "bg-primary/20 border border-primary/30" : "bg-secondary/40"
                    }`}>
                      {m.role === "assistant" ? (
                        <div className="prose-mythos break-words"><ReactMarkdown remarkPlugins={[remarkGfm]}>{m.content}</ReactMarkdown></div>
                      ) : <p className="whitespace-pre-wrap break-words">{m.content}</p>}
                    </div>
                  </div>
                ))}
                {msgs.length === 0 && <p className="text-center text-muted-foreground text-sm">Noch keine Nachrichten. Tipp: schreib „@ai ..." um die AI mit reinzuholen.</p>}
              </div>
              <div className="border-t border-white/5 p-2 flex gap-2">
                <Input value={text} onChange={e => setText(e.target.value)}
                  onKeyDown={e => { if (e.key === "Enter") send(); }}
                  placeholder="Nachricht... (@ai um die AI zu rufen)" className="bg-transparent border-white/10" />
                <Button onClick={send} size="icon" className="bg-foreground text-background hover:bg-foreground/90">
                  <Send className="h-4 w-4" />
                </Button>
              </div>
            </>
          )}
        </Card>
      </main>
    </div>
  );
};
export default Groups;
