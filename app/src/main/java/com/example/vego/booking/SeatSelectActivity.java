package com.example.vego.booking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.TripRepository;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Figma: "Đặt vé · Chọn ghế". Sơ đồ xe giường nằm 34 chỗ (2 tầng x 17 ghế).
 * Mỗi tầng gồm 5 hàng 3 ghế và 1 hàng cuối 2 ghế; các ô ghế được tạo từ item_seat.xml.
 */
public class SeatSelectActivity extends BaseActivity {

    private static final int SEATS_PER_DECK = 17;
    /** Ghế còn trống theo Figma; các ghế khác đã có người đặt. */
    private static final Set<Integer> LOWER_FREE = new HashSet<>(Arrays.asList(3, 5, 8, 11, 14, 17));
    private static final Set<Integer> UPPER_FREE = new HashSet<>(Arrays.asList(2, 5, 8, 11, 14, 17));
    /** Ghế gợi ý chọn sẵn như thiết kế. */
    private static final String DEFAULT_SEAT = "A05";

    private final BookingDraft draft = BookingDraft.get();
    @Nullable private String selectedSeat;

    private TextView tvInfoTitle;
    private TextView tvInfoBody;
    private InfoRowView rowSeat;
    private InfoRowView rowTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_seat_select);

        if (draft.trip == null) draft.selectTrip(TripRepository.suggested());
        Trip trip = draft.trip;
        ((TextView) findViewById(R.id.tv_trip_subtitle)).setText(
                trip.operator + " · " + trip.departTime() + " · " + Format.date(draft.date));

        tvInfoTitle = findViewById(R.id.tv_seat_info_title);
        tvInfoBody = findViewById(R.id.tv_seat_info_body);
        rowSeat = findViewById(R.id.row_selected_seat);
        rowTotal = findViewById(R.id.row_seat_total);

        selectedSeat = draft.seat != null ? draft.seat : DEFAULT_SEAT;
        buildDeck(findViewById(R.id.deck_lower), "A", LOWER_FREE);
        buildDeck(findViewById(R.id.deck_upper), "B", UPPER_FREE);
        refreshSummary();

        setupBackButton(this::finish);
        onClick(R.id.btn_continue, v -> {
            if (selectedSeat == null) {
                VeGoBanner.show(this, R.string.seat_required, VeGoBanner.Type.WARNING);
                return;
            }
            draft.seat = selectedSeat;
            open(PassengerInfoActivity.class);
        });
    }

    /** Thêm các hàng giường vào một tầng: 5 hàng x 3 ghế + 1 hàng 2 ghế. */
    private void buildDeck(LinearLayout deck, String prefix, Set<Integer> free) {
        LayoutInflater inflater = LayoutInflater.from(this);
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        int number = 1;
        while (number <= SEATS_PER_DECK) {
            int perRow = (SEATS_PER_DECK - number + 1) == 2 ? 2 : 3;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            for (int i = 0; i < perRow; i++, number++) {
                View seat = inflater.inflate(R.layout.item_seat, row, false);
                String code = String.format(Locale.ROOT, "%s%02d", prefix, number);
                ((TextView) seat.findViewById(R.id.tv_seat_code)).setText(code);
                seat.setTag(code);
                boolean available = free.contains(number);
                seat.setEnabled(available);
                seat.setSelected(code.equals(selectedSeat));
                seat.setContentDescription(code + (available ? ", còn trống" : ", đã đặt"));
                if (i > 0) ((LinearLayout.LayoutParams) seat.getLayoutParams()).setMarginStart(gap);
                seat.setOnClickListener(v -> selectSeat(code));
                row.addView(seat);
            }
            deck.addView(row);
        }
    }

    private void selectSeat(String code) {
        // Bấm lại ghế đang chọn thì bỏ chọn
        selectedSeat = code.equals(selectedSeat) ? null : code;
        updateSeatStates(findViewById(R.id.deck_lower));
        updateSeatStates(findViewById(R.id.deck_upper));
        refreshSummary();
    }

    private void updateSeatStates(LinearLayout deck) {
        for (int r = 0; r < deck.getChildCount(); r++) {
            View child = deck.getChildAt(r);
            if (!(child instanceof LinearLayout) || child.getTag() != null) continue;
            LinearLayout row = (LinearLayout) child;
            for (int s = 0; s < row.getChildCount(); s++) {
                View seat = row.getChildAt(s);
                Object tag = seat.getTag();
                if (tag != null) seat.setSelected(tag.equals(selectedSeat));
            }
        }
    }

    private void refreshSummary() {
        Trip trip = draft.trip;
        if (selectedSeat == null) {
            tvInfoTitle.setText(R.string.seat_none_title);
            tvInfoBody.setText(R.string.seat_none_body);
            rowSeat.setValue("—");
            rowTotal.setLabel(getString(R.string.seat_total));
            rowTotal.setValue(Format.money(0));
        } else {
            String deck = getString(selectedSeat.startsWith("A") ? R.string.seat_lower : R.string.seat_upper);
            tvInfoTitle.setText(getString(R.string.seat_selected_title, selectedSeat));
            tvInfoBody.setText(R.string.seat_info_body);
            rowSeat.setValue(selectedSeat + " · " + deck);
            rowTotal.setValue(Format.money(trip.price));
        }
    }
}
