### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.0

---

### 🐛 Fixed
- **Universal Web & Remote Workspace Keyboard Support (TYPE_NULL)**: Permitted soft input display for web editors, browsers (e.g. Brave), terminal environments, PWAs, and remote IDEs that instantiate an active InputConnection, without requiring package hardcoding or previous focus triggers.
- **Fast Typing Shift State & Capitalization (#598)**: Reset unshifted-from-shifted transient state on non-shift key presses to prevent inadvertent caps lock during rapid typing, and preserved initial uppercase characters on touch down.
- **Dynamic Colors (Material You) Theme Refresh (#560)**: Evaluated theme attributes via a fresh configuration context to bypass cached resources, ensuring the keyboard immediately reflects system wallpaper and palette changes on Android 12+.
- **Touch Queue & Key Freeze Prevention (#542)**: Cleared active pointer tracker queues on cancellations, released stuck pressed key visuals, and dismissed popup panels on incoming touch down to prevent keyboard lockups and frozen keys.
- **Custom Emoji Font Compatibility & Validation (#528)**: Added glyph validation on pre-Android 13 devices to prevent blank emoji keyboards from unsupported COLRv1 fonts, and reloaded supported emoji caches upon font changes.
- **Edge Key Popup Proximity (#391)**: Used key hitbox bounds in squaredDistanceToEdge calculations to ensure reliable popup activation for keys along keyboard edges.
- **Shift Recapitalization Persistence**: Maintained typed word status when adjusting capitalization with Shift on the actively composing word.

### ✨ Added
- **Exact Visual Line Delete Swipe (Android 14+) (#731)**: Integrated platform-native requestTextBoundsInfo to detect exact character bounding boxes and visual line wrap boundaries during multi-line delete swipes, keeping vertical column position aligned, with seamless fallback for unsupported editors.
- **Settings Progressive Disclosure**: Reorganized settings categories (Appearance, Preferences, Toolbar, Gesture Typing, and Advanced) with expandable fine-tune sections, simplifying the primary interface for everyday users while preserving instant access for power users.
- **Complete Settings Translations**: 100% translation coverage across all 30 supported languages for all new settings, fine-tuning categories, and features.

### 🔄 Changed
- **Fine-Tune Preference Consolidation**: Standardized accordion-style expandable rows into a unified, reusable Compose component with persistent expand/collapse states.
