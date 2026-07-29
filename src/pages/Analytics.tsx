import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import { Card } from "@/components/ui/card";
import { BarChart3, MessageSquare, Brain, Bot, Drama } from "lucide-react";

const Analytics = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState({ convs: 0, messages: 0, memories: 0, personas: 0, agents: 0 });
  const [topTopics, setTopTopics] = useState<{ category: string; count: number }[]>([]);
  const [recent, setRecent] = useState<{ title: string; updated_at: string }[]>([]);

  useEffect(() => {
    if (!user) return;
    (async () => {
      const [c, msgs, mems, perss, ags, recentConvs] = await Promise.all([
        supabase.from("conversations").select("id", { count: "exact", head: true }).eq("user_id", user.id),
        supabase.from("messages").select("id", { count: "exact", head: true }),
        supabase.from("user_memories").select("category").eq("user_id", user.id),
        supabase.from("ai_personas").select("id", { count: "exact", head: true }).eq("user_id", user.id),
        supabase.from("agent_tasks").select("id", { count: "exact", head: true }).eq("user_id", user.id),
        supabase.from("conversations").select("title,updated_at").eq("user_id", user.id).order("updated_at", { ascending: false }).limit(5),
      ]);
      setStats({
        convs: c.count || 0, messages: msgs.count || 0,
        memories: mems.data?.length || 0,
        personas: perss.count || 0, agents: ags.count || 0,
      });
      const counts: Record<string, number> = {};
      mems.data?.forEach((m: any) => { counts[m.category] = (counts[m.category] || 0) + 1; });
      setTopTopics(Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 6).map(([category, count]) => ({ category, count })));
      setRecent(recentConvs.data || []);
    })();
  }, [user]);

  const cards = [
    { icon: MessageSquare, label: "Chats", value: stats.convs, color: "from-cyan-500 to-blue-500" },
    { icon: BarChart3, label: "Nachrichten", value: stats.messages, color: "from-purple-500 to-pink-500" },
    { icon: Brain, label: "Memories", value: stats.memories, color: "from-emerald-500 to-teal-500" },
    { icon: Drama, label: "Personas", value: stats.personas, color: "from-amber-500 to-orange-500" },
    { icon: Bot, label: "Auto-Agents", value: stats.agents, color: "from-rose-500 to-red-500" },
  ];

  return (
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <main className="container py-6 max-w-5xl">
        <h1 className="font-display text-2xl font-bold mb-1">Analytics</h1>
        <p className="text-muted-foreground text-sm mb-6">Dein persönliches Dashboard.</p>

        <div className="grid grid-cols-2 md:grid-cols-5 gap-3 mb-6">
          {cards.map(c => (
            <Card key={c.label} className="glass-strong p-4">
              <div className={`h-9 w-9 rounded-lg bg-gradient-to-br ${c.color} flex items-center justify-center mb-2`}>
                <c.icon className="h-5 w-5 text-white" />
              </div>
              <div className="text-2xl font-bold font-display">{c.value}</div>
              <div className="text-xs text-muted-foreground">{c.label}</div>
            </Card>
          ))}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Card className="glass-strong p-4">
            <h2 className="font-semibold text-sm mb-3">🔥 Top-Themen aus Memories</h2>
            <div className="space-y-2">
              {topTopics.map(t => (
                <div key={t.category}>
                  <div className="flex justify-between text-xs mb-1"><span className="capitalize">{t.category}</span><span className="text-muted-foreground">{t.count}</span></div>
                  <div className="h-1.5 bg-secondary rounded-full overflow-hidden">
                    <div className="h-full bg-gradient-primary" style={{ width: `${Math.min(100, (t.count / Math.max(...topTopics.map(x => x.count), 1)) * 100)}%` }} />
                  </div>
                </div>
              ))}
              {topTopics.length === 0 && <p className="text-xs text-muted-foreground">Noch keine Memories gesammelt.</p>}
            </div>
          </Card>
          <Card className="glass-strong p-4">
            <h2 className="font-semibold text-sm mb-3">🕒 Letzte Chats</h2>
            <div className="space-y-2">
              {recent.map((r, i) => (
                <div key={i} className="flex justify-between text-xs gap-2">
                  <span className="truncate">{r.title}</span>
                  <span className="text-muted-foreground shrink-0">{new Date(r.updated_at).toLocaleDateString("de-DE")}</span>
                </div>
              ))}
              {recent.length === 0 && <p className="text-xs text-muted-foreground">Noch keine Chats.</p>}
            </div>
          </Card>
        </div>
      </main>
    </div>
  );
};
export default Analytics;
