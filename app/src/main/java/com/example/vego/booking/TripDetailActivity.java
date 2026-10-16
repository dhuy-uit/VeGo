package com.example.vego.booking;

import android.os.Bundle;
import android.widget.TextView;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.TripCardBinder;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.TripRepository;

/** Figma: "Đặt vé · Chi tiết chuyến". */
public class TripDetailActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_trip_detail);

        BookingDraft draft = BookingDraft.get();
        if (draft.trip == null) draft.selectTrip(TripRepository.suggested());
        Trip trip = draft.trip;

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
        btnChoose.setOnClickListener(v -> open(SeatSelectActivity.class));

        setupBackButton(this::finish);
    }
}
