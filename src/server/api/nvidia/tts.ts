// Server route proxy for NVIDIA TTS (primary NVIDIA model)
// POST { text: string, model?: string }
// Returns { audioBase64: string }
import { defineEventHandler, readBody, createError } from "h3";
import { NV_API_KEY } from "../../../lib/nvidiaApi";
import { pickModel } from "./_utils";
import { logNvidiaRequest } from "./logger";

export default defineEventHandler(async (event) => {
  const { text, model = "nvidia/magpie-tts-multilingual" } = await readBody(event);
  // Validate that the requested (or default) model is available
  let modelId: string;
  try {
    modelId = await pickModel([model]);
  } catch (e: any) {
    throw createError({ statusCode: 500, statusMessage: e.message });
  }

  const resp = await fetch("https://integrate.api.nvidia.com/v1/audio/speech", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${NV_API_KEY}`,
    },
    body: JSON.stringify({ model: modelId, input: text }),
  });
  await logNvidiaRequest({ url: "https://integrate.api.nvidia.com/v1/audio/speech", method: "POST", model: modelId, payload: { model: modelId, input: text }, response: resp });
  if (!resp.ok) {
    const txt = await resp.text();
    throw createError({ statusCode: resp.status, statusMessage: txt });
  }
  // Convert binary audio to base64 for easy transport to the client
  const blob = await resp.blob();
  const arrayBuffer = await blob.arrayBuffer();
  const base64 = Buffer.from(arrayBuffer).toString("base64");
  return { audioBase64: base64 };
});
