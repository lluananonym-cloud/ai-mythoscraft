// Server route proxy for NVIDIA LLM (any model)
// POST { model: string, messages: [{role:string, content:string}] }
// Returns { content: string }
import { defineEventHandler, readBody, createError } from "h3";
import { NV_API_KEY } from "../../../lib/nvidiaApi";
import { pickModel, fetchModelList } from "./_utils";
import { logNvidiaRequest } from "./logger";

export default defineEventHandler(async (event) => {
  const { model, messages } = await readBody(event);

  // Build an ordered list of candidate model IDs
  const allModels = await fetchModelList();
  const instructCandidates = allModels.filter((id) => /llama.*instruct/i.test(id));
  const candidates = [model, ...instructCandidates];

  let lastError: any;
  for (let i = 0; i < candidates.length; i++) {
    const cand = candidates[i];
    let validatedModel: string;
    try {
      validatedModel = await pickModel([cand]);
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
      body: JSON.stringify({ model: validatedModel, messages, temperature: 0.7 }),
    });
    // Log full request/response details
    await logNvidiaRequest({ url: "https://integrate.api.nvidia.com/v1/chat/completions", method: "POST", model: validatedModel, payload: { model: validatedModel, messages }, response: resp });
    if (resp.ok) {
      const data = await resp.json();
      return { content: data.choices?.[0]?.message?.content ?? "" };
    }
    const txt = await resp.text();
    if (resp.status === 404 && i < candidates.length - 1) {
      // try next candidate
      lastError = new Error(`Model ${validatedModel} not found (404). Trying fallback.`);
      continue;
    }
    // Other errors – break and report
    lastError = new Error(`NVIDIA LLM request failed: ${resp.status} ${txt}`);
    break;
  }
  // If we exit loop without success
  if (lastError) throw createError({ statusCode: 500, statusMessage: lastError.message });
  throw createError({ statusCode: 500, statusMessage: "No viable LLM model found" });
});
