# UmaVPN for Android

A lightweight Android app that automates connecting to the best available VPN server for playing **Umamusume: Pretty Derby (Global)** from regions where the game is not officially available.

## How it works

1. When you tap the **Quick Settings tile** (or the in-app button), the app:
   - Fetches the current list of VPN servers from [api.umavpn.top](https://api.umavpn.top/api/server?sites=umag), which automatically verifies each server works with Umamusume Global and sorts them by ping.
   - Picks the fastest available server (lowest ping to Cygames).
   - Passes the full OpenVPN profile (embedded in the API response) directly to **OpenVPN for Android** via its documented IPC API.
   - Reports success or failure via the tile subtitle and the in-app status screen.

2. To disconnect, tap the tile again (or press **Disconnect** in the app).

## Requirements

| Requirement | Notes |
|---|---|
| Android 8.0+ (API 26) | Minimum supported version |
| [OpenVPN for Android](https://play.google.com/store/apps/details?id=de.blinkt.openvpn) | Free app by Arne Schwabe — handles the actual VPN tunnel |
| Internet access | To fetch the server list on connect |

> **Why a separate app?**  
> Android does not allow third-party apps to implement the OpenVPN protocol directly without bundling native code (NDK). Using OpenVPN for Android's published external IPC API is the cleanest, most maintainable approach and avoids shipping large native binaries.

## Features

- **Quick Settings tile** — Add "UmaVPN" to your notification shade quick panel and toggle the VPN with a single tap, without opening the app.
- **Automatic best-server selection** — The app always picks the lowest-latency server that is verified to work with Cygames. No manual selection needed.
- **Real-time status** — The tile subtitle and in-app screen show connection state (fetching, connecting, connected + IP + ping, error).
- **Zero configuration** — No accounts, no sign-ups, no stored credentials.

## Building

### Prerequisites

- JDK 17+
- Android SDK with Build Tools 34 and Platform 34
- [Android Studio](https://developer.android.com/studio) (recommended) or the Android command-line tools

### Build with Android Studio

1. Clone this repository.
2. Open the `uma-vpn-android/` directory in Android Studio.
3. Let Gradle sync finish.
4. Connect your device (or start an emulator) and click **Run**.

### Build from command line

```bash
cd uma-vpn-android
./gradlew assembleDebug
# APK will be at app/build/outputs/apk/debug/app-debug.apk
```

### Install on a connected device

```bash
./gradlew installDebug
```

## First-time setup

1. Install **OpenVPN for Android** from the Play Store.
2. Install **UmaVPN** on your device.
3. Open UmaVPN — it will ask for permission to control OpenVPN for Android. Tap **Allow**.
4. Android may also show a system VPN permission dialog — tap **OK**.
5. Add the **UmaVPN** tile to your Quick Settings panel:
   - Pull down the notification shade fully.
   - Tap the pencil/edit icon.
   - Find the UmaVPN tile and drag it to the active area.

After setup, just tap the tile to connect before launching Umamusume.

## Architecture

```
com.umavpn/
├── UmaVpnApplication.kt      — Application class; eagerly boots UmaVpnManager
├── UmaVpnManager.kt          — Singleton; owns AIDL binding + connect/disconnect lifecycle
├── MainActivity.kt           — Status screen with toggle button
├── VpnTileService.kt         — Quick Settings TileService
├── api/
│   └── UmaVpnApiClient.kt    — HTTP client for api.umavpn.top (Global: sites=umag, no JP filter)
└── model/
    ├── VpnServer.kt           — API response model
    └── ConnectionState.kt     — Sealed class representing VPN state

de.blinkt.openvpn.api/        — Copied AIDL + Parcelable from ics-openvpn's external API
├── APIVpnProfile.java
├── APIVpnProfile.aidl
├── IOpenVPNAPIService.aidl
└── IOpenVPNStatusCallback.aidl
```

## Server source

Servers are pulled from **[umavpn.top](https://www.umavpn.top)** via `api.umavpn.top`, a public API that:
- Draws servers from [VPN Gate](https://www.vpngate.net/en/) (a free, volunteer-run VPN relay service operated by University of Tsukuba).
- Automatically tests each one against `https://api.games.umamusume.jp/` (Cygames) and `https://bitcoin.dmm.com/` (DMM login).
- Sorts verified servers by ping and refreshes roughly every hour.

The API is used read-only — no data is sent to it.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "OpenVPN for Android required" banner | Install the free [OpenVPN for Android](https://play.google.com/store/apps/details?id=de.blinkt.openvpn) app |
| Connection error / CONNECTRETRY | The chosen server went offline between the API check and the connect attempt. Tap **Retry** to try the next best server |
| Permission denied | Open the UmaVPN app and go through the permission steps manually |
| Tile doesn't appear | The system tile service registers when the app is first installed; a reboot or toggling the app's notification permission may help |

## Credits

- VPN server data: [umavpn.top](https://www.umavpn.top) (Nasu VPN Checker)
- OpenVPN client: [ics-openvpn](https://github.com/schwabe/ics-openvpn) by Arne Schwabe (Apache 2.0)
- VPN relay servers: [VPN Gate](https://www.vpngate.net/) — University of Tsukuba
