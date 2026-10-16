package com.example.vego.booking;

import android.os.Bundle;
import android.widget.TextView;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.HoldTimer;
import com.example.vego.common.TripCardBinder;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.TripRepository;

/** Figma: "Đặt vé · Xác nhận đặt vé" (Bước 3/3). Ghế được giữ 5 phút từ màn này. */
public class BookingConfirmActivity extends BaseActivity {

    private final BookingDraft draft = BookingDraft.get();
    private HoldTimer holdTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_booking_confirm);

        if (draft.trip == null) draft.selectTrip(TripRepository.suggested());
        Trip trip = draft.trip;
        draft.startHoldIfNeeded();

        ((TextView) findViewById(R.id.tv_hold_badge)).setText(getString(R.string.confirm_hold_badge, draft.seat));
        TripCardBinder.bindWithSeat(findViewById(R.id.card_trip), trip, draft.date, draft.seat);
        ((TextView) findViewById(R.id.tv_passenger)).setText(draft.passengerName + " · " + draft.passengerPhone);
        ((TextView) findViewById(R.id.tv_seat_qty)).setText(getString(R.string.confirm_seat_qty_value, draft.seat));
        ((TextView) findViewById(R.id.tv_stops)).setText(
                TripRepository.PICKUP_POINTS[draft.pickupIndex][0] + "\n→ "
                        + TripRepository.DROPOFF_POINTS[draft.dropoffIndex][0]);
        ((InfoRowView) findViewById(R.id.row_fare_fee)).setValue(
                Format.money(trip.price) + " / " + Format.money(0));
        ((InfoRowView) findViewById(R.id.row_total)).setValue(Format.money(draft.total()));

        TextView btnPay = findViewById(R.id.btn_pay);
        btnPay.setText(getString(R.string.confirm_pay, Format.money(draft.total())));
        btnPay.setOnClickListener(v -> open(PaymentActivity.class));

        holdTimer = new HoldTimer(findViewById(R.id.tv_hold_time), () -> BookingNav.holdExpired(this))
                .onOneMinuteLeft(() -> BookingNav.warnOneMinute(this));
        // Theo prototype: bấm vào thanh giữ chỗ để xem màn "Hết thời gian giữ chỗ"
        onClick(R.id.hold_timer, v -> BookingNav.holdExpired(this));

        setupBackButton(this::finish);
        onClick(R.id.btn_cancel_hold, v -> BookingNav.confirmCancelHold(this, this::finish));
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
