package com.example.vego.booking;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;


import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.HoldTimer;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.BookingDraft.PaymentMethod;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.TripRepository;

/** Figma: "Đặt vé · Thanh toán". */
public class PaymentActivity extends BaseActivity {

    private final BookingDraft draft = BookingDraft.get();
    private HoldTimer holdTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_payment);

        if (draft.trip == null) draft.selectTrip(TripRepository.suggested());
        Trip trip = draft.trip;
        draft.startHoldIfNeeded();

        ((TextView) findViewById(R.id.tv_order_title)).setText(trip.operator + " · Ghế " + draft.seat);
        ((TextView) findViewById(R.id.tv_order_route)).setText(trip.origin + " → " + trip.destination);
        ((InfoRowView) findViewById(R.id.row_depart)).setValue(trip.departTime() + " · " + Format.date(draft.date));
        ((InfoRowView) findViewById(R.id.row_fare)).setValue(Format.money(trip.price));
        ((InfoRowView) findViewById(R.id.row_total)).setValue(Format.money(draft.total()));
        ((TextView) findViewById(R.id.tv_hold_note)).setText(getString(R.string.payment_hold_note, draft.seat));

        onClick(R.id.option_momo, v -> selectMethod(PaymentMethod.MOMO));
        onClick(R.id.option_vnpay, v -> selectMethod(PaymentMethod.VNPAY));
        onClick(R.id.option_card, v -> selectMethod(PaymentMethod.CARD));
        selectMethod(draft.paymentMethod);

        holdTimer = new HoldTimer(findViewById(R.id.tv_hold_time), () -> BookingNav.holdExpired(this))
                .onOneMinuteLeft(() -> BookingNav.warnOneMinute(this));
        setupBackButton(this::finish);
        onClick(R.id.btn_pay, v -> simulateGateway());
    }

    @Override
    protected void onResume() {
        super.onResume();
        holdTimer.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        holdTimer.stop();
    }

    private void selectMethod(PaymentMethod method) {
        draft.paymentMethod = method;
        styleOption(R.id.option_momo, R.id.radio_momo, method == PaymentMethod.MOMO);
        styleOption(R.id.option_vnpay, R.id.radio_vnpay, method == PaymentMethod.VNPAY);
        styleOption(R.id.option_card, R.id.radio_card, method == PaymentMethod.CARD);

        int body;
        switch (method) {
            case VNPAY: body = R.string.payment_info_vnpay; break;
            case CARD: body = R.string.payment_info_card; break;
            default: body = R.string.payment_info_momo; break;
        }
        ((TextView) findViewById(R.id.tv_method_info_title))
                .setText(getString(R.string.payment_info_title, method.label));
        ((TextView) findViewById(R.id.tv_method_info_body)).setText(body);
        ((TextView) findViewById(R.id.btn_pay))
                .setText(getString(R.string.payment_pay, method.label, Format.money(draft.total())));
    }

    private void styleOption(int optionId, int radioId, boolean selected) {
        View option = findViewById(optionId);
        option.setSelected(selected);
        ((ImageView) findViewById(radioId))
                .setImageResource(selected ? R.drawable.ic_circle_check : R.drawable.ic_circle);
        // Vòng tròn chưa chọn màu xanh như Figma
        ((ImageView) findViewById(radioId)).setColorFilter(
                getColor(R.color.vego_primary));
    }

    /**
     * Bản prototype chưa nối cổng thanh toán thật: hiện hộp thoại để chọn kết quả
     * giao dịch, giúp đi được cả 2 nhánh Thành công / Thất bại như trong Figma.
     */
    private void simulateGateway() {
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.INFO)
                .title(getString(R.string.payment_sim_title, draft.paymentMethod.label))
                .message(R.string.payment_sim_message)
                .primary(R.string.payment_sim_success, () -> {
                    holdTimer.stop();
                    draft.issueTicketCode();
                    open(PaymentSuccessActivity.class);
                })
                .secondary(R.string.payment_sim_fail, () -> open(PaymentFailedActivity.class))
                .show();
    }
}
