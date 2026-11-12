package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;
import com.winlator.core.GameSetup;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFS;

import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * WinPocket addition (not part of upstream Winlator).
 * Home screen with a one-tap launcher for a single game.
 */
public class GameLauncherFragment extends Fragment {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private PreloaderDialog preloaderDialog;
    private TextView tvStatus;
    private View btPlay;
    private CheckBox cbTouchControls;
    private CheckBox cbSafeMode;
    private Spinner sScreenMode;
    private File exeFile;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup root, @Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        View view = inflater.inflate(R.layout.game_launcher_fragment, root, false);

        ((TextView)view.findViewById(R.id.TVGameTitle)).setText(GameSetup.GAME_NAME);
        tvStatus = view.findViewById(R.id.TVGameStatus);
        btPlay = view.findViewById(R.id.BTPlay);

        cbTouchControls = view.findViewById(R.id.CBTouchControls);
        cbTouchControls.setChecked(preferences.getBoolean(GameSetup.PREF_TOUCH_CONTROLS, true));
        cbTouchControls.setOnCheckedChangeListener((v, isChecked) -> preferences.edit().putBoolean(GameSetup.PREF_TOUCH_CONTROLS, isChecked).apply());

        // Direct touch is on by default from this version on; set once, then the user decides.
        if (!preferences.getBoolean("game_touchscreen_default_applied", false)) {
            preferences.edit().putBoolean("move_cursor_to_touchpoint", true).putBoolean("game_touchscreen_default_applied", true).apply();
        }

        // Same setting as "Move Cursor to Touchpoint" in Settings
        CheckBox cbTouchscreenMode = view.findViewById(R.id.CBTouchscreenMode);
        cbTouchscreenMode.setChecked(preferences.getBoolean("move_cursor_to_touchpoint", false));
        cbTouchscreenMode.setOnCheckedChangeListener((v, isChecked) -> preferences.edit().putBoolean("move_cursor_to_touchpoint", isChecked).apply());

        sScreenMode = view.findViewById(R.id.SScreenMode);
        sScreenMode.setSelection(Math.max(0, Math.min(2, preferences.getInt(GameSetup.PREF_SCREEN_MODE, GameSetup.SCREEN_MODE_WIDE))));
        sScreenMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                preferences.edit().putInt(GameSetup.PREF_SCREEN_MODE, position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        cbSafeMode = view.findViewById(R.id.CBSafeMode);
        cbSafeMode.setChecked(preferences.getBoolean(GameSetup.PREF_SAFE_MODE, false));
        cbSafeMode.setOnCheckedChangeListener((v, isChecked) -> preferences.edit().putBoolean(GameSetup.PREF_SAFE_MODE, isChecked).apply());

        view.findViewById(R.id.BTFindGame).setOnClickListener((v) -> findGame());
        view.findViewById(R.id.BTChooseExe).setOnClickListener((v) -> chooseExe());
        view.findViewById(R.id.BTImportSaves).setOnClickListener((v) -> transferSaves(true));
        view.findViewById(R.id.BTExportSaves).setOnClickListener((v) -> transferSaves(false));
        btPlay.setOnClickListener((v) -> play());

        String savedPath = preferences.getString(GameSetup.PREF_GAME_PATH, null);
        exeFile = savedPath != null && (new File(savedPath)).isFile() ? new File(savedPath) : null;
        updateStatus();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.game);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            String path = uri != null ? FileUtils.getFilePathFromUri(uri) : null;
            if (path != null && path.toLowerCase(Locale.ENGLISH).endsWith(".exe") && (new File(path)).isFile()) {
                setGame(new File(path));
            }
            else AppUtils.showToast(getContext(), R.string.pick_an_exe_from_internal_storage);
        }
    }

    private void setGame(File file) {
        exeFile = file;
        preferences.edit().putString(GameSetup.PREF_GAME_PATH, file.getPath()).apply();
        updateStatus();
    }

    private void updateStatus() {
        if (exeFile != null) {
            String text = getString(R.string.game_found_at)+" "+exeFile.getPath();
            if (!GameSetup.looksComplete(exeFile)) text += "\n\n"+getString(R.string.game_folder_incomplete);
            tvStatus.setText(text);
        }
        else tvStatus.setText(R.string.game_not_found_yet);

        btPlay.setEnabled(exeFile != null);
        btPlay.setAlpha(exeFile != null ? 1.0f : 0.5f);
    }

    private void findGame() {
        preloaderDialog.show(R.string.searching_for_game);
        Executors.newSingleThreadExecutor().execute(() -> {
            final File file = GameSetup.findGameExecutable();
            handler.post(() -> {
                preloaderDialog.close();
                if (!isAdded()) return;
                if (file != null) {
                    setGame(file);
                }
                else AppUtils.showToast(getContext(), R.string.game_was_not_found);
            });
        });
    }

    private void chooseExe() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_FILE_REQUEST_CODE);
    }

    private void play() {
        final Context context = getContext();
        if (exeFile == null || !exeFile.isFile()) {
            exeFile = null;
            updateStatus();
            return;
        }

        if (!RootFS.find(context).isValid()) {
            AppUtils.showToast(context, R.string.system_files_are_not_installed_yet);
            return;
        }

        final ContainerManager manager = new ContainerManager(context);
        Container container = GameSetup.findContainer(manager);
        if (container != null) {
            launch(container);
            return;
        }

        try {
            preloaderDialog.show(R.string.creating_container);
            manager.createContainerAsync(GameSetup.createContainerData(context), (newContainer) -> {
                preloaderDialog.close();
                if (!isAdded()) return;
                if (newContainer != null) {
                    launch(newContainer);
                }
                else AppUtils.showToast(context, R.string.unable_to_create_container);
            });
        }
        catch (JSONException e) {
            preloaderDialog.close();
            AppUtils.showToast(context, R.string.unable_to_create_container);
        }
    }

    private void launch(Container container) {
        final Activity activity = getActivity();
        String dosPath = GameSetup.resolveDosPath(container, exeFile);
        int controlsProfileId = cbTouchControls.isChecked() ? GameSetup.ensureControlsProfile(activity) : 0;
        int screenMode = sScreenMode.getSelectedItemPosition();
        String wideScreenSize = GameSetup.getWideScreenSize(activity);
        File shortcutFile = dosPath != null ? GameSetup.writeShortcut(container, dosPath, controlsProfileId, cbSafeMode.isChecked(), screenMode, wideScreenSize) : null;

        if (shortcutFile == null) {
            AppUtils.showToast(activity, R.string.unable_to_prepare_game);
            return;
        }

        Intent intent = new Intent(activity, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("shortcut_path", shortcutFile.getPath());
        activity.startActivity(intent);
    }

    private void transferSaves(final boolean isImport) {
        final Context context = getContext();
        if (!RootFS.find(context).isValid()) {
            AppUtils.showToast(context, R.string.system_files_are_not_installed_yet);
            return;
        }

        final Container container = GameSetup.findContainer(new ContainerManager(context));
        if (container == null) {
            AppUtils.showToast(context, R.string.press_play_once_first);
            return;
        }

        preloaderDialog.show(R.string.loading);
        Executors.newSingleThreadExecutor().execute(() -> {
            final boolean success = isImport ? GameSetup.importSaves(container) : GameSetup.exportSaves(container);
            if (success && !isImport) scanExportedFiles(context.getApplicationContext());

            handler.post(() -> {
                preloaderDialog.close();
                if (!isAdded()) return;
                if (isImport) {
                    AppUtils.showToast(context, success ? R.string.saves_imported : R.string.no_saves_to_import);
                }
                else if (success) {
                    AppUtils.showToast(context, getString(R.string.saves_exported_to)+" Download/WinPocket/saves");
                }
                else AppUtils.showToast(context, R.string.no_saves_to_export);
            });
        });
    }

    /** Makes freshly exported files visible to a PC connected over USB. */
    private static void scanExportedFiles(Context context) {
        ArrayList<String> paths = new ArrayList<>();
        collectFiles(GameSetup.getSavesExchangeDir(), paths);
        if (!paths.isEmpty()) MediaScannerConnection.scanFile(context, paths.toArray(new String[0]), null, null);
    }

    private static void collectFiles(File dir, ArrayList<String> paths) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collectFiles(file, paths);
            }
            else paths.add(file.getPath());
        }
    }
}
