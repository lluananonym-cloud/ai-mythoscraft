// Centralized logger for NVIDIA proxy requests
export async function logNvidiaRequest(params: {
  url: string;
  method: string;
  model?: string;
  payload?: any;
  response: Response;
}) {
  const { url, method, model, payload, response } = params;
  // Clone response to read body without consuming original
  const clone = response.clone();
  let bodyText: string;
  try {
    bodyText = await clone.text();
  } catch (e) {
    bodyText = `[[failed to read body: ${e}]}`;
  }
  const headersObj: Record<string, string> = {};
  response.headers.forEach((v, k) => (headersObj[k] = v));
  console.log(`[NVIDIA] Request -> ${method} ${url}`);
  if (model) console.log(`[NVIDIA] Model -> ${model}`);
  if (payload) console.log(`[NVIDIA] Payload ->`, payload);
  console.log(`[NVIDIA] Response status -> ${response.status}`);
  console.log(`[NVIDIA] Response headers ->`, headersObj);
  console.log(`[NVIDIA] Response body -> ${bodyText}`);
  return response;
}
