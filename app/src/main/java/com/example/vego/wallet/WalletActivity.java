package com.example.vego.wallet;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.data.CoinWallet;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.util.List;

/** Figma: "Tiện ích · Ví xu". */
public class WalletActivity extends BaseActivity {

    /** Số giao dịch hiện trước khi bấm "Xem tất cả" (Figma hiện 5). */
    private static final int PREVIEW_COUNT = 5;

    private final CoinWallet wallet = CoinWallet.get();
    private boolean showAll = false;

    /** Quay về Ví xu từ màn con (vd "Về Ví xu" ở Kết quả game). */
    public static Intent intent(Context context) {
        return new Intent(context, WalletActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireLogin()) return;
        setScreenContent(R.layout.activity_wallet);

        // Theo prototype: quay lại -> Thông tin cá nhân
        setupBackButton(this::finish);
        onClick(R.id.btn_check_in, v -> open(CheckInActivity.class));
        onClick(R.id.btn_play_game, v -> open(GameIntroActivity.class));
        onClick(R.id.btn_vouchers, v -> open(VoucherActivity.class));
        onClick(R.id.tv_show_all, v -> {
            showAll = !showAll;
            renderHistory();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        ApiClient.api().wallet().enqueue(new ApiCallback<ApiModels.Wallet>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.Wallet w) {
                wallet.update(w);
                render();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                showError(error);
            }
        });
    }

    private void render() {
        // Figma: số dư 40sp, chữ "xu" nhỏ hơn (17sp)
        String number = wallet.isLoaded() ? CoinWallet.coins(wallet.balance()) : "…";
        SpannableString balance = new SpannableString(getString(R.string.xu_amount, number));
        balance.setSpan(new RelativeSizeSpan(17f / 40f), number.length(), balance.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) findViewById(R.id.tv_balance)).setText(balance);
        ((TextView) findViewById(R.id.btn_check_in)).setText(
                wallet.checkedInToday() ? R.string.wallet_checked_in : R.string.wallet_check_in);
        renderHistory();
    }

    private void renderHistory() {
        LinearLayout list = findViewById(R.id.list_transactions);
        list.removeAllViews();
        List<CoinWallet.Transaction> history = wallet.history();
        int count = showAll ? history.size() : Math.min(PREVIEW_COUNT, history.size());
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < count; i++) {
            list.addView(transactionRow(inflater, list, history.get(i)));
        }

        TextView toggle = findViewById(R.id.tv_show_all);
        toggle.setVisibility(history.size() > PREVIEW_COUNT ? View.VISIBLE : View.GONE);
        toggle.setText(showAll ? getString(R.string.wallet_show_less)
                : getString(R.string.wallet_show_all, history.size()));
    }

    static View transactionRow(LayoutInflater inflater, LinearLayout parent, CoinWallet.Transaction tx) {
        Context ctx = parent.getContext();
        View row = inflater.inflate(R.layout.item_coin_transaction, parent, false);
        ((ImageView) row.findViewById(R.id.iv_tx_icon)).setImageResource(tx.source.icon);
        ((TextView) row.findViewById(R.id.tv_tx_title)).setText(tx.label);
        ((TextView) row.findViewById(R.id.tv_tx_time)).setText(tx.time);
        TextView amount = row.findViewById(R.id.tv_tx_amount);
        if (tx.amount >= 0) {
            amount.setText(ctx.getString(R.string.xu_plus, CoinWallet.coins(tx.amount)));
        } else {
            amount.setText(ctx.getString(R.string.xu_minus, CoinWallet.coins(-tx.amount)));
            amount.setTextColor(ContextCompat.getColor(ctx, R.color.vego_danger));
        }
        return row;
    }
}
