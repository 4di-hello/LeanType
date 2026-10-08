# LeanType GIF Plugin

Standalone GIF search and fetch engine plugin for the LeanType keyboard ecosystem.

## Architecture

This plugin runs out-of-process in a separate sandbox (`com.leanbitlab.leantype.gif`). It exposes an AIDL IPC interface (`IGifEngine`) and a read-only ContentProvider (`com.leanbitlab.leantype.gif.thumbs`) to serve GIF items, search queries, trending feeds, and cached media to authorized LeanType keyboard hosts.

### Security
- **Bi-directional SHA-256 certificate pinning**: The host validates the plugin signature, and the plugin enforces caller UID package name and certificate verification (`CallerCheck`).
- **Encrypted credential storage**: API keys are encrypted at rest using AES-256-GCM backed by the Android Keystore (`CredentialStore`).
- **Strict single-ownership PFD streaming**: Media files are passed to the host via read-only `ParcelFileDescriptor` to guarantee zero memory overhead on the IME main thread.
- **Magic byte validation**: Thumbnails and media files are verified for genuine GIF, WebP, PNG, or JPEG file headers.

## Supported Providers
- **Google Tenor** (v2 API)
- **Klipy** (v1 API)

## Building

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires keystore.properties configured)
./gradlew assembleRelease
```
