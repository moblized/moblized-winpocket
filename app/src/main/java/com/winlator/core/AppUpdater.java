package com.winlator.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.contentdialog.ContentDialog;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * WinPocket addition (not part of upstream Winlator).
 * Keeps the app up to date from the repo's "latest" release. Every CI build is stamped with its
 * commit (assets/winpocket/build_sha.txt) and published next to a version.txt holding the same
 * commit. When the two differ, a newer build exists: the app offers to download it and hands
 * the APK to Android's installer. Android checks that it is signed with the same key as the
 * installed app before it replaces it.
 */
public abstract class AppUpdater {
    public static final String PREF_AUTO_UPDATE = "winpocket_auto_update";
    private static final String PREF_LAST_CHECK = "winpocket_update_last_check";
    private static final String RELEASE_URL = "https://github.com/moblized/moblized-winpocket/releases/download/latest/";
    private static final String VERSION_URL = RELEASE_URL+"version.txt";
    private static final String APK_URL = RELEASE_URL+"WinPocket.apk";
    private static final String BUILD_SHA_ASSET = "winpocket/build_sha.txt";
    private static final String FILE_PROVIDER_AUTHORITY = "com.winlator.FileProvider";
    private static final long CHECK_INTERVAL = 2 * 60 * 60 * 1000L;

    public static boolean isCommitSha(String value) {
        return value != null && value.matches("[0-9a-f]{40}");
    }

    /** The commit this app was built from, or null for a build that did not come from CI. */
    public static String getBuildSha(Context context) {
        try (InputStream inStream = context.getAssets().open(BUILD_SHA_ASSET)) {
            String sha = new String(StreamUtils.copyToByteArray(inStream), StandardCharsets.UTF_8).trim();
            return isCommitSha(sha) ? sha : null;
        }
        catch (Exception e) {
            return null;
        }
    }

    /** Short form of the build's commit for display, or null. */
    public static String getBuildLabel(Context context) {
        String sha = getBuildSha(context);
        return sha != null ? sha.substring(0, 7) : null;
    }

    private static File getDownloadFile(Context context) {
        return new File(context.getFilesDir(), "updates/WinPocket.apk");
    }

    /** The automatic check when the app opens: at most once every two hours, and only if enabled. */
    public static void checkOnStart(Activity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        if (!preferences.getBoolean(PREF_AUTO_UPDATE, true) || getBuildSha(activity) == null) return;
        long elapsed = System.currentTimeMillis() - preferences.getLong(PREF_LAST_CHECK, 0);
        if (elapsed >= 0 && elapsed < CHECK_INTERVAL) return;
        check(activity, false);
    }

    /** Looks for a newer build. A manual check reports every outcome; an automatic one stays quiet unless there is an update. */
    public static void check(final Activity activity, final boolean isManual) {
        final String buildSha = getBuildSha(activity);
        if (buildSha == null) {
            if (isManual) AppUtils.showToast(activity, R.string.updates_not_available_for_this_build);
            return;
        }

        HttpUtils.download(VERSION_URL, (content) -> activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            String latestSha = content != null ? content.trim() : null;
            if (!isCommitSha(latestSha)) {
                if (isManual) AppUtils.showToast(activity, R.string.unable_to_check_for_updates);
                return;
            }

            PreferenceManager.getDefaultSharedPreferences(activity).edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();

            if (latestSha.equals(buildSha)) {
                File leftover = getDownloadFile(activity);
                if (leftover.isFile()) leftover.delete();
                if (isManual) AppUtils.showToast(activity, R.string.no_update_available);
                return;
            }

            ContentDialog.confirm(activity, R.string.update_available_confirm, () -> downloadAndInstall(activity));
        }));
    }

    private static void downloadAndInstall(final Activity activity) {
        final File apkFile = getDownloadFile(activity);
        File dir = apkFile.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            AppUtils.showToast(activity, R.string.update_was_not_downloaded);
            return;
        }
        if (apkFile.isFile()) apkFile.delete();

        HttpUtils.download(activity, APK_URL, apkFile, (success) -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (success && apkFile.isFile()) {
                install(activity, apkFile);
            }
            else AppUtils.showToast(activity, R.string.update_was_not_downloaded);
        });
    }

    private static void install(Activity activity, File apkFile) {
        try {
            Uri uri = FileProvider.getUriForFile(activity, FILE_PROVIDER_AUTHORITY, apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        }
        catch (Exception e) {
            AppUtils.showToast(activity, R.string.update_was_not_downloaded);
        }
    }
}
