# MythosCraft Core

Eigenständiges Server-Plugin für **mythoscraft.online**. Ohne Website, externe API oder KI.
Paper **1.21.11** (läuft auch auf neueren Paper-Versionen), **Java 21**, Maven, SQLite oder MySQL.

> Paper 1.21+ braucht Java 21. Deshalb wird mit Java 21 gebaut (die Spezifikation nannte Java 17).

## Installation

1. `MythosCraft.jar` in den Ordner `plugins/` legen und den Server starten.
2. **Spawn bauen:** Auf einer freien Fläche `/spawn place` und danach `/spawn place confirm`. Das Plugin baut eine
   schwebende Spawn-Insel (Radius 46) mit Obelisk, Brunnen, Kristallring, Toren, Kirschgarten und 9 Kisten-Sockeln.
   Danach sind Spawnpunkt, Schutzregion `spawn`, alle 9 Kisten und 3 Hologramm-Bestenlisten automatisch gesetzt.
   Wer einen eigenen Spawn hat, nimmt einfach `/setspawn`.
3. **Turnier-Arenen bauen:** `/mythos arena place daily`, `… weekly`, `… monthly` (jeweils mit `confirm`). Baut ein
   Kolosseum (Durchmesser 35/63/123), setzt Spawns, Zuschauerplatz, Snapshot und trägt die Arena beim Turnier ein.
4. **Resource Pack:** In der `config.yml` steht die mc-packs.net-URL. Nach jedem Update des Packs die neue Datei
   `plugins/MythosCraft/MythosCraft-ResourcePack.zip` (liegt auch neben der JAR in `target/`) auf mc-packs.net hochladen
   und die neue URL eintragen. Der SHA-1 in der URL wird automatisch als Hash verwendet.
   *Alternative:* `resource-pack.self-host.enabled: true`. Dann liefert das Plugin das Pack über **Port 8765** selbst aus.
5. Rechte nur über Permissions vergeben (z.B. mit LuckPerms). `mythos.admin` enthält alle Admin-Rechte.

## Der MYTHOS-Rang

* **MinePro641** hat den Rang MYTHOS immer. Erkannt wird er über seine Mojang-UUID
  `9f799033-c0f0-4d2a-aa6f-b68ce9207af4`, nicht über den Namen.
* **Nur MinePro641** kann MYTHOS per `/rank set <Spieler> MYTHOS` vergeben oder wegnehmen. Das gilt auch gegenüber
  Admins und der Konsole. Seinen eigenen Rang kann niemand ändern.
* Im Offline-Mode (ohne Proxy-Weiterleitung) sind Namen fälschbar. Das Plugin warnt beim Start im Log.

## Funktionen

| Bereich | Inhalt |
|---|---|
| Spawn | `/spawn` mit Countdown, Abbruch bei Bewegung oder Schaden, Respawn am Spawn, Teleport beim ersten Join, alle Spawn-Regeln in der `config.yml` |
| Regionen | Eigene Regionen mit 14 Flags und Prioritäten, schneller Raster-Index, Regionsstab, Partikel-Vorschau der Auswahl |
| Schutz | Bauen/Abbauen, Interaktionen (Kisten, Türen, Schalter, Rahmen, Rüstungsständer, Villager …), PvP auch indirekt (Pfeile, Tränke, TNT, Haustiere, Angel), Explosionen auch von außerhalb, Feuer, Lava/Wasser über Regionsgrenzen, Kolben, Dispenser, Endermen, fallender Sand, Mob-Spawning, Hunger |
| Arenen | Eigene Regionen, Spawnpunkte, Zuschauer, Zustände DISABLED/WAITING/RUNNING/RESETTING, Kits, getrenntes Inventar (absturzsicher in der DB), KeepInventory, Befehlssperre, Snapshot und Reset ohne Lag |
| Homes und TPA | Mehrere Homes (Anzahl je Rang), klickbare Liste, `/tpa` und `/tpahere` mit Buttons, Ablaufzeit, Cooldown, Kampf-Sperre, keine Teleports in Arenen |
| Economy | Coins und Gems, `/pay` (auch `1k`, `2,5k`), atomare Überweisungen in DB-Transaktion mit Protokoll, Admin-Befehle auch für Offline-Spieler |
| Belohnungen | `/daily` mit Streak, Coins für Mob-Kills (keine Spawner-Farmen) und Spieler-Kills (Anti-Farm), Spielzeit-Belohnung nur für aktive Spieler, Killstreak-Meldungen |
| Multiplikatoren | Pro Wochentag (Europe/Berlin), Booster per `/mythos boost`, Rang-Boni |
| Ränge | SPIELER, VIP, ELITE, MYTHOS mit Weight, Homes, Multiplikatoren, Daily-Bonus, Rechten und Ablaufdatum |
| Anzeige | Rang-Badges im Chat, in der Tabliste und über dem Kopf, Sidebar-Scoreboard mit Icons, Tab-Header mit Logo, Chat mit Hover-Infos und Erwähnungen. Spieler ohne Pack sehen farbigen Text statt Grafik |
| Events | 16 Events: CoinRain, GemRush, AncientHunt, Meteorschauer, Ancient Warden, Monsterjagd, Angelwettbewerb, Erntefest und die Saison-Events Kürbisjagd, Geisternacht, Boss Kürbiskönig (Halloween), Geschenkeregen, Schneeballschlacht, Boss Frostriese (Frostfest), Sternschnuppen (Sternenregen), Ostereiersuche (Frühling). Saison-Events starten automatisch nur in ihrer Saison. Mit Countdown, Bossbar, Titeln, Sounds, Ranglisten, Belohnungen, Cooldowns, Auto-Events (Intervall oder feste Uhrzeiten) |
| Ancient Warden | Eigener HP-Pool (Standard 1500), Wut-Phase, Schallwelle, Dunkelheit, Bodenstampfer, Echo-Diener, exaktes Damage-Ranking, Klinge als Beute |
| Crates | 13 Kisten (dazu Bauern-, Sturm-, Drachen- und Rang-Kiste). Kisten können auch Rang-Gutscheine und Verzauberungsbücher enthalten. Bisher: 9 Kisten mit 3D-Modell, schwebendem Deckel beim Öffnen, Walzen-Animation, Vorschau mit Chancen, Massen-Öffnen, Broadcast bei seltenen Gewinnen. Die 4 Kollektions-Kisten (Schauernacht, Frostfest, Sternen, Inferno) sind animiert und haben eigene 3D-Deko (Kürbisstiel, Schneehaube mit Eiszapfen, schwebender Sternenring, Hörner) |
| Custom Items | 43 Items in 6 Kollektionen (Core, Ancient, Schauernacht/Halloween, Frostfest, Sternen, Inferno) plus Trophäen: 3D-Waffen und -Stäbe, Bögen mit eigenen Spann-Stufen, Enterhaken, Rucksack (27 Slots, im Item gespeichert), Heiltotem, Magnet- und Laternen-Talisman, Phönixfeder (rettet vor dem Tod), animierte Texturen (Sternensäbel, Inferno-Axt, Frost-Klinge, Geisterlaterne, Nebula-Kugel …), Essen mit Effekten. Alle Fähigkeiten halten sich an Schutz- und PvP-Regeln |
| Rüstungen | 32 Teile in 8 Sets mit eigener Rolle: Mythos (Allrounder), Kürbiskönig und Sturm (leicht und schnell), Frostwächter (Verteidiger), Inferno (Angreifer), Wächter (schwerer Panzer), Drache (Berserker), Engel (Extra-Herzen, kein Fallschaden). Jedes Set hat eigene Werte, Textur am Körper und Set-Bonus |
| Weitere Items | Insgesamt über 80 Items, u. a. Erntesichel, Gießkanne, Saatbeutel, Tierfänger, Glücksangel, Donnerhammer, Windklinge, Sturm- und Enderbogen, Bumerang, Drachenklinge, Drachenstab, Schmelz- und Erzader-Spitzhacke, Baumeister-Stab, Schatzkarte, Rauch- und Frostbombe, Beutel, Schlüssel-Splitter, Elixiere. Der Hexenbesen ist reitbar (Steuerung mit Blick und Tasten, Tricks mit Combo-Punkten). Jedes Item steckt in mindestens einer Kiste |
| Ränge | SPIELER, VIP, VIP+, ELITE, HELD, LEGENDE, TITAN, MYTHOS plus Team-Ränge SUPPORTER, BUILDER, MOD, ADMIN, OWNER, jeweils mit eigenem Abzeichen. MOD/ADMIN/OWNER (owner-only) und MYTHOS vergibt nur MinePro641. Rang-Gutscheine sind handelbar und gibt es im Shop und in der Rang-Kiste |
| RTP | `/rtp [Welt]` (`/wild`): sichere Zufallsorte außerhalb von Regionen, Wartezeit je Rang, Reiserolle ohne Wartezeit |
| Befehls-Hologramme | Klickbare Buttons für Shop, Auktionshaus, Aufträge, RTP, Turnier, Event, Tagesbelohnung und Stats, automatisch am Spawn. Eigene mit `/mythos holo button <Name> <Befehl>` |
| Saisons | Halloween (15.10.–5.11.), Frostfest (Dezember), Sternenregen (10.–20.08.): Monster droppen Saison-Items und selten Saison-Schlüssel, Anzeige im Scoreboard |
| Turniere | Täglich (5 Plätze), wöchentlich (20), monatlich (100). Die ersten mit `/tournament join` sind dabei. Zufälliges Kit (UHC, Crystal, Sword, Axe, Netherite, Archer, Mace, Potion), eigenes Inventar wird bis zum Tod oder Sieg zwischengespeichert (absturzsicher). Die Arena kann nicht beschädigt werden: nur selbst gesetzte Blöcke sind abbaubar, alles wird nach dem Turnier zurückgesetzt. Belohnungen und Trophäe für die Top 3 |
| Gem-Shop | `/shop`: Kisten-Schlüssel, Spar-Pakete und Extras für Gems (oder Coins). Angebote, Preise, Plätze und Saison-Angebote in der `config.yml` (`shop:`) |
| Auktionshaus | `/ah`: Item in der Hand für Coins oder Gems verkaufen, Angebote durchsuchen und sortieren, Kauf mit Bestätigung, Verkäufer bekommt das Geld auch offline (5 % Steuer), abgelaufene Items abholen, Limit je Rang, Team kann Angebote entfernen. Kauf ist atomar, ein Item kann nie doppelt verkauft werden |
| Farmaufträge | `/auftrag`: Spieler bestellen z. B. 640 Weizen für 2 Coins pro Stück. Die Coins werden vorab hinterlegt, andere liefern per Klick und werden sofort pro Stück bezahlt, der Auftraggeber holt die Items ab. Abbrechen oder Ablauf erstattet den Rest |
| Bestenlisten | `/top <stat>` (12 Statistiken), schwebende Hologramm-Bestenlisten |
| Sonstiges | `/helpop` mit klickbarem Namen und Log-Datei, `/stats`, Willkommensnachricht mit Spielernummer, Status-Übersicht, Config-Prüfung |

## Befehle

**Spieler:** `/spawn` · `/home [Name]` · `/sethome [Name]` · `/delhome <Name>` · `/homes` · `/tpa <Spieler>` ·
`/tpahere <Spieler>` · `/tpaccept [Spieler]` · `/tpdeny [Spieler]` · `/tpacancel` · `/balance` (`/bal`, `/money`) ·
`/coins` · `/gems` · `/pay <Spieler> <Betrag> [coins|gems]` · `/daily` · `/stats [Spieler]` · `/top <Statistik>` ·
`/event` · `/arena <join|spectate|leave|list>` · `/tournament <join|leave|info>` (`/turnier`) · `/rank info|list` ·
`/shop` · `/ah [sell <Preis> [coins|gems] | meine | abholen | suche <Text>]` ·
`/auftrag [erstellen <Item|hand> <Menge> <Preis pro Stück> | meine]` · `/rtp [Welt]` · `/helpop <Nachricht>`

**Admin (`/mythos …`, Kurzform `/mc`):**

| Befehl | Zweck |
|---|---|
| `help`, `status`, `reload`, `bypass` | Hilfe, Systemstatus, Neu laden ohne doppelte Listener oder Tasks, Schutz-Bypass an/aus |
| `region pos1/pos2/wand/create/delete/info/list/set/priority/redefine/flags/reload` | Regionen. `set [Region] <Flag> <true\|false\|none>` |
| `arena create/delete/info/list/pos1/pos2/setspawn/clearspawns/setspectator/enable/disable/set/redefine/snapshot/reset` | Arenen |
| `arena place <daily\|weekly\|monthly> [Name] [confirm]` | Turnier-Arena an der eigenen Position bauen |
| `tournament start <Stufe> [Sekunden]` | Anmeldung sofort öffnen (sonst nach Zeitplan) |
| `tournament test <Stufe> [Bots] [Sekunden]` | Testturnier mit kämpfenden Bots, ohne Belohnungen. Man kann selbst mitkämpfen |
| `tournament stop`, `tournament info`, `tournament setarena <Stufe> <Arena>` | Turniere verwalten |
| `event list/start <id> [Countdown]/stop/info/reload/spawnboss` | Events. IDs: `coinrain gemrush ancienthunt meteor ancientwarden` |
| `eco give/take/set <Spieler> <coins\|gems> <Betrag>` | Economy |
| `crate place <Typ>/remove/list/types/key <Spieler> <Typ> [Anz]/keyall <Typ> [Anz]` | Kisten. Typen: `common vip elite mythos ancient halloween frostfest celestial inferno` |
| `item give <Spieler> <Item> [Anzahl]` | Custom Items und Rüstungen (Tab-Vervollständigung zeigt alle IDs) |
| `boost <coins\|gems\|events> <Faktor> <Dauer>`, `boost stop <Typ>`, `boost info` | Booster |
| `holo create <Name> <Statistik>/delete/list` | Hologramm-Bestenlisten |
| `top <Statistik>` | Bestenliste |

`/setspawn`, `/spawn place [confirm]` und `/rank set|remove <Spieler> [Rang] [Dauer]` sind eigene Befehle.

## Permissions (Auszug)

`mythos.admin` (alles) · `mythos.bypass` · `mythos.spawn.set` · `mythos.region.*` · `mythos.arena.*` ·
`mythos.events.*` bzw. `mythosevents.admin|start|stop|reload|spawnboss` · `mythos.economy.admin` · `mythos.rank.admin` ·
`mythos.crate.admin` · `mythos.tournament.admin` · `mythos.auction.admin` · `mythos.orders.admin` ·
`mythos.auction.unlimited` · `mythos.orders.unlimited` · `mythos.item.give` · `mythos.boost` · `mythos.hologram` · `mythos.helpop.receive` ·
`mythos.team` (TEAM-Badge) · `mythos.chat.color` · `mythos.teleport.instant` · `mythos.homes.unlimited`.
Spieler-Rechte haben standardmäßig alle (`mythos.player`). Admin-Rechte hängen nie an Namen.

## Dateien

| Datei | Inhalt |
|---|---|
| `config.yml` | Alle Einstellungen, kommentiert. Ungültige Werte werden gemeldet und durch Standardwerte ersetzt (`/mythos status`) |
| Updates | `config.yml`, `messages.yml` und `crates.yml` werden nach einem Plugin-Update automatisch ergänzt (neue Kisten, Texte, Einstellungen). Eigene Änderungen bleiben erhalten, die alte Datei wird als `*.v<Nr>.bak` gesichert |
| `messages.yml` | Alle Texte auf Deutsch. Unterstützt `&`-Codes, `&#RRGGBB` und MiniMessage |
| `crates.yml` | Kisten-Typen und Belohnungen |
| `regions.yml`, `arenas.yml` | Werden über Befehle gepflegt |
| `data/…` | Platzierte Kisten, Hologramme, Booster |
| `mythoscraft.db` | SQLite: Spieler, Homes, Transaktionen, Arena-Inventar-Sicherungen, Auktionen, Farmaufträge |
| `helpop.log` | Alle HelpOp-Nachrichten |

## Bauen

```bash
cd mythoscraft-core
mvn clean package        # baut, testet, packt das Resource Pack in die JAR
```

Resource Pack neu generieren (Texturen und 3D-Modelle werden per Code erzeugt):

```bash
java tools/PackGenerator.java resourcepack
```

## Architektur

```
online.mythoscraft.core
├── MythosCraft          Start, Reload und Stop (keine Spiellogik)
├── core/                Tasks (alle Wiederholungs-Tasks, reload-sicher), Perms
├── config/              ConfigReader (Validierung), Messages, YamlStore (atomares Speichern)
├── database/            Ein DB-Thread, SQLite/MySQL, Schema-Versionen, Transaktionen, Retry
├── player/              PlayerData-Cache (Laden beim Login, Speichern bei Quit, Autosave, Transfer), Statistiken
├── economy/             Coins/Gems, Multiplikatoren und Booster, Daily
├── protection/          Regionen, Raster-Index, Schutzdienst, 3 Listener
├── teleport/            Countdown-Teleports, Spawn, Homes, TPA, Kampf-Tracker
├── arena/               Arenen, Sessions, Kits, Inventar-Sicherung, Snapshots
├── tournament/          Turniere (Zeitplan, Anmeldung, Kits, Bots, Block-Tracker zum Zurücksetzen)
├── build/               Bau-Pläne für Spawn und Arenen, verteiltes Bauen ohne Lag
├── market/              Auktionshaus und Farmaufträge (Speicher im RAM, jede Änderung sofort in der DB)
├── shop/                Gem-Shop
├── rank/                Ränge, MYTHOS-Regel (MythosOwner, RankRules)
├── display/             Badges/Glyphs, Scoreboard, Tablist, Chat, Resource Pack + Webserver
├── events/              Event-Basis, Manager/Scheduler, 5 Events
├── crate/  item/  reward/  gui/  stats/  command/
```

Alle Datenbank-Zugriffe laufen über einen eigenen Thread (nie im Main-Thread oder in häufigen Events).
Überweisungen werden sofort in einer Transaktion gespeichert. Fehlgeschlagene Speicherungen werden wiederholt.
Bei einem Absturz während eines Arena-Besuchs bekommt der Spieler sein Inventar beim nächsten Join zurück.

## Grenzen und Hinweise

* Ein Arena-Snapshot speichert nur Blöcke, keinen Kisteninhalt.
* `/spawn place` und `/mythos arena place` räumen den Bereich über dem Boden frei (Bäume, Gebäude). Deshalb ist eine
  Bestätigung nötig.
* Turnier-Bots sind Mannequin-Entities (Paper 1.21.9+) mit einfacher Kampf-KI. Ohne Mannequin werden Zombies benutzt.
* Custom-Modelle und Badges sieht nur, wer das Pack geladen hat. Alle anderen sehen Vanilla-Items und Text-Ränge.
* Ein Vault-Anschluss (für fremde Shop-Plugins) und die Web-/KI-Brücke sind für später vorgesehen.
