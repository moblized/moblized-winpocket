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
import android.widget.Button;
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
import com.winlator.core.Callback;
import com.winlator.core.FileUtils;
import com.winlator.core.GameProfile;
import com.winlator.core.GameSetup;
import com.winlator.core.HttpUtils;
import com.winlator.core.PreloaderDialog;
import com.winlator.widget.SeekBar;
import com.winlator.xenvironment.RootFS;

import org.json.JSONException;

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
    private CheckBox cbTouchscreenMode;
    private CheckBox cbRelativeMouse;
    private View llSteam;
    private TextView tvSteamStatus;
    private CheckBox cbUseSteam;
    private Button btSteam;
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

        // Steering a camera and tapping where you touch exclude each other, so only one can be on.
        cbTouchscreenMode = view.findViewById(R.id.CBTouchscreenMode);
        cbRelativeMouse = view.findViewById(R.id.CBRelativeMouse);
        cbTouchscreenMode.setOnCheckedChangeListener((v, isChecked) -> {
            preferences.edit().putBoolean(game.touchscreenModePref(), isChecked).apply();
            if (isChecked && cbRelativeMouse.isChecked()) cbRelativeMouse.setChecked(false);
        });
        cbRelativeMouse.setOnCheckedChangeListener((v, isChecked) -> {
            putBoolean(GameSetup.PREF_RELATIVE_MOUSE, isChecked);
            if (isChecked && cbTouchscreenMode.isChecked()) cbTouchscreenMode.setChecked(false);
        });

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

        llSteam = view.findViewById(R.id.LLSteam);
        tvSteamStatus = view.findViewById(R.id.TVSteamStatus);
        cbUseSteam = view.findViewById(R.id.CBUseSteam);
        cbUseSteam.setOnCheckedChangeListener((v, isChecked) -> putBoolean(GameSetup.PREF_USE_STEAM, isChecked));
        btSteam = view.findViewById(R.id.BTSteam);
        btSteam.setOnClickListener((v) -> setUpSteam());

        view.findViewById(R.id.BTFindGame).setOnClickListener((v) -> findGame());
        view.findViewById(R.id.BTChooseExe).setOnClickListener((v) -> chooseExe());
        view.findViewById(R.id.BTImportSaves).setOnClickListener((v) -> transferSaves(true));
        view.findViewById(R.id.BTExportSaves).setOnClickListener((v) -> transferSaves(false));
        btPlay.setOnClickListener((v) -> play());

        Spinner sGame = view.findViewById(R.id.SGame);
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

    @Override
    public void onResume() {
        super.onResume();
        // Steam may have been installed while the game screen was open
        if (game != null && llSteam != null) updateSteamSection();
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
        boolean relativeMouse = preferences.getBoolean(game.pref(GameSetup.PREF_RELATIVE_MOUSE), game.defaultRelativeMouse);
        boolean touchscreenMode = !relativeMouse && preferences.getBoolean(game.touchscreenModePref(), game.defaultTouchscreenMode);
        cbRelativeMouse.setChecked(relativeMouse);
        cbTouchscreenMode.setChecked(touchscreenMode);
        // Mouse look only means something in a game that is steered with the mouse
        cbRelativeMouse.setVisibility(game.defaultRelativeMouse || relativeMouse ? View.VISIBLE : View.GONE);
        sScreenMode.setSelection(Math.max(0, Math.min(2, preferences.getInt(game.pref(GameSetup.PREF_SCREEN_MODE), GameSetup.SCREEN_MODE_WIDE))));
        cbSafeMode.setChecked(preferences.getBoolean(game.pref(GameSetup.PREF_SAFE_MODE), false));
        cbUseSteam.setChecked(preferences.getBoolean(game.pref(GameSetup.PREF_USE_STEAM), true));

        String savedPath = preferences.getString(game.pref(GameSetup.PREF_GAME_PATH), null);
        exeFile = savedPath != null && (new File(savedPath)).isFile() ? new File(savedPath) : null;
        updateStatus();
        updateSteamSection();
    }

    private void updateSteamSection() {
        if (!game.supportsSteam()) {
            llSteam.setVisibility(View.GONE);
            return;
        }

        llSteam.setVisibility(View.VISIBLE);
        Container container = GameSetup.findContainer(new ContainerManager(getContext()), game);
        boolean installed = container != null && GameSetup.isSteamInstalled(container);
        tvSteamStatus.setText(installed ? R.string.steam_installed_help : R.string.steam_not_installed_help);
        btSteam.setText(installed ? R.string.open_steam : R.string.install_steam);
        cbUseSteam.setEnabled(installed);
        cbUseSteam.setAlpha(installed ? 1.0f : 0.5f);
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

    /** Finds this game's container, creating it first when it does not exist yet. */
    private void withContainer(final Callback<Container> callback) {
        final Context context = getContext();
        if (!RootFS.find(context).isValid()) {
            AppUtils.showToast(context, R.string.system_files_are_not_installed_yet);
            return;
        }

        final ContainerManager manager = new ContainerManager(context);
        Container container = GameSetup.findContainer(manager, game);
        if (container != null) {
            callback.call(container);
            return;
        }

        try {
            preloaderDialog.show(R.string.creating_container);
            manager.createContainerAsync(GameSetup.createContainerData(context, game), (newContainer) -> {
                preloaderDialog.close();
                if (!isAdded()) return;
                if (newContainer != null) {
                    callback.call(newContainer);
                }
                else AppUtils.showToast(context, R.string.unable_to_create_container);
            });
        }
        catch (JSONException e) {
            preloaderDialog.close();
            AppUtils.showToast(context, R.string.unable_to_create_container);
        }
    }

    private void play() {
        if (exeFile == null || !exeFile.isFile()) {
            exeFile = null;
            updateStatus();
            return;
        }
        withContainer(this::launch);
    }

    private void launch(Container container) {
        final Activity activity = getActivity();
        String dosPath = GameSetup.resolveDosPath(container, exeFile);

        GameSetup.LaunchOptions options = new GameSetup.LaunchOptions();
        options.controlsProfileId = cbTouchControls.isChecked() ? GameSetup.ensureControlsProfile(activity, game) : 0;
        if (options.controlsProfileId > 0) GameSetup.applyStickSize(activity, options.controlsProfileId, Math.round(sbStickSize.getValue()));
        options.safeMode = cbSafeMode.isChecked();
        options.screenMode = sScreenMode.getSelectedItemPosition();
        options.wideScreenSize = GameSetup.getWideScreenSize(activity);
        options.floatingStick = cbFloatingStick.isChecked();
        options.touchscreenMode = cbTouchscreenMode.isChecked();
        options.relativeMouse = cbRelativeMouse.isChecked();
        options.useSteam = game.supportsSteam() && cbUseSteam.isChecked();

        File shortcutFile = dosPath != null ? GameSetup.writeShortcut(container, game, dosPath, options) : null;
        if (shortcutFile == null) {
            AppUtils.showToast(activity, R.string.unable_to_prepare_game);
            return;
        }
        startShortcut(container, shortcutFile);
    }

    private void startShortcut(Container container, File shortcutFile) {
        final Activity activity = getActivity();
        Intent intent = new Intent(activity, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("shortcut_path", shortcutFile.getPath());
        activity.startActivity(intent);
    }

    /** Installs Steam into this game's container, or opens it when it is already there. */
    private void setUpSteam() {
        withContainer((container) -> {
            final Activity activity = getActivity();
            String screenSize = GameSetup.getWideScreenSize(activity);
            String steamDosPath = GameSetup.getSteamDosPath(container);

            if (steamDosPath != null) {
                File shortcutFile = GameSetup.writeToolShortcut(container, GameSetup.STEAM_SHORTCUT_NAME, steamDosPath, GameSetup.STEAM_ARGS, screenSize);
                if (shortcutFile == null) {
                    AppUtils.showToast(activity, R.string.unable_to_prepare_game);
                    return;
                }
                AppUtils.showToast(activity, R.string.steam_hint);
                startShortcut(container, shortcutFile);
                return;
            }

            File winPocketDir = GameSetup.getWinPocketDir(container);
            if (!winPocketDir.isDirectory() && !winPocketDir.mkdirs()) {
                AppUtils.showToast(activity, R.string.unable_to_download_steam);
                return;
            }

            final File setupFile = new File(winPocketDir, GameSetup.STEAM_SETUP_FILENAME);
            final Runnable runSetup = () -> {
                if (!isAdded()) return;
                String scriptDosPath = GameSetup.writeSteamSetupScript(container);
                File shortcutFile = scriptDosPath != null ? GameSetup.writeToolShortcut(container, GameSetup.STEAM_SETUP_SHORTCUT_NAME, scriptDosPath, null, screenSize) : null;
                if (shortcutFile == null) {
                    AppUtils.showToast(activity, R.string.unable_to_prepare_game);
                    return;
                }
                AppUtils.showToast(activity, R.string.steam_setup_hint);
                startShortcut(container, shortcutFile);
            };

            if (setupFile.isFile() && setupFile.length() > 0) {
                runSetup.run();
                return;
            }

            HttpUtils.download(activity, GameSetup.STEAM_SETUP_URL, setupFile, (success) -> {
                if (success) {
                    runSetup.run();
                }
                else if (isAdded()) AppUtils.showToast(activity, R.string.unable_to_download_steam);
            });
        });
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
