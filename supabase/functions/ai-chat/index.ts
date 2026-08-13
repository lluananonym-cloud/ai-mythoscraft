import { createClient } from "npm:@supabase/supabase-js@2.45.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Client-Info, Apikey",
};

interface ChatMessage {
  role: "user" | "assistant" | "system";
  content: string;
}

interface TavilyResult {
  title: string;
  url: string;
  content: string;
}

interface TavilyResponse {
  results: TavilyResult[];
  answer?: string;
}

async function webSearch(query: string): Promise<{ results: TavilyResult[]; answer: string | null }> {
  const tavilyKey = Deno.env.get("TAVILY_API_KEY");
  if (!tavilyKey) {
    return { results: [], answer: null };
  }

  try {
    const response = await fetch("https://api.tavily.com/search", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${tavilyKey}`,
      },
      body: JSON.stringify({
        query,
        max_results: 5,
        include_answer: true,
        search_depth: "advanced",
      }),
    });

    if (!response.ok) {
      return { results: [], answer: null };
    }

    const data: TavilyResponse = await response.json();
    return {
      results: data.results || [],
      answer: data.answer || null,
    };
  } catch {
    return { results: [], answer: null };
  }
}

async function generateAIResponse(
  messages: ChatMessage[],
  context: string | null
): Promise<string> {
  const apiKey = Deno.env.get("ANTHROPIC_API_KEY");
  const baseUrl = Deno.env.get("ANTHROPIC_BASE_URL") || "https://api.anthropic.com";
  const model = Deno.env.get("ANTHROPIC_SMALL_FAST_MODEL") || "claude-haiku-4-5-20251001";

  if (!apiKey) {
    return "Kein API-Schlüssel konfiguriert. Bitte wende dich an den Administrator.";
  }

  const systemPrompt = context
    ? `Du bist ein hilfreicher KI-Assistent. Verwende die folgenden Web-Suchergebnisse, um aktuelle und genaue Informationen zu geben. Zitiere Quellen inline mit [1], [2] usw. wenn du Informationen aus den Suchergebnissen verwendest.\n\nWeb-Suchergebnisse:\n${context}`
    : "Du bist ein hilfreicher KI-Assistent. Antworte auf Deutsch, es sei denn der Nutzer schreibt in einer anderen Sprache.";

  try {
    const response = await fetch(`${baseUrl}/v1/messages`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-api-key": apiKey,
        "anthropic-version": "2023-06-01",
      },
      body: JSON.stringify({
        model,
        max_tokens: 2048,
        system: systemPrompt,
        messages: messages.map((m) => ({
          role: m.role,
          content: m.content,
        })),
      }),
    });

    if (!response.ok) {
      const errText = await response.text();
      return `Entschuldigung, die KI konnte nicht antworten. Fehler: ${response.status}`;
    }

    const data = await response.json();
    const content = data.content?.[0]?.text;
    return content || "Keine Antwort erhalten.";
  } catch (err) {
    return `Ein Fehler ist aufgetreten: ${err.message}`;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: corsHeaders });
  }

  try {
    const { messages, webSearch: useWebSearch } = await req.json();

    if (!messages || !Array.isArray(messages) || messages.length === 0) {
      return new Response(
        JSON.stringify({ error: "Keine Nachrichten angegeben" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const lastUserMessage = [...messages].reverse().find((m: ChatMessage) => m.role === "user");
    if (!lastUserMessage) {
      return new Response(
        JSON.stringify({ error: "Keine Benutzernachricht gefunden" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    let context: string | null = null;
    let sources: { title: string; url: string; snippet: string }[] = [];

    if (useWebSearch) {
      const searchResults = await webSearch(lastUserMessage.content);

      if (searchResults.results.length > 0) {
        sources = searchResults.results.map((r, i) => ({
          title: r.title,
          url: r.url,
          snippet: r.content.substring(0, 200),
        }));

        context = searchResults.results
          .map((r, i) => `[${i + 1}] ${r.title}\nURL: ${r.url}\n${r.content}`)
          .join("\n\n---\n\n");

        if (searchResults.answer) {
          context = `Zusammenfassung: ${searchResults.answer}\n\n${context}`;
        }
      }
    }

    const aiResponse = await generateAIResponse(messages, context);

    return new Response(
      JSON.stringify({
        content: aiResponse,
        sources,
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
