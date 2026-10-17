# Wunschliste LeanType+

Nur in diesem Fork. Ideen und offene Punkte, grob nach Priorität.

## Neue Funktionen

- **Textbausteine (Text-Expander) bequem bearbeiten** – Kürzel wie „cic“, hinter denen lange Prompts liegen
  - Heute: Bearbeiten nur im kleinen Feld (ca. 5 Zeilen hoch) unter Text-Expander, Export nur über die komplette Sicherung (Kategorie Wörterbücher)
  - ~~Wunsch A: alle Bausteine als Text/Datei bearbeiten~~ erledigt („Alle als Text bearbeiten“: === Kürzel ===, Kopieren/Einfügen, Teilen, Datei, Sprungleiste, letzten Stand wiederherstellen)
  - Neue Platzhalter: %tab% (Tab-Zeichen), %tabkey% (Tab-Taste), %enter% (Enter/Senden)
  - ~~Wunsch B: Vollbild-Editor in der App~~ erledigt (Knopf „Im Vollbild bearbeiten“ im Bearbeiten-Dialog)
- **Neue effiziente Qwen-Modelle (Qwen3.8 u. a.) im Offline-AI-Plugin unterstützen** – sollen auch auf schwacher Hardware lokal laufen
  - Letzter Test (Qwen3.5/3.8) hat nicht funktioniert; Ursache vermutlich zu alte llama.cpp-Version im Plugin
  - Stand 17.10.: offiziell als GGUF nur Qwen3.8-27B; ein kleineres MoE-Modell „Qwen3.8-Flash-Next“ ist angekündigt, Community-GGUF noch ohne Dateien
  - Vorgehen: konkretes Modell festlegen, llama.cpp im Plugin aktualisieren, Denkmodus aus, kurze Antworten
- Emoji-Vorschläge zum getippten Wort: prüfen, was LeanType schon kann (`suggest_emojis`, Inline-Emoji-Suche mit Emoji-Wörterbuch), dann entscheiden
- Getrennte Tipp-Profile pro Daumen bzw. Haltung weiter verfeinern (Idee)

## Upstream (LeanBitLab/LeanType)

- PR #680 „Adapt to my typing“ begleiten; nach dem Merge den Fork auf die schlanke Version umstellen (ein Schalter, Daten bleiben)
- Danach einzeln vorschlagen: zuerst Themes (ohne Menüänderungen), eventuell verschlüsselte Backups

## Prüfen / Testen

- Tippgefühl im Vergleich zu SwiftKey: Empfehlungen zu Einstellungen (z. B. Vorschlagsverzögerung) nach eigenem Test
- Qwen3.5 0.8B (Q4_K_M) im Offline-AI-Plugin testen
