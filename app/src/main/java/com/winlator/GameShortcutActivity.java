package com.winlator;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.preference.PreferenceManager;

import com.winlator.core.AppUtils;
import com.winlator.core.GameProfile;
import com.winlator.core.GameSetup;
import com.winlator.core.GameStarter;
import com.winlator.core.PreloaderDialog;

/**
 * WinPocket addition (not part of upstream Winlator).
 * Opened by a home-screen game shortcut. Starts the game with the settings from WinPocket's game
 * screen and closes. When the game is not ready yet, it opens WinPocket on that game instead.
 */
public class GameShortcutActivity extends Activity {
    public static final String EXTRA_GAME_ID = "winpocket_game_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GameProfile game = GameProfile.find(getIntent().getStringExtra(EXTRA_GAME_ID));
        if (game == null) {
            finish();
            return;
        }

        if (!GameStarter.isReady(this, game)) {
            openWinPocket(game);
            return;
        }

        GameStarter.start(this, game, new PreloaderDialog(this), this::finish, (messageResId) -> {
            AppUtils.showToast(getApplicationContext(), messageResId);
            openWinPocket(game);
        });
    }

    /** Shows WinPocket's game screen for this game, where the missing step can be done. */
    private void openWinPocket(GameProfile game) {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(GameSetup.PREF_SELECTED_GAME, game.id).apply();
        AppUtils.showToast(getApplicationContext(), getString(R.string.shortcut_set_up_first, game.name));

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
