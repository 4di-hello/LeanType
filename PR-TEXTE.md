# PR-Texte für LeanType (Stand 21.11.2026)

Nur in diesem Fork. Je PR: auf GitHub „Compare & pull request“ von `4di-hello:<Branch>` nach `LeanBitLab:main`, Titel und Text unten einfügen.

Alle Branches passen auf LeanType 4.3.0-beta4 (a9dc76400) und sind einzeln getestet (Kompilieren + Unit-Tests). `fix/offline-ai-busy` hat seit 1.11. einen zweiten Commit (Feldprüfung, aus dem Sicherheits-Review).

---

## 1. `fix/offline-ai-busy`

**Titel:** Offline AI: no lost results and no "Context is busy"

**Text:**

Problems with the local AI that remain after the longer context in beta4, mostly noticed with custom AI keys:

1. **"Context is busy" / "Model produced no output"**: when the keyboard was hidden, the request was cancelled, but the plugin can't stop a running generation. The next request then failed. Now requests run one after another (a new one waits for the previous one), and hiding the keyboard no longer cancels a request.
2. **Lost results**: some editors hide the keyboard while the result is inserted (they react to the cursor being moved). A custom AI key result that arrives while the keyboard is hidden is now kept and inserted when the keyboard comes back into the same field within two minutes. Because many apps use the same field id for all their text fields (e.g. every chat), the field must also still have the same input type and text, and it is never a password field; otherwise the result is dropped.
3. **Unclear "no output"**: the message now says why: text too long for the context, nothing generated, or removed by the output cleanup.

Also, the log no longer contains the input and generated texts, only their lengths – the texts are private, and the system log can be read by other tools.

Tested with Qwen3-4B-Instruct-2507 Q4_0; the same changes are in daily use in my fork.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01VuiaMhLXwhErf9shhrr1uD

---

## 2. `feature/ai-key-clipboard-prepend`

**Titel:** Custom AI keys: {clipboard} placeholder and "Prepend result"

**Text:**

Two small additions to custom AI keys, building on #725:

- **`{clipboard}`** in a prompt is replaced by the clipboard text. This makes reply keys easy: copy a message, tap the key in the empty reply field, the answer appears. It works with an empty field too. If the prompt has `{clipboard}` but no `{text}`, the field text is not sent. If the clipboard is empty, a short message says so.
- **"Prepend result" (`#prepend`)**: the result is inserted at the cursor (before the selection, if there is one), separated by empty lines; the rest of the text stays. Append and prepend exclude each other in the keyword chips.
- Placeholders ignore case and spaces, so `{Text}` and `{ clipboard }` work too.

Example reply key (only "Append result" or "Prepend result" selected):

```
Write my short reply to a message. Answer every question with its own sentence.

Message: {clipboard}
Reply:
```

Unit tests in `CustomAIKeyTextTest`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01VuiaMhLXwhErf9shhrr1uD

---

## 3. `feature/more-themes`

**Titel:** Add more color themes with a small preview in the theme list

**Text:**

More built-in color themes, without any change to the existing themes or settings:

- **Dynamic colors** in two more variants: *subtle* (neutral functional keys) and *vivid* (tinted background and keys).
- **Simple themes**: Clean Light/Dark, Graphite, Pure Black (dimmed text for OLED), Warm Night, High Contrast Light/Dark, Arctic, Solar Light/Dark, Ocean, Deep Sea, Pastel.
- In these themes the auto-correction and the typed word in the suggestion strip have their own colors, so they are easy to tell apart.
- Light-only themes are hidden from the night theme list.
- The theme list shows a small swatch (background, key, functional key, accent) for each theme.
- A new user theme starts with all colors of the current theme instead of the defaults.

Unit tests in `KeyboardThemeTest` (all themes resolve, readable text contrast).

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01VuiaMhLXwhErf9shhrr1uD

---

## 4. `feature/text-expander-edit-as-text` (schlanke Fassung von #695, wie von LeanType gewünscht)

Basis: LeanType 4.3.2-beta2 (bc4eda600). Ohne Tastendrücke, keine Änderung an `InputLogic`.

**Titel:** Text expander: edit all shortcuts as one text, %tab% placeholder

**Text:**

> As suggested in #695: only the "Edit all as text" editor and a plain `%tab%` placeholder, without key presses (%enter%, %tabkey%) and without any change to InputLogic.
>
> - **Edit all as text:** a full-screen editor for all text shortcuts. Each shortcut starts with a line `=== shortcut ===`, followed by its text. Copy all, paste all, share, save to and load from a file, restore the state before the last save. Saving shows what is new, changed and deleted, and reports errors with line numbers (text before the first shortcut, missing or duplicate shortcut, spaces, invalid regex). Exported text is marked as sensitive on the clipboard.
> - **`%tab%`:** inserts a tab character (`\t`), e.g. for table columns; added to the quick placeholders.
>
> Tests: `TextExpanderTextTest` (format and parse round trip, empty lines, prefixes, diff, errors with line numbers), `TextExpanderTabTest`.

## #680 und #739

Beide am 9.11. von LeanType geschlossen. #680 bleibt nur im Fork. #739 nur neu anbieten, wenn der Sprung die gespeicherten Auf-/Zugeklappt-Zustände nicht mehr ändert, die normale Klick-Erkennung nutzt und ohne globalen Zustand auskommt.

## 5. `feature/voice-dictation` (Spracheingabe)

Basis: LeanType 4.3.2. Link: https://github.com/LeanBitLab/LeanType/compare/main...4di-hello:LeanType:feature/voice-dictation

**Titel:** Voice input: longer processing time limit, full priority while dictating, optional transcription while speaking

**Text:**

> Three changes for offline dictation, found while using it daily:
>
> - **Processing time limit:** the watchdog ends the final recognition after a fixed 15 s. Offline recognition of a longer message on a phone needs more, so longer dictations were cut off. Now at least 60 s, and 15 s plus three times the recording length. It still ends a hung session.
> - **Priority while dictating:** since the plugin is bound with `BIND_NOT_FOREGROUND`, it gets less CPU, and recognition took about twice as long. While recording and recognizing, a second binding with normal priority raises it; it is removed when the dictation ends, so the idle binding stays as it is.
> - **Transcribe while speaking** (new option, off by default): at every pause in speech the part spoken so far is transcribed already, with the recognized text as context for the next part. After stopping, only the last part is left, which shortens the wait for long dictations a lot. If the field or app changes meanwhile, the rest is dropped with a notice instead of going to the wrong place.
>
> Also: recognized text is no longer written to the log, only its length.
>
> Tests: `VoiceProcessingTimeoutTest`. Translations for the two new texts: English and German.

## 6. `feature/short-word-swap-pr` (vertauschte Buchstaben)

Basis: LeanType 4.3.2. Link: https://github.com/LeanBitLab/LeanType/compare/main...4di-hello:LeanType:feature/short-word-swap-pr

**Titel:** Auto-correction: short words with two swapped letters (teh → the)

**Text:**

> Unknown words of up to 3 letters are not auto-corrected, so that abbreviations like "db" or "yt" stay. That also leaves very common typos uncorrected: two neighboring letters typed in the wrong order, like "teh" for "the", "adn" for "and", or in German "ihc" for "ich", "dei" for "die", "nud" for "und".
>
> This adds one exception to that rule: if the first suggestion is the typed word with two neighboring letters swapped, the auto-correction may correct it. The usual threshold still applies, backspace undoes it, and `RevertedCorrectionHistory` still prevents repeating a reverted correction. Abbreviations are not affected, their swapped form is no word.
>
> The check is a small pure function in its own file (`ShortWordSwap`), one line in `Suggest`. No work on the touch path.
>
> Tests: `ShortWordSwapTest`.

## 7. `feature/settings-jump-v2-pr` (Sprung aus der Suche, Neuauflage von #739)

Basis: LeanType 4.3.2. Link: https://github.com/LeanBitLab/LeanType/compare/main...4di-hello:LeanType:feature/settings-jump-v2-pr

**Titel:** Settings search: long press on a result shows the setting in its screen

**Text:**

> A new version of #739, reworked after your review:
>
> - **No saved state is changed.** A closed fine-tune section is opened only for the screen that shows the setting, in that screen's back stack entry. The saved open/closed state is never written. If the user opens or closes the section himself, that is what counts from then on.
> - **Standard long press.** The long press is a normal `combinedClickable` on the preference, with an accessibility label, so it also works with TalkBack. No pointer handling of its own.
> - **No global state.** The setting to show and the sections opened for it are kept in the `SavedStateHandle` of the target screen's back stack entry; the nav controller is passed down as a `CompositionLocal`. No timeout.
> - The setting itself is brought into view, also further down in a large card, and highlighted for a moment.
>
> Tests: `SettingsJumpTest` (the steps: open sections, keep only the one with the setting, give up). Also tested on an emulator: long press on a search result, the setting is shown and highlighted, and the saved section state is unchanged afterwards.

---

## 8. `feature/offline-ai-chat-format` (21.11., auf LeanType 4.3.6-beta2)

**Titel:** Offline AI: chat format for Qwen and LFM2 models, no texts in the log

**Text:**

Local chat models answer much better with their own chat format:

- **Qwen and LFM2** models (detected by the model file name) now get the instruction as system message and the text as user message (ChatML). With the plain "Instruction / Input / Output" prompt they often answered badly, took the few-shot examples as part of the text, and Qwen3 first "thought" and used up the available tokens.
- For Qwen, an empty think block switches thinking off, unless "Show thinking" is enabled.
- Other models get the prompt exactly as before.
- A prompt without `{text}` and an empty field (e.g. a custom key working on the clipboard) is sent as the request itself.

Also, the log no longer contains the input and the generated text, only their lengths: the texts are private, and the system log can be read by other tools (like #825 for typed words).

Tested with Qwen3-4B-Instruct-2507 Q4_0 and LFM2; in daily use in my fork for some weeks.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01VuiaMhLXwhErf9shhrr1uD

---

## 9. `fix/ai-key-result-after-hide` (21.11., auf LeanType 4.3.6-beta2)

**Titel:** Custom AI keys: keep the result when the keyboard hides, insert it only into the same field

**Text:**

Some editors hide the keyboard while a custom AI key is running, e.g. because they react to the cursor being moved for the result (seen in Nine Mail). The request was cancelled and the result was lost.

- **Result kept:** the request keeps running when the keyboard hides. A result that arrives meanwhile is inserted when the keyboard comes back into the same field within two minutes.
- **Only the same field:** many apps use the same field id for all their text fields (e.g. every chat). So the field must also be in the same app, have the same input type and the same text (the text around the cursor, or as a second check the whole text the app gives for extraction, because some apps report the selection unreliably), and is never a password field. Otherwise the result is copied to the clipboard with a short message, instead of going into the wrong place. The same check is used when the keyboard stayed open.
- **No "Context is busy" (offline):** the plugin can't stop a running generation, so a new request failed with "Context is busy" / "Model produced no output". Generations now run one after another.

The field check is a small class with unit tests (`AiFieldCheckTest`). In daily use in my fork since end of October.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01VuiaMhLXwhErf9shhrr1uD

---

## 10. `feature/ai-key-cancel` (21.11., auf LeanType 4.3.6-beta2, passt zusammen mit #888 und #889)

**Titel:** AI keys: the key that started a request shows an X, tapping it cancels

**Text:**

While an AI request runs, only the proofread key turned into an X, also when the request came from the translate key or a custom AI key.

- The key that started the request now shows the X, and gets its own icon back afterwards.
- A second tap on the translate key or a custom AI key cancels the request, like the proofread key already did, with a short "AI request cancelled" message.

This fits well with #889: as requests now keep running when the keyboard hides, there should be a clear way to stop one.

Tested together with #888 and #889 (no merge conflicts, unit tests pass); in daily use in my fork since end of October.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01VuiaMhLXwhErf9shhrr1uD

---

## 11. `feature/settings-search-word-parts` (22.11., auf LeanType 4.3.6-beta3)

**Titel:** Settings search: also find parts of words

**Text:**

The settings search only finds words that start with the search term. In languages with compound words this misses a lot: in German, "korrektur" doesn't find "Autokorrektur", "taste" doesn't find "Löschtaste". In English, "correct" doesn't find "Autocorrect".

- Search terms of 3 or more letters now also find settings whose title or description contains them.
- These results come after the existing ones (title start, title word, description word), so the order of the best matches doesn't change.
- Shorter terms behave as before, to avoid noise.

Small change in `SettingsContainer.filter` (the comment there says filtering could be more elaborate). Unit tests pass; in daily use in my fork since October.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01VuiaMhLXwhErf9shhrr1uD

---

## 12. `feature/settings-search-keywords` (22.11., baut auf 11 auf)

**Titel:** Settings search: additional search words for some settings

**Text:**

Builds on #<Nummer von PR 11> (it contains that commit).

Users search with their own words, which often aren't in the title or description: "color" for the theme, "haptic" for vibration, "swipe" for gesture typing, "copy" for the clipboard history.

- A string array `settings_search_keywords` lists additional words per setting key (`key|word word ...`), English and German for now. Other languages can translate it or leave it out; without it nothing changes.
- A setting is found if one of its words starts with the search term. These results come after the title and description matches.
- All keys in the list exist in the current settings (checked against `Settings` and `SettingsWithoutKey`).

Feel free to take only #<Nummer von PR 11> if you'd rather not have the extra strings.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01VuiaMhLXwhErf9shhrr1uD

---

## 13. `feature/settings-test-field` (22.11., auf LeanType 4.3.6-beta3)

**Titel:** Settings: a field to try the keyboard on the appearance and suggestion screens

**Text:**

To see the effect of a changed theme, key size or suggestion setting, you have to leave the settings and open another app, then come back.

- A text field "Try the keyboard here" at the top of the Appearance and Suggestions screens (using the existing `headerContent` of `SearchSettingsScreen`) lets you try the keyboard right there.
- A short hint says that changes are shown the next time the keyboard opens.
- One small composable (`KeyboardTestField`), two strings (English and German).

In daily use in my fork since October.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01VuiaMhLXwhErf9shhrr1uD

---

## 14. `feature/settings-collapsible-sections` (22.11., optional, zuletzt einstellen)

**Titel:** Settings: sections can be collapsed (optional)

**Text:**

This one is optional - just close it if it doesn't fit your idea of the new settings layout.

Long settings screens are easier to overview when sections you don't need can be collapsed:

- A tap on a section title collapses or expands it, with an arrow showing the state. The state is remembered per screen.
- A jump from the search (#799) opens a collapsed section while the setting is shown, without saving that.
- Only `SearchScreen.kt` and one string (English and German).

In daily use in my fork since October.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01VuiaMhLXwhErf9shhrr1uD
