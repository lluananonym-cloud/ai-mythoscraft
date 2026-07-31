// Server route proxy for NVIDIA LLM (any model)
// POST { model: string, messages: [{role:string, content:string}] }
// Returns { content: string }
import { defineEventHandler, readBody, createError } from "h3";
import { NV_API_KEY } from "../../../lib/nvidiaApi";

export default defineEventHandler(async (event) => {
  const { model, messages } = await readBody(event);
  const resp = await fetch("https://integrate.api.nvidia.com/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${NV_API_KEY}`,
    },
    body: JSON.stringify({ model, messages, temperature: 0.7 }),
  });
  if (!resp.ok) {
    const txt = await resp.text();
    throw createError({ statusCode: resp.status, statusMessage: txt });
  }
  const data = await resp.json();
  return { content: data.choices?.[0]?.message?.content ?? "" };
});
