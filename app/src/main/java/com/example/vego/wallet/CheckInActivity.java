package com.example.vego.wallet;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.CoinWallet;
import com.example.vego.data.Format;

/** Figma: "Tiện ích · Điểm danh". */
public class CheckInActivity extends BaseActivity {

    private final CoinWallet wallet = CoinWallet.get();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_check_in);

        // Theo prototype: quay lại -> Ví xu
        setupBackButton(this::finish);
        onClick(R.id.btn_check_in_now, v -> checkIn());
        render();
    }

    private void render() {
        boolean done = wallet.checkedInToday();
        int today = wallet.todayIndex();

        ((TextView) findViewById(R.id.tv_streak)).setText(done
                ? getString(R.string.check_in_streak_done, wallet.streak())
                : getString(R.string.check_in_streak_pending, wallet.streak()));
        ((TextView) findViewById(R.id.tv_reward_label)).setText(
                getString(R.string.check_in_reward_label, Format.shortDate(wallet.today())));
        ((TextView) findViewById(R.id.tv_reward_amount)).setText(
                getString(R.string.xu_plus, String.valueOf(wallet.todayReward())));
        ((TextView) findViewById(R.id.tv_reward_note)).setText(done
                ? getString(R.string.check_in_reward_note_done, CoinWallet.coins(wallet.balance()))
                : getString(R.string.check_in_reward_note));

        ((TextView) findViewById(R.id.tv_check_in_state)).setText(
                done ? R.string.check_in_state_done : R.string.check_in_state_pending);
        TextView button = findViewById(R.id.btn_check_in_now);
        button.setText(done ? R.string.check_in_done_button : R.string.check_in_now);
        button.setEnabled(!done);

        LinearLayout row = findViewById(R.id.row_days);
        row.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < CoinWallet.CHECK_IN_REWARDS.length; i++) {
            row.addView(dayCell(inflater, row, i, today, done));
        }
    }

    /** Ô ngày: đã điểm danh (dấu tích), hôm nay (đồng xu, viền xanh), ngày tới (ổ khóa). */
    private View dayCell(LayoutInflater inflater, LinearLayout parent, int index, int today, boolean doneToday) {
        View cell = inflater.inflate(R.layout.item_check_in_day, parent, false);
        TextView label = cell.findViewById(R.id.tv_day_label);
        TextView date = cell.findViewById(R.id.tv_day_date);
        ImageView state = cell.findViewById(R.id.iv_day_state);
        String shortDate = Format.shortDate(wallet.weekDay(index));

        date.setText(shortDate);
        ((TextView) cell.findViewById(R.id.tv_day_reward)).setText(
                getString(R.string.xu_plus, String.valueOf(CoinWallet.CHECK_IN_REWARDS[index])));

        boolean isToday = index == today;
        boolean checked = index < today || (isToday && doneToday);
        int primary = ContextCompat.getColor(this, R.color.vego_primary);

        if (isToday) {
            cell.setSelected(true);
            label.setText(R.string.check_in_today);
            label.setTextColor(primary);
            label.setTypeface(ResourcesCompat.getFont(this, R.font.inter_bold));
            date.setTextColor(primary);
        } else {
            label.setText(getString(R.string.check_in_day, index + 1));
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
        int reward = wallet.checkIn();
        if (reward == 0) return;
        render();
        bounce(findViewById(R.id.coin_today));

        int daysToBonus = Math.max(0, CoinWallet.CHECK_IN_REWARDS.length - wallet.streak());
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.SUCCESS)
                .title(R.string.check_in_done_title)
                .message(getString(R.string.check_in_done_message, reward, wallet.streak(), daysToBonus))
                .primary(R.string.action_ok, null)
                .show();
    }

    private static void bounce(View view) {
        view.setScaleX(0.6f);
        view.setScaleY(0.6f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(new OvershootInterpolator(3f)).start();
    }
}
