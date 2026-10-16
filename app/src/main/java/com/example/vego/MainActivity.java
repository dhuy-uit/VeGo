package com.example.vego;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.booking.SearchTripActivity;
import com.example.vego.booking.SeatSelectActivity;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.TripRepository;
import com.example.vego.tickets.NotificationsActivity;

import java.util.Calendar;
import java.util.Locale;

/** Figma: "Khách hàng · Trang chủ". */
public class MainActivity extends BaseActivity {

    private static final String[] VI_DAY_NAMES = {
            "Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy"
    };

    private EditText etFrom;
    private EditText etTo;
    private TextView tvDate;
    /** Ngày khởi hành đang chọn, mặc định 18/10/2026 như thiết kế. */
    private final Calendar departureDate = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_main);

        etFrom = findViewById(R.id.et_from);
        etTo = findViewById(R.id.et_to);
        tvDate = findViewById(R.id.tv_date);

        departureDate.set(2026, Calendar.OCTOBER, 18);
        showDate();

        setupBottomNav(NavTab.HOME);

        onClick(R.id.btn_notifications, v -> open(NotificationsActivity.class));
        onClick(R.id.field_date, v -> pickDate());
        onClick(R.id.btn_search, v -> searchTrips());
        onClick(R.id.card_route_dalat, v -> selectRoute("Đà Lạt"));
        onClick(R.id.card_route_nhatrang, v -> selectRoute("Nha Trang"));
        // Theo prototype: bấm thẻ chuyến -> Chọn ghế
        onClick(R.id.card_trip, v -> {
            BookingDraft draft = BookingDraft.get();
            draft.selectTrip(TripRepository.suggested());
            draft.date.setTimeInMillis(departureDate.getTimeInMillis());
            open(SeatSelectActivity.class);
        });
    }

    @Override
    protected Transition transition() {
        return Transition.FADE;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Lời chào theo tên người dùng (cập nhật khi đổi tên ở Chỉnh sửa hồ sơ)
        TextView greeting = findViewById(R.id.tv_greeting);
        greeting.setText(getString(R.string.home_greeting, UserSession.get().getGivenName()));
    }

    private void pickDate() {
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    departureDate.set(year, month, day);
                    showDate();
                },
                departureDate.get(Calendar.YEAR),
                departureDate.get(Calendar.MONTH),
                departureDate.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void showDate() {
        String dayName = VI_DAY_NAMES[departureDate.get(Calendar.DAY_OF_WEEK) - 1];
        tvDate.setText(String.format(Locale.ROOT, "%s, %02d/%02d/%d",
                dayName,
                departureDate.get(Calendar.DAY_OF_MONTH),
                departureDate.get(Calendar.MONTH) + 1,
                departureDate.get(Calendar.YEAR)));
    }

    /** Bấm thẻ tuyến phổ biến: điền sẵn điểm đi/đến. */
    private void selectRoute(String destination) {
        etFrom.setText("TP. Hồ Chí Minh");
        etTo.setText(destination);
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
