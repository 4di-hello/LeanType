## 🧪 LeanType 4.2.7 Beta (Build 3)

> [!NOTE]
> This is the **third pre-release testing build** for LeanType 4.2.7. Please test and report any issues, input regressions, or feedback on [GitHub Issues](https://github.com/LeanBitLab/LeanType/issues).

---

### 🔐 SMS OTP Auto-Detection Overhaul
- **Universal Pattern Support**: Expanded OTP extraction logic with full support for WebOTP formats (`@domain #code`), hashtag formats (`#code`), multi-digit carrier phrasing, and case-insensitive keyword boundaries.
- **Active Notification Inspection**: Added automatic scanning of existing active status bar notifications upon startup, keyboard connect, or service initialization, ensuring incoming OTPs are immediately available without requiring a new notification to arrive while the keyboard is active.
- **Strip Wipe Protection**: Guarded external suggestion strip against initial recorrection (`INPUT_STYLE_RECORRECTION`) and empty clipboard evaluation clears, ensuring detected OTPs stay pinned until tapped or expired.
- **Battery-Optimized Dynamic Lifecycle**: Added in-memory fast-path filtering, preference caching with dynamic change listeners, and dynamic service component unbinding (`PackageManager.setComponentEnabledSetting` + `requestUnbind()`) so the background listener consumes 0% battery and zero OS callbacks when toggled off.
- **Strict Default SMS Routing**: Restricted notification inspection strictly to the active system Default SMS application, removing confusing manual app dropdowns and redundant fallback allowlists.
- **Permission Flow Streamlining**: Deduplicated settings toggles into **Settings → Suggestions** and added automatic intent resolution to Android's Notification Listener Access settings screen with clear warning states when disabled.

### ⚡ Motion, Animations & Gesture Polish
- **Key Press Previews**: Stabilized key preview popup animations with custom cubic bezier `PathInterpolator` curves (`(0.1, 0.9, 0.2, 1.0)` and `(0.3, 0.0, 0.8, 0.15)`), reduced vertical displacement (6%), 0.4 initial alpha, and 55ms dismissal for a crisp, tactile typing feel.
- **Long-Press Popups**: Replaced `OvershootInterpolator` with smooth `PathInterpolator` and tightened initial scale to 0.84, eliminating wobble and lateral stretching under the finger.
- **Height-Proportional Popup Positioning**: Replaced fixed dp popup offset with keyboard height percentage (0% to 8%, default 4%), dynamically adapting across screen orientations and split keyboard modes while scaling touch slide allowances.
- **Toolbar & View Transitions**: Smoothed transitions for toolbar container slide, suggestion strip fades, pinned keys, chevron flips, emoji palettes, clipboard history, and touchpad mode using Material emphasized decelerate curves and gentle fades.
- **Animation Scale Preference**: Renamed "Animation speed" to "Animation scale" in Appearance settings to align with standard Android system animation scale conventions.

### 📥 Unified Download Streaming & Progress Indicators
- **Streaming Byte-Level Progress**: Implemented `FileUtils.copyStreamWithProgress` for reliable chunked byte streaming with live percentage reporting.
- **Unified Progress Indicators**: Standardized modern `LinearProgressIndicator` and live percentage text across dictionary downloads, dictionary upgrades, gesture typing library, emoji library, sound packs, translation models, handwriting models, and modular AI/OCR plugins.

### 🛠️ Memory, Lifecycle & Native Engine Hardening
- **Lifecycle & Scope Teardown**: Hardened IME teardown by ensuring proper coroutine scope cancellation and safe cursor extraction in dictionary facilitator and clipboard history.
- **Resource Leak Prevention**: Wrapped all file streams and ContentResolver inputs in Kotlin `use` blocks; closed host `ParcelFileDescriptor` copies in voice dictation IME bridge.
- **Haptic Primitive Caching**: Cached vibrator composition primitive support queries to eliminate redundant hardware abstraction layer (HAL) calls.
- **Binary Size & Symbol Optimization**: Enabled dead code section stripping (`-ffunction-sections -fdata-sections -Wl,--gc-sections`) and hidden symbol visibility (`-fvisibility=hidden`) in native C++ libraries; converted foreground raster icon to lossless WebP saving ~50 KB.

---

### 📦 Beta Build Artifacts

| File | Flavor | Description | Size |
|:---|:---|:---|:---:|
| `1-LeanType_4.2.7-standard-release.apk` | Standard | Full features & online/offline AI voice | - |
| `2-LeanType_4.2.7-offline-release.apk` | Offline | Fully air-gapped (zero internet permission) | - |
