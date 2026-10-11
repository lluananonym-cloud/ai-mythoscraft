// Server-Wissen über MythosCraft (mythoscraft.online), erstellt aus dem Server-Plugin
// MythosCraft.jar v1.0.0 (plugin.yml, config.yml, crates.yml, messages.yml und Klassen).
// Reines TypeScript ohne Deno-Imports: wird von Edge Functions UND der Website (Admin) genutzt.
// Bei einem neuen Plugin-Stand diese Datei aktualisieren und SERVER_KNOWLEDGE_VERSION erhöhen.

export const SERVER_KNOWLEDGE_VERSION = "MythosCraft-Plugin 1.0.0";

/** Kategorie-Präfix der Artikel, die aus dem Plugin stammen (auch in knowledge_articles). */
export const PLUGIN_CATEGORY_PREFIX = "plugin-";

export type ServerArticle = { title: string; category: string; body: string; keywords: string[] };

export const SERVER_KNOWLEDGE: ServerArticle[] = [
  {
    title: "Überblick & Verbinden",
    category: "plugin-allgemein",
    keywords: ["server", "ip", "verbinden", "join", "version", "mythoscraft", "adresse", "spielen", "resource", "pack", "texture"],
    body: `- Server-Adresse: **mythoscraft.online** (Minecraft Java Edition, Plugin für Version 1.21).
- Das eigene Plugin **MythosCraft Core** bringt: Spawn, Homes, Teleport-Anfragen, Coins & Gems, Tagesbelohnung, Ränge, automatische Events (inkl. Boss Ancient Warden), Kisten (Crates), Custom Tools, Arenen, Regionsschutz, Statistiken & Bestenlisten.
- Zeitzone für Tageswechsel, Daily und Events: Europe/Berlin.
- **Resource Pack**: wird beim Beitritt angeboten (nicht Pflicht). Es zeigt Rang-Abzeichen, 3D-Kisten, Tool-Modelle und Icons. Wer ablehnt, sieht Ränge als Text und Items klassisch. Nachträglich aktivieren: in der Serverliste den Server bearbeiten → Server-Ressourcenpakete „Aktiviert".
- Scoreboard rechts zeigt Rang, Coins, Gems, Kills, Tode, Spielzeit, laufendes Event und aktiven Coin-Boost. Tab-Liste zeigt Online-Zahl, Event, Ping und TPS.
- Hilfe vom Team: \`/helpop <Nachricht>\` (30 s Abklingzeit; ist niemand online, wird die Nachricht gespeichert).`,
  },
  {
    title: "Alle Spieler-Befehle",
    category: "plugin-befehle",
    keywords: ["befehl", "befehle", "command", "commands", "hilfe", "help", "/"],
    body: `Diese Befehle hat jeder Spieler:
- \`/spawn\` – zum Spawn (3 s Wartezeit, nicht bewegen)
- \`/home [Name]\`, \`/sethome [Name]\`, \`/delhome <Name>\`, \`/homes\` – Homes (Liste ist anklickbar)
- \`/tpa <Spieler>\` – du willst zu jemandem; \`/tpahere <Spieler>\` – jemand soll zu dir
- \`/tpaccept [Spieler]\`, \`/tpdeny [Spieler]\`, \`/tpacancel\` – Anfragen annehmen, ablehnen, zurückziehen (oder auf [ANNEHMEN]/[ABLEHNEN] im Chat klicken)
- \`/balance [Spieler]\` (auch \`/bal\`, \`/money\`), \`/coins\`, \`/gems\` – Kontostand
- \`/pay <Spieler> <Betrag> [coins|gems]\` – Coins überweisen (z. B. \`/pay Max 2,5k\`)
- \`/daily\` – Tagesbelohnung
- \`/stats [Spieler]\` – Statistiken; \`/top <Statistik>\` – Top 10
- \`/event\` – Infos zum laufenden Event (oder wann das nächste kommt)
- \`/arena list|join <Arena>|spectate <Arena>|leave\` – Arenen
- \`/rank info\`, \`/rank list\` – Rang ansehen und alle Ränge auflisten
- \`/helpop <Nachricht>\` – Nachricht ans Team
- \`/mythos help\` (auch \`/mc\`, \`/mythoscraft\`) – Befehlsübersicht
Teamler-Befehle (\`/mythos region|arena|event|eco|crate|item|boost|holo|reload|status|bypass\`, \`/setspawn\`, \`/rank set/remove\`) sind nur fürs Team.`,
  },
  {
    title: "Spawn, Homes & Teleport",
    category: "plugin-teleport",
    keywords: ["spawn", "home", "homes", "sethome", "delhome", "tpa", "tpahere", "tpaccept", "tpdeny", "teleport", "tp", "kampf", "combat"],
    body: `- **Spawn**: \`/spawn\` mit 3 s Countdown, abgebrochen bei Bewegung oder Schaden. Neue Spieler starten am Spawn, nach dem Tod respawnt man dort.
- **Spawn-Schutz** (Radius 64 Blöcke): kein PvP, kein Bauen/Abbauen, keine Kisten öffnen, keine Explosionen/Feuer, kein Schaden, kein Hunger, keine Mob-Spawns. Türen und Schalter gehen, Items droppen geht.
- **Homes**: \`/sethome [Name]\` (Name: Buchstaben, Zahlen, _ und -, max. 16 Zeichen). Anzahl hängt vom Rang ab: Spieler 3, VIP 5, ELITE 8, MYTHOS 15. 3 s Wartezeit, 5 s Abklingzeit. In Arenen kann man kein Home setzen.
- **TPA**: Anfrage läuft nach 60 s ab, 10 s Abklingzeit, max. 5 offene Anfragen pro Ziel, 3 s Wartezeit. Teleports in Arenen sind verboten.
- **Kampf-Sperre**: 10 s nach PvP-Schaden sind \`/home\`, \`/tpa\` und \`/arena join\` gesperrt.`,
  },
  {
    title: "Coins, Gems & Überweisen",
    category: "plugin-economy",
    keywords: ["coins", "coin", "gems", "gem", "geld", "money", "balance", "bal", "pay", "überweisen", "economy", "währung", "verdienen", "reich", "farmen", "mob", "kill", "spielzeit", "multiplikator", "booster", "boost"],
    body: `- Zwei Währungen: **Coins** (Start: 100) und **Gems** (selten). Max. Kontostand 1 Billion.
- **Überweisen**: \`/pay <Spieler> <Betrag>\` – Beträge wie 500, 1k, 2,5k. Max. 1 Mrd. pro Überweisung, 3 s Abklingzeit. **Gems sind nicht überweisbar.**
- **Coins verdienen**:
  - Mobs töten: 1–3 Coins (Mobs aus Spawnern geben nichts). Sonderbelohnungen: Wither 250, Enderdrache 500, Elder Guardian 75, Warden 100.
  - Spieler töten: 15 Coins (nicht nochmal für dasselbe Opfer innerhalb 10 min). Killstreaks von 5, 10, 15, 25 werden angesagt.
  - Aktive Spielzeit: alle 30 min (nicht AFK) 50 Coins.
  - \`/daily\`, Events, Kisten.
- **Gems** gibt es über Daily-Streak, Events (v. a. GemRush, Ancient Hunt, Ancient Warden) und Kisten.
- **Rang-Multiplikator** auf Coins/Gems (siehe Ränge).
- **Tages-Multiplikatoren**: Mi Events x1,25 · Fr Coins x1,25 · Sa Coins x1,5, Gems x1,25, Events x1,25 · So Coins x1,5, Gems x1,25, Events x1,5. Mo, Di, Do normal.
- Das Team kann zusätzlich zeitlich begrenzte **Booster** starten (Coins, Gems oder Events, angezeigt im Chat und Scoreboard).`,
  },
  {
    title: "Tagesbelohnung (/daily)",
    category: "plugin-economy",
    keywords: ["daily", "tagesbelohnung", "täglich", "streak", "belohnung"],
    body: `- \`/daily\` einmal pro Tag (Tageswechsel Europe/Berlin).
- 100 Coins + 25 Coins pro Streak-Tag (der Streak-Bonus wächst bis 7 Tage) + Rang-Bonus (VIP +50, ELITE +100, MYTHOS +250).
- Alle 7 Streak-Tage zusätzlich **3 Gems**.
- Streak = Tage am Stück. Wartet die Belohnung, gibt es eine Erinnerung im Chat.`,
  },
  {
    title: "Ränge",
    category: "plugin-raenge",
    keywords: ["rang", "ränge", "rank", "ranks", "vip", "elite", "mythos-rang", "mythos rang", "team", "prefix", "badge", "minepro641", "owner", "besitzer", "farbe", "chatfarbe"],
    body: `| Rang | Homes | Coins | Gems | Daily-Bonus | Extra |
|---|---|---|---|---|---|
| Spieler (Standard) | 3 | x1,0 | x1,0 | – | – |
| **VIP** | 5 | x1,1 | x1,0 | +50 | – |
| **ELITE** | 8 | x1,25 | x1,1 | +100 | Farbcodes (&) im Chat |
| **MYTHOS** | 15 | x1,5 | x1,5 | +250 | Farbcodes im Chat, Ansage mit Titel beim Join |
- Ränge vergibt nur das Team mit \`/rank set <Spieler> <Rang> [Dauer]\`, auch zeitlich begrenzt (z. B. 7d, 30d, 12h oder perm). Läuft ein Rang ab, fällt man auf Spieler zurück. \`/rank info\` zeigt die Restzeit.
- **MYTHOS** ist der höchste Rang und gehört **MinePro641**. Nur MinePro641 kann MYTHOS vergeben oder entfernen, sein eigener Rang ist fest.
- Teammitglieder tragen zusätzlich das rote **TEAM**-Abzeichen.
- Mit Resource Pack erscheinen Ränge als Grafik-Abzeichen (vip, elite, mythos) im Chat, in der Tab-Liste und über dem Kopf.
- Preise oder Kaufmöglichkeiten für Ränge stehen nicht im Plugin – im Zweifel aufs Team/Discord verweisen.`,
  },
  {
    title: "Events (automatisch)",
    category: "plugin-events",
    keywords: ["event", "events", "coinrain", "coin rain", "gemrush", "gem rush", "ancienthunt", "ancient hunt", "relikt", "relikte", "meteor", "meteorschauer", "meteorkern", "bossbar", "punkte"],
    body: `- Alle **45 min** startet automatisch ein zufälliges Event, wenn mind. 2 Spieler online sind. Vorher 30 s Countdown mit Bossbar. \`/event\` zeigt Stand, Platzierung oder wann das nächste kommt.
- Event-Punkte für die Statistik: Platz 1/2/3/4/5+ = 10/7/5/3/1.
- **CoinRain** (90 s): Münzen regnen um die Spieler, einsammeln gibt 5–25 Coins, 3 % goldene Münze mit x10. Preise: 1. 300 Coins + Gewöhnlicher Schlüssel, 2. 150, 3. 75 Coins.
- **GemRush** (5 min): Erze abbauen und feindliche Mobs töten gibt Punkte (Kohle/Kupfer/Quarz 1, Eisen/Redstone 2, Gold/Lapis 3, Amethyst 4, Diamant 10, Smaragd 15, Antiker Schrott 25, Mob 2). 8 % Chance auf Gem-Splitter. Am Ende 20 Punkte = 1 Gem (max. 25). Preise: 1. 15 Gems + Elite-Schlüssel, 2. 10, 3. 5 Gems; ab 5 Punkten 50 Coins.
- **Ancient Hunt** (10 min): 8 uralte Relikte liegen 40–250 Blöcke um den Spawn, Lichtsäulen und Hinweise ab 60 Blöcken Nähe helfen (Relikt-Kompass zeigt aufs nächste). Pro Relikt 100 Coins. Preise: 1. 750 Coins + 10 Gems + Mythos-Schlüssel, 2. 400 + 5 Gems, 3. 200 + 3 Gems; Teilnahme 50 Coins.
- **Meteorschauer** (5 min): 3 Meteore, Ansage 15 s vorher mit Koordinaten. Am Meteorkern Rechtsklick (3 Spieler können plündern): 150 Coins + 2 Gems, 8 % Chance auf Sternenfall-Stab oder Mythos-Schlüssel. Blöcke werden nur außerhalb geschützter Regionen zerstört. Preise: 1. 400 Coins + VIP-Schlüssel, 2. 200, 3. 100.
- **Ancient Warden**: siehe eigener Artikel.`,
  },
  {
    title: "Boss: Ancient Warden",
    category: "plugin-events",
    keywords: ["warden", "ancient warden", "boss", "echo", "klinge", "wächter"],
    body: `- Selteneres Event (mind. 3 Spieler, höchstens alle 2 h), erscheint 16–32 Blöcke neben einem zufälligen Spieler. Koordinaten werden angesagt.
- 1500 Leben, 18 Schaden, Rüstung 12, größer als ein normaler Warden. Fähigkeiten alle 8 s (Schockwelle, Stampfer, Dunkelheit), unter 30 % Leben **wütend** (alle 5 s). Ruft „Echo"-Diener (3 pro Welle, max. 6).
- Max. 15 min Kampf. Sind 3 min keine Spieler in der Nähe, verschwindet er ohne Belohnung.
- Belohnung nach Schaden: 1. 500 Coins + 10 Gems + Uralter Schlüssel, 2. 300 + 5 Gems + Elite-Schlüssel, 3. 150 + 3 Gems. Teilnahme (ab 25 Schaden): 50 Coins + 1 Gem. Dazu 500 XP und 35 % Chance auf die **Klinge des Wächters**.
- Boss-Schaden zählt als eigene Statistik (\`/top bossdamage\`).`,
  },
  {
    title: "Kisten (Crates) & Schlüssel",
    category: "plugin-kisten",
    keywords: ["kiste", "crate", "crates", "schlüssel", "key", "keys", "lootbox", "jackpot", "seltenheit", "chance", "öffnen"],
    body: `- Kisten stehen am Spawn. **Rechtsklick** mit passendem Schlüssel = öffnen, **Linksklick** = Vorschau mit Chancen, **Schleichen + Rechtsklick** = alle Schlüssel in der Hand auf einmal öffnen.
- Schlüssel bekommt man aus Events, anderen Kisten oder vom Team (z. B. „Key All").
- Seltenheiten: Gewöhnlich, Selten, Episch, Legendär, Mythisch (ab Legendär wird der Gewinn allen angesagt).
- **Gewöhnliche Kiste**: 150 Coins (40 %), 400 Coins (20 %), 1 Gem (15 %), 16 Eisen (15 %), 2 Diamanten (6 %), VIP-Schlüssel (3 %), Relikt-Kompass (1 %).
- **VIP-Kiste**: 500 Coins (~38 %), 1.500 Coins (~16 %), 3 Gems (~16 %), 5 Diamanten (~13 %), Verzauberter Goldener Apfel (~5,5 %), Elite-Schlüssel (~5,5 %), Erdbrecher (~2 %), Weltenbaum-Axt (~2 %).
- **Elite-Kiste**: 1.500 Coins (30 %), 8 Gems (20 %), 1.000 XP (12 %), Netherit-Barren (12 %), Totem (10 %), Weltenbaum-Axt (4 %), Erdbrecher (4 %), Mythos-Schlüssel (5 %), Mythos-Hammer (3 %).
- **Mythos-Kiste**: 5.000 Coins (~29 %), 25 Gems (~23 %), 3 Netherit-Barren (~14 %), Uralter Schlüssel (~9 %), Mythos-Hammer (~9 %), Sternenfall-Stab (~6 %), Elytra (~5 %), Klinge des Wächters (~3 %), **Jackpot 25.000 Coins** (~2 %).
- **Uralte Kiste** (vom Ancient Warden): 3.000 Coins (25 %), 15 Gems (25 %), 8 Echosplitter (15 %), 2 Verz. Goldene Äpfel (10 %), Schallplatte „5" (5 %), Mythos-Schlüssel (8 %), Sternenfall-Stab (6 %), Klinge des Wächters (6 %).
- Geöffnete Kisten zählen als Statistik (\`/top crates\`).`,
  },
  {
    title: "Custom Tools",
    category: "plugin-items",
    keywords: ["tool", "tools", "item", "items", "hammer", "mythos-hammer", "erdbrecher", "excavator", "axt", "timber", "weltenbaum", "klinge", "warden blade", "stab", "sternenfall", "meteor staff", "kompass", "relikt-kompass", "custom"],
    body: `- **Mythos-Hammer** (Netherit-Spitzhacke): baut 3×3 ab (bis Obsidian-Härte). Schleichen = nur ein Block.
- **Erdbrecher** (Netherit-Schaufel): gräbt 3×3 in Erde, Sand, Kies, Schnee usw. Schleichen = nur ein Block.
- **Weltenbaum-Axt** (Netherit-Axt): fällt ganze natürliche Bäume (bis 128 Stämme). Schleichen = nur ein Stamm.
- **Klinge des Wächters** (Netherit-Schwert): Rechtsklick = Schallstrahl, 10 Schaden, 16 Blöcke Reichweite, 8 s Abklingzeit. Drop vom Ancient Warden oder aus Mythos-/Uralter Kiste.
- **Sternenfall-Stab**: Rechtsklick auf einen Block ruft einen Mini-Meteor (8 Schaden, Radius 3,5, Reichweite 40, 15 s Abklingzeit). Respektiert PvP- und Schutzregeln.
- **Relikt-Kompass**: zeigt während der Ancient Hunt auf das nächste Relikt.
- Alle Tools respektieren den Regionsschutz (z. B. kein Abbau am Spawn).`,
  },
  {
    title: "Arenen (PvP)",
    category: "plugin-arena",
    keywords: ["arena", "arenen", "pvp", "kit", "spectate", "zuschauen", "kampf"],
    body: `- \`/arena list\` zeigt alle Arenen mit Status (offen, läuft, geschlossen, wird zurückgesetzt) und Spielerzahl.
- \`/arena join <Arena>\` beitreten, \`/arena spectate <Arena>\` zuschauen, \`/arena leave\` verlassen.
- Je nach Arena bekommt man ein Kit, z. B. **PvP-Kit**: Diamantschwert (Schärfe I), Bogen, 32 Pfeile, 16 Steaks, 2 Goldäpfel, Eisenrüstung, Schild. Das eigene Inventar wird gesichert und beim Verlassen zurückgegeben.
- In Arenen sind die meisten Befehle gesperrt (erlaubt: \`/arena\`, \`/msg\`, \`/r\`, \`/helpop\`). Homes setzen und Teleports hinein sind nicht möglich, und während eines Kampfes geht \`/arena join\` erst nach 10 s.
- Arenen können automatisch zurückgesetzt werden (z. B. wenn sie leer sind).`,
  },
  {
    title: "Statistiken & Bestenlisten",
    category: "plugin-stats",
    keywords: ["stats", "statistik", "statistiken", "top", "bestenliste", "leaderboard", "kd", "k/d", "spielzeit", "playtime", "rangliste"],
    body: `- \`/stats [Spieler]\` zeigt Rang, Coins, Gems, Kills, Tode, K/D, beste Killstreak, Mob-Kills, Spielzeit, Event-Punkte, Event-Siege, Boss-Schaden, Relikte, geöffnete Kisten und Daily-Streak.
- \`/top <Statistik>\` zeigt die Top 10. Statistiken: \`coins\`, \`gems\`, \`kills\`, \`deaths\`, \`playtime\`, \`eventpoints\`, \`eventswon\`, \`bossdamage\`, \`mobkills\`, \`relics\`, \`crates\`, \`streak\`.
- Am Spawn können Hologramm-Bestenlisten stehen.`,
  },
  {
    title: "Regionsschutz & Regeln der Welt",
    category: "plugin-schutz",
    keywords: ["schutz", "region", "regionen", "grief", "griefing", "claim", "grundstück", "wildnis", "bauen", "pvp", "explosion", "feuer"],
    body: `- **Wildnis** (außerhalb aller Regionen): alles erlaubt – PvP, Bauen, Abbauen, Kisten, Explosionen, Feuer, Mobs.
- **Spawn** und vom Team erstellte Regionen sind geschützt („✖ Hier ist das nicht erlaubt"). Neue Regionen verbieten standardmäßig PvP, Bauen, Abbauen, Interagieren, Kisten, Explosionen, Feuer und Tierschaden.
- Es gibt **keine Grundstücke/Claims für Spieler** im Plugin. Regionen erstellt nur das Team (\`/mythos region\`).
- Meteore und Tools zerstören keine Blöcke in geschützten Regionen.
- Chat: Namen-Erwähnungen werden hervorgehoben (mit Ton). Farbcodes im Chat nur ab ELITE.`,
  },
  {
    title: "Team-Befehle (nur für Admins)",
    category: "plugin-admin",
    keywords: ["admin", "team", "op", "permission", "rechte", "reload", "setspawn", "eco", "keyall", "boost", "spawnboss", "region", "holo", "bypass"],
    body: `Nur für das Team (Permissions \`mythos.admin\` bzw. einzelne Rechte). Normalen Spielern nicht als nutzbar anbieten.
- \`/mythos status\`, \`/mythos reload\`, \`/mythos bypass\` (Regionsschutz ignorieren an/aus), \`/setspawn\`
- \`/mythos region wand|pos1|pos2|create <Name> [Prio]|delete|info|list|set <Region> <Flag> <true|false|none>|priority|redefine|flags\` – Flags: pvp, build, break, interact, use, container, explosions, fire, entity-damage, mob-damage, damage, mob-spawning, hunger, item-drop
- \`/mythos arena create|delete|info|list|setspawn|clearspawns|setspectator|enable|disable|set <Arena> <Option> <Wert>|redefine|snapshot|reset\` – Optionen u. a. pvp, build, break, kit, max-players, keep-inventory, respawn-in-arena, reset-on-empty, block-commands, inventory
- \`/mythos event list|start <Event> [Countdown]|stop|info|reload|spawnboss\` – Events: coinrain, gemrush, ancienthunt, meteor, ancientwarden
- \`/mythos eco <give|take|set> <Spieler> <coins|gems> <Betrag>\`
- \`/mythos crate place <Kiste>|remove|list|types|key <Spieler> <Kiste> [Anzahl]|keyall <Kiste> [Anzahl]\`
- \`/mythos item give <Spieler> <Item>\` – mythos_hammer, excavator, timber_axe, warden_blade, meteor_staff, relic_compass, region_wand
- \`/mythos boost <coins|gems|events> <Faktor 0.1-20> <Dauer z.B. 1h>\`, \`/mythos boost stop <Typ>\`
- \`/mythos holo create <Name> <Statistik>\`, \`/mythos holo delete <Name>\`
- \`/rank set <Spieler> <Rang> [Dauer]\`, \`/rank remove <Spieler>\` (MYTHOS nur durch MinePro641)
- Teamler mit \`mythos.helpop.receive\` bekommen \`/helpop\`-Nachrichten.`,
  },
];

const norm = (s: string) => s.toLowerCase();
const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
/** Kurze Keywords (z. B. "ip") nur als ganzes Wort, längere auch als Wortanfang ("homes" in "homes?"). */
const hasWord = (text: string, k: string) =>
  new RegExp(`(^|[^a-zäöüß0-9])${escapeRe(k)}${k.length <= 3 ? "([^a-zäöüß0-9]|$)" : ""}`).test(text);

/** Liegt die Frage beim Thema MythosCraft-Server? (für Chats außerhalb des Support-Modus) */
export function isServerQuestion(text: string): boolean {
  const t = norm(text || "");
  if (!t) return false;
  if (/mythoscraft|mythos\s*craft|minepro641|auf (dem|eurem|deinem) server|minecraft[- ]?server/.test(t)) return true;
  if (/(^|\s)\/(spawn|home|sethome|tpa|tpahere|pay|daily|stats|top|event|arena|rank|helpop|balance|bal|coins|gems|mythos)\b/.test(t)) return true;
  return /(^|[^a-zäöüß])(crates?|kisten?|schlüssel|keys?|gems?|coins|ränge|rang|vip|elite|homes?|daily|tagesbelohnung|killstreak|ancient warden|warden|coinrain|gemrush|ancient ?hunt|meteorschauer|mythos-hammer|erdbrecher|weltenbaum-axt|sternenfall-stab|relikt-kompass|arena|arenen)([^a-zäöüß]|$)/.test(t);
}

/** Artikel nach Relevanz für die Frage (Keyword-Treffer). Ohne Treffer: Überblick + Befehle. */
export function relevantServerArticles(question: string, max = 4): ServerArticle[] {
  const q = norm(question || "");
  const scored = SERVER_KNOWLEDGE.map((a) => {
    let score = 0;
    for (const k of a.keywords) if (hasWord(q, k)) score += k.length > 4 ? 2 : 1;
    if (q.includes(norm(a.title))) score += 3;
    return { a, score };
  }).filter((x) => x.score > 0).sort((x, y) => y.score - x.score).map((x) => x.a);
  if (!scored.length) return SERVER_KNOWLEDGE.slice(0, 2);
  return scored.slice(0, max);
}

export function formatServerArticles(list: ServerArticle[]): string {
  return list.map((a) => `### ${a.title}\n${a.body}`).join("\n\n");
}

/** Komplettes Server-Wissen als Markdown-Block für System-Prompts. */
export function serverKnowledgeText(): string {
  return `## Server-Wissen MythosCraft (aus dem Server-Plugin, ${SERVER_KNOWLEDGE_VERSION})
Dieses Wissen stammt direkt aus dem Plugin und ist verbindlich. Bei Widerspruch zu anderen Quellen gilt es. Erfinde keine Befehle, Preise oder Features, die hier nicht stehen.

${formatServerArticles(SERVER_KNOWLEDGE)}`;
}

/** Suche für Agent-Tools: Artikel, deren Titel/Text/Keywords die Wörter der Anfrage enthalten. */
export function searchServerKnowledge(query: string, max = 5): ServerArticle[] {
  const words = norm(query || "").split(/\s+/).filter((w) => w.length > 1);
  if (!words.length) return [];
  return SERVER_KNOWLEDGE.map((a) => {
    const head = norm(`${a.title} ${a.keywords.join(" ")}`);
    const hay = norm(`${a.category} ${a.body}`);
    const hits = words.reduce((n, w) => n + (head.includes(w) ? 3 : hay.includes(w) ? 1 : 0), 0);
    return { a, hits };
  }).filter((x) => x.hits > 0).sort((x, y) => y.hits - x.hits).slice(0, max).map((x) => x.a);
}
