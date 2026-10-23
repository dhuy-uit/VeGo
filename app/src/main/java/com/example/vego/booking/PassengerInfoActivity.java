package com.example.vego.booking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.util.List;

/** Figma: "Đặt vé · Thông tin hành khách" (Bước 2/3). */
public class PassengerInfoActivity extends BaseActivity {

    private final BookingDraft draft = BookingDraft.get();
    private EditText etName;
    private EditText etPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_passenger_info);

        if (draft.trip == null) {
            goHome();
            return;
        }
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


        onClick(R.id.tab_pickup_station, v -> setPickupShuttle(false));
        onClick(R.id.tab_pickup_shuttle, v -> setPickupShuttle(true));
        onClick(R.id.tab_dropoff_station, v -> setDropoffShuttle(false));
        onClick(R.id.tab_dropoff_shuttle, v -> setDropoffShuttle(true));
        // Số điện thoại trong lời nhắn trung chuyển cập nhật theo ô nhập
        etPhone.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { renderShuttleNotes(); }
        });
        renderStops();
        loadStops();

        setupBackButton(this::finish);
        onClick(R.id.btn_continue, v -> next());
    }

    // ---------- Điểm đón / điểm trả ----------

    /** Điểm đón / trả của chuyến: GET /api/v1/trips/{id}/stops (bến chính của chuyến đứng đầu). */
    private void loadStops() {
        Trip trip = draft.trip;
        if (trip == null || (draft.stops != null && trip.id.equals(draft.stops.tripId))) return;
        ApiClient.api().tripStops(trip.id).enqueue(new ApiCallback<ApiModels.TripStops>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.TripStops stops) {
                if (stops == null || draft.trip == null || !stops.tripId.equals(draft.trip.id)) return;
                draft.stops = stops;
                // Lần đầu: chọn sẵn bến đón đã chọn ở màn Tìm chuyến (nếu chuyến có đón ở đó)
                if (draft.pickupStation == null && draft.preferredPickup != null) {
                    for (ApiModels.Station st : stops.pickups) {
                        if (st.name.equals(draft.preferredPickup)) draft.pickupStation = st.name;
                    }
                }
                renderStops();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                // Không tải được danh sách: vẫn dùng bến chính của chuyến
            }
        });
    }

    /** Danh sách bến: lấy từ server, chưa tải được thì chỉ có bến chính của chuyến. */
    private String[][] points(boolean pickup) {
        Trip trip = draft.trip;
        List<ApiModels.Station> list = draft.stops == null ? null
                : (pickup ? draft.stops.pickups : draft.stops.dropoffs);
        if (list == null || list.isEmpty()) {
            return new String[][] {{ pickup ? trip.pickupStation : trip.dropoffStation,
                    pickup ? trip.pickupAddress : trip.dropoffAddress }};
        }
        String[][] out = new String[list.size()][];
        for (int i = 0; i < list.size(); i++) out[i] = new String[] { list.get(i).name, list.get(i).address };
        return out;
    }

    /** Vị trí bến đang chọn trong danh sách (mặc định bến đầu = bến chính của chuyến). */
    private static int indexOf(String[][] points, @Nullable String name) {
        if (name != null) for (int i = 0; i < points.length; i++) if (points[i][0].equals(name)) return i;
        return 0;
    }

    private void setPickupShuttle(boolean shuttle) {
        if (draft.pickupShuttle == shuttle) return;
        draft.pickupShuttle = shuttle;
        renderStops();
        if (shuttle) VeGoBanner.show(this, R.string.shuttle_selected_pickup, VeGoBanner.Type.INFO);
    }

    private void setDropoffShuttle(boolean shuttle) {
        if (draft.dropoffShuttle == shuttle) return;
        draft.dropoffShuttle = shuttle;
        renderStops();
        if (shuttle) VeGoBanner.show(this, R.string.shuttle_selected_dropoff, VeGoBanner.Type.INFO);
    }

    private void renderStops() {
        Trip trip = draft.trip;
        String[][] pickups = points(true);
        String[][] dropoffs = points(false);
        renderSection(true, draft.pickupShuttle, pickups, indexOf(pickups, draft.pickupStation));
        renderSection(false, draft.dropoffShuttle, dropoffs, indexOf(dropoffs, draft.dropoffStation));
        renderShuttleNotes();

        // Lời nhắc thời gian dưới mỗi phần
        int meetMinutes = (trip.departMinutes - 30 + 24 * 60) % (24 * 60);
        ((TextView) findViewById(R.id.tv_pickup_hint)).setText(draft.pickupShuttle
                ? getString(R.string.shuttle_pickup_hint, trip.departTime(), Format.date(draft.date))
                : getString(R.string.passenger_pickup_hint, Format.time(meetMinutes), Format.date(draft.date)));
        String arriveDate = Format.date(Format.plusDays(draft.date, trip.arriveDayOffset()));
        ((TextView) findViewById(R.id.tv_dropoff_hint)).setText(draft.dropoffShuttle
                ? getString(R.string.shuttle_dropoff_hint, trip.arriveTime(), arriveDate)
                : getString(R.string.passenger_dropoff_hint, trip.arriveTime(), arriveDate));
    }

    /** Một phần chọn: thanh "Tại bến xe / Xe trung chuyển" + danh sách bến hoặc thẻ trung chuyển. */
    private void renderSection(boolean pickup, boolean shuttle, String[][] points, int selected) {
        findViewById(pickup ? R.id.tab_pickup_station : R.id.tab_dropoff_station).setSelected(!shuttle);
        findViewById(pickup ? R.id.tab_pickup_shuttle : R.id.tab_dropoff_shuttle).setSelected(shuttle);

        LinearLayout list = findViewById(pickup ? R.id.list_pickup_stations : R.id.list_dropoff_stations);
        View shuttleCard = findViewById(pickup ? R.id.card_pickup_shuttle : R.id.card_dropoff_shuttle);
        list.setVisibility(shuttle ? View.GONE : View.VISIBLE);
        shuttleCard.setVisibility(shuttle ? View.VISIBLE : View.GONE);
        shuttleCard.setSelected(true);
        if (shuttle) return;

        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int primary = ContextCompat.getColor(this, R.color.vego_primary);
        for (int i = 0; i < points.length; i++) {
            final int index = i;
            boolean isSelected = i == selected;
            View card = inflater.inflate(R.layout.item_stop_option, list, false);
            ((TextView) card.findViewById(R.id.tv_stop_name)).setText(points[i][0]);
            ((TextView) card.findViewById(R.id.tv_stop_address)).setText(points[i][1]);
            ImageView radio = card.findViewById(R.id.iv_stop_radio);
            radio.setImageResource(isSelected ? R.drawable.ic_circle_check : R.drawable.ic_circle);
            radio.setColorFilter(primary);
            card.setSelected(isSelected);
            // Thẻ đang chọn nền xanh nhạt -> ô biểu tượng chuyển sang trắng cho nổi
            card.findViewById(R.id.iv_stop_icon).setBackgroundResource(
                    isSelected ? R.drawable.bg_tile_white : R.drawable.bg_badge_mint);
            card.setContentDescription(isSelected ? getString(R.string.cd_stop_selected, points[i][0]) : points[i][0]);
            card.setOnClickListener(v -> {
                if (pickup) draft.pickupStation = points[index][0]; else draft.dropoffStation = points[index][0];
                renderStops();
            });
            list.addView(card);
        }
    }

    private void renderShuttleNotes() {
        String phone = textOf(etPhone).isEmpty() ? UserSession.get().getPhone() : textOf(etPhone);
        View pickup = findViewById(R.id.card_pickup_shuttle);
        ((TextView) pickup.findViewById(R.id.tv_shuttle_title)).setText(R.string.shuttle_pickup_title);
        ((TextView) pickup.findViewById(R.id.tv_shuttle_body)).setText(getString(R.string.shuttle_pickup_body, phone));
        View dropoff = findViewById(R.id.card_dropoff_shuttle);
        ((TextView) dropoff.findViewById(R.id.tv_shuttle_title)).setText(R.string.shuttle_dropoff_title);
        ((TextView) dropoff.findViewById(R.id.tv_shuttle_body)).setText(getString(R.string.shuttle_dropoff_body, phone));
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
