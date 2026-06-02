import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Sparkles, Check } from "lucide-react";

export default function Paywall({ open, onOpenChange, reason }: { open: boolean; onOpenChange: (o: boolean) => void; reason?: string }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="glass-strong max-w-md">
        <DialogHeader>
          <div className="mx-auto h-12 w-12 rounded-full bg-gradient-primary flex items-center justify-center mb-2">
            <Sparkles className="h-6 w-6 text-primary-foreground" />
          </div>
          <DialogTitle className="text-center text-xl">Mythos Pro freischalten</DialogTitle>
          <DialogDescription className="text-center">{reason || "Diese Funktion ist Teil von Mythos Pro."}</DialogDescription>
        </DialogHeader>
        <ul className="space-y-2 text-sm py-2">
          {["Unbegrenzte Chats","Live-Sprachchat","Bilder & Musik generieren","Alle Personas & Agents","Priorität bei neuen Features"].map(f => (
            <li key={f} className="flex items-center gap-2"><Check className="h-4 w-4 text-primary" />{f}</li>
          ))}
        </ul>
        <p className="text-xs text-center text-muted-foreground">
          Pro wird gerade über Paddle eingerichtet. Frag einen Admin nach einem Gratis-Code oder warte kurz – Checkout kommt bald.
        </p>
        <Button onClick={() => onOpenChange(false)} className="bg-gradient-primary text-primary-foreground">Verstanden</Button>
      </DialogContent>
    </Dialog>
  );
}
