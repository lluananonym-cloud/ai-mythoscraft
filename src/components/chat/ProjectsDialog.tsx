import { useEffect, useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FolderOpen, Plus, Trash2, ArrowLeft } from "lucide-react";
import { getProjects, newId, setProjects, type Project } from "@/lib/chatPrefs";

type Props = {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  onOpenProject: (id: string) => void;
};

/** Projekte: Chats bündeln und allen Chats darin dieselben Anweisungen mitgeben. */
export default function ProjectsDialog({ open, onOpenChange, onOpenProject }: Props) {
  const [list, setList] = useState<Project[]>([]);
  const [edit, setEdit] = useState<Project | null>(null);

  useEffect(() => { if (open) { setList(getProjects()); setEdit(null); } }, [open]);

  const save = (next: Project[]) => { setList(next); setProjects(next); };

  const saveEdit = () => {
    if (!edit || !edit.name.trim()) return;
    const exists = list.some(p => p.id === edit.id);
    save(exists ? list.map(p => p.id === edit.id ? edit : p) : [edit, ...list]);
    if (!exists) { onOpenChange(false); onOpenProject(edit.id); }
    setEdit(null);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="glass-strong max-w-lg max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {edit && <button onClick={() => setEdit(null)} aria-label="Zurück"><ArrowLeft className="h-4 w-4" /></button>}
            {edit ? (list.some(p => p.id === edit.id) ? "Projekt bearbeiten" : "Neues Projekt") : "Projekte"}
          </DialogTitle>
        </DialogHeader>

        {edit ? (
          <div className="space-y-3">
            <Input autoFocus placeholder="Name des Projekts" value={edit.name} onChange={e => setEdit({ ...edit, name: e.target.value })} />
            <Textarea rows={7} placeholder="Anweisungen für alle Chats in diesem Projekt (optional), z. B. Ziel, Stil, Hintergrundwissen…"
              value={edit.instructions} onChange={e => setEdit({ ...edit, instructions: e.target.value })} />
            <Button onClick={saveEdit} disabled={!edit.name.trim()}>Speichern</Button>
          </div>
        ) : (
          <div className="space-y-2">
            <Button className="w-full justify-start" variant="outline"
              onClick={() => setEdit({ id: newId(), name: "", instructions: "", chatIds: [], createdAt: Date.now() })}>
              <Plus className="h-4 w-4 mr-2" /> Neues Projekt
            </Button>
            {list.length === 0 && <p className="text-sm text-muted-foreground text-center py-6">Noch keine Projekte.</p>}
            {list.map(p => (
              <div key={p.id} className="group flex items-center gap-2 rounded-xl border border-white/10 hover:bg-white/5 p-3">
                <button className="flex-1 min-w-0 flex items-center gap-3 text-left" onClick={() => { onOpenChange(false); onOpenProject(p.id); }}>
                  <FolderOpen className="h-4 w-4 text-violet-300 shrink-0" />
                  <div className="min-w-0">
                    <div className="text-sm font-medium truncate">{p.name}</div>
                    <div className="text-xs text-muted-foreground">{p.chatIds.length} Chats{p.instructions ? " · mit Anweisungen" : ""}</div>
                  </div>
                </button>
                <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => setEdit(p)}>Bearbeiten</Button>
                <Button variant="ghost" size="icon" className="h-7 w-7" aria-label="Löschen"
                  onClick={() => { if (confirm(`Projekt „${p.name}" löschen? Die Chats bleiben erhalten.`)) save(list.filter(x => x.id !== p.id)); }}>
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              </div>
            ))}
            <p className="text-[11px] text-muted-foreground pt-1">Projekte werden in diesem Browser gespeichert.</p>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
