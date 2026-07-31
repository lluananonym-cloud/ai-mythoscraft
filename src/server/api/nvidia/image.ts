// Server route proxy for NVIDIA image generation
// POST { prompt: string }
// Returns { url: string }
import { defineEventHandler, readBody, createError } from "h3";
import { NV_API_KEY } from "../../../lib/nvidiaApi";

export default defineEventHandler(async (event) => {
  const { prompt } = await readBody(event);
  const resp = await fetch("https://integrate.api.nvidia.com/v1/images/generations", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${NV_API_KEY}`,
    },
    body: JSON.stringify({ model: "qwen-image-edit-nvpcb-ovsl2sl", prompt, n: 1 }),
  });
  if (!resp.ok) {
    const txt = await resp.text();
    throw createError({ statusCode: resp.status, statusMessage: txt });
  }
  const data = await resp.json();
  const img = data.data?.[0];
  const url = img?.url ?? `data:image/png;base64,${img?.b64_json}`;
  return { url };
});
