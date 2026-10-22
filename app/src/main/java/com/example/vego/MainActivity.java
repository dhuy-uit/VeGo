package com.example.vego;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import com.example.vego.booking.SearchTripActivity;
import com.example.vego.booking.SeatSelectActivity;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.TripRepository;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;
import com.example.vego.tickets.NotificationsActivity;

import java.util.Calendar;
import java.util.List;

/**
 * Figma: "Khách hàng · Trang chủ".
 * Lời chào, khuyến mãi, tuyến phổ biến và chuyến gợi ý lấy từ GET /api/v1/home;
 * khi chưa tải được thì giữ dữ liệu mẫu trong layout.
 */
public class MainActivity extends BaseActivity {

    private EditText etFrom;
    private EditText etTo;
    private TextView tvDate;
    /** Ngày khởi hành đang chọn (mặc định ngày mai, sau đó theo gợi ý của server). */
    private final Calendar departureDate = Calendar.getInstance();
    /** Người dùng đã tự sửa ô tìm kiếm -> không ghi đè bằng giá trị mặc định từ server. */
    private boolean searchTouched;
    private boolean defaultsApplied;

    /** Chuyến gợi ý hiện trên thẻ (mặc định là chuyến mẫu). */
    private Trip suggestedTrip = TripRepository.suggested();
    @Nullable private Calendar suggestedDate;
    @Nullable private List<ApiModels.PopularRoute> routes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_main);

        etFrom = findViewById(R.id.et_from);
        etTo = findViewById(R.id.et_to);
        tvDate = findViewById(R.id.tv_date);

        departureDate.add(Calendar.DAY_OF_MONTH, 1);
        showDate();

        setupBottomNav(NavTab.HOME);

        onClick(R.id.btn_notifications, v -> open(NotificationsActivity.class));
        onClick(R.id.field_date, v -> pickDate());
        onClick(R.id.btn_search, v -> searchTrips());
        onClick(R.id.card_route_dalat, v -> selectRoute(0));
        onClick(R.id.card_route_nhatrang, v -> selectRoute(1));
        // Theo prototype: bấm thẻ chuyến -> Chọn ghế
        onClick(R.id.card_trip, v -> {
            BookingDraft draft = BookingDraft.get();
            draft.selectTrip(suggestedTrip);
            draft.date.setTimeInMillis((suggestedDate != null ? suggestedDate : departureDate).getTimeInMillis());
            open(SeatSelectActivity.class);
        });
        etFrom.setOnFocusChangeListener((v, focused) -> { if (focused) searchTouched = true; });
        etTo.setOnFocusChangeListener((v, focused) -> { if (focused) searchTouched = true; });
    }

    @Override
    protected Transition transition() {
        return Transition.FADE;
    }

    @Override
    protected void onResume() {
        super.onResume();
        showGreeting(UserSession.get().isLoggedIn() ? UserSession.get().getGivenName() : null);
        loadHome();
    }

    private void loadHome() {
        ApiClient.api().home().enqueue(new ApiCallback<ApiModels.Home>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.Home home) {
                if (home != null) bindHome(home);
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                VeGoBanner.show(MainActivity.this, getString(R.string.home_load_failed), VeGoBanner.Type.WARNING);
            }
        });
    }

    private void bindHome(ApiModels.Home home) {
        showGreeting(home.loggedIn ? home.greetingName : null);

        // Ô tìm kiếm: điền sẵn theo gợi ý (chỉ lần đầu và khi người dùng chưa tự sửa)
        if (!defaultsApplied && !searchTouched && home.searchDefaults != null) {
            etFrom.setText(home.searchDefaults.origin);
            etTo.setText(home.searchDefaults.destination);
            Calendar date = UserSession.parseIsoDate(home.searchDefaults.date);
            if (date != null) {
                departureDate.setTimeInMillis(date.getTimeInMillis());
                showDate();
            }
            defaultsApplied = true;
        }

        // Khuyến mãi: hiện chương trình ưu tiên nhất
        View promoCard = findViewById(R.id.card_promo);
        if (home.promotions == null || home.promotions.isEmpty()) {
            promoCard.setVisibility(View.GONE);
        } else {
            ApiModels.Promotion p = home.promotions.get(0);
            promoCard.setVisibility(View.VISIBLE);
            setText(R.id.tv_promo_badge, p.badge);
            setText(R.id.tv_promo_title, p.title);
            setText(R.id.tv_promo_code, p.description);
            setText(R.id.tv_promo_note, p.note == null ? "" : p.note);
            int image = p.imageKey == null ? 0
                    : getResources().getIdentifier(p.imageKey, "drawable", getPackageName());
            ((ImageView) findViewById(R.id.iv_promo_image))
                    .setImageResource(image != 0 ? image : R.drawable.img_dalat_suong);
        }

        // Tuyến phổ biến: 2 thẻ đầu
        routes = home.popularRoutes;
        bindRoute(0, R.id.card_route_dalat, R.id.tv_route1_name, R.id.tv_route1_from, R.id.tv_route1_price);
        bindRoute(1, R.id.card_route_nhatrang, R.id.tv_route2_name, R.id.tv_route2_from, R.id.tv_route2_price);

        // Chuyến gợi ý
        View tripCard = findViewById(R.id.card_trip);
        if (home.suggestedTrip == null || home.suggestedTrip.trip == null) {
            tripCard.setVisibility(View.GONE);
        } else {
            tripCard.setVisibility(View.VISIBLE);
            bindTrip(home.suggestedTrip.trip);
        }
    }

    private void bindRoute(int index, @IdRes int card, @IdRes int name, @IdRes int from, @IdRes int price) {
        if (routes == null || routes.size() <= index) {
            findViewById(card).setVisibility(routes == null ? View.VISIBLE : View.INVISIBLE);
            return;
        }
        ApiModels.PopularRoute r = routes.get(index);
        findViewById(card).setVisibility(View.VISIBLE);
        setText(name, r.destination);
        setText(from, getString(R.string.home_route_from, r.origin));
        setText(price, getString(R.string.home_route_price, Format.money(r.fromPrice)));
    }

    private void bindTrip(ApiModels.TripDto t) {
        suggestedTrip = toTrip(t);
        suggestedDate = UserSession.parseIsoDate(t.departureDate);

        setText(R.id.tv_trip_operator, t.operator);
        setText(R.id.tv_trip_bus, t.busName);
        setText(R.id.tv_trip_rating, suggestedTrip.ratingText());
        setText(R.id.tv_trip_depart, t.departTime);
        setText(R.id.tv_trip_origin, t.origin);
        setText(R.id.tv_trip_duration, suggestedTrip.durationText());
        setText(R.id.tv_trip_arrive, t.arriveTime);
        setText(R.id.tv_trip_destination, t.arriveDayOffset > 0
                ? getString(R.string.home_trip_arrive, t.destination, t.arriveDayOffset)
                : t.destination);
        if (suggestedDate != null) {
            setText(R.id.tv_trip_date, getString(R.string.home_trip_date, Format.date(suggestedDate)));
        }
        setText(R.id.tv_trip_price, Format.money(t.price));
        setText(R.id.tv_trip_seats, getString(R.string.home_trip_seats, t.seatsLeft));
    }

    /** Đổi dữ liệu chuyến từ API sang lớp Trip mà luồng đặt vé đang dùng. */
    private static Trip toTrip(ApiModels.TripDto t) {
        Trip.BusType type;
        try {
            type = Trip.BusType.valueOf(t.busType);
        } catch (RuntimeException e) {
            type = Trip.BusType.SLEEPER;
        }
        return new Trip(t.id, t.operator, t.busName, type, t.rating, t.departMinutes, t.durationMinutes,
                t.price, t.seatsLeft, t.origin, t.destination,
                t.pickupStation, t.pickupAddress, t.dropoffStation, t.dropoffAddress);
    }

    private void showGreeting(@Nullable String name) {
        TextView greeting = findViewById(R.id.tv_greeting);
        greeting.setText(getString(R.string.home_greeting,
                name == null || name.isEmpty() ? getString(R.string.guest_name) : name));
    }

    private void setText(@IdRes int id, CharSequence text) {
        ((TextView) findViewById(id)).setText(text);
    }

    private void pickDate() {
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    departureDate.set(year, month, day);
                    searchTouched = true;
                    showDate();
                },
                departureDate.get(Calendar.YEAR),
                departureDate.get(Calendar.MONTH),
                departureDate.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void showDate() {
        tvDate.setText(Format.longDate(departureDate));
    }

    /** Bấm thẻ tuyến phổ biến: điền sẵn điểm đi/đến. */
    private void selectRoute(int index) {
        searchTouched = true;
        if (routes != null && routes.size() > index) {
            etFrom.setText(routes.get(index).origin);
            etTo.setText(routes.get(index).destination);
        } else {
            etFrom.setText("TP. Hồ Chí Minh");
            etTo.setText(index == 0 ? "Đà Lạt" : "Nha Trang");
        }
    }

    private void searchTrips() {
        if (!requireText(etFrom) || !requireText(etTo)) return;
        if (textOf(etFrom).equalsIgnoreCase(textOf(etTo))) {
            etTo.setError(getString(R.string.error_same_place));
            etTo.requestFocus();
            return;
        }
        // Theo prototype: Tìm chuyến xe -> "Đặt vé · Tìm chuyến"
        BookingDraft draft = BookingDraft.get();
        draft.origin = textOf(etFrom);
        draft.destination = textOf(etTo);
        draft.date.setTimeInMillis(departureDate.getTimeInMillis());
        open(SearchTripActivity.class);
    }
}
