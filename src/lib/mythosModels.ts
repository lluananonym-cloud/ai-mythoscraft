// Mythos model catalog — model + effort in one selectable id.
// Frontend labels/tier gating. The edge function maps ids -> real gateway models.

export type Tier = "free" | "light" | "pro";
export type EffortId = "instant" | "low" | "normal" | "high" | "ultra" | "ultracode" | "giga";

export type MythosEffort = {
  id: EffortId;
  label: string;
  minTier: Tier;
  hint: string;
};

export type MythosFamily = {
  id: string;
  label: string;
  minTier: Tier;
  desc: string;
  efforts: MythosEffort[];
};

const E = (id: EffortId, label: string, minTier: Tier, hint: string): MythosEffort => ({ id, label, minTier, hint });

export const MYTHOS_FAMILIES: MythosFamily[] = [
  {
    id: "v1",
    label: "Mythos v1",
    minTier: "free",
    desc: "Allrounder für Alltag & Support",
    efforts: [
      E("instant", "Instant", "free", "Sofort, sehr kurz"),
      E("low", "Low", "free", "Schnell, knapp"),
      E("normal", "Normal", "free", "Ausbalanciert"),
      E("high", "High", "free", "Gründlich & durchdacht"),
      E("ultra", "Ultra", "light", "Tiefes Reasoning"),
    ],
  },
  {
    id: "code11",
    label: "MythosCode v1.1",
    minTier: "free",
    desc: "Code, Debugging, Technik",
    efforts: [
      E("instant", "Instant", "free", "Schnelle Snippets"),
      E("low", "Low", "free", "Kurze Lösungen"),
      E("normal", "Normal", "free", "Solide Implementierung"),
      E("high", "High", "free", "Architektur & Review"),
      E("ultra", "Ultra", "light", "Große Refactorings"),
      E("ultracode", "Ultra Code", "light", "Maximale Code-Tiefe"),
    ],
  },
  {
    id: "v2",
    label: "Mythos v2",
    minTier: "pro",
    desc: "Nächste Generation — stärkstes Reasoning",
    efforts: [
      E("instant", "Instant", "pro", "Blitzschnell"),
      E("low", "Low", "pro", "Schnell & präzise"),
      E("normal", "Normal", "pro", "Stark & ausbalanciert"),
      E("high", "High", "pro", "Sehr gründlich"),
      E("ultra", "Ultra", "pro", "Maximales Reasoning"),
    ],
  },
  {
    id: "code15",
    label: "MythosCode v1.5",
    minTier: "pro",
    desc: "Profi-Code-Modell",
    efforts: [
      E("instant", "Instant", "pro", "Blitz-Snippets"),
      E("low", "Low", "pro", "Schnelle Fixes"),
      E("normal", "Normal", "pro", "Feature-Entwicklung"),
      E("high", "High", "pro", "Komplexe Systeme"),
      E("ultra", "Ultra", "pro", "Tiefe Analyse"),
      E("giga", "Giga Code", "pro", "Ganze Projekte, maximal"),
    ],
  },
];

export const DEFAULT_MYTHOS_ID = "v1:normal";

const RANK: Record<Tier, number> = { free: 0, light: 1, pro: 2 };
export const tierAllows = (tier: Tier, min: Tier) => RANK[tier] >= RANK[min];

export function parseMythosId(id: string): { family: MythosFamily; effort: MythosEffort } | null {
  const [f, e] = (id || "").split(":");
  const family = MYTHOS_FAMILIES.find(x => x.id === f);
  if (!family) return null;
  const effort = family.efforts.find(x => x.id === e);
  if (!effort) return null;
  return { family, effort };
}

export function mythosLabel(id: string): string {
  const p = parseMythosId(id) ?? parseMythosId(DEFAULT_MYTHOS_ID)!;
  return `${p.family.label} · ${p.effort.label}`;
}

export function isAllowed(id: string, tier: Tier): boolean {
  const p = parseMythosId(id);
  if (!p) return false;
  return tierAllows(tier, p.family.minTier) && tierAllows(tier, p.effort.minTier);
}
