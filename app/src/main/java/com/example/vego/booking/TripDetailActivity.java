package com.example.vego.booking;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.TripCardBinder;
import com.example.vego.common.UserSession;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

/** Figma: "Đặt vé · Chi tiết chuyến". Số ghế trống lấy lại từ GET /api/v1/trips/{id}?date=. */
public class TripDetailActivity extends BaseActivity {

    private final BookingDraft draft = BookingDraft.get();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_trip_detail);
        if (draft.trip == null) {
            goHome();
            return;
        }
        bind(draft.trip);

        findViewById(R.id.btn_choose_seat).setOnClickListener(v -> open(SeatSelectActivity.class));
        setupBackButton(this::finish);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (draft.trip == null) return;
        ApiClient.api().trip(draft.trip.id, UserSession.toIsoDate(draft.date))
                .enqueue(new ApiCallback<ApiModels.TripDto>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.TripDto t) {
                        if (t == null || draft.trip == null || !t.id.equals(draft.trip.id)) return;
                        draft.trip = Trip.from(t);
                        bind(draft.trip);
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        // Giữ thông tin đã có từ danh sách chuyến
                    }
                });
    }

    private void bind(Trip trip) {
        ((TextView) findViewById(R.id.tv_route_subtitle)).setText(trip.origin + " → " + trip.destination);
        TripCardBinder.bindWithSeatsLeft(findViewById(R.id.card_trip), trip, draft.date);

        InfoRowView pickup = findViewById(R.id.row_pickup);
        pickup.setLabel(trip.departTime() + " · " + Format.shortDate(draft.date));
        pickup.setValue(trip.pickupStation);
        ((TextView) findViewById(R.id.tv_pickup_address)).setText(trip.pickupAddress);

        InfoRowView dropoff = findViewById(R.id.row_dropoff);
        dropoff.setLabel(trip.arriveTime() + " · "
                + Format.shortDate(Format.plusDays(draft.date, trip.arriveDayOffset())));
        dropoff.setValue(trip.dropoffStation);
        ((TextView) findViewById(R.id.tv_dropoff_address)).setText(trip.dropoffAddress);

        TextView btnChoose = findViewById(R.id.btn_choose_seat);
        btnChoose.setText(getString(R.string.detail_choose_seat, Format.money(trip.price)));
    }
}
