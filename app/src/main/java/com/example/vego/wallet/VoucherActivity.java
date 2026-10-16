package com.example.vego.wallet;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.CoinWallet;
import com.example.vego.data.Format;

/** Figma: "Tiện ích · Đổi xu lấy voucher". */
public class VoucherActivity extends BaseActivity {

    private final CoinWallet wallet = CoinWallet.get();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_vouchers);
        // Theo prototype: quay lại -> Ví xu
        setupBackButton(this::finish);
        render();
    }

    private void render() {
        ((TextView) findViewById(R.id.tv_voucher_balance)).setText(
                getString(R.string.xu_amount, CoinWallet.coins(wallet.balance())));

        LinearLayout list = findViewById(R.id.list_vouchers);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (CoinWallet.Voucher v : wallet.vouchers()) {
            View card = inflater.inflate(R.layout.item_voucher, list, false);
            ((TextView) card.findViewById(R.id.tv_voucher_name)).setText(
                    getString(R.string.voucher_name, Format.money(v.discount)));
            ((TextView) card.findViewById(R.id.tv_voucher_condition)).setText(
                    getString(R.string.voucher_condition, Format.money(v.minFare)));
            ((TextView) card.findViewById(R.id.tv_voucher_cost)).setText(
                    getString(R.string.xu_amount, CoinWallet.coins(v.cost)));

            TextView note = card.findViewById(R.id.tv_voucher_note);
            TextView button = card.findViewById(R.id.btn_redeem);
            boolean affordable = wallet.canRedeem(v);
            button.setEnabled(affordable);
            if (affordable) {
                note.setText(v.owned > 0 ? getString(R.string.voucher_owned, v.owned)
                        : getString(R.string.voucher_valid));
                button.setOnClickListener(x -> confirm(v));
            } else {
                String missing = CoinWallet.coins(v.cost - wallet.balance());
                note.setText(getString(R.string.voucher_need_more, missing));
                note.setTextColor(ContextCompat.getColor(this, R.color.vego_danger));
                // Nút xám không bấm được -> chạm vào thẻ để biết còn thiếu bao nhiêu xu
                button.setClickable(false);
                card.setOnClickListener(x -> VeGoBanner.show(this,
                        getString(R.string.voucher_not_enough, missing), VeGoBanner.Type.WARNING));
            }
            list.addView(card);
        }
    }

    private void confirm(CoinWallet.Voucher v) {
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.INFO)
                .title(getString(R.string.voucher_confirm_title, CoinWallet.coins(v.cost)))
                .message(getString(R.string.voucher_confirm_message, Format.money(v.discount),
                        Format.money(v.minFare), CoinWallet.coins(wallet.balance() - v.cost)))
                .primary(R.string.voucher_confirm_ok, () -> redeem(v))
                .secondary(R.string.voucher_confirm_later, null)
                .show();
    }

    private void redeem(CoinWallet.Voucher v) {
        String code = wallet.redeem(v);
        if (code == null) return;
        render();
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.SUCCESS)
                .title(R.string.voucher_done_title)
                .message(getString(R.string.voucher_done_message, code))
                .primary(R.string.action_ok, null)
                .show();
    }
}
