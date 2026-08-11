import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem,
  DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import { Check, ChevronDown, Crown, Lock } from "lucide-react";
import {
  MYTHOS_FAMILIES, isAllowed, mythosLabel, parseMythosId, tierAllows, type Tier,
} from "@/lib/mythosModels";

type Props = {
  value: string;
  tier: Tier;
  onChange: (id: string) => void;
  onLocked: (reason: string) => void;
};

export default function ModelPicker({ value, tier, onChange, onLocked }: Props) {
  const current = parseMythosId(value);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          className="h-9 px-2 gap-1.5 text-sm font-medium hover:bg-white/5 max-w-[62vw] sm:max-w-none"
        >
          <span className="truncate">{mythosLabel(value)}</span>
          <ChevronDown className="h-3.5 w-3.5 opacity-60 shrink-0" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="glass-strong w-[290px] max-h-[70vh] overflow-y-auto">
        {MYTHOS_FAMILIES.map((f, fi) => {
          const famOk = tierAllows(tier, f.minTier);
          return (
            <div key={f.id}>
              {fi > 0 && <DropdownMenuSeparator />}
              <DropdownMenuLabel className="flex items-center gap-1.5 text-xs">
                <span>{f.label}</span>
                {!famOk && <Crown className="h-3 w-3 text-fuchsia-400" />}
                <span className="ml-auto text-[10px] font-normal text-muted-foreground truncate">{f.desc}</span>
              </DropdownMenuLabel>
              {f.efforts.map(e => {
                const id = `${f.id}:${e.id}`;
                const ok = isAllowed(id, tier);
                const active = current?.family.id === f.id && current?.effort.id === e.id;
                return (
                  <DropdownMenuItem
                    key={id}
                    onClick={() => {
                      if (!ok) {
                        onLocked(`${f.label} · ${e.label} braucht ${(!famOk ? f.minTier : e.minTier) === "pro" ? "Pro" : "Light"}.`);
                        return;
                      }
                      onChange(id);
                    }}
                    className={`text-sm ${!ok ? "opacity-50" : ""}`}
                  >
                    <span className="w-4 shrink-0">
                      {active ? <Check className="h-3.5 w-3.5" /> : !ok ? <Lock className="h-3 w-3" /> : null}
                    </span>
                    <span>{e.label}</span>
                    <span className="ml-auto text-[10px] text-muted-foreground truncate max-w-[130px]">{e.hint}</span>
                  </DropdownMenuItem>
                );
              })}
            </div>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
