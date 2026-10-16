package com.example.vego.booking;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.TripRepository;

/** Figma: "Đặt vé · Thông tin hành khách" (Bước 2/3). */
public class PassengerInfoActivity extends BaseActivity {

    private final BookingDraft draft = BookingDraft.get();
    private EditText etName;
    private EditText etPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_passenger_info);

        if (draft.trip == null) draft.selectTrip(TripRepository.suggested());
        Trip trip = draft.trip;

        ((TextView) findViewById(R.id.tv_route_badge)).setText(
                trip.origin + " → " + trip.destination + " · " + Format.shortDate(draft.date));
        ((InfoRowView) findViewById(R.id.row_operator)).setValue(trip.operator);
        ((InfoRowView) findViewById(R.id.row_departure)).setValue(
                trip.departTime() + " · " + Format.date(draft.date));
        ((InfoRowView) findViewById(R.id.row_seat)).setValue(
                draft.seat + " · " + Format.money(trip.price));

        etName = findViewById(R.id.et_passenger_name);
        etPhone = findViewById(R.id.et_passenger_phone);
        // Điền sẵn từ hồ sơ người dùng nếu chưa nhập
        UserSession user = UserSession.get();
        etName.setText(draft.passengerName.isEmpty() ? user.getFullName() : draft.passengerName);
        etPhone.setText(draft.passengerPhone.isEmpty() ? user.getPhone() : draft.passengerPhone);

        if (!draft.isHolding()) draft.pickupIndex = draft.pickupPointIndex;
        showStops();

        onClick(R.id.field_pickup, v -> choose(R.string.label_pickup, TripRepository.PICKUP_POINTS,
                draft.pickupIndex, i -> { draft.pickupIndex = i; showStops(); }));
        onClick(R.id.field_dropoff, v -> choose(R.string.label_dropoff, TripRepository.DROPOFF_POINTS,
                draft.dropoffIndex, i -> { draft.dropoffIndex = i; showStops(); }));

        setupBackButton(this::finish);
        onClick(R.id.btn_continue, v -> next());
    }

    private interface OnChosen { void apply(int index); }

    /** Hộp thoại chọn một điểm đón / điểm trả. */
    private void choose(int titleRes, String[][] points, int current, OnChosen onChosen) {
        String[] labels = new String[points.length];
        for (int i = 0; i < points.length; i++) labels[i] = points[i][0] + "\n" + points[i][1];
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, current, (dialog, which) -> {
                    onChosen.apply(which);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showStops() {
        Trip trip = draft.trip;
        ((TextView) findViewById(R.id.tv_pickup)).setText(TripRepository.PICKUP_POINTS[draft.pickupIndex][0]);
        ((TextView) findViewById(R.id.tv_dropoff)).setText(TripRepository.DROPOFF_POINTS[draft.dropoffIndex][0]);
        // Có mặt trước giờ chạy 30 phút
        int meetMinutes = (trip.departMinutes - 30 + 24 * 60) % (24 * 60);
        ((TextView) findViewById(R.id.tv_pickup_hint)).setText(getString(
                R.string.passenger_pickup_hint, Format.time(meetMinutes), Format.date(draft.date)));
        ((TextView) findViewById(R.id.tv_dropoff_hint)).setText(getString(
                R.string.passenger_dropoff_hint, trip.arriveTime(),
                Format.date(Format.plusDays(draft.date, trip.arriveDayOffset()))));
    }

    private void next() {
        if (!requireText(etName) || !requireText(etPhone)) return;
        if (textOf(etPhone).replaceAll("\\D", "").length() != 10) {
            etPhone.setError(getString(R.string.error_invalid_phone));
            etPhone.requestFocus();
            return;
        }
        draft.passengerName = textOf(etName);
        draft.passengerPhone = textOf(etPhone);
        open(BookingConfirmActivity.class);
    }
}
