package com.example.vego.wallet;

import android.os.Bundle;
import android.widget.TextView;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.CoinWallet;

/** Figma: "Tiện ích · Chơi game kiếm xu" — giới thiệu game và số lượt còn lại. */
public class GameIntroActivity extends BaseActivity {

    private final CoinWallet wallet = CoinWallet.get();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_game_intro);

        setupBackButton(this::finish);
        onClick(R.id.btn_start_game, v -> start());
    }

    @Override
    protected void onResume() {
        super.onResume();
        int turns = wallet.turnsLeft();
        ((TextView) findViewById(R.id.tv_turns)).setText(turns > 0
                ? getString(R.string.game_turns_left, turns)
                : getString(R.string.game_no_turns));
        findViewById(R.id.btn_start_game).setAlpha(turns > 0 ? 1f : 0.5f);
    }

    private void start() {
        if (wallet.turnsLeft() <= 0) {
            VeGoDialog.with(this)
                    .type(VeGoDialog.Type.WARNING)
                    .title(R.string.game_no_turns_title)
                    .message(getString(R.string.game_no_turns_message, CoinWallet.GAME_TURNS_PER_DAY))
                    .primary(R.string.game_go_check_in, () -> {
                        open(CheckInActivity.class);
                        finish();
                    })
                    .secondary(R.string.action_ok, null)
                    .show();
            return;
        }
        // Theo prototype: Bắt đầu chơi -> (màn chơi) -> Kết quả chơi game
        open(GameActivity.class);
    }
}
