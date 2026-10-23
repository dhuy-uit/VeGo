package com.example.vego.wallet;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.CoinWallet;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;
import com.example.vego.wallet.game.RunnerGameView;

/** Màn chơi "Khủng long phiêu lưu": chạm để nhảy, chạy càng xa càng nhiều điểm. */
public class GameActivity extends BaseActivity implements RunnerGameView.Listener {

    private final CoinWallet wallet = CoinWallet.get();
    private RunnerGameView game;
    private TextView tvScore;
    private TextView tvCoins;
    /** Đã báo server bắt đầu lượt chưa (chỉ trừ lượt khi bắt đầu chạy thật). */
    private boolean turnUsed = false;
    /** Mã lượt chơi server cấp (POST /wallet/game/start), 0 = chưa có. */
    private long playId = 0;
    /** Điểm chờ gửi khi lượt kết thúc trước lúc server trả mã lượt chơi. */
    private int pendingScore = -1;
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
        if (turnUsed) return;
        turnUsed = true;
        ApiClient.api().startGame().enqueue(new ApiCallback<ApiModels.GameStart>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.GameStart start) {
                if (start == null) return;
                playId = start.playId;
                wallet.setTurnsLeft(start.turnsLeft);
                if (pendingScore >= 0) sendScore(pendingScore);
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                // Hết lượt / mất mạng: dừng game và báo lý do
                game.pause();
                finished = true;
                VeGoDialog.with(GameActivity.this)
                        .type(VeGoDialog.Type.WARNING)
                        .title(R.string.game_no_turns_title)
                        .message(error.message)
                        .cancelable(false)
                        .primary(R.string.action_ok, GameActivity.this::finish)
                        .show();
            }
        });
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
        if (playId == 0) {
            pendingScore = score;   // chờ server trả mã lượt chơi rồi gửi điểm
            return;
        }
        sendScore(score);
    }

    /** Gửi điểm lên server: server đổi ra xu (10 điểm = 1 xu, tối đa 100 xu) và cộng vào ví. */
    private void sendScore(int score) {
        ApiClient.api().finishGame(playId, new ApiModels.ScoreRequest(score))
                .enqueue(new ApiCallback<ApiModels.GameResult>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.GameResult r) {
                        if (r == null) return;
                        wallet.setBalance(r.balance);
                        wallet.setTurnsLeft(r.turnsLeft);
                        wallet.setBestScore(r.bestScore);
                        startActivity(GameResultActivity.intent(GameActivity.this, r.score, r.coins, r.newBest,
                                r.balance, r.turnsLeft));
                        finish();
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        VeGoDialog.with(GameActivity.this)
                                .type(VeGoDialog.Type.WARNING)
                                .title(R.string.game_send_failed_title)
                                .message(error.message)
                                .cancelable(false)
                                .primary(R.string.game_send_retry, () -> sendScore(score))
                                .secondary(R.string.action_cancel, GameActivity.this::finish)
                                .show();
                    }
                });
    }
}
