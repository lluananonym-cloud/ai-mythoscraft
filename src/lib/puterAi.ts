// Puter.js AI integration — free models, user-pays via their Puter account.
// See https://developer.puter.com/tutorials/free-unlimited-openai-api

declare global {
  interface Window {
    puter?: any;
  }
}

export type PuterModel = {
  id: string;
  label: string;
  vendor: string;
};

// Curated list of models Puter.js exposes for free (via user's Puter session).
export const PUTER_MODELS: PuterModel[] = [
  { id: "gpt-5", label: "GPT-5", vendor: "OpenAI" },
  { id: "gpt-5-mini", label: "GPT-5 Mini", vendor: "OpenAI" },
  { id: "gpt-5-nano", label: "GPT-5 Nano", vendor: "OpenAI" },
  { id: "gpt-4o", label: "GPT-4o", vendor: "OpenAI" },
  { id: "gpt-4o-mini", label: "GPT-4o Mini", vendor: "OpenAI" },
  { id: "o1", label: "o1 (Reasoning)", vendor: "OpenAI" },
  { id: "o3-mini", label: "o3-mini (Reasoning)", vendor: "OpenAI" },
  { id: "claude-sonnet-4", label: "Claude Sonnet 4", vendor: "Anthropic" },
  { id: "claude-opus-4", label: "Claude Opus 4", vendor: "Anthropic" },
  { id: "claude-3-7-sonnet", label: "Claude 3.7 Sonnet", vendor: "Anthropic" },
  { id: "claude-3-5-sonnet", label: "Claude 3.5 Sonnet", vendor: "Anthropic" },
  { id: "deepseek-chat", label: "DeepSeek Chat", vendor: "DeepSeek" },
  { id: "deepseek-reasoner", label: "DeepSeek Reasoner", vendor: "DeepSeek" },
  { id: "google/gemini-2.5-flash", label: "Gemini 2.5 Flash", vendor: "Google" },
  { id: "google/gemini-2.5-pro", label: "Gemini 2.5 Pro", vendor: "Google" },
  { id: "google/gemini-2.0-flash-lite-001", label: "Gemini 2.0 Flash Lite", vendor: "Google" },
  { id: "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo", label: "Llama 3.1 70B", vendor: "Meta" },
  { id: "mistral-large-latest", label: "Mistral Large", vendor: "Mistral" },
  { id: "grok-beta", label: "Grok Beta", vendor: "xAI" },
];

export const DEFAULT_MODEL_ID = "lovable-default";

export function isPuterModel(id: string | null | undefined): boolean {
  if (!id || id === DEFAULT_MODEL_ID) return false;
  return PUTER_MODELS.some((m) => m.id === id);
}

export function getPuterLabel(id: string | null | undefined): string {
  if (!id || id === DEFAULT_MODEL_ID) return "Mythos AI (Standard)";
  return PUTER_MODELS.find((m) => m.id === id)?.label ?? id;
}

async function waitForPuter(timeoutMs = 5000): Promise<any> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (typeof window !== "undefined" && window.puter?.ai?.chat) return window.puter;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error("Puter.js konnte nicht geladen werden.");
}

export interface PuterChatMsg {
  role: "system" | "user" | "assistant";
  content: any;
}

/**
 * Stream a chat completion via Puter.js. Yields incremental text deltas.
 * The first call opens Puter's sign-in popup (user-pays; free tier available).
 */
export async function* puterChatStream(
  messages: PuterChatMsg[],
  model: string,
): AsyncGenerator<string, void, void> {
  const puter = await waitForPuter();
  const resp = await puter.ai.chat(messages, { model, stream: true });
  for await (const part of resp) {
    const delta = part?.text ?? part?.message?.content ?? "";
    if (delta) yield String(delta);
  }
}
