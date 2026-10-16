package com.example.vego.wallet;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.CoinWallet;
import com.example.vego.wallet.game.RunnerGameView;

/** Màn chơi "Khủng long phiêu lưu": chạm để nhảy, chạy càng xa càng nhiều điểm. */
public class GameActivity extends BaseActivity implements RunnerGameView.Listener {

    private final CoinWallet wallet = CoinWallet.get();
    private RunnerGameView game;
    private TextView tvScore;
    private TextView tvCoins;
    /** Đã trừ lượt chơi chưa (chỉ trừ khi bắt đầu chạy thật). */
    private boolean turnUsed = false;
    private boolean finished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_game);

        game = findViewById(R.id.game_view);
        tvScore = findViewById(R.id.tv_score);
        tvCoins = findViewById(R.id.tv_coins);
        ((TextView) findViewById(R.id.tv_best)).setText(String.valueOf(wallet.bestScore()));
        game.setListener(this);
        game.setTexts(getString(R.string.game_tap_to_start), getString(R.string.game_paused),
                getString(R.string.game_tap_to_continue), getString(R.string.game_over));

        setupBackButton(this::askQuit);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                askQuit();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Rời app giữa chừng thì tạm dừng; quay lại chạm vào khung để chơi tiếp
        game.pause();
    }

    // ---------- RunnerGameView.Listener ----------

    @Override
    public void onGameStarted() {
        if (!turnUsed) {
            turnUsed = wallet.startGame();
        }
    }

    @Override
    public void onScoreChanged(int score) {
        tvScore.setText(String.valueOf(score));
        tvCoins.setText("+" + CoinWallet.coinsForScore(score));
    }

    @Override
    public void onGameOver(int score) {
        // Dừng một nhịp cho người chơi thấy va chạm rồi mới sang màn kết quả
        game.postDelayed(() -> showResult(score), 750);
    }

    // ---------- Thoát giữa chừng ----------

    private void askQuit() {
        if (finished) return;
        if (game.getState() == RunnerGameView.State.READY) {
            VeGoDialog.with(this)
                    .type(VeGoDialog.Type.INFO)
                    .title(R.string.game_quit_ready_title)
                    .message(R.string.game_quit_ready_message)
                    .primary(R.string.game_quit_ready_confirm, this::finish)
                    .secondary(R.string.game_quit_continue, null)
                    .show();
            return;
        }
        RunnerGameView.State state = game.getState();
        if (state != RunnerGameView.State.RUNNING && state != RunnerGameView.State.PAUSED) return;
        game.pause();
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.WARNING)
                .title(R.string.game_quit_title)
                .message(getString(R.string.game_quit_message, game.getScore()))
                .cancelable(false)
                .primary(R.string.game_quit_confirm, () -> game.endNow())
                .secondary(R.string.game_quit_continue, () -> game.resume())
                .show();
    }

    private void showResult(int score) {
        if (finished || isFinishing()) return;
        finished = true;
        int best = wallet.bestScore();
        int earned = wallet.finishGame(score);
        startActivity(GameResultActivity.intent(this, score, earned, score > best && score > 0));
        finish();
    }
}
