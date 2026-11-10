# WinPocket

An Android app that runs the Windows version of Hollow Knight on a phone with one tap.

WinPocket is a modified build of [Winlator](https://github.com/brunodev85/winlator) by brunodev85. Winlator runs Windows (x86_64) programs on Android with Wine and Box64. WinPocket keeps all of that and adds a home screen that sets one game up for you.

The game is not included. You need your own copy.

## Install

1. Open the [latest release](https://github.com/moblized/winpocket/releases/tag/latest) on the phone and download `WinPocket.apk`.
2. Install it. If the official Winlator app is installed, uninstall it first: both apps use the package name `com.winlator`, so only one can be on the phone.
3. Open WinPocket, allow storage access, and wait for the system files to install.

## First-time setup

1. On your PC, open Steam, right-click Hollow Knight, then Manage, then Browse local files.
2. Copy that whole folder to the phone's `Download` folder. It is about 9 GB.
3. In WinPocket, tap **Find game**, then **Play**.

The Steam copy starts without the Steam client. Steam Cloud and achievements do not work on the phone.

## Controls

- **On-screen touch controls** uses a layout mapped to the game's default keyboard keys. Untick it if you play with a controller.
- A Bluetooth or USB controller works through Winlator's gamepad support.
- To move or resize the buttons, open the side menu, then Input Controls, and edit the profile "Hollow Knight (touch)".

## Saves

On the PC, saves are in `%USERPROFILE%\AppData\LocalLow\Team Cherry`.

- **Import:** copy that `Team Cherry` folder to `Download/WinPocket/saves` on the phone, then tap **Import saves**.
- **Export:** tap **Export saves**. The files appear in `Download/WinPocket/saves/Team Cherry`.

Press **Play** once before importing, so the game's container exists.

## If the game crashes or stutters

- Tick **Compatibility mode**. It switches Box64 to its Stability preset.
- Everything Winlator offers is still in the side menu: Containers, Shortcuts, Input Controls, Settings. The game runs from a container and a shortcut both named "Hollow Knight", and you can edit either one.
- Phones with a Snapdragon chip run this best. Other chips use Winlator's Vortek driver and can be slower.

## What WinPocket changes

Changes made to Winlator 11.2 (upstream commit `2c33741`), November 2026:

- New home screen, `GameLauncherFragment`, with `GameSetup`: finds the game folder, creates a container, writes the shortcut, imports and exports saves.
- A bundled touch layout, `assets/winpocket/hollow_knight_touch.icp`.
- New name and launcher icon. The icon has a light and a dark version and follows the app theme in Settings.
- The Winlator logo is replaced by the app name in the side menu, and About credits Winlator.
- Builds are signed with one fixed key and built by GitHub Actions.

The package name stays `com.winlator` because Winlator's native code and Linux userland use paths under `/data/data/com.winlator`.

## Build

Every push to `main` builds the APK with GitHub Actions and publishes it as the `latest` release. To build locally you need JDK 17, the Android SDK, NDK 24.0.8215888 and CMake 3.22.1:

```
./gradlew assembleDebug
```

## Credits

- [Winlator](https://github.com/brunodev85/winlator) by brunodev85
- GLIBC patches by [Termux Pacman](https://github.com/termux-pacman/glibc-packages)
- [Wine](https://www.winehq.org/)
- Box86/Box64 by [ptitSeb](https://github.com/ptitSeb)
- [Mesa](https://www.mesa3d.org) (Turnip, Zink, VirGL)
- [DXVK](https://github.com/doitsujin/dxvk)
- [VKD3D](https://gitlab.winehq.org/wine/vkd3d)
- [CNC DDraw](https://github.com/FunkyFr3sh/cnc-ddraw)

Hollow Knight is a trademark of Team Cherry. WinPocket is not affiliated with or endorsed by Team Cherry, Valve or the Winlator project.

## License

LGPL-2.1, the same as Winlator. See [LICENSE](LICENSE).
