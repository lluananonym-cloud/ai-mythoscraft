// AI model catalog — routed through Lovable AI Gateway (no Puter, no key needed).
// Kept file name for import compatibility.

export type ChatModel = {
  id: string;
  label: string;
  vendor: string;
  desc?: string;
};

export const DEFAULT_MODEL_ID = "lovable-default";

// Curated premium models available via Lovable AI Gateway.
export const PUTER_MODELS: ChatModel[] = [
  { id: "openai/gpt-5.5-pro",              label: "GPT-5.5 Pro (Ultra)",       vendor: "OpenAI",  desc: "Stärkstes Reasoning" },
  { id: "google/gemini-3.1-pro-preview",   label: "Gemini 3.1 Pro (Preview)",  vendor: "Google",  desc: "Riesiges Kontext-Fenster, Multimodal" },
  { id: "openai/gpt-5.5",                  label: "GPT-5.5",                   vendor: "OpenAI",  desc: "Top Allrounder" },
  { id: "openai/gpt-5.4",                  label: "GPT-5.4 (Code Beast)",      vendor: "OpenAI",  desc: "Spezialist für Code & Analyse" },
];

export function isPuterModel(id: string | null | undefined): boolean {
  // Retained name for compat — semantics: "is a non-default custom model".
  if (!id || id === DEFAULT_MODEL_ID) return false;
  return PUTER_MODELS.some((m) => m.id === id);
}

export function getPuterLabel(id: string | null | undefined): string {
  if (!id || id === DEFAULT_MODEL_ID) return "Mythos AI (Standard)";
  return PUTER_MODELS.find((m) => m.id === id)?.label ?? id;
}
