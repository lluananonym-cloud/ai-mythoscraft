import { createClient } from "npm:@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Client-Info, Apikey",
};

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } }
);

interface VideoGenerateRequest {
  prompt: string;
  aspect_ratio: string;
  duration: number;
}

interface NvidiaNIMResponse {
  video?: string;
  download_url?: string;
  url?: string;
  id?: string;
  status?: string;
  message?: string;
  detail?: string;
}

async function generateSkyReelsVideo(
  prompt: string,
  aspectRatio: string,
  duration: number
): Promise<{ videoUrl: string | null; error: string | null }> {
  const nvidiaKey = Deno.env.get("NVIDIA_API_KEY");

  if (!nvidiaKey) {
    return { videoUrl: null, error: "NVIDIA API-Schlüssel nicht konfiguriert. Bitte füge NVIDIA_API_KEY als Secret hinzu." };
  }

  const modelId = "skywork/skyreels-v2";
  const apiUrl = `https://integrate.api.nvidia.com/v1/genai/${modelId}`;

  const aspectRatioMap: Record<string, string> = {
    "16:9": "1280x720",
    "9:16": "720x1280",
    "1:1": "720x720",
  };

  const resolution = aspectRatioMap[aspectRatio] || "1280x720";

  const requestBody = {
    text: prompt,
    duration: duration,
    aspect_ratio: aspectRatio,
    resolution: resolution,
    model: modelId,
    n: 1,
    seed: Math.floor(Math.random() * 1000000),
    cfg_scale: 7.0,
    quality: "standard",
    output_format: "mp4",
  };

  try {
    const response = await fetch(apiUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${nvidiaKey}`,
        "Accept": "application/json",
      },
      body: JSON.stringify(requestBody),
    });

    if (!response.ok) {
      const errText = await response.text();
      let errMsg = `NVIDIA API Fehler (${response.status})`;

      try {
        const errJson = JSON.parse(errText);
        errMsg = errJson.detail || errJson.message || errJson.error || errMsg;
      } catch {
        if (errText) errMsg = `${errMsg}: ${errText.substring(0, 200)}`;
      }

      return { videoUrl: null, error: errMsg };
    }

    const data: NvidiaNIMResponse = await response.json();

    const videoUrl = data.video || data.download_url || data.url || null;

    if (!videoUrl) {
      return { videoUrl: null, error: "Keine Video-URL in der API-Antwort erhalten." };
    }

    return { videoUrl, error: null };
  } catch (err) {
    return { videoUrl: null, error: `Netzwerkfehler: ${err.message}` };
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(
        JSON.stringify({ error: "Nicht authentifiziert" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const token = authHeader.replace("Bearer ", "");
    const { data: userData, error: userError } = await supabase.auth.getUser(token);

    if (userError || !userData.user) {
      return new Response(
        JSON.stringify({ error: "Ungültige Sitzung" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const userId = userData.user.id;
    const { prompt, aspect_ratio, duration } = await req.json() as VideoGenerateRequest;

    if (!prompt || !prompt.trim()) {
      return new Response(
        JSON.stringify({ error: "Prompt ist erforderlich" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const { data: jobRow, error: insertError } = await supabase
      .from("video_jobs")
      .insert({
        user_id: userId,
        prompt: prompt.trim(),
        status: "processing",
        model: "skyreels-v2",
        aspect_ratio: aspect_ratio || "16:9",
        duration: duration || 5,
      })
      .select()
      .single();

    if (insertError || !jobRow) {
      return new Response(
        JSON.stringify({ error: "Job konnte nicht erstellt werden" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const result = await generateSkyReelsVideo(prompt.trim(), aspect_ratio || "16:9", duration || 5);

    if (result.error || !result.videoUrl) {
      await supabase
        .from("video_jobs")
        .update({
          status: "failed",
          error_message: result.error || "Unbekannter Fehler",
          updated_at: new Date().toISOString(),
        })
        .eq("id", jobRow.id);

      return new Response(
        JSON.stringify({ error: result.error || "Video-Generierung fehlgeschlagen" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    await supabase
      .from("video_jobs")
      .update({
        status: "completed",
        video_url: result.videoUrl,
        updated_at: new Date().toISOString(),
      })
      .eq("id", jobRow.id);

    return new Response(
      JSON.stringify({
        success: true,
        job_id: jobRow.id,
        video_url: result.videoUrl,
      }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err) {
    return new Response(
      JSON.stringify({ error: err.message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
