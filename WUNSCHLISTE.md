# Wunschliste LeanType+

Nur in diesem Fork. Ideen und offene Punkte, grob nach Priorität.

## Neue Funktionen

- **Textblöcke (angeheftete Zwischenablage-Einträge) bequem bearbeiten** – werden viel für Prompts genutzt
  - Export als Datei (z. B. eine Textdatei pro Block oder eine gemeinsame Datei), in einem externen Editor bearbeiten und wieder importieren
  - Alternativ: in der App ein großes Editor-Fenster zum Bearbeiten eines Blocks öffnen
  - Offen: genaues Format (Text, Markdown, JSON), Umgang mit Duplikaten beim Import
- Emoji-Vorschläge zum getippten Wort: prüfen, was LeanType schon kann (`suggest_emojis`, Inline-Emoji-Suche mit Emoji-Wörterbuch), dann entscheiden
- Getrennte Tipp-Profile pro Daumen bzw. Haltung weiter verfeinern (Idee)

## Upstream (LeanBitLab/LeanType)

- PR #680 „Adapt to my typing“ begleiten; nach dem Merge den Fork auf die schlanke Version umstellen (ein Schalter, Daten bleiben)
- Danach einzeln vorschlagen: zuerst Themes (ohne Menüänderungen), eventuell verschlüsselte Backups

## Prüfen / Testen

- Tippgefühl im Vergleich zu SwiftKey: Empfehlungen zu Einstellungen (z. B. Vorschlagsverzögerung) nach eigenem Test
- Qwen3.5 0.8B (Q4_K_M) im Offline-AI-Plugin testen
