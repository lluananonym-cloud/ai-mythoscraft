import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";
import TopNav from "@/components/TopNav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogFooter } from "@/components/ui/dialog";
import { Plus, Edit, Trash2, Shield, Users, Phone, Gift } from "lucide-react";
import { toast } from "sonner";
import PhoneInbox from "@/components/PhoneInbox";
import RewardsAdmin from "@/components/RewardsAdmin";

type Article = { id: string; title: string; category: string; body: string; is_published: boolean; created_at: string };
type UserRow = { user_id: string; email: string; display_name: string | null; role?: string };

const Admin = () => {
  const [articles, setArticles] = useState<Article[]>([]);
  const [users, setUsers] = useState<UserRow[]>([]);
  const [editing, setEditing] = useState<Article | null>(null);
  const [open, setOpen] = useState(false);

  const loadArticles = async () => {
    const { data } = await supabase.from("knowledge_articles").select("*").order("category").order("title");
    if (data) setArticles(data as any);
  };
  const loadUsers = async () => {
    const { data: profs } = await supabase.from("profiles").select("user_id,email,display_name");
    const { data: roles } = await supabase.from("user_roles").select("user_id,role");
    if (profs) {
      const merged = profs.map((p: any) => ({ ...p, role: roles?.find((r: any) => r.user_id === p.user_id)?.role || "user" }));
      setUsers(merged);
    }
  };
  useEffect(() => { loadArticles(); loadUsers(); }, []);

  const save = async (a: Partial<Article>) => {
    if (a.id) {
      await supabase.from("knowledge_articles").update({ title: a.title, category: a.category, body: a.body, is_published: a.is_published }).eq("id", a.id);
    } else {
      await supabase.from("knowledge_articles").insert({ title: a.title!, category: a.category!, body: a.body!, is_published: a.is_published ?? true });
    }
    setOpen(false); setEditing(null); loadArticles();
    toast.success("Gespeichert");
  };
  const del = async (id: string) => { if (!confirm("Löschen?")) return; await supabase.from("knowledge_articles").delete().eq("id", id); loadArticles(); };

  const toggleAdmin = async (uid: string, isAdmin: boolean) => {
    if (isAdmin) await supabase.from("user_roles").delete().eq("user_id", uid).eq("role", "admin");
    else await supabase.from("user_roles").insert({ user_id: uid, role: "admin" });
    loadUsers();
    toast.success("Rolle aktualisiert");
  };

  return (
    <div className="min-h-screen">
      <TopNav />
      <main className="container py-6 md:py-8 max-w-6xl">
        <div className="flex items-center gap-3 mb-6">
          <Shield className="h-6 w-6 md:h-7 md:w-7 text-primary" />
          <h1 className="font-display text-2xl md:text-3xl font-bold">Admin Panel</h1>
        </div>

        <Tabs defaultValue="inbox">
          <TabsList className="bg-secondary/50 flex-wrap h-auto">
            <TabsTrigger value="inbox"><Phone className="h-3.5 w-3.5 mr-1.5" />Phone Inbox</TabsTrigger>
            <TabsTrigger value="kb">Knowledge Base</TabsTrigger>
            <TabsTrigger value="users">Users / Roles</TabsTrigger>
            <TabsTrigger value="rewards"><Gift className="h-3.5 w-3.5 mr-1.5" />Abos & Boost-Codes</TabsTrigger>
          </TabsList>

          <TabsContent value="inbox" className="mt-6">
            <PhoneInbox />
          </TabsContent>

          <TabsContent value="kb" className="mt-6">
            <div className="flex justify-between items-center mb-4">
              <p className="text-sm text-muted-foreground">{articles.length} Artikel · Werden automatisch in den Support-Mode injiziert</p>
              <Dialog open={open} onOpenChange={(o) => { setOpen(o); if (!o) setEditing(null); }}>
                <DialogTrigger asChild>
                  <Button onClick={() => setEditing({ id: "", title: "", category: "general", body: "", is_published: true } as Article)} className="bg-gradient-primary text-primary-foreground hover:opacity-90">
                    <Plus className="h-4 w-4 mr-1.5" />Neuer Artikel
                  </Button>
                </DialogTrigger>
                <DialogContent className="glass-strong max-w-2xl">
                  <DialogHeader><DialogTitle>{editing?.id ? "Artikel bearbeiten" : "Neuer Artikel"}</DialogTitle></DialogHeader>
                  {editing && (
                    <div className="space-y-3">
                      <div><Label>Titel</Label><Input value={editing.title} onChange={(e) => setEditing({ ...editing, title: e.target.value })} className="mt-1.5" /></div>
                      <div><Label>Kategorie</Label><Input value={editing.category} onChange={(e) => setEditing({ ...editing, category: e.target.value })} className="mt-1.5" placeholder="rules, commands, plugins, faq..." /></div>
                      <div><Label>Inhalt (Markdown)</Label><Textarea value={editing.body} onChange={(e) => setEditing({ ...editing, body: e.target.value })} className="mt-1.5 min-h-[200px] font-mono text-sm" /></div>
                    </div>
                  )}
                  <DialogFooter>
                    <Button onClick={() => editing && save(editing)} disabled={!editing?.title?.trim()} className="bg-gradient-primary text-primary-foreground">Speichern</Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            </div>
            <div className="glass-strong rounded-2xl divide-y divide-border/50">
              {articles.map(a => (
                <div key={a.id} className="p-4 flex items-start justify-between gap-3">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-medium">{a.title}</span>
                      <span className="text-xs px-2 py-0.5 rounded bg-primary/20 text-primary">{a.category}</span>
                    </div>
                    <p className="text-xs text-muted-foreground line-clamp-2">{a.body}</p>
                  </div>
                  <div className="flex gap-1">
                    <Button size="icon" variant="ghost" onClick={() => { setEditing(a); setOpen(true); }}><Edit className="h-4 w-4" /></Button>
                    <Button size="icon" variant="ghost" onClick={() => del(a.id)} className="hover:text-destructive"><Trash2 className="h-4 w-4" /></Button>
                  </div>
                </div>
              ))}
            </div>
          </TabsContent>

          <TabsContent value="users" className="mt-6">
            <div className="glass-strong rounded-2xl divide-y divide-border/50">
              {users.map(u => (
                <div key={u.user_id} className="p-4 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <Users className="h-4 w-4 text-muted-foreground" />
                    <div>
                      <div className="font-medium text-sm">{u.display_name || u.email}</div>
                      <div className="text-xs text-muted-foreground">{u.email}</div>
                    </div>
                  </div>
                  <Button size="sm" variant={u.role === "admin" ? "default" : "outline"} onClick={() => toggleAdmin(u.user_id, u.role === "admin")}>
                    {u.role === "admin" ? "Admin entfernen" : "Zum Admin machen"}
                  </Button>
                </div>
              ))}
            </div>
          </TabsContent>

          <TabsContent value="rewards" className="mt-6">
            <RewardsAdmin />
          </TabsContent>
        </Tabs>
      </main>
    </div>
  );
};
export default Admin;
