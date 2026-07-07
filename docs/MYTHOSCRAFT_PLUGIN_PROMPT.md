# Mythos AI Minecraft Plugin — Generation Prompt

Kopiere den kompletten Block unten in einen KI-Coder (Claude / GPT / Lovable-agent-mode)
um das fertige **MythosAI.jar** Plugin (Paper/Spigot 1.20+) generieren zu lassen.

---

Du bist ein Senior-Minecraft-Plugin-Entwickler. Baue **MythosAI**, ein Paper/Spigot 1.20.x+
Plugin (Java 17). Ziel: verbindet den Server mit der **Mythos AI Website** und bringt
dem Spieler alle Web-Funktionen ingame.

## Config (`plugins/MythosAI/config.yml`)

```yaml
website: "https://ai-mythoscraft.lovable.app"   # Basis-URL der Mythos AI Website
api_key: "sk-mc-DEIN-KEY-HIER"                  # aus dem Web-Admin -> Minecraft
default_key: "191306"                            # optional shortcut — Plugin akzeptiert diesen "master" Key
chat_trigger: "!ai"                              # optional zusätzlicher Chat-Trigger
enable_events: true                              # join/death Events an Website senden
enable_ingame_chat: true                         # !ai im Public-Chat erlauben
```

**WICHTIG**: Wenn `api_key` leer oder gleich `191306` ist, benutze den intern
hinterlegten Shared-Key `191306` als Bearer-Token. Alle Requests gehen an
`{website}/functions/v1/mc-bridge` mit Header `Authorization: Bearer <key>`.

## Commands

| Command | Beschreibung |
|---|---|
| `/ai <text>` | Chattet mit Mythos AI. Erster Aufruf ohne Link -> Verifizierungs-Code, den man auf der Website in *Profil -> Minecraft verknüpfen* eingibt. |
| `/ai link` | Zeigt den aktuellen Link-Status + Code nochmal an. |
| `/ai unlink` | Entkoppelt den Account (Bestätigung nötig). |
| `/ai stats` | Öffnet `{website}/plugin?uuid={UUID}` als klickbaren Chat-Link — dort sieht der Spieler alle Stats. |
| `/pay <player> <betrag>` | Wrapper für Vault-Economy `pay`. Loggt Transaktion an Website. |
| `/mythos` | Alias-Root: `/mythos help`, `/mythos reload`, `/mythos status`. |

## HTTP-Contract (POST JSON zu `{website}/functions/v1/mc-bridge`)

Alle Bodies enthalten `action`. Response ist immer JSON, oft mit `reply` (String für den Chat).

- `{"action":"ping"}` — Health-Check beim Enable + alle 5 Min.
- `{"action":"ai","player":"Steve","uuid":"<mc-uuid>","message":"Wer bist du?"}` — /ai Command.
  - Antwort: `{reply: "..."}`. Ersten Kontakt liefert Website automatisch den 6-stelligen Verifizierungs-Code.
- `{"action":"event","type":"join|leave|death","player":"Steve","content":"Fell into lava"}`
- `{"action":"chat","player":"Steve","message":"!ai wie ist das wetter"}` (nur wenn `enable_ingame_chat`).

## Verhaltensregeln

- Async! Nie im Main-Thread auf HTTP warten. Nutze `Bukkit.getScheduler().runTaskAsynchronously`.
- Cache Link-Status pro UUID für 5 Min, aber lösche Cache bei `/ai unlink` und `join`.
- Bei HTTP `429` (Rate-Limit) zeige `§eDie AI ist gerade beschäftigt, versuch es in 5s nochmal.` und retrye maximal 2x mit exponential backoff (1s, 3s).
- Bei HTTP `402` (Credits leer) zeige `§cKeine AI-Credits übrig — sag es dem Admin.`.
- **Nie den API-Key im Chat oder Log zeigen.**
- Farben: `§b[MythosAI]§r` als Prefix. AI-Antworten als `§7» §f<text>`.
- Beim ersten Join zeige eine 1-Zeilen-Toast: `§eTipp: schreib /ai um mit Mythos AI zu chatten.`

## Website-Deep-Links

Der `stats`-Command postet einen JSON-Clickable-Text (`ClickEvent.OPEN_URL`) mit:
`{website}/plugin?mc={UUID}&from=ingame` — die Website erkennt den Query-Parameter,
matched ihn gegen `mc_players` und zeigt dann Profil + verfügbare Commands
(darunter `/pay`, `/tpa`, `/home` usw. als Buttons, die per HTTP zurück in den Server callen).

## Deliverables

1. Vollständiger Maven-Projekt (`pom.xml` mit `shade` für JSON-Lib).
2. `src/main/java/online/mythoscraft/ai/*` mit Klassen: `MythosAIPlugin`, `Config`, `Api`,
   `AiCommand`, `PayCommand`, `MythosCommand`, `PlayerListener`, `LinkCache`.
3. `plugin.yml` mit allen Commands + Permissions (`mythosai.use` default true, `mythosai.admin` op).
4. `README.md` mit Install- und Config-Anleitung.
5. Build-Script/GH-Action optional.

Am Ende: baue die `.jar` und liefere sie als Download aus. Kein Placeholder-Code.
