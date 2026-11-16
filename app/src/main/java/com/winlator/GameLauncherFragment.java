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
import android.widget.ArrayAdapter;
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
import com.winlator.contentdialog.ContentDialog;
import com.winlator.core.AppUpdater;
import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;
import com.winlator.core.GameProfile;
import com.winlator.core.GameSetup;
import com.winlator.core.GameShortcuts;
import com.winlator.core.GameStarter;
import com.winlator.core.PreloaderDialog;
import com.winlator.widget.SeekBar;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * WinPocket addition (not part of upstream Winlator).
 * Home screen with a one-tap launcher for each supported game.
 */
public class GameLauncherFragment extends Fragment {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private PreloaderDialog preloaderDialog;
    private GameProfile game;
    private TextView tvTitle;
    private TextView tvStatus;
    private TextView tvSavesHelp;
    private TextView tvSetupSteps;
    private View btPlay;
    private CheckBox cbTouchControls;
    private CheckBox cbSafeMode;
    private Spinner sScreenMode;
    private CheckBox cbFloatingStick;
    private SeekBar sbStickSize;
    private SeekBar sbButtonSize;
    private CheckBox cbTouchscreenMode;
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

        tvTitle = view.findViewById(R.id.TVGameTitle);
        tvStatus = view.findViewById(R.id.TVGameStatus);
        tvSavesHelp = view.findViewById(R.id.TVSavesHelp);
        tvSetupSteps = view.findViewById(R.id.TVFirstTimeSetupSteps);
        btPlay = view.findViewById(R.id.BTPlay);

        // Direct touch is on by default for Hollow Knight from this version on; set once, then the user decides.
        if (!preferences.getBoolean("game_touchscreen_default_applied", false)) {
            preferences.edit().putBoolean("move_cursor_to_touchpoint", true).putBoolean("game_touchscreen_default_applied", true).apply();
        }

        cbTouchControls = view.findViewById(R.id.CBTouchControls);
        cbTouchControls.setOnCheckedChangeListener((v, isChecked) -> putBoolean(GameSetup.PREF_TOUCH_CONTROLS, isChecked));

        cbFloatingStick = view.findViewById(R.id.CBFloatingStick);
        cbFloatingStick.setOnCheckedChangeListener((v, isChecked) -> putBoolean(GameSetup.PREF_FLOATING_STICK, isChecked));

        sbStickSize = view.findViewById(R.id.SBStickSize);
        sbStickSize.setOnValueChangeListener((seekBar, value) -> preferences.edit().putInt(game.pref(GameSetup.PREF_STICK_SIZE), Math.round(value)).apply());

        sbButtonSize = view.findViewById(R.id.SBButtonSize);
        sbButtonSize.setOnValueChangeListener((seekBar, value) -> preferences.edit().putInt(game.pref(GameSetup.PREF_BUTTON_SIZE), Math.round(value)).apply());

        view.findViewById(R.id.BTEditLayout).setOnClickListener((v) -> editLayout());
        view.findViewById(R.id.BTResetLayout).setOnClickListener((v) -> resetLayout());

        String buildLabel = AppUpdater.getBuildLabel(context);
        ((TextView)view.findViewById(R.id.TVBuild)).setText(getString(R.string.build_label)+" "+(buildLabel != null ? buildLabel : "-"));
        CheckBox cbAutoUpdate = view.findViewById(R.id.CBAutoUpdate);
        cbAutoUpdate.setChecked(preferences.getBoolean(AppUpdater.PREF_AUTO_UPDATE, true));
        cbAutoUpdate.setOnCheckedChangeListener((v, isChecked) -> preferences.edit().putBoolean(AppUpdater.PREF_AUTO_UPDATE, isChecked).apply());
        view.findViewById(R.id.BTCheckForUpdates).setOnClickListener((v) -> AppUpdater.check(getActivity(), true));

        cbTouchscreenMode = view.findViewById(R.id.CBTouchscreenMode);
        cbTouchscreenMode.setOnCheckedChangeListener((v, isChecked) -> preferences.edit().putBoolean(game.touchscreenModePref(), isChecked).apply());

        sScreenMode = view.findViewById(R.id.SScreenMode);
        sScreenMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                preferences.edit().putInt(game.pref(GameSetup.PREF_SCREEN_MODE), position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        cbSafeMode = view.findViewById(R.id.CBSafeMode);
        cbSafeMode.setOnCheckedChangeListener((v, isChecked) -> putBoolean(GameSetup.PREF_SAFE_MODE, isChecked));

        view.findViewById(R.id.BTFindGame).setOnClickListener((v) -> findGame());
        view.findViewById(R.id.BTChooseExe).setOnClickListener((v) -> chooseExe());
        view.findViewById(R.id.BTImportSaves).setOnClickListener((v) -> transferSaves(true));
        view.findViewById(R.id.BTExportSaves).setOnClickListener((v) -> transferSaves(false));
        btPlay.setOnClickListener((v) -> play());
        view.findViewById(R.id.BTAddToHomeScreen).setOnClickListener((v) -> addToHomeScreen());

        // The picker only shows when WinPocket supports more than one game
        Spinner sGame = view.findViewById(R.id.SGame);
        int pickerVisibility = GameProfile.ALL.length > 1 ? View.VISIBLE : View.GONE;
        sGame.setVisibility(pickerVisibility);
        view.findViewById(R.id.TVChooseGame).setVisibility(pickerVisibility);
        sGame.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, GameProfile.names()));
        game = GameSetup.getSelectedGame(context);
        sGame.setSelection(GameProfile.indexOf(game), false);
        sGame.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                GameProfile selected = GameProfile.ALL[position];
                if (selected == game) return;
                preferences.edit().putString(GameSetup.PREF_SELECTED_GAME, selected.id).apply();
                bindGame(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        bindGame(game);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.game);
    }

    private void putBoolean(String key, boolean value) {
        preferences.edit().putBoolean(game.pref(key), value).apply();
    }

    /** Shows the settings, texts and game folder of one game. */
    private void bindGame(GameProfile game) {
        this.game = game;
        tvTitle.setText(game.name);
        tvSavesHelp.setText(game.savesHelpResId);
        tvSetupSteps.setText(game.setupStepsResId);

        cbTouchControls.setChecked(preferences.getBoolean(game.pref(GameSetup.PREF_TOUCH_CONTROLS), true));
        cbFloatingStick.setChecked(preferences.getBoolean(game.pref(GameSetup.PREF_FLOATING_STICK), true));
        sbStickSize.setValue(preferences.getInt(game.pref(GameSetup.PREF_STICK_SIZE), GameSetup.DEFAULT_STICK_SIZE));
        sbButtonSize.setValue(preferences.getInt(game.pref(GameSetup.PREF_BUTTON_SIZE), GameSetup.DEFAULT_BUTTON_SIZE));
        cbTouchscreenMode.setChecked(GameStarter.isTouchscreenMode(preferences, game));
        sScreenMode.setSelection(Math.max(0, Math.min(2, preferences.getInt(game.pref(GameSetup.PREF_SCREEN_MODE), GameSetup.SCREEN_MODE_WIDE))));
        cbSafeMode.setChecked(preferences.getBoolean(game.pref(GameSetup.PREF_SAFE_MODE), false));

        String savedPath = preferences.getString(game.pref(GameSetup.PREF_GAME_PATH), null);
        exeFile = savedPath != null && (new File(savedPath)).isFile() ? new File(savedPath) : null;
        updateStatus();
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
        preferences.edit().putString(game.pref(GameSetup.PREF_GAME_PATH), file.getPath()).apply();
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
        final GameProfile searchedGame = game;
        preloaderDialog.show(R.string.searching_for_game);
        Executors.newSingleThreadExecutor().execute(() -> {
            final File file = GameSetup.findGameExecutable(searchedGame);
            handler.post(() -> {
                preloaderDialog.close();
                if (!isAdded() || searchedGame != game) return;
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

    private void showError(int messageResId) {
        if (isAdded()) AppUtils.showToast(getContext(), messageResId);
    }

    private void play() {
        if (exeFile == null || !exeFile.isFile()) {
            exeFile = null;
            updateStatus();
            return;
        }
        GameStarter.start(getActivity(), game, preloaderDialog, null, this::showError);
    }

    /** Opens the layout editor on this game's buttons, with the current slider sizes applied. */
    private void editLayout() {
        final Context context = getContext();
        int profileId = GameSetup.prepareControls(context, game);
        if (profileId <= 0) {
            showError(R.string.unable_to_open_layout);
            return;
        }

        Intent intent = new Intent(context, ControlsEditorActivity.class);
        intent.putExtra("profile_id", profileId);
        startActivity(intent);
    }

    private void resetLayout() {
        final Context context = getContext();
        final GameProfile resetGame = game;
        ContentDialog.confirm(context, R.string.reset_layout_confirm, () -> {
            boolean success = GameSetup.resetControlsProfile(context, resetGame) > 0;
            showError(success ? R.string.layout_was_reset : R.string.unable_to_open_layout);
        });
    }

    private void addToHomeScreen() {
        if (exeFile == null || !exeFile.isFile()) {
            showError(R.string.shortcut_find_game_first);
            return;
        }
        GameShortcuts.requestPin(getActivity(), game, exeFile);
    }

    private void transferSaves(final boolean isImport) {
        final Context context = getContext();
        final GameProfile transferredGame = game;
        if (!RootFS.find(context).isValid()) {
            AppUtils.showToast(context, R.string.system_files_are_not_installed_yet);
            return;
        }

        final Container container = GameSetup.findContainer(new ContainerManager(context), transferredGame);
        if (container == null) {
            AppUtils.showToast(context, R.string.press_play_once_first);
            return;
        }

        preloaderDialog.show(R.string.loading);
        Executors.newSingleThreadExecutor().execute(() -> {
            final boolean success = isImport ? GameSetup.importSaves(container, transferredGame) : GameSetup.exportSaves(container, transferredGame);
            if (success && !isImport) scanExportedFiles(context.getApplicationContext(), transferredGame);

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
    private static void scanExportedFiles(Context context, GameProfile game) {
        ArrayList<String> paths = new ArrayList<>();
        collectFiles(GameSetup.getSavesExchangeDir(game), paths);
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
