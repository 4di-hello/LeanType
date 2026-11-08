# Ende-zu-Ende-Tests (nur in diesem Fork)

Die Tests laufen auf einem Android-Emulator (API 34, Pixel 6, Englisch US) in GitHub Actions:
Workflow **E2E (Emulator)** (`.github/workflows/e2e-emulator.yml`). Er startet bei jedem Push auf
einen Zweig `ci/e2e*` oder von Hand („Run workflow“). Dauer ca. 15 Minuten.

Ergebnis: grün, wenn `am instrument` „OK (n tests)“ meldet. Bildschirmfotos jedes Schritts, Logcat und
Absturzberichte liegen im Artefakt `e2e` und zusätzlich auf dem Zweig `e2e-results`.

## Was getestet wird

Echte Tastatur, echtes Textfeld, echte Berührungen (Tastenmitte), echtes englisches Wörterbuch
(`run.sh` legt `main_en-US.dict` als Nutzerwörterbuch ab, weil LeanType keine Wörterbücher mitliefert).

| Test | Prüft |
|---|---|
| `a_plainTypingReachesTheField` | Tippen kommt im Feld an |
| `b_autocorrectionAndBackspaceAreCounted` | Autokorrektur einiger Tippfehler (Leiste im Log), Rücktaste macht sie rückgängig, Statistik zählt Wort, Korrektur und Rücknahme |
| `c_aTypoCorrectedTwiceByHandIsLearned` | Zweimal von Hand korrigiert → gelernt (auch Wörter mit 3 Buchstaben, die LeanType sonst nie korrigiert), danach angewendet, Rücktaste vergisst |
| `d_automaticStrengthGetsCarefulWhenCorrectionsAreTakenBack` | Stufe „Automatisch“: viele Rücknahmen → vorsichtigere Stufe, Grund wird gespeichert |
| `e_otherAppsCanSwitchTheKeyboardOnlyWhenAllowed` | Umschalten per Broadcast nur mit Schalter; danach tippt eine echte Berührung ein Zeichen der Symboltastatur |
| `f_wordsFromOwnTextsAreSuggested` | Aus eigenem Text gelerntes Wort wird vorgeschlagen |
| `g_aiResultFieldCheckOnARealTextField` | KI-Feldprüfung: gleiches Feld, andere Auswahl, geänderter Text |
| `largeBackupIsEncryptedAndDecryptedWithTheAndroidCryptoProvider` | 40-MB-Sicherung mit dem Android-Kryptoanbieter, falsches Passwort, abgeschnittene Datei |
| `backupOfAnOlderVersionCanStillBeRead` | Sicherungsformat 1 ist weiter lesbar |
| `onlyOfficialPluginsAreTrusted` | Plugins mit fremdem Schlüssel werden abgelehnt |
| `newSettingsScreensOpen` | Neue Einstellungsseiten öffnen sich, keine Absturzberichte |

## Was nicht getestet wird

- KI- und Sprach-Plugins (nicht im Emulator installiert): KI-Ergebnis im echten Ablauf, Spracheingabe in Stücken
- Deutsch und Deutsch+ (der Emulator tippt Englisch)
- Echte Geräte, andere Apps als Textfeld (Browser, WhatsApp, Mail-Apps)
- Gestentippen, Querformat, Einhand- und schwebende Tastatur
- Automatische Sicherung in einen echten Ordner (Ordnerwahl braucht den System-Dialog)
