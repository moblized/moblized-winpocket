# WinPocket

An Android app that runs the Windows versions of Hollow Knight and Schedule I on a phone with one tap.

WinPocket is a modified build of [Winlator](https://github.com/brunodev85/winlator) by brunodev85. Winlator runs Windows (x86_64) programs on Android with Wine and Box64. WinPocket keeps all of that and adds a home screen that sets each game up for you.

The games are not included. You need your own copies.

## Install

1. Open the [latest release](https://github.com/moblized/moblized-winpocket/releases/tag/latest) on the phone and download `WinPocket.apk`.
2. Install it. If the official Winlator app is installed, uninstall it first: both apps use the package name `com.winlator`, so only one can be on the phone.
3. Open WinPocket, allow storage access, and wait for the system files to install.

## Choosing a game

Pick the game at the top of the home screen. Each game has its own container, shortcut, touch layout, settings and saves, so setting one up does not change the other.

## First-time setup

1. On your PC, open Steam, right-click the game, then Manage, then Browse local files.
2. Copy that whole folder to the phone's `Download` folder. Hollow Knight is about 9 GB.
3. In WinPocket, pick the game, tap **Find game**, then **Play**.

Hollow Knight's Steam copy starts without the Steam client. Steam Cloud and achievements do not work on the phone.

## Schedule I and Steam

Schedule I is tied to your Steam account, and it may close as it starts when the Steam client is not running. If that happens:

1. On the Schedule I screen, tap **Install Steam**. WinPocket downloads Valve's installer, installs Steam into the Schedule I container, and opens it. Steam updates itself first, which can take several minutes.
2. Sign in with the account that owns Schedule I and tick **Remember me**. Then open the side menu and tap **Exit**.
3. Leave **Start Steam with the game** ticked and tap **Play**. WinPocket starts Steam in the background, waits until you are signed in, then starts the game. When you quit the game, Steam closes too.

**Open Steam** opens the Steam window again, to sign in or switch accounts. The Steam client running under Wine is the least tested part of WinPocket. If its window stays blank, exit and tap Open Steam again.

## Controls

- **On-screen touch controls** uses a layout mapped to the game's default keyboard keys, with a joystick for movement. Untick it if you play with a controller.
- **Floating joystick** puts the joystick under your thumb: touch anywhere on the left half of the screen that is not a button, and it appears there. It returns to its place when you lift your finger.
- **Joystick size** sets how big the joystick is, from 50% to 250%.
- **Tap where you touch** makes a tap act at the spot you touch, instead of steering a mouse pointer like a trackpad. For Hollow Knight it is the same setting as "Move Cursor to Touchpoint" in Settings.
- **Mouse look** (Schedule I) turns the camera when you swipe the right half of the screen. It sends your finger's movement as relative mouse motion, which is what first-person games read. It and Tap where you touch cannot both be on. If the camera does not turn, untick it.
- A Bluetooth or USB controller works through Winlator's gamepad support.
- To move or resize the buttons, open the side menu, then Input Controls, and edit the profile "Hollow Knight (touch)" or "Schedule I (touch)". An app update that ships a new layout replaces that profile.

### Schedule I touch layout

| Button | Key | What it does |
|---|---|---|
| Joystick | W A S D | Walk |
| RUN | Shift | Sprint. Stays on until you tap it again |
| CROUCH | Ctrl | Crouch. Stays on until you tap it again |
| CLICK | Left mouse button | Use the item in your hand, click in menus. Hold it and swipe to drag |
| R-CLICK | Right mouse button | Pick up furniture |
| E | E | Interact |
| JUMP | Space | Jump |
| PHONE | Tab | Open the phone |
| LIGHT | F | Flashlight |
| Number strip | 1 to 0 | Pick a hotbar slot. Swipe the strip to see more numbers |
| MAP, TEXTS, JOURNAL | M, T, J | Open those screens |
| VIEW | V | Hold for the third-person view |
| MENU | Esc | Pause menu |

Swipe anywhere else on the right side to look around. A quick tap there clicks.

## Screen

- **Fill screen (widescreen)** runs the game at the phone's own shape. Hollow Knight needs a game version with widescreen support (February 2026 update or later); older versions still show black bars.
- **Fill screen (stretched)** runs the game at 16:9 and stretches the picture over the whole screen.
- **16:9 with black bars** leaves the picture as it is.

## Saves

- Hollow Knight: on the PC, saves are in `%USERPROFILE%\AppData\LocalLow\Team Cherry`. Copy that `Team Cherry` folder to `Download/WinPocket/saves` on the phone.
- Schedule I: on the PC, saves are in `%USERPROFILE%\AppData\LocalLow\TVGS`. Copy that `TVGS` folder to `Download/WinPocket/saves` on the phone. Schedule I keeps each Steam account's saves in a folder named after the account, so use the same account on both.

Then pick the game and tap **Import saves**. **Export saves** writes the phone's saves back to the same place, for example `Download/WinPocket/saves/TVGS`.

Press **Play** once before importing, so the game's container exists.

## If the game crashes or stutters

- Tick **Compatibility mode**. It switches Box64 to its Stability preset.
- Schedule I is heavier than Hollow Knight. Set the graphics quality to Low in its settings.
- Everything Winlator offers is still in the side menu: Containers, Shortcuts, Input Controls, Settings. Each game runs from a container and a shortcut named after it, and you can edit either one.
- Phones with a Snapdragon chip run this best. Other chips use Winlator's Vortek driver and can be slower.

## What WinPocket changes

Changes made to Winlator 11.2 (upstream commit `2c33741`), November 2026:

- New home screen, `GameLauncherFragment`, with `GameSetup`: finds the game folder, creates a container, writes the shortcut, imports and exports saves. `GameProfile` holds what differs between the supported games.
- Bundled touch layouts, `assets/winpocket/hollow_knight_touch.icp` and `assets/winpocket/schedule_i_touch.icp`.
- For games tied to Steam, an optional batch launcher (`C:\winpocket\<game>_steam.bat` in the container) that starts the Steam client, waits for a signed-in account, then starts the game. Steam is installed from Valve's own installer.
- A shortcut can turn on relative mouse movement and choose "Move Cursor to Touchpoint" for itself (`XServerDisplayActivity`).
- Screen modes on the home screen, including a stretch mode that can be on from launch (`GLRenderer.setFullscreen`), and correct touch mapping while stretched (`TouchpadView`).
- A joystick bound to keys works as an 8-way stick (`ControlElement`).
- A floating joystick option (`InputControlsView`) and a joystick size setting.
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

Hollow Knight is a trademark of Team Cherry. Schedule I is a trademark of TVGS. WinPocket is not affiliated with or endorsed by Team Cherry, TVGS, Valve or the Winlator project.

## License

LGPL-2.1, the same as Winlator. See [LICENSE](LICENSE).
