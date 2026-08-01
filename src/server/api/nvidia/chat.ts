// Server route proxy for NVIDIA chat (fallback model)
// POST { messages: [{role:string, content:string}] }
// Returns { content: string }
import { defineEventHandler, readBody, createError } from "h3";
import { NV_API_KEY } from "../../../lib/nvidiaApi";
import { fetchModelList, pickModel } from "./_utils";
import { logNvidiaRequest } from "./logger";

export default defineEventHandler(async (event) => {
  const { messages } = await readBody(event);

  // Retrieve full model list first
  const allModels = await fetchModelList();
  // Primary candidate is the documented fallback model
  const primary = "gpt-oss-120gb";
  // Build fallback list: primary + any other model that looks like a chat model
  const fallbackCandidates = [primary, ...allModels.filter((id) => /gpt|llama|mixtral|phi|wizard|mistral/i.test(id))];

  let lastError: any;
  for (let i = 0; i < fallbackCandidates.length; i++) {
    const cand = fallbackCandidates[i];
    let modelId: string;
    try {
      modelId = await pickModel([cand]);
    } catch (e: any) {
      // candidate not present – try next
      continue;
    }
    const resp = await fetch("https://integrate.api.nvidia.com/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${NV_API_KEY}`,
      },
      body: JSON.stringify({ model: modelId, messages, temperature: 0.7 }),
    });
    await logNvidiaRequest({ url: "https://integrate.api.nvidia.com/v1/chat/completions", method: "POST", model: modelId, payload: { model: modelId, messages }, response: resp });
    if (resp.ok) {
      const data = await resp.json();
      return { content: data.choices?.[0]?.message?.content ?? "" };
    }
    const txt = await resp.text();
    if (resp.status === 404 && i < fallbackCandidates.length - 1) {
      lastError = new Error(`Model ${modelId} not found (404). Trying fallback.`);
      continue;
    }
    // Any other status – abort
    lastError = new Error(`NVIDIA chat request failed: ${resp.status} ${txt}`);
    break;
  }
  if (lastError) throw createError({ statusCode: 500, statusMessage: lastError.message });
  throw createError({ statusCode: 500, statusMessage: "No viable chat model found" });
});
