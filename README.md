# opencode-android

A Material You (Material 3) Android client that connects to **opencode** — the open source coding agent.

## How it works

- Connects to a remote **opencode server** (`opencode serve`). The APK stays light — model execution and token handling happen server-side.
- Sessions and messages are cached on-device with **Room** (in Android app data), so history survives restarts.
- Live replies stream from the server's **SSE** endpoint (`/event`); tool states, reasoning, and streaming text update instantly and fluidly.
- Includes model selection/switching, file attachments (SAF), and OpenAI-compatible custom provider registration.

## Features

- Connection: URL + Basic auth (username / password)
- Session list, create, delete — everything cached locally
- Chat: streaming, stop (abort/interrupt), reasoning, tool state cards
- Model picker (when creating a session) + in-session model switching
- File attachment: `*/*` document picker, contents sent along with the message
- Token & cost display, custom provider registration (config PATCH)
- Material You: dynamic color (Android 12+), dark/light theme, animated message/list transitions

## Server setup

Start the server on any device/host:

```bash
opencode serve --port 3587
```

Optionally password-protect it:

```bash
OPENCODE_SERVER_PASSWORD=mysecret opencode serve --port 3587
```

In the app, enter the server address, e.g. `http://192.168.1.20:3587`.

## Build

GitHub Actions (`.github/workflows/build.yml`) produces APKs on every push:

- `app/build/outputs/apk/debug/*.apk` — always (debug-signed)
- `app/build/outputs/apk/release/*.apk` — **signed** when the repo secrets below exist:

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64 of the `.jks` keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Alias name |
| `KEY_PASSWORD` | Alias password |

Generate a keystore:

```bash
keytool -genkey -v -keystore keystore.jks -alias opencode -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 keystore.jks   # paste the output into KEYSTORE_BASE64
```

## Embedded server

The app bundles the `opencode-linux-arm64-musl` binary (built into the APK in CI)
and can run it **on-device** — no external server, no Termux needed:

- Default `embedded` mode: on first launch the app starts its own loopback server
  (`127.0.0.1:<port>`) and auto-connects. OpenCode Zen **free** models
  (`opencode/deepseek-v4-flash-free`, etc.) are used out of the box, no API key
  required. An optional Zen key can be added in Settings.
- **Requires Android 12+ (API 31+).** On Android ≤ 11 the system seccomp policy
  blocks Bun's runtime (the bundled binary dies with `Bad system call`), so the app
  shows a clear message and falls back to the connect screen.
- The binary is pinned and downloaded in `.github/workflows/build.yml`
  (step "Bundle embedded opencode binary"), cached and placed at
  `app/src/main/assets/opencode_bin/opencode`.
- To use a remote server instead, switch off "Cihaz iç sunucu" in Settings and
  connect normally.

### Running the server on your phone (Termux)

On devices where the embedded binary can't run (or when you want the full agent with
file/terminal/MCP tool access), host `opencode serve` yourself in Termux — the app
then connects to `127.0.0.1` or your phone's LAN address.

→ **Full step-by-step guide: [docs/TERMUX-SERVER.md](docs/TERMUX-SERVER.md)**
(Termux-native and proot installs, config, background keep-alive, LAN access,
troubleshooting).

## Releases

`VERSION` at the repo root holds the current version (`1.0.0`). Each APK is versioned from it:
run the **Build APK** workflow manually (`workflow_dispatch`) with:

- `release` = `true` → tags `vX.Y.Z`, uploads both APKs to a GitHub Release, then bumps `VERSION`.
- `bump` = `patch` | `minor` | `major` → next-version bump after the release (default `patch`).

## License

AGPL-3.0 — see [LICENSE](LICENSE). Built on opencode (AGPL-3.0); the derivative stays open under the same license.

## Development

Open the root folder in Android Studio. Requirements: minSdk 26 (Android 8.0+).

Stack: Kotlin · Jetpack Compose · Material 3 (Material You) · OkHttp + okhttp-sse · kotlinx.serialization · Room · DataStore · Navigation Compose.