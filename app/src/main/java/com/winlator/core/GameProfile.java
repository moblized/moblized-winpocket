package com.winlator.core;

import com.winlator.R;

/**
 * WinPocket addition (not part of upstream Winlator).
 * Everything that differs between the games WinPocket can set up: how to recognise the game,
 * which touch layout it ships with, where its saves live and which input defaults suit it.
 */
public final class GameProfile {
    public static final GameProfile HOLLOW_KNIGHT = new GameProfile.Builder("hollow_knight", "Hollow Knight")
        .exeName("hollowknight")
        .controls("Hollow Knight (touch)", "winpocket/hollow_knight_touch.icp", 3)
        .execArgs("-force-gfx-direct")
        .saves("AppData/LocalLow/Team Cherry", "Team Cherry", R.string.saves_help)
        .setupSteps(R.string.first_time_setup_steps)
        .touchscreenMode(true)
        .legacyPreferences()
        .build();

    public static final GameProfile SCHEDULE_I = new GameProfile.Builder("schedule_i", "Schedule I")
        .exeName("schedulei")
        .controls("Schedule I (touch)", "winpocket/schedule_i_touch.icp", 1)
        .execArgs("")
        .saves("AppData/LocalLow/TVGS", "TVGS", R.string.saves_help_schedule_i)
        .setupSteps(R.string.first_time_setup_steps_schedule_i)
        .relativeMouse(true)
        .steamAppId(3164500)
        .build();

    public static final GameProfile[] ALL = {HOLLOW_KNIGHT, SCHEDULE_I};

    /** Stable id, used for preference keys and file names. */
    public final String id;
    /** Display name. The container and the shortcut are called this too. */
    public final String name;
    /** The exe's base name in lower case with everything but letters and digits removed. */
    public final String normalizedExeName;
    public final String controlsProfileName;
    public final String controlsProfileAsset;
    /** Raise this when the bundled touch layout changes, so installed copies get replaced. */
    public final int controlsProfileVersion;
    public final String defaultExecArgs;
    /** Save folder inside the container's user folder. */
    public final String savesRelativePath;
    /** Name of the save folder in Download/WinPocket/saves. */
    public final String savesFolderName;
    public final int savesHelpResId;
    public final int setupStepsResId;
    /** Default for "Tap where you touch". Off suits first-person games, which steer a camera. */
    public final boolean defaultTouchscreenMode;
    /** Default for "Mouse look". Sends finger movement as relative mouse motion. */
    public final boolean defaultRelativeMouse;
    /** Steam app id, or 0 when the game never needs the Steam client. */
    public final int steamAppId;
    private final boolean legacyPreferences;

    private GameProfile(Builder builder) {
        id = builder.id;
        name = builder.name;
        normalizedExeName = builder.normalizedExeName;
        controlsProfileName = builder.controlsProfileName;
        controlsProfileAsset = builder.controlsProfileAsset;
        controlsProfileVersion = builder.controlsProfileVersion;
        defaultExecArgs = builder.defaultExecArgs;
        savesRelativePath = builder.savesRelativePath;
        savesFolderName = builder.savesFolderName;
        savesHelpResId = builder.savesHelpResId;
        setupStepsResId = builder.setupStepsResId;
        defaultTouchscreenMode = builder.defaultTouchscreenMode;
        defaultRelativeMouse = builder.defaultRelativeMouse;
        steamAppId = builder.steamAppId;
        legacyPreferences = builder.legacyPreferences;
    }

    public boolean supportsSteam() {
        return steamAppId > 0;
    }

    /**
     * Preference key for one of this game's settings. Hollow Knight keeps the keys it had before
     * WinPocket supported more than one game, so existing settings carry over.
     */
    public String pref(String key) {
        return legacyPreferences ? key : id+"_"+key;
    }

    /** Hollow Knight's switch has always been the global "Move Cursor to Touchpoint" setting. */
    public String touchscreenModePref() {
        return legacyPreferences ? "move_cursor_to_touchpoint" : pref("move_cursor_to_touchpoint");
    }

    public static GameProfile byId(String id) {
        for (GameProfile game : ALL) if (game.id.equals(id)) return game;
        return HOLLOW_KNIGHT;
    }

    public static String[] names() {
        String[] names = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) names[i] = ALL[i].name;
        return names;
    }

    public static int indexOf(GameProfile game) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i] == game) return i;
        return 0;
    }

    private static final class Builder {
        private final String id;
        private final String name;
        private String normalizedExeName;
        private String controlsProfileName;
        private String controlsProfileAsset;
        private int controlsProfileVersion = 1;
        private String defaultExecArgs = "";
        private String savesRelativePath;
        private String savesFolderName;
        private int savesHelpResId;
        private int setupStepsResId;
        private boolean defaultTouchscreenMode = false;
        private boolean defaultRelativeMouse = false;
        private int steamAppId = 0;
        private boolean legacyPreferences = false;

        Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        Builder exeName(String normalizedExeName) {
            this.normalizedExeName = normalizedExeName;
            return this;
        }

        Builder controls(String profileName, String asset, int version) {
            controlsProfileName = profileName;
            controlsProfileAsset = asset;
            controlsProfileVersion = version;
            return this;
        }

        Builder execArgs(String execArgs) {
            defaultExecArgs = execArgs;
            return this;
        }

        Builder saves(String relativePath, String folderName, int helpResId) {
            savesRelativePath = relativePath;
            savesFolderName = folderName;
            savesHelpResId = helpResId;
            return this;
        }

        Builder setupSteps(int resId) {
            setupStepsResId = resId;
            return this;
        }

        Builder touchscreenMode(boolean enabled) {
            defaultTouchscreenMode = enabled;
            return this;
        }

        Builder relativeMouse(boolean enabled) {
            defaultRelativeMouse = enabled;
            return this;
        }

        Builder steamAppId(int appId) {
            steamAppId = appId;
            return this;
        }

        Builder legacyPreferences() {
            legacyPreferences = true;
            return this;
        }

        GameProfile build() {
            return new GameProfile(this);
        }
    }
}
