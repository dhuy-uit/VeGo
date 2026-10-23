package com.example.vego.booking;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.auth.LoginActivity;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.HoldTimer;
import com.example.vego.common.TripCardBinder;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.TripRepository;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

/**
 * Figma: "Đặt vé · Xác nhận đặt vé" (Bước 3/3).
 * Vào màn này app gọi POST /api/v1/bookings để server giữ ghế 5 phút (đồng hồ đếm theo giờ server).
 */
public class BookingConfirmActivity extends BaseActivity {

    private final BookingDraft draft = BookingDraft.get();
    private HoldTimer holdTimer;
    private boolean holding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_booking_confirm);

        if (draft.trip == null) draft.selectTrip(TripRepository.suggested());
        Trip trip = draft.trip;

        ((TextView) findViewById(R.id.tv_hold_badge)).setText(getString(R.string.confirm_hold_badge, draft.seat));
        TripCardBinder.bindWithSeat(findViewById(R.id.card_trip), trip, draft.date, draft.seat);
        ((TextView) findViewById(R.id.tv_passenger)).setText(draft.passengerName + " · " + draft.passengerPhone);
        ((TextView) findViewById(R.id.tv_seat_qty)).setText(getString(R.string.confirm_seat_qty_value, draft.seat));
        ((TextView) findViewById(R.id.tv_stops)).setText(
                draft.pickupLabel() + "\n→ " + draft.dropoffLabel());
        ((InfoRowView) findViewById(R.id.row_fare_fee)).setValue(
                Format.money(trip.price) + " / " + Format.money(0));
        bindTotal();

        onClick(R.id.btn_pay, v -> {
            if (holding) open(PaymentActivity.class);
            else holdSeat();
        });

        holdTimer = new HoldTimer(findViewById(R.id.tv_hold_time), () -> BookingNav.holdExpired(this))
                .onOneMinuteLeft(() -> BookingNav.warnOneMinute(this));

        setupBackButton(this::finish);
        onClick(R.id.btn_cancel_hold, v -> BookingNav.confirmCancelHold(this, this::finish));

        if (draft.hasServerBooking()) {
            holding = true;
        } else {
            holdSeat();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (holding) holdTimer.start();
        bindTotal();
    }

    @Override
    protected void onPause() {
        super.onPause();
        holdTimer.stop();
    }

    private void bindTotal() {
        ((InfoRowView) findViewById(R.id.row_total)).setValue(Format.money(draft.total()));
        ((TextView) findViewById(R.id.btn_pay)).setText(holding
                ? getString(R.string.confirm_pay, Format.money(draft.total()))
                : getString(R.string.confirm_hold_seat));
    }

    /** Giữ ghế trên server. Chưa đăng nhập thì mời đăng nhập trước. */
    private void holdSeat() {
        if (!UserSession.get().isLoggedIn()) {
            VeGoDialog.with(this)
                    .type(VeGoDialog.Type.INFO)
                    .title(R.string.booking_login_title)
                    .message(R.string.booking_login_message)
                    .primary(R.string.login_button, () -> open(LoginActivity.class))
                    .secondary(R.string.action_cancel, null)
                    .show();
            return;
        }
        // Lượt giữ cũ (khác ghế / khác hành khách) sẽ được server tự hủy khi giữ lượt mới
        draft.releaseHold();
        holding = false;
        setBusy(R.id.btn_pay, true);
        ApiModels.CreateBookingRequest body = new ApiModels.CreateBookingRequest(
                draft.trip.id, com.example.vego.common.UserSession.toIsoDate(draft.date), draft.seat,
                draft.passengerName, draft.passengerPhone, draft.pickupLabel(), draft.dropoffLabel());
        ApiClient.api().createBooking(body).enqueue(new ApiCallback<ApiModels.Booking>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.Booking booking) {
                if (booking == null) return;
                draft.applyServerBooking(booking);
                holding = true;
                holdTimer.start();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                if (error.is("SEAT_TAKEN")) {
                    VeGoDialog.with(BookingConfirmActivity.this)
                            .type(VeGoDialog.Type.WARNING)
                            .title(R.string.seat_taken_title)
                            .message(error.message)
                            .cancelable(false)
                            .primary(R.string.seat_taken_choose, () -> BookingNav.backToSeatSelect(BookingConfirmActivity.this))
                            .show();
                } else if (error.is("TRIP_DEPARTED") || error.is("TRIP_NOT_FOUND")) {
                    VeGoDialog.with(BookingConfirmActivity.this)
                            .type(VeGoDialog.Type.WARNING)
                            .title(R.string.trip_unavailable_title)
                            .message(error.message)
                            .cancelable(false)
                            .primary(R.string.trip_unavailable_choose, () -> BookingNav.backToTripList(BookingConfirmActivity.this))
                            .show();
                } else {
                    showError(error);
                }
            }

            @Override
            protected void onDone() {
                setBusy(R.id.btn_pay, false);
                bindTotal();
            }
        });
    }
}
