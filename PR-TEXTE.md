# PR-Texte für LeanType (Stand 27.10.2026)

Nur in diesem Fork. Je PR: auf GitHub „Compare & pull request“ von `4di-hello:<Branch>` nach `LeanBitLab:main`, Titel und Text unten einfügen.

Alle Branches basieren auf LeanType 4.3.0-beta1 (0db093626) und sind einzeln getestet (Kompilieren + Unit-Tests).

---

## 1. `fix/offline-ai-busy`

**Titel:** Offline AI: no lost results and no "Context is busy"

**Text:**

Three problems with the local AI, mostly noticed with custom AI keys:

1. **"Context is busy" / "Model produced no output"**: when the keyboard was hidden, the request was cancelled, but the plugin can't stop a running generation. The next request then failed. Now requests run one after another (a new one waits for the previous one), and hiding the keyboard no longer cancels a request.
2. **Lost results**: some editors hide the keyboard while the result is inserted (they react to the cursor being moved). A custom AI key result that arrives while the keyboard is hidden is now kept and inserted when the keyboard comes back into the same field within two minutes.
3. **Long texts**: with a context of 2048 tokens a longer text (about 5000 characters) didn't fit and the model generated nothing. The context is now 4096 tokens (about 300 MB more memory for a 4B model), and "no output" says why: text too long, nothing generated, or removed by the output cleanup.

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

## Kommentar für #680 und #739 (nach dem Update)

> Rebased onto 4.3.0-beta1. #739: a long press on a search result now also opens the closed fine-tune section that contains the setting (only that one stays open). #680: no functional change, the setting stays a single switch in Text correction.
