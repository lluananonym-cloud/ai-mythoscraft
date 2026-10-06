import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem,
  DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import { Check, ChevronDown, Crown, Gauge, Lock } from "lucide-react";
import {
  DEFAULT_MYTHOS_ID, MYTHOS_FAMILIES, isAllowed, parseMythosId, tierAllows, type Tier,
} from "@/lib/mythosModels";

type Props = {
  value: string;
  tier: Tier;
  onChange: (id: string) => void;
  onLocked: (reason: string) => void;
};

const needs = (t: Tier) => (t === "pro" ? "Pro" : "Light");

/** Modell- und Aufwand-Auswahl wie bei Claude: oben das Modell, darunter der Aufwand. */
export default function ModelPicker({ value, tier, onChange, onLocked }: Props) {
  const current = parseMythosId(value) ?? parseMythosId(DEFAULT_MYTHOS_ID)!;

  const pickFamily = (famId: string) => {
    const fam = MYTHOS_FAMILIES.find(f => f.id === famId)!;
    if (!tierAllows(tier, fam.minTier)) { onLocked(`${fam.label} braucht ${needs(fam.minTier)}.`); return; }
    // Aufwand beibehalten, wenn das neue Modell ihn hat und der Plan ihn erlaubt.
    const keep = fam.efforts.find(e => e.id === current.effort.id && tierAllows(tier, e.minTier));
    const fallback = fam.efforts.find(e => e.id === "normal") ?? fam.efforts[0];
    onChange(`${fam.id}:${(keep ?? fallback).id}`);
  };

  const pickEffort = (effId: string) => {
    const id = `${current.family.id}:${effId}`;
    const eff = current.family.efforts.find(e => e.id === effId)!;
    if (!isAllowed(id, tier)) { onLocked(`${current.family.label} · ${eff.label} braucht ${needs(eff.minTier)}.`); return; }
    onChange(id);
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          className="h-9 px-2.5 gap-1.5 text-sm font-medium hover:bg-white/5 max-w-[62vw] sm:max-w-none"
        >
          <span className="truncate">{current.family.label}</span>
          <span className="text-muted-foreground font-normal truncate hidden sm:inline">{current.effort.label}</span>
          <ChevronDown className="h-3.5 w-3.5 opacity-60 shrink-0" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="glass-strong w-[300px] max-h-[75vh] overflow-y-auto p-1.5">
        {MYTHOS_FAMILIES.map(f => {
          const ok = tierAllows(tier, f.minTier);
          const active = current.family.id === f.id;
          return (
            <DropdownMenuItem
              key={f.id}
              onSelect={(e) => { e.preventDefault(); pickFamily(f.id); }}
              className={`flex items-start gap-2 rounded-lg px-2 py-2 ${!ok ? "opacity-60" : ""}`}
            >
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-1.5 text-sm font-medium">
                  {f.label}
                  {!ok && <Crown className="h-3 w-3 text-fuchsia-400" />}
                </div>
                <div className="text-xs text-muted-foreground truncate">{f.desc}</div>
              </div>
              <span className="w-4 shrink-0 mt-0.5">
                {active ? <Check className="h-4 w-4 text-violet-300" /> : !ok ? <Lock className="h-3.5 w-3.5" /> : null}
              </span>
            </DropdownMenuItem>
          );
        })}

        <DropdownMenuSeparator />
        <DropdownMenuLabel className="flex items-center gap-1.5 text-xs text-muted-foreground font-normal">
          <Gauge className="h-3.5 w-3.5" /> Aufwand
        </DropdownMenuLabel>
        {current.family.efforts.map(e => {
          const ok = isAllowed(`${current.family.id}:${e.id}`, tier);
          const active = current.effort.id === e.id;
          return (
            <DropdownMenuItem
              key={e.id}
              onSelect={(ev) => { ev.preventDefault(); pickEffort(e.id); }}
              className={`flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm ${!ok ? "opacity-60" : ""}`}
            >
              <span>{e.label}</span>
              <span className="text-xs text-muted-foreground truncate">{e.hint}</span>
              <span className="ml-auto w-4 shrink-0">
                {active ? <Check className="h-4 w-4 text-violet-300" /> : !ok ? <Lock className="h-3.5 w-3.5" /> : null}
              </span>
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
