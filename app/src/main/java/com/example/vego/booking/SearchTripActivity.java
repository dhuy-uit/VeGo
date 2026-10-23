package com.example.vego.booking;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.TripRepository;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Figma: "Đặt vé · Tìm chuyến".
 * Điểm đón lấy từ GET /api/v1/stations theo điểm đi; bấm Tìm thì gọi GET /api/v1/trips/search.
 */
public class SearchTripActivity extends BaseActivity {

    private EditText etFrom;
    private EditText etTo;
    private TextView tvDate;
    private TextView btnSearch;
    private LinearLayout listPickup;
    private final BookingDraft draft = BookingDraft.get();
    private final List<ApiModels.Station> stations = new ArrayList<>();
    /** Thành phố đã tải danh sách bến (tránh gọi lại khi không đổi). */
    @Nullable private String stationsCity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_search_trip);

        etFrom = findViewById(R.id.et_from);
        etTo = findViewById(R.id.et_to);
        tvDate = findViewById(R.id.tv_date);
        btnSearch = findViewById(R.id.btn_search);
        listPickup = findViewById(R.id.list_pickup);

        etFrom.setText(draft.origin);
        etTo.setText(draft.destination);
        showDate();
        loadStations();
        // Đổi điểm đi -> tải lại danh sách bến đón
        etFrom.setOnFocusChangeListener((v, focused) -> { if (!focused) loadStations(); });

        // Theo prototype: quay lại -> Trang chủ (màn trước đó)
        setupBackButton(this::finish);
        onClick(R.id.field_date, v -> pickDate());
        btnSearch.setOnClickListener(v -> search());
    }

    // ---------- Điểm đón ----------

    private void loadStations() {
        String city = textOf(etFrom);
        if (city.isEmpty() || city.equalsIgnoreCase(stationsCity)) return;
        stationsCity = city;
        ApiClient.api().stations(city).enqueue(new ApiCallback<List<ApiModels.Station>>(this) {
            @Override
            protected void onSuccess(@Nullable List<ApiModels.Station> list) {
                if (!city.equalsIgnoreCase(stationsCity)) return;
                stations.clear();
                if (list != null) stations.addAll(list);
                buildPickupOptions();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                stationsCity = null;
                stations.clear();
                buildPickupOptions();
            }
        });
    }

    private void buildPickupOptions() {
        listPickup.removeAllViews();
        // Bến đã chọn không còn trong danh sách (đổi điểm đi) -> bỏ chọn
        boolean found = false;
        for (ApiModels.Station s : stations) if (s.name.equals(draft.preferredPickup)) found = true;
        if (!found) draft.preferredPickup = null;

        LayoutInflater inflater = LayoutInflater.from(this);
        for (ApiModels.Station s : stations) {
            View row = inflater.inflate(R.layout.item_option_row, listPickup, false);
            ((TextView) row.findViewById(R.id.tv_option_title)).setText(s.name);
            ((TextView) row.findViewById(R.id.tv_option_subtitle)).setText(s.address);
            row.setTag(s.name);
            row.setOnClickListener(v -> {
                // Bấm lại bến đang chọn thì bỏ chọn (dùng bến chính của chuyến)
                draft.preferredPickup = s.name.equals(draft.preferredPickup) ? null : s.name;
                refreshPickupOptions();
            });
            listPickup.addView(row);
        }
        int visibility = stations.isEmpty() ? View.GONE : View.VISIBLE;
        listPickup.setVisibility(visibility);
        refreshPickupOptions();
    }

    private void refreshPickupOptions() {
        for (int i = 0; i < listPickup.getChildCount(); i++) {
            View row = listPickup.getChildAt(i);
            ImageView radio = row.findViewById(R.id.iv_radio);
            boolean on = row.getTag() != null && row.getTag().equals(draft.preferredPickup);
            radio.setImageResource(on ? R.drawable.ic_circle_check : R.drawable.ic_circle);
        }
    }

    // ---------- Ngày đi ----------

    private void pickDate() {
        Calendar d = draft.date;
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    d.set(year, month, day);
                    showDate();
                },
                d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        // Server chỉ bán vé trước tối đa 60 ngày
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis() + 60L * 24 * 3600 * 1000);
        dialog.show();
    }

    private void showDate() {
        tvDate.setText(Format.longDate(draft.date));
        btnSearch.setText(getString(R.string.search_button, Format.shortDate(draft.date)));
    }

    // ---------- Tìm chuyến ----------

    private void search() {
        if (!requireText(etFrom) || !requireText(etTo)) return;
        if (textOf(etFrom).equalsIgnoreCase(textOf(etTo))) {
            etTo.setError(getString(R.string.error_same_place));
            etTo.requestFocus();
            return;
        }
        String origin = textOf(etFrom);
        String destination = textOf(etTo);
        setBusy(R.id.btn_search, true);
        ApiClient.api().searchTrips(origin, destination, UserSession.toIsoDate(draft.date))
                .enqueue(new ApiCallback<ApiModels.TripSearch>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.TripSearch result) {
                        if (result == null) return;
                        if (!result.routeFound) {
                            VeGoDialog.with(SearchTripActivity.this)
                                    .type(VeGoDialog.Type.INFO)
                                    .title(R.string.search_no_route_title)
                                    .message(getString(R.string.search_no_route_message, origin, destination))
                                    .primary(R.string.action_ok, null)
                                    .show();
                            return;
                        }
                        // Tuyến khác lần trước -> bỏ bộ lọc cũ
                        String previous = draft.origin + "|" + draft.destination;
                        if (!previous.equals(result.origin + "|" + result.destination)) draft.filter = null;
                        draft.origin = result.origin;
                        draft.destination = result.destination;
                        etFrom.setText(result.origin);
                        etTo.setText(result.destination);
                        TripRepository.setResults(result);
                        open(TripListActivity.class);
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        if (!showFieldErrors(error, "origin", etFrom, "destination", etTo)) showError(error);
                    }

                    @Override
                    protected void onDone() {
                        setBusy(R.id.btn_search, false);
                        showDate();
                    }
                });
    }
}
