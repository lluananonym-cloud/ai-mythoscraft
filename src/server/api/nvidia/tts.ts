// Server route proxy for NVIDIA TTS
// POST { text: string, model?: string }
// Returns { audioBase64: string }
import { defineEventHandler, readBody, createError } from "h3";
import { NV_API_KEY } from "../../../lib/nvidiaApi";

export default defineEventHandler(async (event) => {
  const { text, model = "magpie-tts-multilingual" } = await readBody(event);
  const resp = await fetch("https://integrate.api.nvidia.com/v1/audio/speech", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${NV_API_KEY}`,
    },
    body: JSON.stringify({ model, input: text }),
  });
  if (!resp.ok) {
    const txt = await resp.text();
    throw createError({ statusCode: resp.status, statusMessage: txt });
  }
  const blob = await resp.blob();
  const arrayBuffer = await blob.arrayBuffer();
  const base64 = Buffer.from(arrayBuffer).toString("base64");
  return { audioBase64: base64 };
});
