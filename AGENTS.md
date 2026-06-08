# AGENTS.md

## Cursor Cloud specific instructions

### Product overview

This repo contains **UmaVPN for Android** (`uma-vpn-android/`): a Kotlin Android app that fetches VPN server lists from umapyoi.net and delegates tunneling to **OpenVPN for Android** via AIDL. There is no backend, Docker stack, or npm/pip dependency tree.

### Prerequisites (one-time VM setup)

The Android SDK is installed under `$HOME/Android/Sdk` with:

- `platform-tools`
- `platforms;android-34`
- `build-tools;34.0.0`
- `emulator` + `system-images;android-34;google_apis;x86_64` (optional, for on-VM device testing)

JDK 17+ is required (JDK 21 on this VM works). `ANDROID_SDK_ROOT` / `ANDROID_HOME` are set in `~/.bashrc`.

Create `uma-vpn-android/local.properties` (gitignored) if missing:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > uma-vpn-android/local.properties
```

### Build, lint, and test

All commands run from `uma-vpn-android/`:

| Task | Command | Notes |
|------|---------|-------|
| Debug APK | `./gradlew assembleDebug` | Output: `app/build/outputs/apk/debug/app-debug.apk` |
| Unit tests | `./gradlew test` | No unit test sources yet; task succeeds with nothing to run |
| Lint | `./gradlew lint` | **Fails today** with 2 pre-existing errors in `VpnTileService.kt` (`StartActivityAndCollapseDeprecated`) |
| Install on device | `./gradlew installDebug` | Requires a connected device/emulator via ADB |

See `uma-vpn-android/README.md` for full product and architecture docs.

### Running on an emulator in Cloud VMs

An AVD named `uma_vpn_api34` may exist. Start with:

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/platform-tools"
emulator -avd uma_vpn_api34 -no-audio -gpu swiftshader_indirect -accel off
```

**Gotcha:** Cloud VMs typically lack `/dev/kvm`, so the emulator runs in software mode and boots very slowly (10+ minutes). APK install may fail until `sys.boot_completed=1` and the package manager/storage services are fully up; retry `adb install -r -g` after waiting. For day-to-day agent work, **`assembleDebug` + `test` are the reliable checks**; full in-emulator UI testing is best done on a physical device or a KVM-enabled host.

### External services (no local setup)

The app calls these at runtime (no API keys):

- `https://umapyoi.net/api/v1/vpn/cygames` — server list
- `http://ip-api.com/batch` — GeoIP filtering
- `https://api-umamusume.cygames.jp/` — connectivity check

### Release builds

Release signing needs `keystore.properties` + `keystore.jks` (both gitignored). Debug builds need no secrets.
