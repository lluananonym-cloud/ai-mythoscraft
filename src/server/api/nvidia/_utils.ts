// Utility helpers for NVIDIA proxy routes
// All routes read the API key from the server environment (never from client code)
export const NV_API_KEY = process.env.NV_API_KEY || "";
if (!NV_API_KEY) {
  console.error("[NVIDIA] NV_API_KEY not set in server environment");
}

/**
 * Retrieve the list of model IDs that the current NVIDIA endpoint exposes.
 * Returns an array of strings (model IDs) or throws on network error.
 */
export async function fetchModelList(): Promise<string[]> {
  const resp = await fetch("https://integrate.api.nvidia.com/v1/models", {
    headers: { Authorization: `Bearer ${NV_API_KEY}` },
  });
  if (!resp.ok) {
    const txt = await resp.text();
    throw new Error(`Failed to fetch model list: ${resp.status} ${txt}`);
  }
  const data = await resp.json();
  // Expected shape: { data: [{ id: "model-id" }, ...] }
  if (!Array.isArray(data?.data)) return [];
  const models = data.data.map((m: any) => m.id).filter(Boolean);
  // Log every returned model id
  console.log("[NVIDIA] Available model IDs:", models);
  return models;
}

/**
 * Pick the first available model from a list of candidates.
 * The function fetches the live model list once per invocation.
 */
export async function pickModel(candidates: string[]): Promise<string> {
  const models = await fetchModelList();
  for (const cand of candidates) {
    if (models.includes(cand)) return cand;
    // also allow prefix matches for Qwen image‑edit variants
    const prefixMatch = models.find(m => m.startsWith(cand.split('-')[0] + "-"));
    if (prefixMatch) return prefixMatch;
  }
  throw new Error(`None of the candidate models are available: ${candidates.join(", ")}`);
}
