// Automatische Erkennung von Bild-Wünschen im Chat und Auflösung von Bezügen aus dem Verlauf.
// Beispiel: Vor 4 Nachrichten ging es um einen blauen Apfel, jetzt schreibt man
// "generiere mir das als Bild" -> Prompt "a glossy blue apple ...".
// Die Erkennung ist bewusst identisch mit src/lib/imageIntent.ts (Client: Paywall + Routing).
import { aiText } from "./ai.ts";

const VERB = String.raw`(generier|erstell|erzeug|mach|mal|male|zeichne|zeig|kreier|design|render|visualisier|illustrier|generate|create|make|draw|paint|show|render|visuali[sz]e|illustrate|design)`;
const NOUN = String.raw`(bild|bilder|foto|fotos|grafik|illustration|zeichnung|gemälde|wallpaper|hintergrundbild|poster|logo|avatar|profilbild|image|picture|pic|photo|drawing|painting|artwork)`;
const STRONG = [
  new RegExp(String.raw`\b${VERB}\w*\b[^.?!\n]{0,40}?\b(ein|eine|einen|das|dein|als|zwei|drei|vier|an?|the|some|two|three|four)\s+(\S+\s+){0,3}?${NOUN}\b`, "i"),
  new RegExp(String.raw`\b(als|zu einem|in ein|as an?|into an?)\s+${NOUN}\b`, "i"),
  new RegExp(String.raw`\b${NOUN}\b[^.?!\n]{0,40}?\b(machen|generieren|erstellen|erzeugen|zeichnen|malen|make|generate|create|draw)\b`, "i"),
  /^\s*(zeichne|male|mal mir|draw|paint)\b/i,
];
// Fragen über Bilder oder Bild-Analysen sind keine Generier-Wünsche.
const NOT = /^\s*(wie|warum|wieso|weshalb|was ist|what is|how|why)\b|\b(was ist auf|was siehst du|beschreib|analysier|erkenn|what'?s in|describe|analy[sz]e)\b|\b(code|komponente|component|html|css|svg|website|webseite|landingpage|funktion|function)\b/i;

export function isImageRequest(text: string): boolean {
  const t = (text || "").trim();
  if (!t || t.startsWith("/") || t.startsWith("🎯") || t.length > 600) return false;
  if (NOT.test(t)) return false;
  return STRONG.some((r) => r.test(t));
}

type Msg = { role: string; content: unknown };
const textOf = (c: unknown): string =>
  typeof c === "string" ? c : Array.isArray(c) ? c.map((p: any) => (p?.type === "text" ? p.text : "")).join(" ") : "";

// Füllwörter weg, um zu prüfen, ob die Anfrage selbst schon ein Motiv enthält.
const FILLER = /\b(bitte|mal|mir|uns|doch|jetzt|nochmal|kannst du|könntest du|du|ein|eine|einen|einem|das|dies|dieses|davon|daraus|dazu|es|ihn|sie|als|von|vom|zu|in|generier\w*|erstell\w*|erzeug\w*|mach\w*|male?|zeichne|zeig\w*|kreier\w*|bild|bilder|foto|image|picture|photo|please|me|an?|the|it|that|this|of|as|generate|create|make|draw|paint|show)\b/gi;

function heuristicPrompt(last: string, history: Msg[]): string {
  const rest = last.replace(FILLER, " ").replace(/[^\p{L}\p{N}\s,-]/gu, " ").replace(/\s+/g, " ").trim();
  if (rest.split(" ").filter((w) => w.length > 2).length >= 2) return last;
  // Bezug ("das", "es") -> die letzten Nachrichten des Nutzers als Motiv nehmen.
  const prev = history.filter((m) => m.role === "user").map((m) => textOf(m.content).trim())
    .filter((t) => t && !t.startsWith("/")).slice(-4).join(". ");
  return (prev || last).slice(0, 500);
}

/** Baut aus dem Verlauf einen eigenständigen Bild-Prompt. Fällt ohne KI auf eine Heuristik zurück. */
export async function resolveImagePrompt(request: string, messages: Msg[], lovableKey?: string): Promise<string> {
  // Verlauf ohne die aktuelle Nachricht, nur Text, gekürzt.
  const all = messages.filter((m) => m.role === "user" || m.role === "assistant");
  const history = all.slice(0, -1).slice(-12);
  const transcript = history
    .map((m) => `${m.role === "user" ? "User" : "Assistant"}: ${textOf(m.content).replace(/\s+/g, " ").slice(0, 600)}`)
    .filter((l) => !/:\s*$/.test(l))
    .join("\n");
  try {
    const out = await aiText({
      model: "google/gemini-2.5-flash",
      temperature: 0.4,
      max_tokens: 300,
      messages: [
        {
          role: "system",
          content:
            "You write prompts for an image generator. The user's last message asks for an image. " +
            "Use the conversation to resolve references like 'das', 'es', 'dies', 'it', 'that', 'the one above' to the concrete subject discussed earlier " +
            "(keep every detail mentioned: colors, objects, style, mood). If the request itself names the subject, use that. " +
            "Answer with ONLY the image prompt in English: one to three vivid sentences, no quotes, no explanations.",
        },
        { role: "user", content: `Conversation:\n${transcript || "(empty)"}\n\nImage request: ${request}` },
      ],
    }, lovableKey);
    const p = out.trim().replace(/^["'`]+|["'`]+$/g, "").replace(/^(prompt|image prompt)\s*:\s*/i, "");
    if (p.length >= 8) return p.slice(0, 1500);
  } catch (e) {
    console.warn("[imageIntent] resolve failed", e instanceof Error ? e.message : String(e));
  }
  return heuristicPrompt(request, history);
}
