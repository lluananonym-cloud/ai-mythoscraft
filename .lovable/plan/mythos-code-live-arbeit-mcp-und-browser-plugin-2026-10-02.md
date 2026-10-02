# Mythos Code: Live-Arbeit, MCP und Browser-Plugin

## Ziel
Die Windows-App bekommt eine hochwertige, aufklappbare Live-Arbeitsansicht, eine verständliche MCP-/Plugin-Verwaltung und eine neu gestaltete Browser-Erweiterung. App und Erweiterung können sich sicher koppeln und Browser-Aufgaben gemeinsam ausführen.

## Umsetzung

### 1. Live-Arbeitsansicht in Mythos Code
- Den bisherigen kleinen „Denkt nach“-Block zu einer aufklappbaren Aktivitätsleiste mit laufender Zeit ausbauen.
- Während der Arbeit live und schrittweise anzeigen: aktuelles Ziel, untersuchte Dateien, verwendete Werkzeuge, laufende Befehle, Änderungen, Tests und Wiederholungsversuche.
- Neue Einträge sofort ergänzen und automatisch zum neuesten Schritt scrollen; nach Abschluss eingeklappt als Arbeitsprotokoll erhalten.
- Unterbrechen und neue Hinweise während einer laufenden Aufgabe weiterhin erlauben.
- Keine privaten internen Gedankengänge ausgeben; stattdessen sichere, konkrete Arbeitsschritte live anzeigen. Das wirkt wie die gewünschte Base44-Ansicht, ohne versteckte Modellgedanken offenzulegen.

### 2. MCP- und Plugin-Zentrale
- Den bereits vorhandenen MCP-Client behalten und eine richtige Verbindungsansicht dafür ergänzen.
- Globale und projektbezogene MCP-Server mit Status, Werkzeuganzahl, Fehlerdetails, Neuverbinden und Konfiguration anzeigen.
- Einen Plugin-Bereich ergänzen, in dem das Browser-Plugin als eigene Verbindung mit Status und Berechtigungen erscheint.
- Vertrauens- und Bestätigungsregeln beibehalten: Projekt-MCPs starten nur für ausdrücklich vertraute Ordner; schreibende oder ausführende Werkzeuge bleiben kontrollierbar.

### 3. Browser-Erweiterung neu gestalten
- Die vorhandene Manifest-V3-Erweiterung als echte Quellstruktur im Projekt pflegen und daraus die Download-ZIP neu erzeugen.
- Popup im gleichen Schwarz/Weiß-Glasstil wie Website und Coding-App gestalten, mit klarer Verbindung, Aufgabe, Live-Aktivitäten, Stopp und Verlauf.
- Zustände für getrennt, bereit, arbeitet, wartet auf Nutzer und Fehler professionell darstellen.
- Die Chat-Einbettung auf der Website optisch an dieselbe Oberfläche angleichen.

### 4. App–Browser-Kommunikation
- In der Desktop-App eine lokale, nur auf dem eigenen Rechner erreichbare Brücke starten.
- Erweiterung und App einmalig mit einem zufälligen Kopplungscode verbinden; das Geheimnis lokal geschützt speichern.
- Browser-Aufgaben aus Mythos Code an die Erweiterung senden und Aktionen/Ergebnisse live zurück in das Arbeitsprotokoll streamen.
- Browser-Werkzeuge in Mythos Code wie andere Agentenwerkzeuge verfügbar machen; sensible Schritte wie Login, Passwort, Zahlung und Captcha bleiben beim Nutzer.
- Verbindung stoppen, entkoppeln und neu koppeln können; keine offene Fernsteuerung ohne gültige Kopplung.

### 5. Stabilität und Prüfung
- Wiederverbindung und verständliche Fehlermeldungen für MCP und Browser-Brücke ergänzen.
- JavaScript-Prüfungen für App und Erweiterung ausführen, die Erweiterungs-ZIP neu packen und den Website-Download testen.
- Website auf Desktop und schmaler Ansicht prüfen; zentrale Kopplungs-, Start-, Live- und Stopp-Abläufe testen.

## Technische Details
- Die bestehende Electron-App bleibt eigenständig; CLI und iPhone-App werden nicht verändert.
- Die lokale Brücke nutzt ausschließlich `127.0.0.1`, kurzlebige Anfragen und einen zufälligen Kopplungsschlüssel.
- MCP bleibt kompatibel mit `.mcp.json`, `stdio` und Streamable HTTP.
- Die Browser-Erweiterung bleibt Manifest V3 und speichert nur lokale Verbindungsdaten in Chrome Storage.
