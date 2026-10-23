package com.example.vego.booking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

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
import java.util.Locale;

/**
 * Figma: "Đặt vé · Chọn ghế". Sơ đồ ghế lấy từ GET /api/v1/trips/{id}/seats?date=
 * (xe giường nằm 2 tầng A/B, xe ghế ngồi 1 khoang); ghế đã bán / đang có người giữ bị khóa.
 * Tải lại mỗi lần quay về màn này để thấy ghế người khác vừa đặt.
 */
public class SeatSelectActivity extends BaseActivity {

    /** Trong mỗi tầng: 2 view đầu là tiêu đề tầng + dòng "Đầu xe", các hàng ghế thêm sau. */
    private static final int DECK_HEADER_VIEWS = 2;

    private final BookingDraft draft = BookingDraft.get();
    @Nullable private String selectedSeat;
    @Nullable private ApiModels.SeatMap seatMap;

    private TextView tvInfoTitle;
    private TextView tvInfoBody;
    private InfoRowView rowSeat;
    private InfoRowView rowTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_seat_select);
        if (draft.trip == null) {
            goHome();
            return;
        }
        Trip trip = draft.trip;
        ((TextView) findViewById(R.id.tv_trip_subtitle)).setText(
                trip.operator + " · " + trip.departTime() + " · " + Format.date(draft.date));

        tvInfoTitle = findViewById(R.id.tv_seat_info_title);
        tvInfoBody = findViewById(R.id.tv_seat_info_body);
        rowSeat = findViewById(R.id.row_selected_seat);
        rowTotal = findViewById(R.id.row_seat_total);

        selectedSeat = draft.seat;
        refreshSummary();

        setupBackButton(this::finish);
        onClick(R.id.btn_continue, v -> {
            if (seatMap == null) {
                VeGoBanner.show(this, R.string.seat_loading, VeGoBanner.Type.INFO);
                loadSeats();
                return;
            }
            if (selectedSeat == null) {
                VeGoBanner.show(this, R.string.seat_required, VeGoBanner.Type.WARNING);
                return;
            }
            draft.seat = selectedSeat;
            open(PassengerInfoActivity.class);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (draft.trip != null) loadSeats();
    }

    private void loadSeats() {
        Trip trip = draft.trip;
        if (trip == null) return;
        ApiClient.api().seatMap(trip.id, UserSession.toIsoDate(draft.date))
                .enqueue(new ApiCallback<ApiModels.SeatMap>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.SeatMap map) {
                        if (map == null || map.decks == null) return;
                        seatMap = map;
                        // Ghế đang chọn vừa bị người khác giữ -> bỏ chọn và báo
                        if (selectedSeat != null && !isAvailable(map, selectedSeat)) {
                            VeGoBanner.show(SeatSelectActivity.this,
                                    getString(R.string.seat_no_longer_free, selectedSeat), VeGoBanner.Type.WARNING);
                            selectedSeat = null;
                            if (!draft.isHolding()) draft.seat = null;
                        }
                        buildDecks(map);
                        refreshSummary();
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        showError(error);
                    }
                });
    }

    private static boolean isAvailable(ApiModels.SeatMap map, String code) {
        for (ApiModels.Deck d : map.decks) {
            for (List<ApiModels.Seat> row : d.rows) {
                for (ApiModels.Seat s : row) if (s.code.equals(code)) return s.available;
            }
        }
        return false;
    }

    /** Dựng các tầng theo dữ liệu server: tầng A vào khung trái, tầng B vào khung phải (nếu có). */
    private void buildDecks(ApiModels.SeatMap map) {
        LinearLayout lower = findViewById(R.id.deck_lower);
        LinearLayout upper = findViewById(R.id.deck_upper);
        clearRows(lower);
        clearRows(upper);
        upper.setVisibility(map.decks.size() > 1 ? View.VISIBLE : View.GONE);
        if (!map.decks.isEmpty()) fillDeck(lower, map.decks.get(0));
        if (map.decks.size() > 1) fillDeck(upper, map.decks.get(1));
    }

    private static void clearRows(LinearLayout deck) {
        while (deck.getChildCount() > DECK_HEADER_VIEWS) deck.removeViewAt(DECK_HEADER_VIEWS);
    }

    private void fillDeck(LinearLayout deck, ApiModels.Deck data) {
        int count = 0;
        for (List<ApiModels.Seat> r : data.rows) count += r.size();
        View title = deck.getChildAt(0);
        if (title instanceof TextView && data.name != null) {
            ((TextView) title).setText(getString(R.string.seat_deck_title, data.name, count));
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        for (List<ApiModels.Seat> seats : data.rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            for (int i = 0; i < seats.size(); i++) {
                ApiModels.Seat s = seats.get(i);
                View seat = inflater.inflate(R.layout.item_seat, row, false);
                ((TextView) seat.findViewById(R.id.tv_seat_code)).setText(s.code);
                seat.setTag(s.code);
                seat.setEnabled(s.available);
                seat.setSelected(s.code.equals(selectedSeat));
                seat.setContentDescription(s.code + (s.available ? ", còn trống" : ", đã đặt"));
                if (i > 0) ((LinearLayout.LayoutParams) seat.getLayoutParams()).setMarginStart(gap);
                seat.setOnClickListener(v -> selectSeat(s.code));
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
        for (int r = DECK_HEADER_VIEWS; r < deck.getChildCount(); r++) {
            View child = deck.getChildAt(r);
            if (!(child instanceof LinearLayout)) continue;
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
        if (trip == null) return;
        if (selectedSeat == null) {
            tvInfoTitle.setText(R.string.seat_none_title);
            tvInfoBody.setText(seatMap == null ? getString(R.string.seat_loading)
                    : getString(R.string.seat_none_body_left, seatMap.seatsLeft));
            rowSeat.setValue("—");
            rowTotal.setLabel(getString(R.string.seat_total));
            rowTotal.setValue(Format.money(0));
        } else {
            tvInfoTitle.setText(getString(R.string.seat_selected_title, selectedSeat));
            tvInfoBody.setText(R.string.seat_info_body);
            rowSeat.setValue(selectedSeat + " · " + deckName(selectedSeat));
            rowTotal.setValue(Format.money(seatMap != null ? seatMap.price : trip.price));
        }
    }

    /** Tên tầng của ghế (theo dữ liệu server, mặc định A = tầng dưới, B = tầng trên). */
    private String deckName(String seat) {
        String prefix = seat.substring(0, 1).toUpperCase(Locale.ROOT);
        if (seatMap != null) {
            for (ApiModels.Deck d : seatMap.decks) if (prefix.equals(d.code) && d.name != null) return d.name;
        }
        return getString(prefix.equals("A") ? R.string.seat_lower : R.string.seat_upper);
    }
}
