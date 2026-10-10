// Abgeschnittene Code-Antworten erkennen und nahtlos fortsetzen lassen (Chat und App-Builder).

/** Wie oft eine abgeschnittene Code-Antwort automatisch fortgesetzt wird. */
export const MAX_CONTINUE = 3;

/** Sieht die Antwort nach mittendrin abgebrochenem Code aus? */
export function looksCut(text: string): boolean {
  if (!text) return false;
  const fences = (text.match(/^\s*```/gm) || []).length;
  if (fences % 2 === 1) return true;
  return /<html[\s>]/i.test(text) && !/<\/html>/i.test(text);
}

/** Fortsetzung sauber anhängen: ein neu geöffneter ```-Block innerhalb eines offenen Blocks wird entfernt. */
export function stitch(before: string, cont: string): string {
  const open = ((before.match(/^\s*```/gm) || []).length) % 2 === 1;
  return open ? cont.replace(/^\s*```[\w+#.-]*[^\n]*\n/, "") : cont;
}

export const CONTINUE_PROMPT =
  "Deine Antwort wurde abgeschnitten. Schreib genau ab dem letzten Zeichen weiter: keine Einleitung, nichts wiederholen, keinen neuen ```-Block öffnen. Schließ am Ende alle offenen Blöcke und Tags.";
