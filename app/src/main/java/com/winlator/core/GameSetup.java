package com.winlator.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.preference.PreferenceManager;

import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Drive;
import com.winlator.container.GraphicsDrivers;
import com.winlator.container.Shortcut;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.InputControlsManager;
import com.winlator.widget.FrameRating;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;

/**
 * WinPocket addition (not part of upstream Winlator).
 * One-tap setup for the PC games in {@link GameProfile}: locates the game folder the user copied
 * to the phone, prepares a dedicated container, writes a shortcut for it, moves save files in and
 * out and, for games that need it, starts the Steam client before the game.
 */
public abstract class GameSetup {
    public static final String PREF_SELECTED_GAME = "winpocket_selected_game";
    public static final String PREF_GAME_PATH = "game_exec_path";
    public static final String PREF_TOUCH_CONTROLS = "game_touch_controls";
    public static final String PREF_SAFE_MODE = "game_safe_mode";
    public static final String PREF_SCREEN_MODE = "game_screen_mode";
    public static final String PREF_FLOATING_STICK = "game_floating_joystick";
    public static final String PREF_STICK_SIZE = "game_joystick_size";
    public static final String PREF_RELATIVE_MOUSE = "game_relative_mouse";
    public static final String PREF_USE_STEAM = "game_use_steam";
    public static final String PREF_CONTROLS_PROFILE_VERSION = "game_controls_profile_version";
    /** Joystick size in percent; matches the scale the bundled layouts ship with. */
    public static final int DEFAULT_STICK_SIZE = 130;
    /** The container renders at the phone's own shape, so the picture fills the screen. */
    public static final int SCREEN_MODE_WIDE = 0;
    /** The container renders at its own size and the picture is stretched over the screen. */
    public static final int SCREEN_MODE_STRETCH = 1;
    /** The container renders at its own size with black bars. */
    public static final int SCREEN_MODE_BARS = 2;
    /** Valve's official Steam installer for Windows. */
    public static final String STEAM_SETUP_URL = "https://cdn.akamai.steamstatic.com/client/installer/SteamSetup.exe";
    public static final String STEAM_SETUP_FILENAME = "SteamSetup.exe";
    /** Steam's web views need no GPU and no sandbox this way, which is what works under Wine. */
    public static final String STEAM_ARGS = "-no-cef-sandbox -cef-disable-gpu -cef-disable-gpu-compositing";
    public static final String STEAM_SHORTCUT_NAME = "Steam";
    public static final String STEAM_SETUP_SHORTCUT_NAME = "Steam Setup";
    private static final String SCREEN_ARGS_PATTERN = "\\s*-screen-(?:width|height|fullscreen)\\s+\\d+";
    private static final String WINPOCKET_DIR = "winpocket";
    private static final String[] STEAM_EXE_PATHS = {"Program Files (x86)/Steam/steam.exe", "Program Files/Steam/steam.exe"};
    private static final byte MAX_SCAN_DEPTH = 5;

    /** Settings the home screen passes to {@link #writeShortcut}. */
    public static class LaunchOptions {
        public int controlsProfileId;
        public boolean safeMode;
        public int screenMode = SCREEN_MODE_WIDE;
        public String wideScreenSize;
        public boolean floatingStick;
        public boolean touchscreenMode;
        public boolean relativeMouse;
        public boolean useSteam;
    }

    public static GameProfile getSelectedGame(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return GameProfile.byId(preferences.getString(PREF_SELECTED_GAME, GameProfile.HOLLOW_KNIGHT.id));
    }

    public static File getSavesExchangeDir(GameProfile game) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadsDir, "WinPocket/saves/"+game.savesFolderName);
    }

    public static File getContainerSavesDir(Container container, GameProfile game) {
        return new File(container.getUserDir(), game.savesRelativePath);
    }

    public static boolean isGameExecutable(GameProfile game, String filename) {
        String lower = filename.toLowerCase(Locale.ENGLISH);
        if (!lower.endsWith(".exe")) return false;
        String base = lower.substring(0, lower.length() - 4).replaceAll("[^a-z0-9]", "");
        return base.equals(game.normalizedExeName);
    }

    /** True when the folder around the exe looks like a complete Unity game install. */
    public static boolean looksComplete(File exeFile) {
        File dir = exeFile.getParentFile();
        if (dir == null) return false;
        String base = FileUtils.getBasename(exeFile.getName());
        return (new File(dir, base+"_Data")).isDirectory() && (new File(dir, "UnityPlayer.dll")).isFile();
    }

    /** Breadth-first search of shared storage, Downloads first. Call from a background thread. */
    public static File findGameExecutable(GameProfile game) {
        File storageDir = Environment.getExternalStorageDirectory();
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        File result = scan(game, downloadsDir, MAX_SCAN_DEPTH);
        if (result == null) result = scan(game, storageDir, MAX_SCAN_DEPTH);
        return result;
    }

    private static File scan(GameProfile game, File rootDir, int maxDepth) {
        if (rootDir == null || !rootDir.isDirectory()) return null;
        ArrayDeque<File> dirs = new ArrayDeque<>();
        ArrayDeque<Integer> depths = new ArrayDeque<>();
        dirs.add(rootDir);
        depths.add(0);

        while (!dirs.isEmpty()) {
            File dir = dirs.poll();
            int depth = depths.poll();
            File[] files = dir.listFiles();
            if (files == null) continue;

            for (File file : files) {
                if (file.isFile() && isGameExecutable(game, file.getName())) return file;
            }

            if (depth >= maxDepth) continue;
            for (File file : files) {
                String name = file.getName();
                if (!file.isDirectory() || name.startsWith(".") || name.equals("Android")) continue;
                dirs.add(file);
                depths.add(depth + 1);
            }
        }
        return null;
    }

    public static Container findContainer(ContainerManager manager, GameProfile game) {
        for (Container container : manager.getContainers()) {
            if (container.getName().equals(game.name)) return container;
        }
        return null;
    }

    /** Same defaults the "new container" screen starts with, under the game's name. */
    public static JSONObject createContainerData(Context context, GameProfile game) throws JSONException {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String cpuList = Container.getFallbackCPUList();

        JSONObject data = new JSONObject();
        data.put("name", game.name);
        data.put("screenSize", Container.DEFAULT_SCREEN_SIZE);
        data.put("envVars", Container.DEFAULT_ENV_VARS);
        data.put("cpuList", cpuList);
        data.put("cpuListWoW64", cpuList);
        data.put("graphicsDriver", GraphicsDrivers.getDefaultDriver(context));
        data.put("dxwrapper", Container.DEFAULT_DXWRAPPER);
        data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
        data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
        data.put("drives", Container.DEFAULT_DRIVES);
        data.put("hudMode", FrameRating.Mode.DISABLED.ordinal());
        data.put("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL);
        data.put("box64Preset", preferences.getString("box64_preset", Box64Preset.DEFAULT));
        data.put("desktopTheme", WineThemeManager.DEFAULT_DESKTOP_THEME);
        return data;
    }

    /**
     * Returns the Windows path of the exe inside the container, mapping a new drive letter
     * to the game folder when no existing drive covers it. Returns null when no letter is free.
     */
    public static String resolveDosPath(Container container, File exeFile) {
        String exePath = exeFile.getPath();
        ArrayList<String> usedLetters = new ArrayList<>();

        for (Drive drive : container.drivesIterator()) {
            usedLetters.add(drive.letter.toUpperCase(Locale.ENGLISH));
            String drivePath = StringUtils.removeEndSlash(drive.path);
            if (exePath.startsWith(drivePath+"/")) {
                return drive.letter+":"+exePath.substring(drivePath.length()).replace("/", "\\");
            }
        }

        File gameDir = exeFile.getParentFile();
        if (gameDir == null) return null;

        for (int i = 0; i < Container.MAX_DRIVE_LETTERS; i++) {
            String letter = String.valueOf((char)(i + 68));
            if (usedLetters.contains(letter)) continue;
            container.setDrives(container.getDrives()+letter+":"+gameDir.getPath());
            container.saveData();
            return letter+":\\"+exeFile.getName();
        }
        return null;
    }

    /** Inverse of StringUtils.unescapeDOSPath, in the form Shortcut expects on the Exec line. */
    public static String escapeForDesktopExec(String dosPath) {
        return dosPath.replace("\\", "\\\\\\\\").replace(" ", "\\\\ ");
    }

    public static File getShortcutFile(Container container, String name) {
        return new File(container.getUserDir(), "Desktop/"+name+".desktop");
    }

    /**
     * Creates the game shortcut, or re-points an existing one at a new path while keeping any
     * per-shortcut settings the user changed. The home screen's switches are applied on every
     * call. When Steam should start first, the shortcut runs a small batch file that does that
     * and then starts the game.
     */
    public static File writeShortcut(Container container, GameProfile game, String dosPath, LaunchOptions options) {
        File file = getShortcutFile(container, game.name);
        File desktopDir = file.getParentFile();
        if (!desktopDir.isDirectory() && !desktopDir.mkdirs()) return null;

        String targetPath = dosPath;
        if (options.useSteam && game.supportsSteam() && isSteamInstalled(container)) {
            targetPath = writeSteamLauncher(container, game, dosPath);
            if (targetPath == null) return null;
        }

        String execLine = "Exec=env WINEPREFIX=\"/home/xuser/.wine\" wine "+escapeForDesktopExec(targetPath);
        String wmClassLine = "StartupWMClass="+FileUtils.getName(dosPath);
        boolean isNew = !file.isFile();

        if (isNew) {
            String content = "[Desktop Entry]\n" +
                             "Name="+game.name+"\n" +
                             execLine+"\n" +
                             "Type=Application\n" +
                             "StartupNotify=true\n" +
                             wmClassLine+"\n";
            if (!FileUtils.writeString(file, content)) return null;
        }
        else {
            StringBuilder content = new StringBuilder();
            for (String line : FileUtils.readLines(file)) {
                if (line.startsWith("Exec=")) line = execLine;
                else if (line.startsWith("StartupWMClass=")) line = wmClassLine;
                content.append(line).append("\n");
            }
            if (!FileUtils.writeString(file, content.toString())) return null;
        }

        Shortcut shortcut = new Shortcut(container, file);
        boolean isWide = options.screenMode == SCREEN_MODE_WIDE && options.wideScreenSize != null;
        String screenSize = isWide ? options.wideScreenSize : container.getScreenSize();
        String execArgs = isNew ? game.defaultExecArgs : shortcut.getExtra("execArgs");

        shortcut.putExtra("execArgs", buildExecArgs(execArgs, screenSize));
        shortcut.putExtra("screenSize", isWide ? options.wideScreenSize : null);
        shortcut.putExtra("stretchFullscreen", options.screenMode == SCREEN_MODE_STRETCH ? "1" : null);
        shortcut.putExtra("floatingStick", options.floatingStick ? "1" : null);
        shortcut.putExtra("moveCursorToTouchpoint", options.touchscreenMode ? "1" : "0");
        shortcut.putExtra("relativeMouse", options.relativeMouse ? "1" : null);
        shortcut.putExtra("controlsProfile", options.controlsProfileId > 0 ? String.valueOf(options.controlsProfileId) : null);
        shortcut.putExtra("box64Preset", options.safeMode ? Box64Preset.STABILITY : null);
        shortcut.saveData();
        return file;
    }

    /**
     * Writes a plain shortcut for a helper program, such as the Steam installer or Steam itself.
     * Taps click where you touch and no touch layout is shown, which suits ordinary windows.
     */
    public static File writeToolShortcut(Container container, String name, String dosPath, String execArgs, String screenSize) {
        File file = getShortcutFile(container, name);
        File desktopDir = file.getParentFile();
        if (!desktopDir.isDirectory() && !desktopDir.mkdirs()) return null;

        String content = "[Desktop Entry]\n" +
                         "Name="+name+"\n" +
                         "Exec=env WINEPREFIX=\"/home/xuser/.wine\" wine "+escapeForDesktopExec(dosPath)+"\n" +
                         "Type=Application\n" +
                         "StartupNotify=true\n" +
                         "StartupWMClass="+FileUtils.getName(dosPath)+"\n";
        if (!FileUtils.writeString(file, content)) return null;

        Shortcut shortcut = new Shortcut(container, file);
        shortcut.putExtra("execArgs", execArgs != null && !execArgs.isEmpty() ? execArgs : null);
        shortcut.putExtra("screenSize", screenSize);
        shortcut.putExtra("moveCursorToTouchpoint", "1");
        shortcut.saveData();
        return file;
    }

    /**
     * Keeps whatever arguments the shortcut already has and replaces only the three that tell
     * the game which size to run at, so it always matches the container's screen.
     */
    public static String buildExecArgs(String currentArgs, String screenSize) {
        String base = (currentArgs != null ? currentArgs : "").replaceAll(SCREEN_ARGS_PATTERN, "").trim();
        String[] parts = screenSize != null ? screenSize.split("x") : new String[0];
        if (parts.length != 2) return base;

        String screenArgs = "-screen-fullscreen 1 -screen-width "+parts[0].trim()+" -screen-height "+parts[1].trim();
        return base.isEmpty() ? screenArgs : base+" "+screenArgs;
    }

    /**
     * A screen size with the phone's own proportions, 720 pixels tall. Limited to the range
     * Unity games handle well: from 16:10 up to 21:9.
     */
    public static String getWideScreenSize(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        return computeWideScreenSize(metrics.widthPixels, metrics.heightPixels);
    }

    public static String computeWideScreenSize(int displayWidth, int displayHeight) {
        float longSide = Math.max(displayWidth, displayHeight);
        float shortSide = Math.max(1, Math.min(displayWidth, displayHeight));
        float aspect = longSide / shortSide;
        int width, height;

        if (aspect >= 16.0f / 9.0f) {
            height = 720;
            width = Math.min(1680, Math.round(height * aspect / 4.0f) * 4);
        }
        else {
            width = 1280;
            height = Math.min(800, Math.round(width / aspect / 4.0f) * 4);
        }
        return width+"x"+height;
    }

    /**
     * Returns the id of the game's bundled touch layout. Imports it on first use, and replaces
     * the installed copy when a newer layout ships with the app. 0 on failure.
     */
    public static int ensureControlsProfile(Context context, GameProfile game) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String versionKey = game.pref(PREF_CONTROLS_PROFILE_VERSION);
        boolean isOutdated = preferences.getInt(versionKey, 0) < game.controlsProfileVersion;
        InputControlsManager manager = new InputControlsManager(context);
        ControlsProfile installed = null;

        for (ControlsProfile profile : manager.getProfiles()) {
            if (profile.getName().equals(game.controlsProfileName)) {
                installed = profile;
                break;
            }
        }

        if (installed != null && !isOutdated) return installed.id;

        try {
            JSONObject data = new JSONObject(FileUtils.readString(context, game.controlsProfileAsset));
            if (installed != null) manager.removeProfile(installed);
            ControlsProfile profile = manager.importProfile(data);
            if (profile == null) return 0;

            preferences.edit().putInt(versionKey, game.controlsProfileVersion).apply();
            return profile.id;
        }
        catch (Exception e) {
            return installed != null ? installed.id : 0;
        }
    }

    /** Sets the size of every joystick in an installed layout. Size is in percent. */
    public static boolean applyStickSize(Context context, int profileId, int sizePercent) {
        try {
            File file = ControlsProfile.getProfileFile(context, profileId);
            JSONObject data = new JSONObject(FileUtils.readString(file));
            String updated = setStickScale(data, sizePercent / 100.0);
            return updated == null || FileUtils.writeString(file, updated);
        }
        catch (Exception e) {
            return false;
        }
    }

    /** Returns the new layout text, or null when every joystick already has that scale. */
    public static String setStickScale(JSONObject data, double scale) throws JSONException {
        JSONArray elements = data.getJSONArray("elements");
        boolean changed = false;

        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);
            if (element.optString("type").equals("STICK") && Math.abs(element.optDouble("scale", 1.0) - scale) > 0.001) {
                element.put("scale", scale);
                changed = true;
            }
        }
        return changed ? data.toString() : null;
    }

    /** Copies the container's save folder to Download/WinPocket/saves. Background thread. */
    public static boolean exportSaves(Container container, GameProfile game) {
        File srcDir = getContainerSavesDir(container, game);
        if (!srcDir.isDirectory() || FileUtils.isEmpty(srcDir)) return false;
        return FileUtils.copy(srcDir, getSavesExchangeDir(game));
    }

    /** Copies Download/WinPocket/saves into the container's save folder. Background thread. */
    public static boolean importSaves(Container container, GameProfile game) {
        File srcDir = getSavesExchangeDir(game);
        if (!srcDir.isDirectory() || FileUtils.isEmpty(srcDir)) return false;
        return FileUtils.copy(srcDir, getContainerSavesDir(container, game), (file) -> FileUtils.chmod(file, 0771));
    }

    public static File getDriveCDir(Container container) {
        return new File(container.getRootDir(), ".wine/drive_c");
    }

    /** C:\winpocket inside the container, for files WinPocket writes there. */
    public static File getWinPocketDir(Container container) {
        return new File(getDriveCDir(container), WINPOCKET_DIR);
    }

    public static String getWinPocketDosPath(String filename) {
        return "C:\\"+WINPOCKET_DIR+"\\"+filename;
    }

    /** The installed steam.exe, or null when Steam is not installed in this container. */
    public static File findSteamExe(Container container) {
        File driveC = getDriveCDir(container);
        for (String path : STEAM_EXE_PATHS) {
            File file = new File(driveC, path);
            if (file.isFile()) return file;
        }
        return null;
    }

    public static boolean isSteamInstalled(Container container) {
        return findSteamExe(container) != null;
    }

    public static String getSteamDosPath(Container container) {
        File steamExe = findSteamExe(container);
        if (steamExe == null) return null;
        String relative = steamExe.getPath().substring(getDriveCDir(container).getPath().length());
        return "C:"+relative.replace("/", "\\");
    }

    /**
     * Writes C:\winpocket\(game id)_steam.bat, which starts Steam in the background, waits until
     * an account is signed in, then starts the game with the arguments it is given and closes
     * Steam when the game exits. Returns the batch file's Windows path, or null on failure.
     */
    public static String writeSteamLauncher(Container container, GameProfile game, String gameDosPath) {
        String steamDosPath = getSteamDosPath(container);
        if (steamDosPath == null) return null;

        File dir = getWinPocketDir(container);
        if (!dir.isDirectory() && !dir.mkdirs()) return null;

        String filename = game.id+"_steam.bat";
        String script = buildSteamLauncherScript(steamDosPath, gameDosPath, game.steamAppId);
        if (!FileUtils.writeString(new File(dir, filename), script)) return null;
        return getWinPocketDosPath(filename);
    }

    /**
     * Writes C:\winpocket\steam_setup.bat, which runs the Steam installer without questions and
     * then opens Steam with the arguments that suit Wine. Returns its Windows path, or null.
     */
    public static String writeSteamSetupScript(Container container) {
        File dir = getWinPocketDir(container);
        if (!dir.isDirectory() && !dir.mkdirs()) return null;

        String filename = "steam_setup.bat";
        String[] lines = {
            "@echo off",
            "rem Written by WinPocket. Installs Steam, then opens it so you can sign in.",
            "echo Installing Steam. This takes a minute or two.",
            "\""+getWinPocketDosPath(STEAM_SETUP_FILENAME)+"\" /S",
            "set \"STEAM_EXE=C:\\Program Files (x86)\\Steam\\steam.exe\"",
            "if not exist \"%STEAM_EXE%\" set \"STEAM_EXE=C:\\Program Files\\Steam\\steam.exe\"",
            "if not exist \"%STEAM_EXE%\" goto failed",
            "echo Opening Steam. It updates itself first, which can take several minutes.",
            "start \"\" \"%STEAM_EXE%\" "+STEAM_ARGS,
            "goto end",
            ":failed",
            "echo Steam did not install. Exit from the side menu and try again.",
            "pause",
            ":end",
        };
        if (!FileUtils.writeString(new File(dir, filename), String.join("\r\n", lines)+"\r\n")) return null;
        return getWinPocketDosPath(filename);
    }

    public static String buildSteamLauncherScript(String steamDosPath, String gameDosPath, int steamAppId) {
        int index = gameDosPath.lastIndexOf('\\');
        String gameDir = index != -1 ? gameDosPath.substring(0, index) : gameDosPath;
        String gameExe = index != -1 ? gameDosPath.substring(index + 1) : gameDosPath;
        if (gameDir.endsWith(":")) gameDir += "\\";

        String[] lines = {
            "@echo off",
            "rem Written by WinPocket. Starts Steam, waits until you are signed in, then starts the game.",
            "set \"STEAM_EXE="+steamDosPath+"\"",
            "echo Starting Steam. Sign in if Steam asks you to. The game starts when Steam is ready.",
            "start \"\" \"%STEAM_EXE%\" -silent "+STEAM_ARGS,
            "set /a TRIES=0",
            ":wait",
            "set \"ACTIVE_USER=\"",
            "for /f \"tokens=1,2,3\" %%a in ('reg query \"HKCU\\Software\\Valve\\Steam\\ActiveProcess\" /v ActiveUser 2^>nul') do if /i \"%%a\"==\"ActiveUser\" set \"ACTIVE_USER=%%c\"",
            "if not \"%ACTIVE_USER%\"==\"\" if not \"%ACTIVE_USER%\"==\"0x0\" goto game",
            "set /a TRIES+=1",
            "if %TRIES% GEQ 300 goto game",
            "timeout /t 2 /nobreak >nul 2>&1 || ping -n 3 127.0.0.1 >nul",
            "goto wait",
            ":game",
            "echo Starting the game.",
            "set SteamAppId="+steamAppId,
            "set SteamGameId="+steamAppId,
            "cd /d \""+gameDir+"\"",
            "\""+gameExe+"\" %*",
            "start \"\" \"%STEAM_EXE%\" -shutdown",
        };
        return String.join("\r\n", lines)+"\r\n";
    }
}
