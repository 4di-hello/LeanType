# Test-Tagebuch: Offline-KI und Spracheingabe in LeanType+

Rohmaterial für die PR-Texte an LeanType (je Stufe ein Abschnitt „Wie wir getestet haben“), für einen
Erfahrungsbericht auf GitHub und für einen Reddit-Beitrag. Chronologisch, mit allen Messungen, auch den
Fehlschlägen. Keine persönlichen Daten.

## Testaufbau

- **Handy:** Xiaomi 15T Pro, Dimensity 9400+ (1× Cortex-X925, 3× Cortex-X4, 4× Cortex-A720)
- **Testtext:** ein deutscher Absatz (rund 100 Wörter) mit **11 eingebauten Fehlern**: „meinen Bruder“,
  fehlende Kommas vor „weil“, „als“, „die“, „als“, „wolten“, „schönes“, „ankammen“, „eingepakt“, „zuhause“
  (erlaubt, „zu Hause“ zählt auch), „dass“ statt „das“, „Seid“ statt „Seit“
- **Anweisung:** „Korrigiere Rechtschreibung, Grammatik, Kommas und falsch verwendete Wörter im folgenden
  deutschen Text. Ändere sonst nichts. Gib nur den korrigierten Text aus.“
- **Bewertung:** Anzahl der korrigierten Fehler, und ob sonst etwas geändert wurde
- **Vergleichs-Apps** mit denselben Modelldateien:
  - **Offline LLM** (zeigt Tokens/s)
  - **PocketPal AI** (zeigt ms/Token, Tokens/s und die Zeit bis zum ersten Token „TTFT“; viele
    Einstellungen: Threads, Flash Attention, Memory-Map, Weight Repacking, KV-Cache-Typ)
- **Zeitmessung:** anfangs von Hand mit der Stoppuhr, ab Stufe 2 mit der eingebauten Leistungsanzeige.
  **Wichtig:** Die Stoppuhr-Werte vom 26./27.11. waren deutlich zu niedrig (13 s gezählt, 20,9 s gemessen).
  Erst eine Profi-Stoppuhr (24 s) passte zur Anzeige (24,4 s). Für den Vergleich zählen nur Werte aus der
  Anzeige.

## Ausgangslage (26.11.)

- Altes Plugin von LeanBitLab: llamacpp-kotlin mit älterer llama.cpp-Version
- Qwen3-4B Q4_0: 10/11, rund 18 s (Stoppuhr)
- Gemma 4 lieferte nur Müll (Steuerzeichen wie `<unused…>`) → Verdacht: alte llama.cpp kennt Gemma 4 nicht

## Stufe 1: neues Plugin auf aktueller llama.cpp (26.–27.11.)

Gebaut:
- llama.cpp b12782 als Git-Submodul, eigene schlanke JNI-Schicht statt llamacpp-kotlin
- CPU-Backend wählt zur Laufzeit die beste Variante für den Prozessor (armv8.0 bis armv9.2, mit KleidiAI-
  Kernen für quantisierte Modelle)
- Chatformat aus der Modelldatei (Jinja-Vorlage) statt fest eingebautem ChatML → funktioniert für Gemma,
  Qwen, Llama usw.; Steuerzeichen werden nur in der Vorlage erkannt, nie im Text des Nutzers
- Modell wird über den Dateideskriptor der gewählten Datei geladen (kein Kopieren, kein Dateipfad nötig)
- Texte gehen als UTF-8-Bytes über JNI und landen nie im Log, nur Zahlen (Tokens, ms)
- Schutz: Antwortet ein Modell nur mit Steuerzeichen, kommt eine Fehlermeldung statt Müll im Text
- Bau und Signatur in GitHub Actions; LeanType+ vertraut zusätzlich Plugins mit seinem eigenen Schlüssel

Stolpersteine:
1. Plugin als App installiert → „Konflikt“. Richtig: in LeanType+ über „Aus Datei laden“ laden
2. „Plugin not loaded: unknown signing key“ → LeanType+ kannte den eigenen Schlüssel noch nicht
3. Laden über `/proc/self/fd/…` scheiterte („failed to load model“) → Laden direkt über den Dateideskriptor,
   die echte Fehlermeldung von llama.cpp wird jetzt durchgereicht
4. **Gemma 4 lieferte weiter Müll.** Ursache war nicht die Engine: Die erste „Modelldatei“ war eine
   gespeicherte Webseite (`.mht`, über einen „blob“-Link heruntergeladen), die zweite ein abgebrochener
   Download („(1)“ im Namen). Mit sauberen Dateien (Download-Links mit `/resolve/main/`) lief Gemma 4 sofort.
   Lehre: Dateigröße prüfen, nur `/resolve/`-Links nutzen
5. „Denken anzeigen“ heißt nur: Gedanken eines denkenden Modells mit einfügen. Es schaltet den Denkmodus
   nicht ein. Die Tastatur sendet deshalb `enable_thinking = false`, unabhängig von „Denken anzeigen“

### Modelle (Stufe 1, Zeit per Stoppuhr, deshalb vermutlich zu niedrig)

| Modell | Datei | Zeit | Ergebnis |
|---|---|---|---|
| Qwen3-4B | Q4_0 | 16 s / 15 s (altes Plugin: 18 s) | 10/11 |
| Gemma 4 E4B | Q4_K_M | 14 s | 11/11 |
| Gemma 4 E4B | QAT Q4_0 (Google) | 13 s | 11/11 |
| **Gemma 4 E2B** | **QAT Q4_0 (google/gemma-4-E2B-it-qat-q4_0-gguf)** | **9 s** | **11/11** |

→ Empfehlung: **Gemma 4 E2B QAT Q4_0** als Standard, E4B QAT als Reserve

## Messreihen in den Vergleichs-Apps (27.11.)

### Offline LLM, Gemma 4 E2B QAT

| Einstellung | Tokens/s |
|---|---|
| 4 Threads, Memory-Map aus | 9,2 / 10,3 (rund 10 s) |
| Memory-Map an | 6,7 |
| 8 Threads | 6,0 |
| 5 Threads | 5,6 |

→ **4 Threads sind am schnellsten:** nur die 4 großen Kerne rechnen. Ab dem 5. Thread kommt ein mittlerer
Kern dazu, alle warten bei jedem Token auf den langsamsten.

### PocketPal AI

Einstellungen: Flash Attention aus, Weight Repacking an, Kontext 12288, Batch 512.

| Modell | Threads | Memory-Map | Tokens/s | bis zum ersten Token |
|---|---|---|---|---|
| E2B | 8 | aus | 11,9 | 3,4 s |
| E4B | 8 | aus | 3,05 | 2,4 s |
| E4B | 8 | an | 4,16 | 8,4 s |
| E4B | 4 | an | 6,0 (1. Lauf nach dem Laden) | 15,9 s |
| E4B | 4 | an | **9,5** (2. Lauf) | 6,3 s |

Erkenntnisse:
- 4 Threads auch hier mehr als doppelt so schnell wie 8 (E4B: 9,5 statt 3–4 Tokens/s)
- Mit Memory-Map dauert der erste Lauf nach dem Laden viel länger (das Modell wird erst beim Rechnen
  stückweise eingelesen) → „Modell geladen halten“ lohnt sich, und das Plugin lädt jetzt komplett
- Alle Läufe beider Modelle: 11/11

## Stufe 2: Einstellungen und Leistungsanzeige (27.–28.11.)

Gebaut (Tastatur):
- „Rechenkerne“: Automatisch (die schnellen Kerne, höchstens 4) oder 2 (spart Akku)
- „Kontextgröße“: 4096 (Standard), 2048, 1024
- „Denkmodus“ als eigener Schalter (getrennt von „Denken anzeigen“), mit mehr Tokens für die Antwort
- „Leistungsanzeige“ (Standard aus): nach jeder Antwort Tokens, Sekunden, Tokens/s, Zeit zum Lesen des
  Textes und Gesamtzeit seit dem Tastendruck, dazu die Ladezeit, wenn neu geladen wurde
- „Modell geladen halten“ erklärt, was es bewirkt (Entladen nach 10 Minuten, 2–5 GB Speicher)

Gebaut (Plugin 1.1.1 / 1.2.0):
- Modell wird komplett gelesen, Memory-Map nur noch als Ausweichweg (Gegentest zu PocketPal)
- Denkmodus: llama.cpp trennt Gedanken und Antwort nach dem Chatformat des Modells (Gemma-4-Gedankenkanal,
  Qwen3-`<think>`); eingefügt wird nur die Antwort, die Gedanken nur mit „Denken anzeigen“. Gehen beim
  Denken die Tokens aus, kommt eine klare Fehlermeldung statt halber Gedanken im Text
- `cancelGeneration()`: Abbrechen stoppt die Berechnung wirklich

Funde mit der Leistungsanzeige:
1. **Stoppuhr vs. Anzeige:** 13 s gezählt, 20,9 s angezeigt. Nachmessung mit Profi-Stoppuhr: 24 s,
   Anzeige 24,4 s. Die Anzeige stimmt, die frühen Handmessungen waren zu niedrig
2. **Abgebrochene Anfragen rechneten weiter.** Nach rund 3 s erschien die Anzeige einer *früheren*
   Anfrage (22,5 s, 9,3 Tokens/s): Abbrechen (X oder Tastatur zu) hatte nur das Ergebnis verworfen, das
   Plugin rechnete im Hintergrund weiter, die neue Anfrage wartete darauf. Behoben: Die Tastatur ruft
   `cancelGeneration()` im Plugin auf; abgebrochene Anfragen zeigen keine Anzeige mehr
3. Werte E2B QAT (Plugin, noch mit Hintergrund-Überlappung): 139 Tokens, 9,3 Tokens/s, Text gelesen in
   3,7 s. PocketPal: 11,9 Tokens/s. Offene Frage: Bekommt die Tastatur als Hintergrundprozess die
   schnellsten Kerne?

Ausstehend: Messung mit Plugin 1.2.0 ohne Überlappung.

## Nebenschauplatz: KI-Tasten in Nine Mail

- **Problem:** Nine Mail klappt die Tastatur ein, sobald sich der Cursor bewegt. Die KI-Taste hat vor der
  Anfrage den Text markiert → Tastatur zu → Anfrage abgebrochen
- **PR #889 (kleine Lösung):** Cursor und Markierung erst beim Einfügen ändern. Der Reviewer fand: Ein
  angefangenes Wort wurde verdoppelt („hello wor“ → „hello Hello world.“) → Wort vorher abschließen, mit
  Test. Ein unabhängiger Review fand alte Vorschläge und falsche Großschreibung nach dem Ersetzen → behoben
- **Test 27.11. (LeanType+ 4.3.9):** Ersetzen, Markierung ersetzen und angefangenes Wort klappen.
  **„Anhängen“ mit Cursor im Text nicht:** Der Cursor sprang ans Ende, der Text kam nie an
- **Versuch 1:** Cursor bewegen und Text in einem Batch-Edit senden → Nine Mail klappt trotzdem zu
- **Versuch 2:** den ganzen Text einmal mit eingefügtem Ergebnis senden (wie „Ersetzen“, das über „Alles
  auswählen“ geht und den Cursor nicht direkt bewegt) → wurde nicht genommen: Eine Sicherheitsprüfung
  verlangte, dass Nine Mails Cursorposition zur Textlänge passt, tut sie dort nicht
- **Versuch 3:** Nur der gelesene Text entscheidet, wo das Ergebnis hingehört → **funktioniert in Nine Mail**
- **Unabhängiger Review:** Der Ganzer-Text-Weg galt für alle Apps. Dort kostet er Formatierung, Erwähnungen
  und eingebettete Bilder; bei unvollständig gelesenem Text (lange zitierte Mails) oder Apps ohne „Alles
  auswählen“ droht Textverlust oder doppelter Text. Die neuen Tests hätten auch mit dem kaputten Code bestanden
- **Entscheidung:** keine Sonderlösung für eine einzelne App. Alle drei Versuche zurückgenommen; in Nine Mail
  geht „Anhängen“ mit dem Cursor im Text nicht, mit dem Cursor am Ende schon. PR #889 bleibt bei der kleinen
  Lösung

## Spracheingabe (bisher)

Schon erledigt (Tastaturseite, LeanType+):
- 29.10.: „Schon beim Sprechen erkennen“ (Standard aus): Die Aufnahme wird an Sprechpausen zerlegt (0,7 s,
  Stück mindestens 4 s), jedes Stück wird schon erkannt, während man weiterspricht; der bisherige Text geht
  als Kontext mit
- 4.11.: LeanTypes anpassende Stille-Erkennung gilt auch für das Zerlegen → Pausen werden auch bei
  Umgebungslärm gefunden
- 12.11.: Zeitlimit der Erkennung abhängig von der Aufnahmelänge (mindestens 60 s statt fester 15 s, die
  längere Diktate abbrachen)
- 12.11.: Während des Diktats bekommt das Plugin wieder normale Priorität. Seit LeanType 4.3.2-beta5 dauerte
  die Erkennung etwa doppelt so lange
- An LeanType übernommen: PR #797 (Spracheingabe)

Stand des Sprach-Plugins (LeanBitLab/LeanType-Voice-Plugin), noch unverändert:
- whisper.cpp 1.7.4, eigene App
- Rechnet immer mit dem vollen 30-Sekunden-Fenster, auch bei 3 Sekunden Sprache
- 4 Threads
- Schreibt den erkannten Text ins Systemprotokoll (Datenschutz)

Nächste Hebel: Rechenfenster passend zur Aufnahmelänge (`audio_ctx`), neuere whisper.cpp,
Modellwahl, Leistungsanzeige, Text nicht mehr ins Log.
