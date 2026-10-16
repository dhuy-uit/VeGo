package com.example.vego.booking;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.TripRepository;

import java.util.Calendar;

/** Figma: "Đặt vé · Tìm chuyến". */
public class SearchTripActivity extends BaseActivity {

    private EditText etFrom;
    private EditText etTo;
    private TextView tvDate;
    private TextView btnSearch;
    private LinearLayout listPickup;
    private final BookingDraft draft = BookingDraft.get();

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
        buildPickupOptions();

        // Theo prototype: quay lại -> Trang chủ (màn trước đó)
        setupBackButton(this::finish);
        onClick(R.id.field_date, v -> pickDate());
        btnSearch.setOnClickListener(v -> search());
    }

    private void buildPickupOptions() {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < TripRepository.PICKUP_POINTS.length; i++) {
            View row = inflater.inflate(R.layout.item_option_row, listPickup, false);
            ((TextView) row.findViewById(R.id.tv_option_title)).setText(TripRepository.PICKUP_POINTS[i][0]);
            ((TextView) row.findViewById(R.id.tv_option_subtitle)).setText(TripRepository.PICKUP_POINTS[i][1]);
            final int index = i;
            row.setOnClickListener(v -> {
                draft.pickupPointIndex = index;
                refreshPickupOptions();
            });
            listPickup.addView(row);
        }
        refreshPickupOptions();
    }

    private void refreshPickupOptions() {
        for (int i = 0; i < listPickup.getChildCount(); i++) {
            ImageView radio = listPickup.getChildAt(i).findViewById(R.id.iv_radio);
            radio.setImageResource(i == draft.pickupPointIndex ? R.drawable.ic_circle_check : R.drawable.ic_circle);
        }
    }

    private void pickDate() {
        Calendar d = draft.date;
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    d.set(year, month, day);
                    showDate();
                },
                d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void showDate() {
        tvDate.setText(Format.longDate(draft.date));
        btnSearch.setText(getString(R.string.search_button, Format.shortDate(draft.date)));
    }

    private void search() {
        if (!requireText(etFrom) || !requireText(etTo)) return;
        if (textOf(etFrom).equalsIgnoreCase(textOf(etTo))) {
            etTo.setError(getString(R.string.error_same_place));
            etTo.requestFocus();
            return;
        }
        draft.origin = textOf(etFrom);
        draft.destination = textOf(etTo);
        open(TripListActivity.class);
    }
}
