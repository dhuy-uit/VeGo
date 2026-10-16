package com.example.vego.booking;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.HoldTimer;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.TripRepository;

/** Figma: "Đặt vé · Thanh toán thất bại". Ghế vẫn được giữ, có thể thử lại. */
public class PaymentFailedActivity extends BaseActivity {

    private final BookingDraft draft = BookingDraft.get();
    private HoldTimer holdTimer;

    @Override
    protected Transition transition() {
        return Transition.RESULT;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_payment_failed);

        if (draft.trip == null) draft.selectTrip(TripRepository.suggested());
        Trip trip = draft.trip;
        draft.startHoldIfNeeded();

        ((TextView) findViewById(R.id.tv_failed_body)).setText(
                getString(R.string.failed_body, draft.paymentMethod.label));
        ((InfoRowView) findViewById(R.id.row_trip)).setValue(trip.origin + " → " + trip.destination);
        ((InfoRowView) findViewById(R.id.row_depart)).setValue(trip.departTime() + " · " + Format.date(draft.date));
        ((InfoRowView) findViewById(R.id.row_holding_seat)).setValue(draft.seat);
        ((InfoRowView) findViewById(R.id.row_amount)).setValue(Format.money(draft.total()));

        ResultAnimations.popIn(findViewById(R.id.iv_result));

        holdTimer = new HoldTimer(findViewById(R.id.tv_hold_time), () -> {
            BookingNav.holdExpired(this);
            finish();
        });

        // Theo prototype: thử lại / đổi phương thức -> quay về màn Thanh toán
        onClick(R.id.btn_retry, v -> finish());
        onClick(R.id.btn_other_method, v -> finish());
        // Hủy giữ chỗ / quay lại -> Danh sách chuyến
        onClick(R.id.btn_cancel_hold, v ->
                BookingNav.confirmCancelHold(this, () -> BookingNav.backToTripList(this)));
        setupBackButton(() -> BookingNav.backToTripList(this));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                BookingNav.backToTripList(PaymentFailedActivity.this);
            }
        });
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
}
