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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;

/**
 * WinPocket addition (not part of upstream Winlator).
 * One-tap setup for a single PC game: locates the game folder the user copied to the phone,
 * prepares a dedicated container, writes a shortcut for it and moves save files in and out.
 */
public abstract class GameSetup {
    public static final String GAME_NAME = "Hollow Knight";
    public static final String CONTROLS_PROFILE_NAME = "Hollow Knight (touch)";
    public static final String CONTROLS_PROFILE_ASSET = "winpocket/hollow_knight_touch.icp";
    public static final String DEFAULT_EXEC_ARGS = "-force-gfx-direct";
    public static final String PREF_GAME_PATH = "game_exec_path";
    public static final String PREF_TOUCH_CONTROLS = "game_touch_controls";
    public static final String PREF_SAFE_MODE = "game_safe_mode";
    public static final String PREF_SCREEN_MODE = "game_screen_mode";
    public static final String PREF_CONTROLS_PROFILE_VERSION = "game_controls_profile_version";
    /** Raise this when the bundled touch layout changes, so installed copies get replaced. */
    public static final int CONTROLS_PROFILE_VERSION = 2;
    /** The container renders at the phone's own shape, so the picture fills the screen. */
    public static final int SCREEN_MODE_WIDE = 0;
    /** The container renders at its own size and the picture is stretched over the screen. */
    public static final int SCREEN_MODE_STRETCH = 1;
    /** The container renders at its own size with black bars. */
    public static final int SCREEN_MODE_BARS = 2;
    private static final String SCREEN_ARGS_PATTERN = "\\s*-screen-(?:width|height|fullscreen)\\s+\\d+";
    private static final String SAVES_RELATIVE_PATH = "AppData/LocalLow/Team Cherry";
    private static final String NORMALIZED_GAME_NAME = "hollowknight";
    private static final byte MAX_SCAN_DEPTH = 5;

    public static File getSavesExchangeDir() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadsDir, "WinPocket/saves/Team Cherry");
    }

    public static File getContainerSavesDir(Container container) {
        return new File(container.getUserDir(), SAVES_RELATIVE_PATH);
    }

    public static boolean isGameExecutable(String filename) {
        String lower = filename.toLowerCase(Locale.ENGLISH);
        if (!lower.endsWith(".exe")) return false;
        String base = lower.substring(0, lower.length() - 4).replaceAll("[^a-z0-9]", "");
        return base.equals(NORMALIZED_GAME_NAME);
    }

    /** True when the folder around the exe looks like a complete Unity game install. */
    public static boolean looksComplete(File exeFile) {
        File dir = exeFile.getParentFile();
        if (dir == null) return false;
        String base = FileUtils.getBasename(exeFile.getName());
        return (new File(dir, base+"_Data")).isDirectory() && (new File(dir, "UnityPlayer.dll")).isFile();
    }

    /** Breadth-first search of shared storage, Downloads first. Call from a background thread. */
    public static File findGameExecutable() {
        File storageDir = Environment.getExternalStorageDirectory();
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        File result = scan(downloadsDir, MAX_SCAN_DEPTH);
        if (result == null) result = scan(storageDir, MAX_SCAN_DEPTH);
        return result;
    }

    private static File scan(File rootDir, int maxDepth) {
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
                if (file.isFile() && isGameExecutable(file.getName())) return file;
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

    public static Container findContainer(ContainerManager manager) {
        for (Container container : manager.getContainers()) {
            if (container.getName().equals(GAME_NAME)) return container;
        }
        return null;
    }

    /** Same defaults the "new container" screen starts with, under a fixed name. */
    public static JSONObject createContainerData(Context context) throws JSONException {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String cpuList = Container.getFallbackCPUList();

        JSONObject data = new JSONObject();
        data.put("name", GAME_NAME);
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

    public static File getShortcutFile(Container container) {
        return new File(container.getUserDir(), "Desktop/"+GAME_NAME+".desktop");
    }

    /**
     * Creates the game shortcut, or re-points an existing one at a new path while keeping any
     * per-shortcut settings the user changed. The touch controls and safe mode switches are
     * applied on every call.
     */
    public static File writeShortcut(Container container, String dosPath, int controlsProfileId, boolean safeMode, int screenMode, String wideScreenSize) {
        File file = getShortcutFile(container);
        File desktopDir = file.getParentFile();
        if (!desktopDir.isDirectory() && !desktopDir.mkdirs()) return null;

        String execLine = "Exec=env WINEPREFIX=\"/home/xuser/.wine\" wine "+escapeForDesktopExec(dosPath);
        String wmClassLine = "StartupWMClass="+FileUtils.getName(dosPath);
        boolean isNew = !file.isFile();

        if (isNew) {
            String content = "[Desktop Entry]\n" +
                             "Name="+GAME_NAME+"\n" +
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
        boolean isWide = screenMode == SCREEN_MODE_WIDE && wideScreenSize != null;
        String screenSize = isWide ? wideScreenSize : container.getScreenSize();
        String execArgs = isNew ? DEFAULT_EXEC_ARGS : shortcut.getExtra("execArgs");

        shortcut.putExtra("execArgs", buildExecArgs(execArgs, screenSize));
        shortcut.putExtra("screenSize", isWide ? wideScreenSize : null);
        shortcut.putExtra("stretchFullscreen", screenMode == SCREEN_MODE_STRETCH ? "1" : null);
        shortcut.putExtra("controlsProfile", controlsProfileId > 0 ? String.valueOf(controlsProfileId) : null);
        shortcut.putExtra("box64Preset", safeMode ? Box64Preset.STABILITY : null);
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
     * A screen size with the phone's own proportions, 720 pixels tall. Limited to the range the
     * game supports: from 16:10 up to 21:9.
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
     * Returns the id of the bundled touch layout. Imports it on first use, and replaces the
     * installed copy when a newer layout ships with the app. 0 on failure.
     */
    public static int ensureControlsProfile(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isOutdated = preferences.getInt(PREF_CONTROLS_PROFILE_VERSION, 1) < CONTROLS_PROFILE_VERSION;
        InputControlsManager manager = new InputControlsManager(context);
        ControlsProfile installed = null;

        for (ControlsProfile profile : manager.getProfiles()) {
            if (profile.getName().equals(CONTROLS_PROFILE_NAME)) {
                installed = profile;
                break;
            }
        }

        if (installed != null && !isOutdated) return installed.id;

        try {
            JSONObject data = new JSONObject(FileUtils.readString(context, CONTROLS_PROFILE_ASSET));
            if (installed != null) manager.removeProfile(installed);
            ControlsProfile profile = manager.importProfile(data);
            if (profile == null) return 0;

            preferences.edit().putInt(PREF_CONTROLS_PROFILE_VERSION, CONTROLS_PROFILE_VERSION).apply();
            return profile.id;
        }
        catch (Exception e) {
            return installed != null ? installed.id : 0;
        }
    }

    /** Copies the container's save folder to Download/WinPocket/saves. Background thread. */
    public static boolean exportSaves(Container container) {
        File srcDir = getContainerSavesDir(container);
        if (!srcDir.isDirectory() || FileUtils.isEmpty(srcDir)) return false;
        return FileUtils.copy(srcDir, getSavesExchangeDir());
    }

    /** Copies Download/WinPocket/saves into the container's save folder. Background thread. */
    public static boolean importSaves(Container container) {
        File srcDir = getSavesExchangeDir();
        if (!srcDir.isDirectory() || FileUtils.isEmpty(srcDir)) return false;
        return FileUtils.copy(srcDir, getContainerSavesDir(container), (file) -> FileUtils.chmod(file, 0771));
    }
}
