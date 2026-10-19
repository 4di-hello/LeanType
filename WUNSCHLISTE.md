# Wunschliste LeanType+

Nur in diesem Fork. Ideen und offene Punkte, grob nach Priorität.

## Neue Funktionen

- **Textbausteine (Text-Expander) bequem bearbeiten** – Kürzel wie „cic“, hinter denen lange Prompts liegen
  - Heute: Bearbeiten nur im kleinen Feld (ca. 5 Zeilen hoch) unter Text-Expander, Export nur über die komplette Sicherung (Kategorie Wörterbücher)
  - ~~Wunsch A: alle Bausteine als Text/Datei bearbeiten~~ erledigt („Alle als Text bearbeiten“: === Kürzel ===, Kopieren/Einfügen, Teilen, Datei, Sprungleiste, letzten Stand wiederherstellen)
  - Neue Platzhalter: %tab% (Tab-Zeichen), %tabkey% (Tab-Taste), %enter% (Enter/Senden)
  - ~~Wunsch B: Vollbild-Editor in der App~~ erledigt (Knopf „Im Vollbild bearbeiten“ im Bearbeiten-Dialog)
- **Lokale KI mit Qwen** – ~~läuft nicht~~ läuft (18.10.)
  - Empfohlen: Qwen3-4B-Instruct-2507 **Q4_0** (schnellstes Format auf dem Handy-Prozessor), langer Absatz (~320 Wörter) ca. 30 s, kurze Nachrichten wenige Sekunden
  - Getestet 18.10.: Qwen3-1.7B Q4_K_M ca. 24 s, ok aber schwächer; Qwen3-1.7B **Q4_0 kaputt** (nur Punkte); IQ4_XS langsamer als Q4_0
  - Einstellungen: max. Tokens 1024 (sonst werden lange Texte abgeschnitten), Modell geladen halten
  - Behoben in LeanType+: Chat-Format für Qwen ohne Denkmodus, Diagnose zeigt den echten Ladefehler
  - 18.10.: Anweisung und Text getrennt (System-/Nutzernachricht), kein eingebautes Beispiel mehr („großes Haus“); `{text}` nicht mehr nötig
  - LFM2 (Liquid, z. B. LFM2-2.6B Q4_K_M) wird erkannt (Dateiname enthält „lfm“) – testen
  - Idee: Modus „Nur Korrekturen“ (Modell gibt nur „alt → neu“ aus, deutlich schneller)
  - Qwen3.5 (2B/4B) lädt nicht: llama.cpp im Plugin (April 2026) lehnt die Dateien ab. Nur mit eigenem Plugin und aktueller llama.cpp – optional, falls Qwen3 nicht reicht
  - Qwen3.8-Flash-Next (125B MoE, ~100 GB) passt nicht aufs Handy
- Emoji-Vorschläge zum getippten Wort: prüfen, was LeanType schon kann (`suggest_emojis`, Inline-Emoji-Suche mit Emoji-Wörterbuch), dann entscheiden
- Getrennte Tipp-Profile pro Daumen bzw. Haltung weiter verfeinern (Idee)

## Sicherheit (Review 18.10.)

- Erledigt 19.10.: Tastendrücke (%enter%, %tabkey%) nur aus dem Baustein selbst, nie aus der Zwischenablage; Rest eines Bausteins wird bei App-Wechsel verworfen (auch in PR #695)
- Erledigt 19.10.: KI-Fehlerdiagnose kopiert keine Texte mehr, Protokoll nur mit Textlängen
- Erledigt 19.10.: neutrale Beispiele statt persönlicher (Fork, PR #695, Branches, Releases)
- Erledigt 19.10.: Pipeline-Bausteine (GitHub Actions) auf feste Versionen festgelegt
- Signierschlüssel: selbst sichern (Passwortmanager)
- Automatische Sicherung: Passwort setzen (dann ist alles verschlüsselt)
- Erledigt 19.10.: Pipeline bricht ab, falls das APK Internet-Berechtigung bekommt
- Offen, optional: Signieren in eigenem Schritt ohne Gradle
- Offen, PR-Kandidaten: Diktat-Texte nicht ins App-Protokoll; Plugin-Signatur prüfen; unverschlüsselte Sicherung erst prüfen, dann löschen

## Upstream (LeanBitLab/LeanType)

- PR #680 „Adapt to my typing“ begleiten; nach dem Merge den Fork auf die schlanke Version umstellen (ein Schalter, Daten bleiben)
- Danach einzeln vorschlagen: zuerst Themes (ohne Menüänderungen), eventuell verschlüsselte Backups

## Prüfen / Testen

- Tippgefühl im Vergleich zu SwiftKey: Empfehlungen zu Einstellungen (z. B. Vorschlagsverzögerung) nach eigenem Test
- Qwen3.5 0.8B (Q4_K_M) im Offline-AI-Plugin testen
