// Erkennt Bild-Wünsche in normaler Sprache ("generiere mir das als Bild", "zeichne einen Drachen").
// Gleiche Regeln wie supabase/functions/_shared/imageIntent.ts; der Server löst den Prompt
// anschließend mit dem Chatverlauf auf.
const VERB = String.raw`(generier|erstell|erzeug|mach|mal|male|zeichne|zeig|kreier|design|render|visualisier|illustrier|generate|create|make|draw|paint|show|render|visuali[sz]e|illustrate|design)`;
const NOUN = String.raw`(bild|bilder|foto|fotos|grafik|illustration|zeichnung|gemälde|wallpaper|hintergrundbild|poster|logo|avatar|profilbild|image|picture|pic|photo|drawing|painting|artwork)`;
const STRONG = [
  new RegExp(String.raw`\b${VERB}\w*\b[^.?!\n]{0,40}?\b(ein|eine|einen|das|dein|als|zwei|drei|vier|an?|the|some|two|three|four)\s+(\S+\s+){0,3}?${NOUN}\b`, "i"),
  new RegExp(String.raw`\b(als|zu einem|in ein|as an?|into an?)\s+${NOUN}\b`, "i"),
  new RegExp(String.raw`\b${NOUN}\b[^.?!\n]{0,40}?\b(machen|generieren|erstellen|erzeugen|zeichnen|malen|make|generate|create|draw)\b`, "i"),
  /^\s*(zeichne|male|mal mir|draw|paint)\b/i,
];
const NOT = /^\s*(wie|warum|wieso|weshalb|was ist|what is|how|why)\b|\b(was ist auf|was siehst du|beschreib|analysier|erkenn|what'?s in|describe|analy[sz]e)\b|\b(code|komponente|component|html|css|svg|website|webseite|landingpage|funktion|function)\b/i;

export function isImageRequest(text: string): boolean {
  const t = (text || "").trim();
  if (!t || t.startsWith("/") || t.startsWith("🎯") || t.length > 600) return false;
  if (NOT.test(t)) return false;
  return STRONG.some((r) => r.test(t));
}
