/*
 * Helper functions to call NVIDIA hosted AI APIs (gpt-oss, image generation, TTS).
 * The API key is stored in the environment (or hard‑coded for the demo).
 */

export const NV_API_KEY = "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";

/**
 * Generic chat completion using NVIDIA "gpt-oss-120gb" model.
 * @param messages Array of {role, content} objects.
 */
export async function nvidiaChat(messages: { role: string; content: string }[]): Promise<string> {
  const resp = await fetch("/api/nvidia/chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${NV_API_KEY}`,
    },
    body: JSON.stringify({
      model: "gpt-oss-120gb",
      messages,
      temperature: 0.7,
    }),
  });
  if (!resp.ok) throw new Error(`NVIDIA chat error ${resp.status}`);
  const data = await resp.json();
  return data.choices?.[0]?.message?.content ?? "";
}

/**
 * Generic LLM chat using a specified NVIDIA model (e.g., meta/llama-3.3-70b-instruct).
 */
export async function nvidiaLLM(model: string, messages: { role: string; content: string }[]): Promise<string> {
  const resp = await fetch("/api/nvidia/llm", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${NV_API_KEY}`,
    },
    body: JSON.stringify({
      model,
      messages,
      temperature: 0.7,
    }),
  });
  if (!resp.ok) throw new Error(`NVIDIA LLM error ${resp.status}`);
  const data = await resp.json();
  return data.choices?.[0]?.message?.content ?? "";
}

/**
 * Image generation using NVIDIA "qwen-image-edit-nvpcb-ovsl2sl" model.
 * Returns a URL to the generated image (base64 data URL or hosted URL).
 */
export async function nvidiaGenerateImage(prompt: string): Promise<string> {
  const resp = await fetch("/api/nvidia/image", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${NV_API_KEY}`,
    },
    body: JSON.stringify({
      model: "qwen-image-edit-nvpcb-ovsl2sl",
      prompt,
      n: 1,
    }),
  });
  if (!resp.ok) throw new Error(`NVIDIA image error ${resp.status}`);
  const data = await resp.json();
    return data.url;
}

/**
 * Text‑to‑speech using NVIDIA TTS models (magpie‑tts‑multilingual or others).
 * Returns an audio Blob.
 */
export async function nvidiaTTS(text: string, model: string = "magpie-tts-multilingual"): Promise<Blob> {
  const resp = await fetch("/api/nvidia/tts", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${NV_API_KEY}`,
    },
    body: JSON.stringify({
      model,
      input: text,
    }),
  });
  if (!resp.ok) throw new Error(`NVIDIA TTS error ${resp.status}`);
    const data = await resp.json();
    const base64 = data.audioBase64;
    const binary = Uint8Array.from(atob(base64), c => c.charCodeAt(0));
    return new Blob([binary.buffer], { type: "audio/mpeg" });
}
