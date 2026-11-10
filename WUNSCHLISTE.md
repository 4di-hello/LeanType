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
  - „Nur Korrekturen“ (Chip und Schalter für die Korrektur-Taste) 31.10. wieder entfernt: mit Qwen3-4B kaum schneller (31 s statt 35 s bei 200 Wörtern), aber fast keine Fehler gefunden. Die Zeit geht fürs Lesen des Textes drauf, nicht fürs Schreiben
- Spracheingabe: Schalter „Schon beim Sprechen erkennen“ (29.10., Standard aus). Aufnahme wird bei Sprechpausen (0,7 s, Stück mind. 4 s) zerlegt, jedes Stück als eigene Plugin-Sitzung, der bisherige Text geht als Kontext mit. Bekannt: das Sprach-Plugin schreibt erkannten Text ins Systemprotokoll (nur mit eigenem Plugin änderbar)
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

## Tippen wie Gboard/SwiftKey (seit 1.11.)

- Erledigt 9.11.: kurze Wörter mit zwei vertauschten Buchstaben werden korrigiert (dsa→das, ihc→ich, dei→die, nud→und, teh→the). LeanType korrigiert unbekannte Wörter bis 3 Buchstaben sonst nie (damit Abkürzungen wie db, yt bleiben); vertauschte Nachbarbuchstaben sind aber fast immer ein Tippfehler
- Ziel: weniger falsche und fehlende Korrekturen, bessere Vorschläge fürs nächste Wort, flüssigeres Gefühl
- Erledigt 1.11.: „Aus meinen Texten lernen“ (Textkorrektur): eigene Texte einfügen oder Textdatei laden, lernt Wörter und Wortfolgen in den persönlichen Verlauf (unbekannte Wörter nur ab 2× im Text, gegen gelernte Tippfehler); Text wird beim nächsten Öffnen der Tastatur in der aktuellen Sprache gelernt und gelöscht
- Befund 1.11.: Das deutsche Hauptwörterbuch (HeliBoard, 1,3 Mio. Wörter) hat nur höchstens 3 Folgewörter pro Wort, aus Nachrichtentexten, alle mit gleichem Gewicht – daher die schwachen Vorschläge fürs nächste Wort
- Erledigt 1.11.: Eigenes Wörterbuch „Deutsch+“: gleiche Wörter, bis zu 20 Folgewörter pro Wort (480.000 insgesamt) aus 5 Mio. Sätzen (Wortschatz Leipzig, CC BY 4.0: News 2024, Web 2021/2019, Mixed 2011, Wikipedia 2021); nur Folgewörter, die nach dem Wort deutlich häufiger sind als allgemein. Download: Release `dict-de-plus` (`main_de_plus.dict`), Bauanleitung im Branch `ci/dict-de`. Zurück: normales Wörterbuch wieder herunterladen
- Erledigt 1.11.: Stärke der Trefferzonen-Anpassung „Automatisch“ (neue Stellung ganz links am Regler, Standard bei Neuinstallation): die Tastatur rechnet Deine aufgezeichneten Anschläge mit jeder Stärke nach (jeder Anschlag zählt dabei nicht für seine eigene Taste mit) und nimmt die mit den wenigsten Fehltreffern, bei Gleichstand die schwächere; ab 300 Anschlägen je Layout, vorher „Empfohlen“; wird nach jedem Speichern neu gewählt. Trefferkarte zeigt die nachgerechneten Fehltreffer
- Erledigt 1.11.: Korrektur-Statistik (Textkorrektur → „Korrektur-Statistik“, letzte 30 Tage, nur Zahlen, nicht in Passwortfeldern/Inkognito): getippte Wörter, Autokorrekturen, davon zurückgenommen (sofort per Rücktaste, später zurückgeändert, durch Tippen aufs getippte Wort verhindert), selbst korrigierte übersehene Tippfehler, genutzte Vervollständigungen und Wortvorhersagen. Spätere Änderungen erkennt die Tastatur an der Stelle des Wortes (nur im Speicher, 10 Minuten)
- Erledigt 1.11.: Autokorrektur-Stärke „Automatisch“ (neue Stellung ganz links am Regler): Schwelle in Stufen vorsichtig 0,185 / mittel 0,12 / ausgewogen 0,067 / mutig 0,04, eine Stufe vorsichtiger ab 12 % zurückgenommen (mind. 40 Korrekturen), eine Stufe mutiger ab 1,2 % übersehenen Tippfehlern (mind. 400 Wörter) bei wenig Zurückgenommenem. „Mehr Autokorrektur“ bleibt, wie es ist
- Erledigt 1.11.: „Tippfehler lernen“ (Standard an): denselben übersehenen Tippfehler zweimal gleich selbst korrigiert → wird danach automatisch korrigiert; Rücktaste direkt danach oder späteres Zurückändern vergisst ihn; Liste „Gelernte Tippfehler“ mit Löschen
- Geprüft 1.11., kein Fehler: Satzanfang – die Tastatur sucht Folgewörter bei unbekanntem großgeschriebenem Vorwort beim kleingeschriebenen; der persönliche Verlauf speichert Paare nach Satzanfang klein; Deutsch+ legt Satzanfangs-Paare nach derselben Regel ab
- Idee: Vorschläge für das erste Wort eines Satzes aus Deutsch+ (braucht Satzanfangs-Einträge im Wörterbuch; bisher lernt das nur der persönliche Verlauf)

## Updates (seit 27.10.)
- Automatisch (montags): Workflow setzt `daily` auf LeanType neu auf, testet, baut und schiebt erst dann
- Mit Konflikten: von Hand lösen, auf `update/<name>` pushen. Der Workflow baut und testet dort, nur wenn alles grün ist, ersetzt er `daily` und veröffentlicht. Sicherung des alten Stands vorher als `backup/…`
- 27.10.: Auf LeanType 4.3.0-beta1 aktualisiert (Sicherung `backup/daily-2026-10-27b`)
- 28.10.: Auf LeanType 4.3.0-beta2 aktualisiert, nur Konflikte in den deutschen Texten (Sicherung `backup/daily-2026-10-28`); #680 ebenfalls auf beta2
- 29.10.: Auf LeanType 4.3.0-beta3 aktualisiert, keine Konflikte (Sicherung `backup/daily-2026-10-29`); alle PR-Branches passen ohne Konflikt
- 7.11.: Auf LeanType 4.3.2-beta1 aktualisiert (Sicherung `backup/daily-2026-11-07`), ohne Konflikte. Das neue Umschalten per Broadcast (#501, Tasker usw.) kann jede App auslösen: im Fork nur mit dem Schalter „Umschalten durch andere Apps erlauben“ (Erweitert, Standard aus)
- 9.11.: Auf LeanType 4.3.2-beta2 aktualisiert (Sicherung `backup/daily-2026-11-09`), Konflikte nur in Texten, Farben und Navigation. Neu bei LeanType: Unicode-Schriftstile; Wörter in einem Schriftstil zählen nicht für die Korrekturstatistik und lehren keine Tippfehler. Vor der Veröffentlichung die Ende-zu-Ende-Tests auf dem Emulator laufen lassen (`.github/e2e/README.md` im Branch `ci/e2e`)
- 10.11.: Auf LeanType 4.3.2-beta3 aktualisiert (Sicherung `backup/daily-2026-11-10`). LeanTypes Massen-Editor übernommen (ohne „Alles kopieren/einfügen/teilen“), Vollbild-Editor für einzelne lange Bausteine bleibt. Das wöchentliche Update löst Konflikte, die nur Texte betreffen, jetzt selbst (beide Seiten behalten, bei gleichem Namen gilt LeanTypes Text)
- 4.11.: Auf LeanType 4.3.1-beta3 aktualisiert (Sicherung `backup/daily-2026-11-04`): Konflikte in Texten, InputLogic (neues „Wort löschen“ per Wischen) und Spracheingabe. LeanTypes neue anpassende Stille-Erkennung gilt jetzt auch für das Zerlegen beim Sprechen, so werden Pausen auch bei Umgebungslärm gefunden
- 2.11.: Auf LeanType 4.3.1-beta2 aktualisiert (Sicherung `backup/daily-2026-11-02`), ein Konflikt in InputLogic (LeanType ersetzt ThreadLocal.withInitial für Android 7)
- 2.11. nachmittags: LeanType hat beta2 nachgelegt (9 Commits: Absturz in der Wortsuche, mehrsprachiger Verlauf, Standard-Schwelle der Autokorrektur jetzt „Ausgewogen“ 0,067, fehlende Menüpunkte) – ohne Konflikte übernommen. „Gelernte Wörter löschen“ löscht jetzt auch die gelernten Tippfehler
- 1.11.: Auf LeanType 4.3.1-beta1 aktualisiert (Sicherung `backup/daily-2026-11-01`). Erster Build scheiterte an zwei deutschen Texten, die LeanType gestrichen hat (Lint „ExtraTranslation“) – nach einem Update mit Text-Konflikten prüfen, ob values-de Namen hat, die in values fehlen
- 30.10.: Auf LeanType 4.3.0 (fertige Version) aktualisiert, nur Doku und Update-Anzeige geändert, keine Konflikte (Sicherung `backup/daily-2026-10-30`)
- 29.10.: Auf LeanType 4.3.0-beta4 aktualisiert (Sicherung `backup/daily-2026-10-29b`). LeanType hat jetzt selbst Kontext 4096 und passt die max. Tokens an die Textlänge an (512–2048); unser PR `fix/offline-ai-busy` ohne den Kontext-Teil neu aufgesetzt
- Achtung beim Update-Branch: Ist der oberste Commit ein `[skip ci]`-Commit, startet GitHub keinen Build – dann einen normalen Commit obendrauf

## Zukunft von LeanType unsicher (21.10.; am 22.10. kam trotzdem v4.2.9)

- Gesichert: Quellcode aller 7 Plugins (Branches `backup/plugins/*`) und alle Plugin-APKs (Release „plugins-backup“)
- Bauanleitung baut weiter, auch wenn LeanType verschwindet
- PRs #680/#695/#739 wurden am 9.11. geschlossen, alles ist im Fork enthalten
- Umzug auf HeliBoard (aktiv gepflegt): nur bei echtem Bedarf, sehr aufwendig (KI, Sprache, Textbausteine fehlen dort)

## Sicherheit (Review 1.11., unabhängige Zweitprüfung)

- Erledigt: Build-Pipeline in zwei Teile getrennt – „build“ führt den Code von LeanType und des Forks aus, ohne Geheimnisse und ohne Schreibrecht (kein Token in .git/config, Gradle-Wrapper wird geprüft); „publish“ führt keinen Code aus dem Repository aus, signiert mit apksigner, prüft Schlüssel und 16-KB-Ausrichtung, schiebt `daily` und veröffentlicht. Eine böswillige Änderung in LeanType kann so den Schlüssel nicht stehlen und das Repository nicht verändern
- Bleibt: Montags wird LeanType-Code automatisch eingebaut und veröffentlicht. Wird LeanType kompromittiert, landet das in der nächsten Version (Schutz nur durch Freigabe von Hand vor dem Veröffentlichen, optional)
- Erledigt: Plugins werden vor dem Entpacken geprüft (vorher erst nach dem Entpacken der .so-Dateien); Dateinamen aus dem Plugin ohne Pfad („../“)
- Erledigt: Prüfsumme der Gestenbibliothek nicht mehr in Sicherungen (sie erlaubt das Laden von nativem Code)
- Erledigt: KI-Ergebnisse nur noch ins selbe Feld mit unverändertem Text (viele Apps nutzen dieselbe Feld-ID für alle Chats), nie in Passwortfelder; sonst in die Zwischenablage mit Hinweis
- Erledigt: Diktat „Schon beim Sprechen erkennen“: restliche Stücke nur ins Feld, in dem die Aufnahme begann
- Erledigt: Sprach-Plugin wird nur mit dem bekannten LeanBitLab-Schlüssel benutzt
- Erledigt: Sicherungen verschlüsselt in Abschnitten von 1 MB (Format 2): große Sicherungen ohne Speicherproblem, Abschnitte können nicht vertauscht oder abgeschnitten werden; PBKDF2 mit 600.000 statt 200.000 Runden (Zahl steht in der Datei); alte Sicherungen bleiben lesbar
- Erledigt: Automatisches Sichern prüft jede Datei vollständig (auch unverschlüsselt), bevor alte gelöscht werden; Passwort mind. 8 Zeichen und zweimal einzugeben
- Erledigt: Wiederherstellen einer unverschlüsselten Datei fragt nach, wenn ein Sicherungspasswort gesetzt ist
- Erledigt: Keine 60 Zeichen KI-Ausgabe mehr im Protokoll; „Alle kopieren“ der Textbausteine als vertraulich markiert; ältere Screenshots nicht im Inkognito-Modus in den Verlauf; „Aus meinen Texten lernen“ zerlegt den Text im Hintergrund (vorher Hauptthread) und liest höchstens 2 MB

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

- 9.11.: LeanType hat #680 (Anpassung an meine Tippweise), #695 (Textbausteine als Text) und #739 (Einstellungssprung) geschlossen, Issue #676 ebenfalls. Alles bleibt im Fork
  - #680 grundsätzlich abgelehnt: Trefferflächen sollen sich nie ändern, Fehler soll nur das Wörterbuch auffangen. Bleibt dauerhaft nur im Fork (eigene Änderungen in `KeyDetector`/`PointerTracker`, bei Updates im Blick behalten)
  - #695: LeanType wollte einen schlanken PR nur mit „Alle als Text bearbeiten“ und `%tab%`. Als #781 am 10.11. übernommen (in 4.3.2-beta3), von LeanType als „Massen-Editor“ mit nur Laden und Exportieren gestaltet. Der Fork nutzt seit 10.11. LeanTypes Massen-Editor; nur die Tastendrücke `%tabkey%` und `%enter%` bleiben Fork-Extras
  - #739 abgelehnt wegen: speichert den Auf-/Zugeklappt-Zustand dauerhaft um, eigene Touch-Erkennung, globaler Zustand. Erledigt 9.11. im Fork: Bereiche gehen nur für die angezeigte Seite auf (gespeicherter Zustand bleibt), langer Druck über das normale `combinedClickable` (mit Bedienungshilfen-Beschriftung), Sprungziel im Zustand der Seite statt in einem globalen Objekt. Der E2E-Test fand dabei einen alten Fehler: eine Option weit unten in einer großen Karte blieb außerhalb des Bildschirms, jetzt wird sie selbst ins Bild geholt. Als PR neu anbieten: offen
- PR-Branches 27.10. vorbereitet (Texte in `PR-TEXTE.md`): `fix/offline-ai-busy` (kein „Context is busy“, Ergebnis bleibt bei eingeklappter Tastatur, Kontext 4096, Grund bei „no output“, keine Texte im Log), `feature/ai-key-clipboard-prepend` ({clipboard}, Ergebnis voranstellen), `feature/more-themes` (Themes ohne Menüänderungen)
- Später: verschlüsselte Backups

## Prüfen / Testen

- Tippgefühl im Vergleich zu SwiftKey: Empfehlungen zu Einstellungen (z. B. Vorschlagsverzögerung) nach eigenem Test
- Qwen3.5 0.8B (Q4_K_M) im Offline-AI-Plugin testen
