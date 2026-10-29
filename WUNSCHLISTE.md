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
  - Behoben in LeanType+: Chat-Format für Qwen ohne Denkmodus, Diagnose zeigt den echten Ladefehler (seit 26.10. nur in der Meldung, nichts in der Zwischenablage)
  - 18.10.: Anweisung und Text getrennt (System-/Nutzernachricht), kein eingebautes Beispiel mehr („großes Haus“); `{text}` nicht mehr nötig
  - LFM2 (Liquid, z. B. LFM2-2.6B Q4_K_M) wird erkannt (Dateiname enthält „lfm“) – getestet 29.10.: viel langsamer als Qwen3-4B Q4_0, verworfen
  - Erledigt 29.10.: Schalter „Korrektur-Taste: nur Fehler“ (Einstellungen → KI) für die eingebaute Korrektur-Taste
  - Erledigt 27.10.: Chip „Nur Korrekturen“ (#corrections) für KI-Tasten – Modell nennt nur „falsch → richtig“, LeanType+ setzt es ein; Prompt optional
  - Erledigt 27.10.: KI-Taste, Korrektur- oder Übersetzen-Taste nochmal tippen bricht ab (Plugin rechnet im Hintergrund zu Ende, Ergebnis wird verworfen)
  - Erledigt 28.10.: Fortschritt als dünner Balken im Laderahmen (geschätzt aus den letzten Läufen derselben Taste), die gestartete Taste zeigt ein X. Wortzähler geht nicht, das Plugin liefert erst am Ende Text
  - Qwen3.5 (2B/4B) lädt nicht: llama.cpp im Plugin (April 2026) lehnt die Dateien ab. Nur mit eigenem Plugin und aktueller llama.cpp – optional, falls Qwen3 nicht reicht
  - Qwen3.8-Flash-Next (125B MoE, ~100 GB) passt nicht aufs Handy
- KI-Tasten (Benutzerdefinierte AI-Tasten):
  - ~~Ergebnis vor den Text setzen~~ erledigt 24.10.: Chip „Ergebnis voranstellen“ (#prepend), mit Leerzeile
  - ~~`#append` mit Leerzeile~~ erledigt 24.10.
  - Erledigt 24.10.: Chip-Anweisungen vor den Prompt (vorher dahinter, störte „Antwort:“ am Ende)
  - Erledigt 24.10.: Platzhalter `{clipboard}` (Zwischenablage, auch bei leerem Feld)
  - ~~Getestete Anweisungen festhalten~~ erledigt: `prompts/KI-TASTEN.md`
  - PR #725 (Leerzeile bei #append, Anweisungen vor den Prompt) – am 27.10. von LeanType übernommen
  - Antwort-Tasten (Fa) ohne „Text bearbeiten“: dessen Anweisung lässt das Modell die Nachricht umschreiben statt zu antworten
- Erledigt 25.10.: Löschen per Wischen mit echten Bildschirmzeilen (übernommen aus PR #716 von Zokii0, Schalter „Genaue Zeilen …“ unter Gesten-Tippen)
- Erledigt 25.10.: Einstellungssuche – langer Druck auf ein Ergebnis springt zur Option (scrollt, klappt auf, hebt hervor); PR #739 bei LeanType
- Fremde PRs geprüft 25.10.: #434 (Screenshot-Absturz) schon in LeanType enthalten; #574 (Wörtlich-Tippen) nicht nötig, Autokorrektur-Knopf reicht; #571 offen
- Screenshots in der Zwischenablage: nur mit Fotos-Berechtigung „Alle erlauben“ und Tastatur innerhalb von 5 Minuten geöffnet; Idee: ältere Screenshots beim Öffnen des Verlaufs einsammeln
- Erledigt 26.10.: Shift auf das getippte Wort (Großschreibung ändern) behielt das Wort nicht als getippt, Vorschläge wie „Rad Umfang“; PR #733 – am 27.10. von LeanType übernommen
- Erledigt 26.10.: Lokale KI – „no output“ bei langen Texten: Kontext 4096 statt 2048 Token, Fehlermeldung nennt den Grund (Text zu lang / nichts erzeugt / beim Aufräumen entfernt). Umlaute im Prompt waren nicht die Ursache
- Bekannt, nicht weiter verfolgt (26.10.): KI-Tasten in Nine Mail – der Editor klappt die Tastatur ein, wenn sie den Cursor verschiebt, das Ergebnis kommt nicht an. Umgehung: Cursor vorher ans Textende setzen oder „Ergebnis voranstellen“ nutzen; andere Apps (Obsidian, Notizen) funktionieren
- 27.10.: Auf LeanType-Stand vom 27.10. aktualisiert (Konflikte gelöst: gelernte Großschreibung, KI-Tasten, Löschen per Wischen mit #716). Sicherung davor: Branch `backup/daily-2026-10-27`
- Emoji-Vorschläge zum getippten Wort: geprüft 27.10. – LeanType kann das schon („Emojis vorschlagen“, Standard an), braucht aber das Emoji-Wörterbuch der Sprache (z. B. `emoji_de.dict` aus Helium314/aosp-dictionaries, Ordner `emoji_cldr_signal_dictionaries`), über Wörterbücher → Wörterbuch hinzufügen. Nichts zu bauen
- Getrennte Tipp-Profile pro Daumen bzw. Haltung weiter verfeinern (Idee)

## Updates (seit 27.10.)
- Automatisch (montags): Workflow setzt `daily` auf LeanType neu auf, testet, baut und schiebt erst dann
- Mit Konflikten: von Hand lösen, auf `update/<name>` pushen. Der Workflow baut und testet dort, nur wenn alles grün ist, ersetzt er `daily` und veröffentlicht. Sicherung des alten Stands vorher als `backup/…`
- 27.10.: Auf LeanType 4.3.0-beta1 aktualisiert (Sicherung `backup/daily-2026-10-27b`)
- 28.10.: Auf LeanType 4.3.0-beta2 aktualisiert, nur Konflikte in den deutschen Texten (Sicherung `backup/daily-2026-10-28`); #680 ebenfalls auf beta2

## Zukunft von LeanType unsicher (21.10.; am 22.10. kam trotzdem v4.2.9)

- Gesichert: Quellcode aller 7 Plugins (Branches `backup/plugins/*`) und alle Plugin-APKs (Release „plugins-backup“)
- Bauanleitung baut weiter, auch wenn LeanType verschwindet
- PRs #680/#695 werden wohl nicht mehr übernommen, beides ist im Fork enthalten
- Umzug auf HeliBoard (aktiv gepflegt): nur bei echtem Bedarf, sehr aufwendig (KI, Sprache, Textbausteine fehlen dort)

## Sicherheit (Review 18.10.)

- Erledigt 19.10.: Tastendrücke (%enter%, %tabkey%) nur aus dem Baustein selbst, nie aus der Zwischenablage; Rest eines Bausteins wird bei App-Wechsel verworfen (auch in PR #695)
- Erledigt 19.10.: KI-Fehlerdiagnose kopiert keine Texte mehr, Protokoll nur mit Textlängen
- Erledigt 19.10.: neutrale Beispiele statt persönlicher (Fork, PR #695, Branches, Releases)
- Erledigt 19.10.: Pipeline-Bausteine (GitHub Actions) auf feste Versionen festgelegt
- Signierschlüssel: selbst sichern (Passwortmanager)
- Automatische Sicherung: Passwort setzen (dann ist alles verschlüsselt)
- Erledigt 19.10.: Pipeline bricht ab, falls das APK Internet-Berechtigung bekommt
- Offen, optional: Signieren in eigenem Schritt ohne Gradle
- Erledigt 27.10.: Diktat-Texte nicht mehr im App-Protokoll (nur Längen)
- Erledigt 27.10.: Plugins in der Tastatur (KI, Übersetzung, OCR, Handschrift, CJK) nur noch mit bekanntem LeanBitLab-Schlüssel; Handschrift-Plugin ist unsigniert, dort zählen die bekannten Release-Dateien (v1.0.0–1.0.6). Neues Plugin mit unbekanntem Schlüssel → Meldung „Plugin not loaded“, dann Schlüssel/Datei in `PluginTrust.kt` ergänzen. Sprach- und GIF-Plugin sind eigene Apps, nicht betroffen
- Erledigt 27.10.: Automatische Sicherung prüft die neue Datei (entschlüsseln, ZIP lesbar), erst dann werden alte gelöscht; mit Passwort werden alte unverschlüsselte Sicherungen entfernt

## Upstream (LeanBitLab/LeanType)

- PR #680 „Adapt to my typing“ begleiten; nach dem Merge den Fork auf die schlanke Version umstellen (ein Schalter, Daten bleiben). 27.10. auf 4.3 neu aufgesetzt
- PR #739 (Einstellungssprung) 27.10. auf 4.3 angepasst: öffnet auch zugeklappte Feinabstimmungs-Bereiche
- PR-Branches 27.10. vorbereitet (Texte in `PR-TEXTE.md`): `fix/offline-ai-busy` (kein „Context is busy“, Ergebnis bleibt bei eingeklappter Tastatur, Kontext 4096, Grund bei „no output“, keine Texte im Log), `feature/ai-key-clipboard-prepend` ({clipboard}, Ergebnis voranstellen), `feature/more-themes` (Themes ohne Menüänderungen)
- Später: verschlüsselte Backups

## Prüfen / Testen

- Tippgefühl im Vergleich zu SwiftKey: Empfehlungen zu Einstellungen (z. B. Vorschlagsverzögerung) nach eigenem Test
- Qwen3.5 0.8B (Q4_K_M) im Offline-AI-Plugin testen
