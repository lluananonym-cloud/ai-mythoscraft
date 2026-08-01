// Server route to expose NVIDIA model list (for internal sanity checks)
import { defineEventHandler, createError } from "h3";
import { NV_API_KEY } from "../../../lib/nvidiaApi";

export default defineEventHandler(async () => {
  const resp = await fetch("https://integrate.api.nvidia.com/v1/models", {
    headers: { Authorization: `Bearer ${NV_API_KEY}` },
  });
  if (!resp.ok) {
    const txt = await resp.text();
    throw createError({ statusCode: resp.status, statusMessage: txt });
  }
  const data = await resp.json();
  return data; // forward unchanged JSON list
});
