package com.example.vego.wallet;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.CoinWallet;
import com.example.vego.data.Format;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.util.Calendar;

/**
 * Figma: "Tiện ích · Điểm danh". Chu kỳ 7 ngày lấy từ GET /api/v1/wallet/check-in,
 * bấm điểm danh gọi POST (server cộng xu, chống điểm danh 2 lần).
 */
public class CheckInActivity extends BaseActivity {

    private final CoinWallet wallet = CoinWallet.get();
    @Nullable private ApiModels.CheckInStatus status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireLogin()) return;
        setScreenContent(R.layout.activity_check_in);

        // Theo prototype: quay lại -> Ví xu
        setupBackButton(this::finish);
        onClick(R.id.btn_check_in_now, v -> checkIn());
        findViewById(R.id.btn_check_in_now).setEnabled(false);
        load();
    }

    private void load() {
        ApiClient.api().checkInStatus().enqueue(new ApiCallback<ApiModels.CheckInStatus>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.CheckInStatus s) {
                if (s == null) return;
                status = s;
                render();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                showError(error);
            }
        });
    }

    private void render() {
        ApiModels.CheckInStatus s = status;
        if (s == null || s.days == null) return;
        boolean done = s.checkedInToday;
        Calendar today = UserSession.parseIsoDate(s.today);

        ((TextView) findViewById(R.id.tv_streak)).setText(done
                ? getString(R.string.check_in_streak_done, s.streak)
                : getString(R.string.check_in_streak_pending, s.streak));
        ((TextView) findViewById(R.id.tv_reward_label)).setText(
                getString(R.string.check_in_reward_label, today != null ? Format.shortDate(today) : ""));
        ((TextView) findViewById(R.id.tv_reward_amount)).setText(
                getString(R.string.xu_plus, String.valueOf(s.todayReward)));
        ((TextView) findViewById(R.id.tv_reward_note)).setText(done
                ? getString(R.string.check_in_reward_note_done, CoinWallet.coins(s.balance))
                : getString(R.string.check_in_reward_note));

        ((TextView) findViewById(R.id.tv_check_in_state)).setText(
                done ? R.string.check_in_state_done : R.string.check_in_state_pending);
        TextView button = findViewById(R.id.btn_check_in_now);
        button.setText(done ? R.string.check_in_done_button : R.string.check_in_now);
        button.setEnabled(!done);

        LinearLayout row = findViewById(R.id.row_days);
        row.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ApiModels.CheckInDay day : s.days) {
            row.addView(dayCell(inflater, row, day, s.todayIndex));
        }
    }

    /** Ô ngày: đã điểm danh (dấu tích), hôm nay (đồng xu, viền xanh), ngày tới (ổ khóa). */
    private View dayCell(LayoutInflater inflater, LinearLayout parent, ApiModels.CheckInDay day, int todayIndex) {
        View cell = inflater.inflate(R.layout.item_check_in_day, parent, false);
        TextView label = cell.findViewById(R.id.tv_day_label);
        TextView date = cell.findViewById(R.id.tv_day_date);
        ImageView state = cell.findViewById(R.id.iv_day_state);
        Calendar c = UserSession.parseIsoDate(day.date);
        String shortDate = c != null ? Format.shortDate(c) : "";

        date.setText(shortDate);
        ((TextView) cell.findViewById(R.id.tv_day_reward)).setText(
                getString(R.string.xu_plus, String.valueOf(day.reward)));

        boolean isToday = day.index == todayIndex;
        boolean checked = "CHECKED".equals(day.state);
        int primary = ContextCompat.getColor(this, R.color.vego_primary);

        if (isToday) {
            cell.setSelected(true);
            label.setText(R.string.check_in_today);
            label.setTextColor(primary);
            label.setTypeface(ResourcesCompat.getFont(this, R.font.inter_bold));
            date.setTextColor(primary);
        } else {
            label.setText(getString(R.string.check_in_day, day.index + 1));
        }

        if (checked) {
            state.setImageResource(R.drawable.ic_check);
            state.setBackgroundResource(R.drawable.bg_tile_mint);
        } else if (isToday) {
            state.setImageResource(R.drawable.ic_coins);
        } else {
            state.setImageResource(R.drawable.ic_lock_keyhole);
            ImageViewCompat.setImageTintList(state,
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.vego_text_secondary)));
            cell.setOnClickListener(v -> VeGoBanner.show(this,
                    getString(R.string.check_in_locked, shortDate), VeGoBanner.Type.INFO));
        }
        return cell;
    }

    private void checkIn() {
        if (status == null || status.checkedInToday) return;
        setBusy(R.id.btn_check_in_now, true);
        ApiClient.api().checkIn().enqueue(new ApiCallback<ApiModels.CheckInResult>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.CheckInResult r) {
                if (r == null || r.status == null) return;
                status = r.status;
                wallet.setBalance(r.status.balance);
                wallet.setCheckedInToday(true);
                setBusy(R.id.btn_check_in_now, false);
                render();
                bounce(findViewById(R.id.coin_today));
                VeGoDialog.with(CheckInActivity.this)
                        .type(VeGoDialog.Type.SUCCESS)
                        .title(R.string.check_in_done_title)
                        .message(getString(R.string.check_in_done_message, r.reward, r.status.streak,
                                r.status.daysToBonus))
                        .primary(R.string.action_ok, null)
                        .show();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                setBusy(R.id.btn_check_in_now, false);
                showError(error);
                load();
            }
        });
    }

    private static void bounce(View view) {
        view.setScaleX(0.6f);
        view.setScaleY(0.6f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(new OvershootInterpolator(3f)).start();
    }
}
