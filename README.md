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

## License

AGPL-3.0 — see [LICENSE](LICENSE). Built on opencode (AGPL-3.0); the derivative stays open under the same license.

## Development

Open the root folder in Android Studio. Requirements: minSdk 26 (Android 8.0+).

Stack: Kotlin · Jetpack Compose · Material 3 (Material You) · OkHttp + okhttp-sse · kotlinx.serialization · Room · DataStore · Navigation Compose.