
Großes Paket – ich teile in 4 Phasen auf, damit nichts wieder bricht. Phase 1 baue ich **jetzt direkt**, Phase 2–4 starten danach automatisch hintereinander (eine Phase pro Antwort, damit ich zwischendurch testen und reparieren kann).

## Phase 1 — Stabilität & UX-Basis (jetzt)

**Ziel:** Keine Abstürze mehr, Privacy-Screen funktioniert wirklich, Logo wie ChatGPT überall.

- **PWA-Stabilität:** Service-Worker auf `NetworkFirst` für HTML umstellen (statt CacheFirst, der die Abstürze/Restarts verursacht), Workbox `cleanupOutdatedCaches`, harte Cache-Limits. Update-Prompt statt Auto-Reload.
- **Privacy-Screen:** Aktuell nutzt `visibilitychange` – iOS feuert das im App-Switcher **nicht zuverlässig**. Ich nutze zusätzlich `pagehide`, `blur`, und vor allem `webkitvisibilitychange` + ein synchroner Overlay-Mount per `useLayoutEffect`. Hintergrund 100% schwarz, Logo zentriert.
- **Login-Screen Logo:** großes Mythos-Logo (ohne Hintergrund) statt Sparkles.
- **„Start im Chat"-Einstellung:** Neue Spalte `profiles.start_in_chat boolean`, Toggle in Profileinstellungen, beim App-Open Redirect.
- **Logo im Chat fixiert** wie ChatGPT (sticky in TopNav, klickbar → /).
- **Skin-Viewer Crash:** WebGL-Context-Loss-Handler + Fallback auf 2D-Avatar, wenn WebGL fehlschlägt.

## Phase 2 — Live-Sprachchat (free, ohne Setup)

**Free heißt:** Browser Web Speech API (STT) + Browser SpeechSynthesis (TTS) + Lovable AI Gemini Flash für Antworten. Kein API-Key nötig.
- Neue Route `/voice` mit großem animiertem Mic-Orb (Canvas, reagiert auf `AudioContext.AnalyserNode` Lautstärke)
- Push-to-talk + Auto-VAD
- Während AI spricht: Orb pulsiert in anderer Farbe
- Interrupt: User-Sprechen stoppt TTS

## Phase 3 — Anhänge, Musik, Video fixen

- **Anhänge:** Upload zu `chat-uploads` Bucket prüfen, MIME-Validierung, im Chat-Verlauf anzeigen.
- **Musik:** Aktuelles Transformers.js-Setup ist instabil im PWA-Modus. Umstellen auf Edge-Function mit Background-Job (`EdgeRuntime.waitUntil`) + Status-Polling. Lovable AI hat keine Musik-Generierung — ich nutze Replicate via API-Key **oder** behalte client-seitig MusicGen mit besserer Fehlerbehandlung. **Frage:** Hast du einen Replicate-Key oder soll ich beim client-seitigen MusicGen bleiben?
- **Video:** Neue Edge-Function mit `videogen` über Lovable Cloud → ist aktuell **nicht in Lovable AI Gateway** verfügbar. Ich integriere Replicate (z.B. Wan2.5) → braucht `REPLICATE_API_TOKEN`. Background-Job mit Status-Polling, Resultat in `chat-uploads`.

## Phase 4 — Pro-Abo & Admin-Geschenke (Paddle)

- `recommend_payment_provider` → `enable_paddle_payments`
- Tabelle `subscriptions` (user_id, tier, source: `paid`/`gift`/`admin`, expires_at)
- **Free-Limits:** 20 Chats/Tag, kein Voice, kein Bild/Musik/Video
- **Pro-Features:** Unlimited Chat, Voice, alle Generierungen
- **Admin-UI:** `/admin` Seite – Nutzer suchen, "Pro für X Tage verschenken" Button
- Paywall-Modal bei Limit-Überschreitung
- Webhook für Paddle-Events

---

## Was ich von dir brauche (vor Phase 3):

1. **Replicate-API-Key** für Video (und optional Musik). Falls du keinen hast: Video fällt weg, Musik bleibt client-seitig.
2. **Bestätigung Paddle:** Beim Enable musst du im Formular Email/Name eingeben – das geht nicht über mich.

## Reihenfolge im Ausführen

Ich starte **jetzt mit Phase 1** und melde mich danach für Phase 2. Sag Bescheid wenn du eine andere Reihenfolge willst oder Phase 3 (Replicate-Key) jetzt schon klären willst.
