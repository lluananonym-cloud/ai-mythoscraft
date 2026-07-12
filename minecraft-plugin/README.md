# MythosAI Minecraft Plugin

Paper/Spigot 1.20.x+ Plugin (Java 17). Verbindet den Server mit **ai-mythoscraft.lovable.app**.

## Fertige .jar bekommen (ohne selber bauen)

Bei jedem Push auf `main` baut die GitHub Action automatisch die `MythosAI.jar` und
lädt sie unter **Releases → latest → Assets** hoch. Einfach runterladen und ins
`plugins/` Verzeichnis deines Servers kopieren.

## Selber bauen

```bash
cd minecraft-plugin
mvn clean package
# -> target/MythosAI.jar
```

## Installation

1. `MythosAI.jar` nach `plugins/` kopieren
2. Server starten (erstellt `plugins/MythosAI/config.yml`)
3. Config prüfen — der Default-Key `191306` funktioniert sofort (Shared Server).
   Für einen eigenen Server-Slot: Web-Dashboard → Minecraft → Key erstellen und in die config eintragen.
4. `/reload` oder Server neu starten.

## Commands

| Command | Was passiert |
|---|---|
| `/ai <text>` | Chat mit Mythos AI |
| `/ai link` | Zeigt 6-stelligen Verifizierungscode zum Verknüpfen |
| `/ai unlink` | Trennt den Account |
| `/ai stats` | Link zur Website mit deinem Profil |
| `/mythos reload` | Config neu laden (op) |
| `/mythos status` | Verbindungscheck (op) |

Im Chat kann außerdem `!ai <frage>` geschrieben werden (deaktivierbar in der Config).
