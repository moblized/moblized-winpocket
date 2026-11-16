package com.winlator.core;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.xenvironment.RootFS;

import org.json.JSONException;

import java.io.File;

/**
 * WinPocket addition (not part of upstream Winlator).
 * Starts a game the same way from the home screen's Play button and from a home-screen
 * shortcut: reads the game's saved settings, makes sure its container exists, writes the
 * shortcut file and opens the game.
 */
public abstract class GameStarter {
    /** Reports why a game could not start, as a string resource id. */
    public interface ErrorListener {
        void onError(int messageResId);
    }

    /** The game's exe as last found or chosen on the home screen, or null when it is gone. */
    public static File getGameExe(Context context, GameProfile game) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String path = preferences.getString(game.pref(GameSetup.PREF_GAME_PATH), null);
        File file = path != null ? new File(path) : null;
        return file != null && file.isFile() ? file : null;
    }

    public static boolean hasStoragePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /** True when everything is in place to start the game without opening WinPocket first. */
    public static boolean isReady(Context context, GameProfile game) {
        return hasStoragePermission(context) && RootFS.find(context).isValid() && getGameExe(context, game) != null;
    }

    public static boolean isTouchscreenMode(SharedPreferences preferences, GameProfile game) {
        return preferences.getBoolean(game.touchscreenModePref(), game.defaultTouchscreenMode);
    }

    /** The home screen's settings for this game, as saved. Imports the touch layout if needed. */
    public static GameSetup.LaunchOptions readOptions(Context context, GameProfile game) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        GameSetup.LaunchOptions options = new GameSetup.LaunchOptions();

        if (preferences.getBoolean(game.pref(GameSetup.PREF_TOUCH_CONTROLS), true)) {
            options.controlsProfileId = GameSetup.prepareControls(context, game);
        }

        options.safeMode = preferences.getBoolean(game.pref(GameSetup.PREF_SAFE_MODE), false);
        options.screenMode = Math.max(0, Math.min(2, preferences.getInt(game.pref(GameSetup.PREF_SCREEN_MODE), GameSetup.SCREEN_MODE_WIDE)));
        options.wideScreenSize = GameSetup.getWideScreenSize(context);
        options.floatingStick = preferences.getBoolean(game.pref(GameSetup.PREF_FLOATING_STICK), true);
        options.touchscreenMode = isTouchscreenMode(preferences, game);
        return options;
    }

    /** Finds the game's container, creating it first when it does not exist yet. */
    public static void withContainer(final Activity activity, final GameProfile game, final PreloaderDialog preloaderDialog,
                                     final Callback<Container> callback, final ErrorListener errorListener) {
        if (!RootFS.find(activity).isValid()) {
            errorListener.onError(R.string.system_files_are_not_installed_yet);
            return;
        }

        final ContainerManager manager = new ContainerManager(activity);
        Container container = GameSetup.findContainer(manager, game);
        if (container != null) {
            callback.call(container);
            return;
        }

        try {
            if (preloaderDialog != null) preloaderDialog.show(R.string.creating_container);
            manager.createContainerAsync(GameSetup.createContainerData(activity, game), (newContainer) -> {
                if (preloaderDialog != null) preloaderDialog.close();
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (newContainer != null) {
                    callback.call(newContainer);
                }
                else errorListener.onError(R.string.unable_to_create_container);
            });
        }
        catch (JSONException e) {
            if (preloaderDialog != null) preloaderDialog.close();
            errorListener.onError(R.string.unable_to_create_container);
        }
    }

    /**
     * Starts the game with its saved settings. Calls onStarted once the game screen is opening,
     * or errorListener when it cannot start.
     */
    public static void start(final Activity activity, final GameProfile game, final PreloaderDialog preloaderDialog,
                             final Runnable onStarted, final ErrorListener errorListener) {
        final File exeFile = getGameExe(activity, game);
        if (exeFile == null) {
            errorListener.onError(R.string.game_not_found_yet);
            return;
        }

        withContainer(activity, game, preloaderDialog, (container) -> {
            String dosPath = GameSetup.resolveDosPath(container, exeFile);
            GameSetup.LaunchOptions options = readOptions(activity, game);
            File shortcutFile = dosPath != null ? GameSetup.writeShortcut(container, game, dosPath, options) : null;

            if (shortcutFile == null) {
                errorListener.onError(R.string.unable_to_prepare_game);
                return;
            }

            startShortcut(activity, container, shortcutFile);
            if (onStarted != null) onStarted.run();
        }, errorListener);
    }

    public static void startShortcut(Activity activity, Container container, File shortcutFile) {
        Intent intent = new Intent(activity, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("shortcut_path", shortcutFile.getPath());
        activity.startActivity(intent);
    }
}
