package com.example.vego.tickets;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoChoiceDialog;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.Format;
import com.example.vego.data.Ticket;
import com.example.vego.data.TicketStore;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đổi vé: chọn ngày mới, chuyến mới (cùng tuyến, cùng giá vé) và ghế trống rồi xác nhận.
 * Chuyến và ghế do server kiểm tra lại khi gọi POST /api/v1/tickets/{code}/exchange.
 */
public class ExchangeTicketActivity extends BaseActivity {

    private static final String EXTRA_CODE = "ticket_code";

    private Ticket ticket;
    private final Calendar date = Calendar.getInstance();
    private final List<ApiModels.TripDto> options = new ArrayList<>();
    @Nullable private ApiModels.TripDto selected;
    @Nullable private String seat;

    private TextView tvDate;
    private TextView tvSeat;
    private TextView tvStatus;
    private LinearLayout listTrips;

    public static Intent intent(Context context, String ticketCode) {
        return new Intent(context, ExchangeTicketActivity.class).putExtra(EXTRA_CODE, ticketCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_exchange_ticket);

        String code = getIntent().getStringExtra(EXTRA_CODE);
        ticket = code != null ? TicketStore.get().find(code) : null;
        if (ticket == null) {
            finish();
            return;
        }
        if (!ticket.exchangeable) {
            VeGoBanner.show(this, R.string.eticket_cannot_exchange, VeGoBanner.Type.WARNING);
            finish();
            return;
        }
        date.setTimeInMillis(ticket.date.getTimeInMillis());

        tvDate = findViewById(R.id.tv_date);
        tvSeat = findViewById(R.id.tv_seat);
        tvStatus = findViewById(R.id.tv_status);
        listTrips = findViewById(R.id.list_trips);

        ((TextView) findViewById(R.id.tv_route)).setText(ticket.route());
        ((InfoRowView) findViewById(R.id.row_code)).setValue(ticket.code);
        ((InfoRowView) findViewById(R.id.row_operator)).setValue(ticket.operator);
        ((InfoRowView) findViewById(R.id.row_depart)).setValue(ticket.departTime() + " · " + Format.date(ticket.date));
        ((InfoRowView) findViewById(R.id.row_seat)).setValue(ticket.seat);

        setupBackButton(this::finish);
        onClick(R.id.btn_keep, v -> finish());
        onClick(R.id.field_date, v -> pickDate());
        onClick(R.id.field_seat, v -> pickSeat());
        onClick(R.id.btn_confirm_exchange, v -> confirm());

        showDate();
        loadTrips();
    }

    // ---------- Ngày ----------

    private void pickDate() {
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    date.set(year, month, day);
                    showDate();
                    selected = null;
                    seat = null;
                    showSeat();
                    loadTrips();
                },
                date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        // Server chỉ bán vé trước tối đa 60 ngày
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis() + 60L * 24 * 3600 * 1000);
        dialog.show();
    }

    private void showDate() {
        tvDate.setText(Format.longDate(date));
    }

    // ---------- Chuyến ----------

    private void loadTrips() {
        options.clear();
        listTrips.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.exchange_loading);
        String iso = UserSession.toIsoDate(date);
        ApiClient.api().exchangeTrips(ticket.code, iso).enqueue(new ApiCallback<List<ApiModels.TripDto>>(this) {
            @Override
            protected void onSuccess(@Nullable List<ApiModels.TripDto> list) {
                // Người dùng đã đổi sang ngày khác trong lúc chờ -> bỏ kết quả cũ
                if (!iso.equals(UserSession.toIsoDate(date))) return;
                options.clear();
                if (list != null) options.addAll(list);
                buildTripRows();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                tvStatus.setText(error.message);
                // Vé không còn đổi được (đã đổi, sát giờ, đã hủy...) -> quay lại
                if (error.status == 409) {
                    showError(error);
                    finish();
                }
            }
        });
    }

    private boolean isCurrent(ApiModels.TripDto t) {
        return t.id.equals(ticket.tripId) && iso(ticket.date).equals(iso(date));
    }

    private static String iso(Calendar c) {
        return UserSession.toIsoDate(c);
    }

    private void buildTripRows() {
        listTrips.removeAllViews();
        if (selected != null) {
            boolean still = false;
            for (ApiModels.TripDto t : options) if (t.id.equals(selected.id)) still = true;
            if (!still) {
                selected = null;
                seat = null;
                showSeat();
            }
        }
        if (options.isEmpty()) {
            listTrips.setVisibility(View.GONE);
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText(R.string.exchange_no_trips);
            return;
        }
        tvStatus.setVisibility(View.GONE);
        listTrips.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (ApiModels.TripDto t : options) {
            View row = inflater.inflate(R.layout.item_option_row, listTrips, false);
            String title = t.departTime + " · " + t.operator;
            if (isCurrent(t)) title += " · " + getString(R.string.exchange_current);
            ((TextView) row.findViewById(R.id.tv_option_title)).setText(title);
            String left = t.seatsLeft > 0 ? getString(R.string.exchange_seats_left, t.seatsLeft)
                    : getString(R.string.exchange_sold_out);
            ((TextView) row.findViewById(R.id.tv_option_subtitle)).setText(t.busName + " · " + left);
            row.setTag(t.id);
            if (t.seatsLeft <= 0) {
                row.setAlpha(0.5f);
            } else {
                row.setOnClickListener(v -> {
                    if (selected == null || !selected.id.equals(t.id)) {
                        selected = t;
                        seat = null;
                        showSeat();
                    }
                    refreshTripRows();
                });
            }
            listTrips.addView(row);
        }
        refreshTripRows();
    }

    private void refreshTripRows() {
        for (int i = 0; i < listTrips.getChildCount(); i++) {
            View row = listTrips.getChildAt(i);
            ImageView radio = row.findViewById(R.id.iv_radio);
            boolean on = selected != null && selected.id.equals(row.getTag());
            radio.setImageResource(on ? R.drawable.ic_circle_check : R.drawable.ic_circle);
        }
    }

    // ---------- Ghế ----------

    private void showSeat() {
        tvSeat.setText(seat == null ? "" : seat);
    }

    private void pickSeat() {
        if (selected == null) {
            VeGoBanner.show(this, R.string.exchange_pick_trip_first, VeGoBanner.Type.WARNING);
            return;
        }
        ApiModels.TripDto trip = selected;
        ApiClient.api().seatMap(trip.id, iso(date)).enqueue(new ApiCallback<ApiModels.SeatMap>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.SeatMap map) {
                if (map == null || map.decks == null || selected == null || !selected.id.equals(trip.id)) return;
                // nhãn hiển thị -> mã ghế
                Map<String, String> labels = new LinkedHashMap<>();
                for (ApiModels.Deck deck : map.decks) {
                    if (deck.rows == null) continue;
                    for (List<ApiModels.Seat> row : deck.rows) {
                        for (ApiModels.Seat s : row) {
                            if (!s.available) continue;
                            String label = map.decks.size() > 1 && deck.name != null
                                    ? deck.name + " · " + s.code : s.code;
                            labels.put(label, s.code);
                        }
                    }
                }
                if (labels.isEmpty()) {
                    VeGoBanner.show(ExchangeTicketActivity.this, R.string.exchange_no_free_seat,
                            VeGoBanner.Type.WARNING);
                    return;
                }
                String current = null;
                for (Map.Entry<String, String> e : labels.entrySet()) {
                    if (e.getValue().equals(seat)) current = e.getKey();
                }
                VeGoChoiceDialog.show(ExchangeTicketActivity.this, getString(R.string.exchange_seat_dialog_title),
                        new ArrayList<>(labels.keySet()), current, label -> {
                            seat = labels.get(label);
                            showSeat();
                        });
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                showError(error);
            }
        });
    }

    // ---------- Xác nhận ----------

    private void confirm() {
        if (selected == null) {
            VeGoBanner.show(this, R.string.exchange_pick_trip, VeGoBanner.Type.WARNING);
            return;
        }
        if (seat == null) {
            VeGoBanner.show(this, R.string.exchange_pick_seat, VeGoBanner.Type.WARNING);
            return;
        }
        ApiModels.TripDto trip = selected;
        String newSeat = seat;
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.INFO)
                .title(getString(R.string.exchange_dialog_title, ticket.code))
                .message(getString(R.string.exchange_dialog_message, trip.operator, trip.departTime,
                        Format.shortDate(date), newSeat))
                .primary(R.string.exchange_dialog_confirm, () -> exchange(trip, newSeat))
                .secondary(R.string.exchange_dialog_review, null)
                .show();
    }

    private void exchange(ApiModels.TripDto trip, String newSeat) {
        setBusy(R.id.btn_confirm_exchange, true);
        ApiClient.api().exchangeTicket(ticket.code, new ApiModels.ExchangeRequest(trip.id, iso(date), newSeat))
                .enqueue(new ApiCallback<ApiModels.Ticket>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.Ticket dto) {
                        if (dto == null) return;
                        Ticket updated = TicketStore.get().put(dto);
                        VeGoDialog.with(ExchangeTicketActivity.this)
                                .type(VeGoDialog.Type.SUCCESS)
                                .title(R.string.exchange_done_title)
                                .message(getString(R.string.exchange_done_message, updated.code, updated.operator,
                                        updated.departTime(), Format.shortDate(updated.date), updated.seat))
                                .cancelable(false)
                                .primary(R.string.action_ok, ExchangeTicketActivity.this::finish)
                                .show();
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        showError(error);
                        if (error.is("SEAT_TAKEN")) {
                            // Ghế vừa bị người khác lấy: chọn lại ghế và làm mới số ghế trống
                            seat = null;
                            showSeat();
                            loadTrips();
                        }
                    }

                    @Override
                    protected void onDone() {
                        setBusy(R.id.btn_confirm_exchange, false);
                    }
                });
    }
}
