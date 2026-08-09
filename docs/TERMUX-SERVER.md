# Running the opencode server on your phone (Termux)

The app is a *client* for the **opencode server** (`opencode serve`). The server can
run anywhere — a PC, a VPS, or on the phone itself. This guide explains how to run
it **on the phone via Termux**, so you get a local agent with full tool access
(file editing, terminal, MCP) without buying a second machine.

> **Why Termux instead of a built-in server?**
> Recent app versions removed the bundled on-device binary to keep the APK light;
> the binary's runtime also can't run under Android 11/seccomp. Running opencode
> under **Termux** gives the binary a real Unix environment (shell, libs, storage).

---

## Architecture

```
┌──────────────────────────────  phone  ─────────────────────────────┐
│                                                                    │
│  ┌──────────────┐        localhost / Wi‑Fi       ┌──────────────┐  │
│  │  opencode-app │ ──── HTTP + SSE ────────────► │  opencode    │  │
│  │  (this APK)   │  (Basic auth, /session,       │  serve       │  │
│  │               │   /event, /model …)           │  (Termux)    │  │
│  └──────────────┘                                └──────┬───────┘  │
│                                                         │ HTTPS   │
│                                                         ▼          │
│                                                  OpenCode Zen /   │
│                                                  any provider     │
└────────────────────────────────────────────────────────────────────┘
```

The app talks only to `opencode serve`. The agent itself (tools, terminal, MCP)
lives inside the Termux process.

---

## Prerequisites

- **Termux from F-Droid** (the Google Play build is outdated and unmaintained).
  Install once: <https://f-droid.org/en/packages/com.termux/>
- Run once and grant storage permission:

  ```bash
  termux-setup-storage
  ```

- **Recommended:** Android 12+ (Android 11 and older may hit the seccomp limit
  described above — see "Compatibility check").
- For LAN access: phone and any client on the **same Wi‑Fi network**.

---

## Step 0 — Compatibility check (5 minutes)

Grab the opencode binary and see whether this device can run it.

### Path A — Termux native (fastest)

```bash
pkg update && pkg upgrade -y
pkg install -y curl
cd ~

curl -fsSLo opencode.tgz \
  https://github.com/anomalyco/opencode/releases/download/v1.18.15/opencode-linux-arm64-musl.tar.gz
tar -xzf opencode.tgz
chmod +x opencode

./opencode --version
```

- A version string → **this device is compatible**, continue to Step 1.
- `Bad system call` (exit 159) → seccomp blocked it (typical on Android ≤ 11).
  Continue with **Path B** below.
- `cannot execute: required file not found` → download was truncated; re-run the
  `curl` step.

### Path B — proot (Debian/Ubuntu userspace)

A full Linux userspace that isolates the process from some sandbox limits:

```bash
pkg install -y proot-distro
proot-distro install ubuntu            # or: proot-distro install debian
proot-distro login ubuntu
```

Inside the guest:

```bash
apt update && apt install -y curl
cd ~

curl -fsSLo opencode.tgz \
  https://github.com/anomalyco/opencode/releases/download/v1.18.15/opencode-linux-arm64-musl.tar.gz
tar -xzf opencode.tgz
chmod +x opencode

./opencode --version
```

> If **Path B also** ends with `Bad system call`, the device's seccomp blocks the
> runtime no matter what userspace you use. In that case the phone itself can't host
> opencode — run the server on a PC/VPS (or an Android 12+ phone) and use the LAN
> steps below.

**Use the same terminal for the rest of this guide** — remember whether you are in
the proot guest (Path B) or plain Termux (Path A). The commands are identical;
only the environment differs.

---

## Step 1 — Configure models (optional but recommended)

opencode comes with **OpenCode Zen** configured; its **free models work without any
API key**. The default model is already the free tier, so you can skip this step.

If you have a Zen API key (or want to pin a model), create the config:

```bash
mkdir -p ~/.config/opencode
cat > ~/.config/opencode/opencode.json <<'EOF'
{
  "model": "opencode/deepseek-v4-flash-free",
  "provider": {
    "opencode": {
      "apiKey": ""          // leave empty for free models, or paste your key
    }
  }
}
EOF
```

Model ids (all free, no key needed):

```
opencode/deepseek-v4-flash-free
opencode/mimo-v2.5-free
opencode/ling-3.0-tiny-free
```

You can also authenticate interactively later with `./opencode auth login`.

---

## Step 2 — Start the server

```bash
cd ~
OPENCODE_SERVER_PASSWORD=change-me ./opencode serve --port 3000
```

- Default hostname is `127.0.0.1` — only reachable from the same phone. Good for
  privacy; the app on the same phone connects fine.
- `OPENCODE_SERVER_PASSWORD` enables HTTP **Basic auth**. The username is
  `opencode` by default (`OPENCODE_SERVER_USERNAME` to override). Set a password
  whenever the server is reachable beyond the phone itself.
- Verify from another Termux session:

  ```bash
  curl -u opencode:change-me http://127.0.0.1:3000/health
  ```

  A 2xx (e.g. `{}` or `ok`) means the server is up. **401** means the password/env
  isn't being read — restart with the variable set in the same shell.

### Keep it running (background)

The `serve` command stays in the foreground. Keep it alive across sessions with
**tmux** (simplest) or **termux-services**:

```bash
pkg install -y tmux
tmux new -s oc -d 'OPENCODE_SERVER_PASSWORD=change-me ./opencode serve --port 3000'
tmux attach -t oc     # to see logs again
```

Auto-start on boot with `termux-services`:

```bash
pkg install -y termux-services
# create $PREFIX/etc/termux-services/opencode.sh:
#   #!/data/data/com.termux/files/usr/bin/env bash
#   export OPENCODE_SERVER_PASSWORD=change-me
#   exec $HOME/opencode serve --port 3000
chmod +x $PREFIX/etc/termux-services/opencode.sh
sv-enable opencode
```

---

## Step 3 — Connect the app

### Same phone (recommended)

1. Open the app. If no server is saved yet, the **connect** screen appears.
2. Enter:
   - Server: `http://127.0.0.1:3000` (when both server and app are on the phone)
   - Username: `opencode`
   - Password: your `OPENCODE_SERVER_PASSWORD`
3. Connect. The connection is **remembered** — next app launches go straight to
   your sessions. To switch servers later, use Settings → "Kayıtlı sunucu
   bağlantısını sil".

### Any device on the same Wi‑Fi (LAN)

Start the server bound to all interfaces:

```bash
OPENCODE_SERVER_PASSWORD=change-me ./opencode serve --port 3000 --hostname 0.0.0.0
```

Find the phone's IP:

```bash
ifconfig    # look for inet on wlan0, e.g. 192.168.1.20
```

Then in the app on any device: `http://192.168.1.20:3000`, user `opencode`, same
password. **Keep the password set** — binding to `0.0.0.0` exposes the server to
the whole network.

> If the phone runs the server and the client is the same phone, prefer `127.0.0.1`
> and keep `--hostname` at the default — no firewall exposure at all.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `401 Unauthorized` on `/health` | `OPENCODE_SERVER_PASSWORD` not loaded in the server process | Start with `OPENCODE_SERVER_PASSWORD=... ./opencode serve …` in the *same* shell; restart |
| `Connection refused` | Server not running or wrong port | `curl -u opencode:pass http://127.0.0.1:3000/health`; check tmux/svc is alive |
| App connects but no models | Zen unreachable or config wrong | Check internet; verify `./opencode --version`; set `model` in `opencode.json` |
| Timeout from another device | Wi‑Fi isolation / wrong subnet | Same network; server started with `--hostname 0.0.0.0`; test `curl` from that device |
| `Bad system call` (exit 159) | Android ≤ 11 seccomp blocks Bun | Use Path B (proot); if it also fails, host on a PC / Android 12+ device |
| `required file not found` | Truncated download | Re-run `curl` (the tarball is ~120 MB) |
| Empty/cached sessions | Room cache is local | Expected: server sessions are separate from the app's on‑device cache |

---

## Security notes

- Always run with `OPENCODE_SERVER_PASSWORD` set when the port is reachable beyond
  `127.0.0.1`.
- Don't expose the server to the public internet with Basic auth alone — the
  traffic is plaintext HTTP.
- For remote access outside your home network, prefer a VPN (e.g. Tailscale) and
  keep the server bound to the VPN interface.
- The phone's app data (sessions cache) stays on the device.

---

## Related

- Full server docs: <https://opencode.ai/docs/server/>
