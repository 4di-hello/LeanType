# Wunschliste LeanType+

Nur in diesem Fork. Ideen und offene Punkte, grob nach Priorität.

## Neue Funktionen

- **Textbausteine (Text-Expander) bequem bearbeiten** – Kürzel wie „cic“, hinter denen lange Prompts liegen
  - Heute: Bearbeiten nur im kleinen Feld (ca. 5 Zeilen hoch) unter Text-Expander, Export nur über die komplette Sicherung (Kategorie Wörterbücher)
  - Wunsch A: alle Bausteine als Datei exportieren, in einem externen Editor bearbeiten und wieder importieren
  - Wunsch B: Vollbild-Editor in der App für einen Baustein, mit Scrollen und den Platzhaltern (%clipboard% usw.)
  - Offen: Dateiformat (lesbares Markdown mit Kürzel als Überschrift oder JSON), was beim Import mit vorhandenen Kürzeln passiert (ersetzen, überspringen, nachfragen)
- Emoji-Vorschläge zum getippten Wort: prüfen, was LeanType schon kann (`suggest_emojis`, Inline-Emoji-Suche mit Emoji-Wörterbuch), dann entscheiden
- Getrennte Tipp-Profile pro Daumen bzw. Haltung weiter verfeinern (Idee)

## Upstream (LeanBitLab/LeanType)

- PR #680 „Adapt to my typing“ begleiten; nach dem Merge den Fork auf die schlanke Version umstellen (ein Schalter, Daten bleiben)
- Danach einzeln vorschlagen: zuerst Themes (ohne Menüänderungen), eventuell verschlüsselte Backups

## Prüfen / Testen

- Tippgefühl im Vergleich zu SwiftKey: Empfehlungen zu Einstellungen (z. B. Vorschlagsverzögerung) nach eigenem Test
- Qwen3.5 0.8B (Q4_K_M) im Offline-AI-Plugin testen
