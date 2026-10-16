# MythosPlots – Plot-Server für MythosCraft

Eigener Server mit Plot-Welt (wie CityBuild). Vom Hauptserver kommt man mit `/plot` hierher, zurück mit `/smp` (oder `/mythos`, `/hub`).

## Einrichtung

1. `MythosPlots.jar` in den `plugins`-Ordner des Plot-Servers (mythoscraftsmpplot.minekeep.gg) legen und den Server starten.
2. In der `server.properties` auf **beiden** Servern (Haupt- und Plot-Server): `accepts-transfers=true` – sonst klappt das Hin- und Herbeamen nicht.
3. Fertig. Die Plot-Welt `plots` wird beim ersten Start automatisch mit eigenem Generator angelegt (keine Flat-Welt, kein Bau-Befehl nötig). Die Standard-Welt des Servers wird nicht benutzt.

## Was drin ist

- Endlose Plot-Welt: 48×48 Plots, 7 Blöcke breite Wege, Rand-Stufen (frei = glatter Stein, belegt = Quarz)
- `/plot auto | claim | home [n] | list | visit <Spieler> | info | add/remove <Spieler> | deny/undeny <Spieler> | sethome | clear confirm | unclaim confirm`
- Admins (`mythosplots.admin`, Standard: OP): überall bauen, unbegrenzt Plots, `/plot admin unclaim | tp <x;z>`
- Mehr Plots pro Spieler: Recht `mythosplots.plots.<Anzahl>` (z. B. `mythosplots.plots.3`)
- Schutz: nur Besitzer + hinzugefügte Spieler bauen; Wege unveränderbar; kein Fließen/Wachsen/Kolben über Plot-Grenzen; keine Explosionen, kein PvP, keine Monster
- Gleiches Resource Pack wie der Hauptserver (beim Bauen aus `mythoscraft-core/resourcepack` in die JAR gepackt) → alle Custom-Items sehen gleich aus
- Spielmodus in der Plot-Welt: `world.gamemode` (Standard CREATIVE)

## Resource Pack

Das Plugin liefert das Pack selbst aus (eigener kleiner Webserver). MineKeep erlaubt nur die Ports 2053, 2083, 2087, 2096.
In `config.yml` → `resource-pack.self-host.public-url` die öffentliche Adresse eintragen (Standard `https://mythoscraftsmpplot.minekeep.dev:2053`).
Alternativ `resource-pack.url` + `sha1` mit einem externen Link setzen.

## Bauen

`mvn -B package` → `target/MythosPlots.jar` (der Ordner `../mythoscraft-core/resourcepack` muss daneben liegen).
