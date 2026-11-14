# WinPocket

An Android app that runs the Windows version of Hollow Knight on a phone with one tap.

WinPocket is a modified build of [Winlator](https://github.com/brunodev85/winlator) by brunodev85. Winlator runs Windows (x86_64) programs on Android with Wine and Box64. WinPocket keeps all of that and adds a home screen that sets the game up for you.

The game is not included. You need your own copy.

## Install

1. Open the [latest release](https://github.com/moblized/moblized-winpocket/releases/tag/latest) on the phone and download `WinPocket.apk`.
2. Install it. If the official Winlator app is installed, uninstall it first: both apps use the package name `com.winlator`, so only one can be on the phone.
3. Open WinPocket, allow storage access, and wait for the system files to install.

## Home screen shortcuts

Once the game is found, tap **Add to home screen**. The shortcut starts the game straight away, with the settings from WinPocket's game screen at the time you tap it, so change a setting in WinPocket and the shortcut picks it up.

- The icon is the game's own, read from your copy of the game. If it cannot be read, the tile shows the game's initials.
- The tile is dark or light to match WinPocket's theme in Settings, like the WinPocket icon. Switching the theme redraws shortcuts already on the home screen the next time WinPocket opens.
- If the game is not ready yet (WinPocket's first-time setup is not finished, or the game folder moved), the shortcut opens WinPocket instead.
- Tapping Add to home screen again when the shortcut is already there just refreshes its icon.
- A shortcut left over from Schedule I, which WinPocket no longer includes, is greyed out. Remove it from the home screen.

## First-time setup

1. On your PC, open Steam, right-click Hollow Knight, then Manage, then Browse local files.
2. Copy that whole folder to the phone's `Download` folder. Hollow Knight is about 9 GB.
3. In WinPocket, tap **Find game**, then **Play**.

Hollow Knight's Steam copy starts without the Steam client. Steam Cloud and achievements do not work on the phone.

## Controls

The on-screen buttons are an Xbox controller, laid out like one and sending the same buttons. Hollow Knight sees a controller, so its on-screen prompts and tutorials name the same buttons you see.

| Button | What it does in Hollow Knight |
|---|---|
| Left stick | Move, aim attacks up and down, move through menus |
| A (green) | Jump. Confirm in menus |
| X (blue) | Attack. Hold to charge Nail Arts |
| B (red) | Focus/Cast. Hold to heal with Focus. Back in menus |
| Y (yellow) | Dream Nail. Hold to use it |
| RT | Dash |
| RB | Quick Cast |
| LT | Super Dash (Crystal Heart) |
| LB | Quick Map |
| VIEW | Inventory |
| MENU | Pause |
| Right stick (small, bottom right) | Look up and down |

These are the game's default controller buttons. If you remapped the controller in Hollow Knight's own settings, the game follows your mapping.

- **On-screen touch controls** shows those buttons. Untick it if you play with a real controller.
- **Floating joystick** puts the left stick under your thumb: touch anywhere on the left half of the screen that is not a button, and it appears there. It returns to its place when you lift your finger. The small right stick stays where it is.
- **Joystick size** sets how big both sticks are, from 50% to 250%.
- **Tap where you touch** makes a tap act at the spot you touch, instead of steering a mouse pointer like a trackpad. It is the same setting as "Move Cursor to Touchpoint" in Settings.
- A Bluetooth or USB controller works through Winlator's gamepad support.
- To move or resize the buttons, open the side menu, then Input Controls, and edit the profile "Hollow Knight (touch)". An app update that ships a new layout replaces that profile.

## Screen

- **Fill screen (widescreen)** runs the game at the phone's own shape. Hollow Knight needs a game version with widescreen support (February 2026 update or later); older versions still show black bars.
- **Fill screen (stretched)** runs the game at 16:9 and stretches the picture over the whole screen.
- **16:9 with black bars** leaves the picture as it is.

## Saves

On the PC, saves are in `%USERPROFILE%\AppData\LocalLow\Team Cherry`. Copy that `Team Cherry` folder to `Download/WinPocket/saves` on the phone, then tap **Import saves**. **Export saves** writes the phone's saves back to `Download/WinPocket/saves/Team Cherry`.

Press **Play** once before importing, so the game's container exists.

## If the game crashes or stutters

- Tick **Compatibility mode**. It switches Box64 to its Stability preset.
- Everything Winlator offers is still in the side menu: Containers, Shortcuts, Input Controls, Settings. The game runs from a container and a shortcut named Hollow Knight, and you can edit either one.
- If you set up Schedule I in an earlier version, its container is still on the phone. Delete the "Schedule I" container under Containers to free the space.
- Phones with a Snapdragon chip run this best. Other chips use Winlator's Vortek driver and can be slower.

## What WinPocket changes

Changes made to Winlator 11.2 (upstream commit `2c33741`), November 2026:

- New home screen, `GameLauncherFragment`, with `GameSetup`: finds the game folder, creates a container, writes the shortcut, imports and exports saves. `GameProfile` holds the game's details.
- A bundled touch layout, `assets/winpocket/hollow_knight_touch.icp`, that acts as a virtual Xbox controller.
- Buttons in a layout can have their own colour, an optional `color` field such as `"#6BBF3A"` (`ControlElement`, `ControlsProfile`).
- Home-screen game shortcuts (`GameShortcuts`, `GameShortcutActivity`), with the icon read from the game's exe (`ExeIconReader`). Play and the shortcuts share one start-up path (`GameStarter`).
- A shortcut can choose "Move Cursor to Touchpoint" for itself (`XServerDisplayActivity`).
- Screen modes on the home screen, including a stretch mode that can be on from launch (`GLRenderer.setFullscreen`), and correct touch mapping while stretched (`TouchpadView`).
- A joystick bound to keys works as an 8-way stick (`ControlElement`).
- A floating joystick option (`InputControlsView`) that only ever moves a stick on the left half, and a joystick size setting.
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

Hollow Knight is a trademark of Team Cherry. Xbox is a trademark of Microsoft. WinPocket is not affiliated with or endorsed by Team Cherry, Microsoft, Valve or the Winlator project.

## License

LGPL-2.1, the same as Winlator. See [LICENSE](LICENSE).
