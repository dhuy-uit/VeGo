package com.example.vego.wallet;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.data.CoinWallet;

/** Figma: "Tiện ích · Kết quả chơi game". */
public class GameResultActivity extends BaseActivity {

    private static final String EXTRA_SCORE = "score";
    private static final String EXTRA_EARNED = "earned";
    private static final String EXTRA_NEW_BEST = "new_best";
    private static final String EXTRA_BALANCE = "balance";
    private static final String EXTRA_TURNS = "turns_left";

    /** Kết quả do server tính (điểm hợp lệ, xu nhận được, số dư mới, lượt còn lại). */
    public static Intent intent(Context context, int score, int earned, boolean newBest, int balance, int turnsLeft) {
        return new Intent(context, GameResultActivity.class)
                .putExtra(EXTRA_SCORE, score)
                .putExtra(EXTRA_EARNED, earned)
                .putExtra(EXTRA_NEW_BEST, newBest)
                .putExtra(EXTRA_BALANCE, balance)
                .putExtra(EXTRA_TURNS, turnsLeft);
    }

    @Override
    protected Transition transition() {
        return Transition.RESULT;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_game_result);

        CoinWallet wallet = CoinWallet.get();
        int score = getIntent().getIntExtra(EXTRA_SCORE, 0);
        int earned = getIntent().getIntExtra(EXTRA_EARNED, 0);
        boolean newBest = getIntent().getBooleanExtra(EXTRA_NEW_BEST, false);

        ((TextView) findViewById(R.id.tv_result_heading)).setText(
                score >= 500 ? R.string.game_result_great
                        : earned > 0 ? R.string.game_result_ok : R.string.game_result_low);
        ((TextView) findViewById(R.id.tv_result_body)).setText(
                earned > 0 ? R.string.game_result_body : R.string.game_result_body_zero);
        ((TextView) findViewById(R.id.tv_result_rule)).setText(
                earned >= CoinWallet.MAX_COINS_PER_GAME ? R.string.game_result_rule_capped : R.string.game_result_rule);
        findViewById(R.id.tv_new_best).setVisibility(newBest ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tv_result_balance)).setText(getString(R.string.xu_amount,
                CoinWallet.coins(getIntent().getIntExtra(EXTRA_BALANCE, wallet.balance()))));

        int turns = getIntent().getIntExtra(EXTRA_TURNS, wallet.turnsLeft());
        ((TextView) findViewById(R.id.tv_result_turns)).setText(turns > 0
                ? getString(R.string.game_turns_left, turns) : getString(R.string.game_no_turns));

        countUp(findViewById(R.id.tv_result_score), score, "", "");
        countUp(findViewById(R.id.tv_result_coins), earned, "+", " xu");
        animateCoins();

        // Theo prototype: Chơi lại -> "Chơi game kiếm xu"; Về Ví xu / quay lại -> Ví xu
        onClick(R.id.btn_play_again, v -> finish());
        onClick(R.id.btn_back_wallet, v -> backToWallet());
        setupBackButton(this::backToWallet);
    }

    private void backToWallet() {
        startActivity(WalletActivity.intent(this));
        finish();
    }

    /** Số điểm / số xu chạy dần từ 0 lên. */
    private static void countUp(TextView view, int target, String prefix, String suffix) {
        ValueAnimator anim = ValueAnimator.ofInt(0, target);
        anim.setDuration(Math.min(1200, 400 + target));
        anim.setStartDelay(250);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> view.setText(prefix + CoinWallet.coins((int) a.getAnimatedValue()) + suffix));
        view.setText(prefix + "0" + suffix);
        anim.start();
    }

    /** Đồng xu lớn nảy lên, hai xu nhỏ bay vào, vầng sáng hiện dần. */
    private void animateCoins() {
        View fx = findViewById(R.id.iv_reward_fx);
        fx.setAlpha(0f);
        fx.setScaleX(0.7f);
        fx.setScaleY(0.7f);
        fx.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500).setStartDelay(100).start();

        View big = findViewById(R.id.coin_big);
        big.setScaleX(0.3f);
        big.setScaleY(0.3f);
        big.setAlpha(0f);
        big.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(480).setStartDelay(150)
                .setInterpolator(new OvershootInterpolator(2.4f)).start();

        float d = getResources().getDisplayMetrics().density;
        flyIn(findViewById(R.id.coin_small_left), 40 * d, 30 * d, 350);
        flyIn(findViewById(R.id.coin_small_right), -40 * d, 30 * d, 450);
    }

    private static void flyIn(View coin, float fromX, float fromY, long delay) {
        coin.setTranslationX(fromX);
        coin.setTranslationY(fromY);
        coin.setAlpha(0f);
        coin.animate().translationX(0).translationY(0).alpha(1f).setDuration(420).setStartDelay(delay)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
    }
}
