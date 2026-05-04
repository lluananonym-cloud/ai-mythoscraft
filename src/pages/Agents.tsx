import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Bot, Plus, Trash2, Clock, RefreshCw, CheckCircle2, XCircle } from "lucide-react";
import { toast } from "sonner";

type Task = {
  id: string; title: string; prompt: string; mode: string;
  schedule_at: string; recurrence: string; status: string;
  last_run_at: string | null; last_result: string | null;
};

const Agents = () => {
  const { user } = useAuth();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [title, setTitle] = useState("");
  const [prompt, setPrompt] = useState("");
  const [when, setWhen] = useState("");
  const [recurrence, setRecurrence] = useState("once");
  const [creating, setCreating] = useState(false);

  const load = async () => {
    const { data } = await supabase.from("agent_tasks").select("*").order("schedule_at", { ascending: true });
    if (data) setTasks(data as Task[]);
  };
  useEffect(() => { if (user) load(); }, [user]);

  const create = async () => {
    if (!title.trim() || !prompt.trim() || !when) return toast.error("Bitte alle Felder ausfüllen");
    setCreating(true);
    const { error } = await supabase.from("agent_tasks").insert({
      user_id: user!.id, title, prompt, schedule_at: new Date(when).toISOString(), recurrence,
    });
    setCreating(false);
    if (error) return toast.error(error.message);
    toast.success("🤖 Auto-Agent geplant!");
    setTitle(""); setPrompt(""); setWhen(""); setRecurrence("once");
    load();
  };

  const remove = async (id: string) => {
    await supabase.from("agent_tasks").delete().eq("id", id);
    load();
  };

  return (
    <div className="min-h-screen flex flex-col">
      <TopNav />
      <main className="container py-6 max-w-4xl">
        <div className="flex items-center gap-2 mb-2">
          <Bot className="h-6 w-6 text-primary" />
          <h1 className="font-display text-2xl font-bold">Auto-Agents</h1>
        </div>
        <p className="text-muted-foreground text-sm mb-6">
          Plane Aufgaben, die deine AI im Hintergrund ausführt — z.B. „Finde mir morgen die besten Minecraft Plugins". Ergebnisse landen als neuer Chat in deinem Verlauf.
        </p>

        <Card className="glass-strong p-4 mb-6 space-y-3">
          <h2 className="font-semibold text-sm">Neuer Agent</h2>
          <Input placeholder="Titel (z.B. Daily Plugin News)" value={title} onChange={e => setTitle(e.target.value)} />
          <Textarea placeholder="Was soll die AI tun? (kann /research, /image etc. enthalten)" value={prompt} onChange={e => setPrompt(e.target.value)} rows={3} />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-muted-foreground">Wann?</label>
              <Input type="datetime-local" value={when} onChange={e => setWhen(e.target.value)} />
            </div>
            <div>
              <label className="text-xs text-muted-foreground">Wiederholung</label>
              <Select value={recurrence} onValueChange={setRecurrence}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="once">Einmal</SelectItem>
                  <SelectItem value="daily">Täglich</SelectItem>
                  <SelectItem value="weekly">Wöchentlich</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <Button onClick={create} disabled={creating} className="bg-gradient-primary text-primary-foreground">
            <Plus className="h-4 w-4 mr-1" /> Agent planen
          </Button>
        </Card>

        <div className="space-y-2">
          {tasks.map(t => (
            <Card key={t.id} className="glass p-3 flex items-start gap-3">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-medium">{t.title}</span>
                  <Badge variant={t.status === "done" ? "default" : t.status === "failed" ? "destructive" : "secondary"} className="text-[10px]">
                    {t.status === "pending" && <Clock className="h-3 w-3 mr-1" />}
                    {t.status === "done" && <CheckCircle2 className="h-3 w-3 mr-1" />}
                    {t.status === "failed" && <XCircle className="h-3 w-3 mr-1" />}
                    {t.status}
                  </Badge>
                  {t.recurrence !== "once" && <Badge variant="outline" className="text-[10px]"><RefreshCw className="h-2.5 w-2.5 mr-1" />{t.recurrence}</Badge>}
                </div>
                <p className="text-xs text-muted-foreground mt-1 line-clamp-2">{t.prompt}</p>
                <p className="text-[10px] text-muted-foreground mt-1">📅 {new Date(t.schedule_at).toLocaleString("de-DE")}</p>
              </div>
              <Button size="icon" variant="ghost" onClick={() => remove(t.id)}>
                <Trash2 className="h-4 w-4 text-destructive" />
              </Button>
            </Card>
          ))}
          {tasks.length === 0 && <p className="text-center text-muted-foreground text-sm py-8">Noch keine Auto-Agents geplant.</p>}
        </div>
      </main>
    </div>
  );
};
export default Agents;
