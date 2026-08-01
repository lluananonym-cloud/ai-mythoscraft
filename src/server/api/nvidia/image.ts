// Server route proxy for NVIDIA image generation / editing
// POST { prompt: string, sourceImage?: string (base64) }
// Returns { url: string }
import { defineEventHandler, readBody, createError } from "h3";
import { NV_API_KEY } from "../../../lib/nvidiaApi";
import { fetchModelList, pickModel } from "./_utils";
import { logNvidiaRequest } from "./logger";

export default defineEventHandler(async (event) => {
  const { prompt, sourceImage } = await readBody(event);

  // Get full model list to allow proper fallback
  const allModels = await fetchModelList();
  const editCandidates = ["qwen-image-edit-nvpcb-ovsl2sl", "qwen-image-edit"];
  const genCandidates = ["qwen-image"];
  const baseCandidates = sourceImage ? editCandidates : genCandidates;
  const candidates = [...baseCandidates, ...allModels]; // ensure we have something

  let lastError: any;
  for (let i = 0; i < candidates.length; i++) {
    const cand = candidates[i];
    let modelId: string;
    try {
      modelId = await pickModel([cand]);
    } catch (e: any) {
      continue; // candidate not present
    }
    const payload: any = { model: modelId, prompt, n: 1 };
    if (sourceImage) payload.images = [{ data: sourceImage }];

    const resp = await fetch("https://integrate.api.nvidia.com/v1/images/generations", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${NV_API_KEY}`,
      },
      body: JSON.stringify(payload),
    });
    await logNvidiaRequest({ url: "https://integrate.api.nvidia.com/v1/images/generations", method: "POST", model: modelId, payload, response: resp });
    if (resp.ok) {
      const data = await resp.json();
      const img = data.data?.[0];
      const url = img?.url ?? `data:image/png;base64,${img?.b64_json}`;
      return { url };
    }
    const txt = await resp.text();
    if (resp.status === 404 && i < candidates.length - 1) {
      lastError = new Error(`Model ${modelId} not found (404). Trying fallback.`);
      continue;
    }
    lastError = new Error(`NVIDIA image request failed: ${resp.status} ${txt}`);
    break;
  }

  if (lastError) throw createError({ statusCode: 500, statusMessage: lastError.message });
  throw createError({ statusCode: 500, statusMessage: "No viable image model found" });
});
