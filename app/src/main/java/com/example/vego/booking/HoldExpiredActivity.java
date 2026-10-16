package com.example.vego.booking;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.TripRepository;

/** Figma: "Đặt vé · Hết thời gian giữ chỗ". */
public class HoldExpiredActivity extends BaseActivity {

    @Override
    protected Transition transition() {
        return Transition.RESULT;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_hold_expired);

        BookingDraft draft = BookingDraft.get();
        if (draft.trip == null) draft.selectTrip(TripRepository.suggested());
        Trip trip = draft.trip;
        String seat = draft.seat != null ? draft.seat : "—";

        ((TextView) findViewById(R.id.tv_expired_body)).setText(getString(R.string.expired_body, seat));
        ((InfoRowView) findViewById(R.id.row_operator)).setValue(trip.operator);
        ((InfoRowView) findViewById(R.id.row_route)).setValue(trip.origin + " → " + trip.destination);
        ((InfoRowView) findViewById(R.id.row_depart)).setValue(trip.departTime() + " · " + Format.date(draft.date));
        ((InfoRowView) findViewById(R.id.row_previous_seat)).setValue(getString(R.string.expired_seat_value, seat));
        ((TextView) findViewById(R.id.tv_expired_info)).setText(
                getString(R.string.expired_info_body, Format.money(trip.price)));

        ResultAnimations.popIn(findViewById(R.id.iv_result));
        // Ghế đã giải phóng
        draft.seat = null;

        // Theo prototype: quay lại / "Quay lại chọn chuyến" -> Danh sách chuyến
        setupBackButton(() -> BookingNav.backToTripList(this));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                BookingNav.backToTripList(HoldExpiredActivity.this);
            }
        });
        onClick(R.id.btn_back_to_list, v -> BookingNav.backToTripList(this));
        onClick(R.id.btn_new_search, v -> goHome());
    }
}
