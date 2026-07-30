import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { X } from "lucide-react";

interface UpgradeDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Optional explanation why the feature is locked */
  reason?: string;
  /** Feature name, e.g. "Bild‑Generierung" */
  feature?: string;
}

export const UpgradeDialog = ({ open, onOpenChange, reason, feature }: UpgradeDialogProps) => {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{feature ? `${feature} (Upgrade nötig)` : "Upgrade nötig"}</DialogTitle>
          {reason && <DialogDescription>{reason}</DialogDescription>}
        </DialogHeader>
        <DialogFooter className="flex flex-col gap-2 sm:flex-row">
          <Button variant="outline" onClick={() => onOpenChange(false)} className="w-full sm:w-auto">
            <X className="h-4 w-4 mr-1" /> Schließen
          </Button>
          <Button asChild className="w-full sm:w-auto bg-gradient-primary text-primary-foreground">
            <a href="/dashboard">Auf Pro upgraden</a>
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default UpgradeDialog;
