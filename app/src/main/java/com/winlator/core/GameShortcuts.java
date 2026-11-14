package com.winlator.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.PreferenceManager;

import com.winlator.GameShortcutActivity;
import com.winlator.R;
import com.winlator.SettingsFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * WinPocket addition (not part of upstream Winlator).
 * Home-screen shortcuts that start one game directly. The icon is the game's own, read from the
 * user's copy of the exe, on a tile that follows the app theme like the launcher icon does.
 */
public abstract class GameShortcuts {
    private static final String SHORTCUT_ID_PREFIX = "game_";
    private static final String PREF_ICONS_DARK = "game_shortcut_icons_dark";
    /** Adaptive icons are 108dp with the visible part in the middle 72dp; this is 108dp at 4x. */
    private static final int ICON_SIZE = 432;
    private static final int GAME_ICON_SIZE = 200;
    private static final int TILE_DARK = 0xff202124;
    private static final int TILE_LIGHT = 0xfff1f3f4;
    private static final int ACCENT = 0xff0277bd;

    public static String getShortcutId(GameProfile game) {
        return SHORTCUT_ID_PREFIX+game.id;
    }

    private static boolean isDarkTheme(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getInt("app_theme", SettingsFragment.APP_THEME_DARK) == SettingsFragment.APP_THEME_DARK;
    }

    /** The largest image of the exe's icon that decodes, or null. Background thread. */
    public static Bitmap loadExeIcon(File exeFile) {
        if (exeFile == null || !exeFile.isFile()) return null;

        for (ExeIconReader.IconImage image : ExeIconReader.read(exeFile)) {
            Bitmap bitmap = null;
            if (image.isPng()) {
                bitmap = BitmapFactory.decodeByteArray(image.data, 0, image.data.length);
            }
            else {
                ExeIconReader.Pixels pixels = ExeIconReader.decodeDib(image.data);
                if (pixels != null) bitmap = Bitmap.createBitmap(pixels.argb, pixels.width, pixels.height, Bitmap.Config.ARGB_8888);
            }
            if (bitmap != null) return bitmap;
        }
        return null;
    }

    /** The shortcut's picture: the game's icon on a themed tile, or its initials when it has none. */
    public static Bitmap createIcon(GameProfile game, Bitmap gameIcon, boolean isDark) {
        Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(isDark ? TILE_DARK : TILE_LIGHT);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float center = ICON_SIZE * 0.5f;

        if (gameIcon != null) {
            float half = GAME_ICON_SIZE * 0.5f;
            Rect src = new Rect(0, 0, gameIcon.getWidth(), gameIcon.getHeight());
            canvas.drawBitmap(gameIcon, src, new RectF(center - half, center - half, center + half, center + half), paint);
        }
        else {
            paint.setColor(ACCENT);
            canvas.drawCircle(center, center, ICON_SIZE * 0.3f, paint);

            paint.setColor(Color.WHITE);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(ICON_SIZE * 0.22f);
            String initials = getInitials(game.name);
            canvas.drawText(initials, center, center - (paint.descent() + paint.ascent()) * 0.5f, paint);
        }
        return bitmap;
    }

    /** "Hollow Knight" gives "HK", "Schedule I" gives "SI". */
    public static String getInitials(String name) {
        StringBuilder initials = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (!word.isEmpty() && initials.length() < 2) initials.append(Character.toUpperCase(word.charAt(0)));
        }
        return initials.toString();
    }

    public static ShortcutInfo buildShortcut(Context context, GameProfile game, Bitmap icon) {
        Intent intent = new Intent(context, GameShortcutActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(GameShortcutActivity.EXTRA_GAME_ID, game.id);

        return new ShortcutInfo.Builder(context, getShortcutId(game))
            .setShortLabel(game.name)
            .setLongLabel(context.getString(R.string.play_game, game.name))
            .setIcon(Icon.createWithAdaptiveBitmap(icon))
            .setIntent(intent)
            .build();
    }

    private static boolean isPinned(ShortcutManager manager, String id) {
        for (ShortcutInfo shortcut : manager.getPinnedShortcuts()) {
            if (shortcut.getId().equals(id)) return true;
        }
        return false;
    }

    /**
     * Asks the home screen to add a shortcut for the game. If one is already there, its icon is
     * refreshed instead, since home screens ignore a second request for the same shortcut.
     */
    public static void requestPin(final Activity activity, final GameProfile game, final File exeFile) {
        final ShortcutManager manager = activity.getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            AppUtils.showToast(activity, R.string.pin_shortcuts_not_supported);
            return;
        }

        final boolean isDark = isDarkTheme(activity);
        final Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            final Bitmap icon = createIcon(game, loadExeIcon(exeFile), isDark);
            handler.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                ShortcutInfo shortcut = buildShortcut(activity, game, icon);
                try {
                    if (isPinned(manager, shortcut.getId())) {
                        manager.updateShortcuts(java.util.Collections.singletonList(shortcut));
                        AppUtils.showToast(activity, activity.getString(R.string.shortcut_already_added, game.name));
                    }
                    else manager.requestPinShortcut(shortcut, null);
                    PreferenceManager.getDefaultSharedPreferences(activity).edit().putBoolean(PREF_ICONS_DARK, isDark).apply();
                }
                catch (RuntimeException e) {
                    AppUtils.showToast(activity, R.string.pin_shortcuts_not_supported);
                }
            });
        });
    }

    /**
     * Greys out pinned shortcuts for games WinPocket no longer supports, so tapping one explains
     * why instead of doing nothing. Home screens do not let apps remove a pinned shortcut.
     */
    public static void disableRemovedGames(Context context) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return;

        try {
            List<String> removedIds = new ArrayList<>();
            for (ShortcutInfo shortcut : manager.getPinnedShortcuts()) {
                String id = shortcut.getId();
                if (!shortcut.isEnabled() || !id.startsWith(SHORTCUT_ID_PREFIX)) continue;
                if (GameProfile.find(id.substring(SHORTCUT_ID_PREFIX.length())) == null) removedIds.add(id);
            }
            if (!removedIds.isEmpty()) manager.disableShortcuts(removedIds, context.getString(R.string.game_removed_from_winpocket));
        }
        catch (RuntimeException e) {}
    }

    /**
     * Redraws the tiles of pinned game shortcuts when the app theme has changed since they were
     * drawn, so they stay in step with the launcher icon. Cheap when nothing changed.
     */
    public static void refreshIconsIfThemeChanged(final Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean isDark = isDarkTheme(context);
        if (!preferences.contains(PREF_ICONS_DARK) || preferences.getBoolean(PREF_ICONS_DARK, true) == isDark) return;

        final Context appContext = context.getApplicationContext();
        final ShortcutManager manager = appContext.getSystemService(ShortcutManager.class);
        if (manager == null) return;

        final List<ShortcutInfo> pinned;
        try {
            pinned = manager.getPinnedShortcuts();
        }
        catch (RuntimeException e) {
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            List<ShortcutInfo> updated = new ArrayList<>();
            for (GameProfile game : GameProfile.ALL) {
                boolean found = false;
                for (ShortcutInfo shortcut : pinned) found |= shortcut.getId().equals(getShortcutId(game));
                if (!found) continue;

                Bitmap icon = createIcon(game, loadExeIcon(GameStarter.getGameExe(appContext, game)), isDark);
                updated.add(buildShortcut(appContext, game, icon));
            }

            try {
                if (!updated.isEmpty()) manager.updateShortcuts(updated);
                preferences.edit().putBoolean(PREF_ICONS_DARK, isDark).apply();
            }
            catch (RuntimeException e) {}
        });
    }
}
