package com.example.vego.booking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.TripFilter;
import com.example.vego.data.TripRepository;

import java.util.List;

/** Figma: "Đặt vé · Bộ lọc chuyến". Mở dạng bảng trượt lên từ dưới. */
public class TripFilterActivity extends BaseActivity {

    private static final int[] TIME_CHIPS = {
            R.id.chip_time_0, R.id.chip_time_1, R.id.chip_time_2, R.id.chip_time_3
    };
    private static final String[] TIME_LABELS = {
            "00:00–06:00", "06:00–12:00", "12:00–18:00", "18:00–24:00"
    };

    private final BookingDraft draft = BookingDraft.get();
    /** Bản nháp bộ lọc; chỉ ghi vào đơn khi bấm Áp dụng. */
    private TripFilter working;

    private LinearLayout listOperators;
    private InfoRowView rowMaxPrice;
    private TextView btnApply;

    @Override
    protected Transition transition() {
        return Transition.SHEET;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_trip_filter);

        working = draft.filter != null ? draft.filter.copy() : TripFilter.suggested();
        listOperators = findViewById(R.id.list_operators);
        rowMaxPrice = findViewById(R.id.row_max_price);
        btnApply = findViewById(R.id.btn_apply);
        ((InfoRowView) findViewById(R.id.row_min_price)).setValue(Format.money(TripFilter.PRICE_MIN));

        setupTimeChips();
        setupOperators();
        setupBusTypes();
        setupPriceSlider();
        refresh();

        setupBackButton(this::finish);
        btnApply.setOnClickListener(v -> {
            draft.filter = working.isEmpty() ? null : working;
            VeGoBanner.post(getString(R.string.filter_applied, TripRepository.search(draft.filter).size()),
                    VeGoBanner.Type.SUCCESS);
            finish();
        });
        onClick(R.id.btn_clear, v -> {
            draft.filter = null;
            VeGoBanner.post(getString(R.string.filter_cleared), VeGoBanner.Type.INFO);
            finish();
        });
    }

    private void setupTimeChips() {
        for (int i = 0; i < TIME_CHIPS.length; i++) {
            final int slot = i;
            findViewById(TIME_CHIPS[i]).setOnClickListener(v -> {
                if (!working.timeSlots.remove(slot)) working.timeSlots.add(slot);
                refresh();
            });
        }
    }

    private void setupOperators() {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String op : TripRepository.operators()) {
            View row = inflater.inflate(R.layout.item_check_row, listOperators, false);
            ((TextView) row.findViewById(R.id.tv_check_label)).setText(op);
            row.setTag(op);
            row.setOnClickListener(v -> {
                if (!working.operators.remove(op)) working.operators.add(op);
                refresh();
            });
            listOperators.addView(row);
        }
    }

    private void setupBusTypes() {
        bindBusChip(R.id.chip_bus_limousine, Trip.BusType.LIMOUSINE);
        bindBusChip(R.id.chip_bus_sleeper, Trip.BusType.SLEEPER);
        bindBusChip(R.id.chip_bus_seater, Trip.BusType.SEATER);
    }

    private void bindBusChip(int id, Trip.BusType type) {
        findViewById(id).setOnClickListener(v -> {
            if (!working.busTypes.remove(type)) working.busTypes.add(type);
            refresh();
        });
    }

    private void setupPriceSlider() {
        SeekBar seek = findViewById(R.id.seek_max_price);
        int steps = (TripFilter.PRICE_MAX - TripFilter.PRICE_MIN) / TripFilter.PRICE_STEP;
        seek.setMax(steps);
        seek.setProgress((working.maxPrice - TripFilter.PRICE_MIN) / TripFilter.PRICE_STEP);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                working.maxPrice = TripFilter.PRICE_MIN + progress * TripFilter.PRICE_STEP;
                refresh();
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

    /** Cập nhật trạng thái chip, ô chọn và số chuyến trên nút Áp dụng. */
    private void refresh() {
        for (int i = 0; i < TIME_CHIPS.length; i++) {
            TextView chip = findViewById(TIME_CHIPS[i]);
            boolean on = working.timeSlots.contains(i);
            chip.setSelected(on);
            chip.setText(on ? TIME_LABELS[i] + " ✓" : TIME_LABELS[i]);
        }
        styleBusChip(R.id.chip_bus_limousine, Trip.BusType.LIMOUSINE, R.string.bus_limousine);
        styleBusChip(R.id.chip_bus_sleeper, Trip.BusType.SLEEPER, R.string.bus_sleeper);
        styleBusChip(R.id.chip_bus_seater, Trip.BusType.SEATER, R.string.bus_seater);

        for (int i = 0; i < listOperators.getChildCount(); i++) {
            View row = listOperators.getChildAt(i);
            boolean on = working.operators.contains((String) row.getTag());
            ((ImageView) row.findViewById(R.id.iv_check))
                    .setImageResource(on ? R.drawable.ic_square_check : R.drawable.ic_square);
        }

        rowMaxPrice.setValue(Format.money(working.maxPrice));
        List<Trip> result = TripRepository.search(working);
        btnApply.setText(getString(R.string.filter_apply, result.size()));
    }

    private void styleBusChip(int id, Trip.BusType type, int labelRes) {
        TextView chip = findViewById(id);
        boolean on = working.busTypes.contains(type);
        chip.setSelected(on);
        chip.setText(on ? getString(labelRes) + " ✓" : getString(labelRes));
    }
}
