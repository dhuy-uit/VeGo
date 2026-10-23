package com.example.vego.wallet;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.CoinWallet;
import com.example.vego.data.Format;
import com.example.vego.data.NotificationStore;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Figma: "Tiện ích · Đổi xu lấy voucher". Danh sách lấy từ GET /api/v1/wallet/vouchers;
 * đổi gọi POST .../redeem, mã nhận được nhập ở màn Thanh toán (cũng hiện sẵn trong gợi ý mã).
 */
public class VoucherActivity extends BaseActivity {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CoinWallet wallet = CoinWallet.get();
    @Nullable private ApiModels.WalletVouchers data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireLogin()) return;
        setScreenContent(R.layout.activity_vouchers);
        // Theo prototype: quay lại -> Ví xu
        setupBackButton(this::finish);
        ((TextView) findViewById(R.id.tv_voucher_balance)).setText(
                getString(R.string.xu_amount, CoinWallet.coins(wallet.balance())));
        load();
    }

    private void load() {
        ApiClient.api().walletVouchers().enqueue(new ApiCallback<ApiModels.WalletVouchers>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.WalletVouchers v) {
                if (v == null) return;
                data = v;
                wallet.setBalance(v.balance);
                render();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                showError(error);
            }
        });
    }

    private void render() {
        ApiModels.WalletVouchers v = data;
        if (v == null) return;
        ((TextView) findViewById(R.id.tv_voucher_balance)).setText(
                getString(R.string.xu_amount, CoinWallet.coins(v.balance)));

        LinearLayout list = findViewById(R.id.list_vouchers);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ApiModels.VoucherOffer o : v.offers) {
            View card = inflater.inflate(R.layout.item_voucher, list, false);
            ((TextView) card.findViewById(R.id.tv_voucher_name)).setText(
                    getString(R.string.voucher_name, Format.money(o.discount)));
            ((TextView) card.findViewById(R.id.tv_voucher_condition)).setText(
                    getString(R.string.voucher_condition, Format.money(o.minOrder)));
            ((TextView) card.findViewById(R.id.tv_voucher_cost)).setText(
                    getString(R.string.xu_amount, CoinWallet.coins(o.cost)));

            TextView note = card.findViewById(R.id.tv_voucher_note);
            TextView button = card.findViewById(R.id.btn_redeem);
            boolean affordable = v.balance >= o.cost;
            button.setEnabled(affordable);
            if (affordable) {
                note.setText(o.owned > 0 ? getString(R.string.voucher_owned_days, o.owned, o.validDays)
                        : getString(R.string.voucher_valid_days, o.validDays));
                button.setOnClickListener(x -> confirm(o));
            } else {
                String missing = CoinWallet.coins(o.cost - v.balance);
                note.setText(getString(R.string.voucher_need_more, missing));
                note.setTextColor(ContextCompat.getColor(this, R.color.vego_danger));
                // Nút xám không bấm được -> chạm vào thẻ để biết còn thiếu bao nhiêu xu
                button.setClickable(false);
                card.setOnClickListener(x -> VeGoBanner.show(this,
                        getString(R.string.voucher_not_enough, missing), VeGoBanner.Type.WARNING));
            }
            list.addView(card);
        }

        // Voucher đã đổi: chạm để sao chép mã
        if (v.mine != null && !v.mine.isEmpty()) {
            list.addView(sectionTitle(getString(R.string.voucher_mine_title)));
            for (ApiModels.MyVoucher m : v.mine) {
                View item = inflater.inflate(R.layout.item_voucher_suggestion, list, false);
                ((TextView) item.findViewById(R.id.tv_voucher_code)).setText(m.code);
                ((TextView) item.findViewById(R.id.tv_voucher_desc)).setText(myVoucherText(m));
                boolean usable = "AVAILABLE".equals(m.status);
                item.setAlpha(usable ? 1f : 0.5f);
                item.setOnClickListener(x -> {
                    if (usable) copyCode(m.code);
                });
                list.addView(item);
            }
        }
    }

    private String myVoucherText(ApiModels.MyVoucher m) {
        String state;
        if ("USED".equals(m.status)) {
            state = getString(R.string.voucher_state_used);
        } else if ("EXPIRED".equals(m.status)) {
            state = getString(R.string.voucher_state_expired);
        } else {
            Instant exp = NotificationStore.parseInstant(m.expiresAt);
            state = exp == null ? "" : getString(R.string.voucher_state_until,
                    DAY.format(exp.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))));
        }
        return getString(R.string.voucher_mine_desc, Format.money(m.discount), Format.money(m.minOrder), state);
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(ContextCompat.getColor(this, R.color.vego_text));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(ResourcesCompat.getFont(this, R.font.inter_bold));
        title.setPadding(0, Math.round(8 * getResources().getDisplayMetrics().density), 0, 0);
        return title;
    }

    private void copyCode(String code) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("VeGo voucher", code));
        VeGoBanner.show(this, getString(R.string.voucher_copied, code), VeGoBanner.Type.SUCCESS);
    }

    private void confirm(ApiModels.VoucherOffer o) {
        int balance = data != null ? data.balance : wallet.balance();
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.INFO)
                .title(getString(R.string.voucher_confirm_title, CoinWallet.coins(o.cost)))
                .message(getString(R.string.voucher_confirm_message, Format.money(o.discount),
                        Format.money(o.minOrder), CoinWallet.coins(balance - o.cost)))
                .primary(R.string.voucher_confirm_ok, () -> redeem(o))
                .secondary(R.string.voucher_confirm_later, null)
                .show();
    }

    private void redeem(ApiModels.VoucherOffer o) {
        ApiClient.api().redeemVoucher(o.id).enqueue(new ApiCallback<ApiModels.Redeemed>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.Redeemed r) {
                if (r == null || r.voucher == null) return;
                wallet.setBalance(r.balance);
                load();
                VeGoDialog.with(VoucherActivity.this)
                        .type(VeGoDialog.Type.SUCCESS)
                        .title(R.string.voucher_done_title)
                        .message(getString(R.string.voucher_done_message, r.voucher.code))
                        .primary(R.string.voucher_copy, () -> copyCode(r.voucher.code))
                        .secondary(R.string.action_ok, null)
                        .show();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                showError(error);
                load();
            }
        });
    }
}
