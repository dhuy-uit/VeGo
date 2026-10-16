package com.example.vego.common;

import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.vego.R;
import com.example.vego.data.Format;
import com.example.vego.data.Ticket;
import com.example.vego.data.Trip;
import com.example.vego.data.TripRepository;

import java.util.Calendar;

/** Gắn dữ liệu một chuyến xe vào layout view_trip_card. */
public final class TripCardBinder {

    private TripCardBinder() { }

    /** Thẻ ở Danh sách / Chi tiết: badge cam "Còn N ghế". */
    public static void bindWithSeatsLeft(View card, Trip trip, Calendar date) {
        bind(card, trip, date);
        setBadge(card, "Còn " + trip.seatsLeft + " ghế", false);
    }

    /** Thẻ có nhãn tùy chọn (vd "Phù hợp sở thích" ở màn Gợi ý). */
    public static void bindWithBadge(View card, Trip trip, Calendar date, String label, boolean mint) {
        bind(card, trip, date);
        setBadge(card, label, mint);
    }

    /** Thẻ ở Xác nhận / Thành công: badge xanh "Ghế A05". */
    public static void bindWithSeat(View card, Trip trip, Calendar date, String seat) {
        bind(card, trip, date);
        setBadge(card, "Ghế " + seat, true);
    }

    /** Thẻ ở Vé điện tử: dựng từ thông tin vé đã đặt. */
    public static void bindTicket(View card, Ticket t) {
        double rating = t.tripId != null ? TripRepository.byId(t.tripId).rating : 4.8;
        text(card, R.id.tv_operator, t.operator);
        text(card, R.id.tv_bus_name, t.busName);
        text(card, R.id.tv_rating, Format.rating(rating) + " ★");
        text(card, R.id.tv_depart_time, t.departTime());
        text(card, R.id.tv_origin, t.origin);
        int h = t.durationMinutes / 60, m = t.durationMinutes % 60;
        text(card, R.id.tv_duration, m == 0 ? h + " giờ" : h + " giờ " + m + " phút");
        text(card, R.id.tv_arrive_time, t.arriveTime());
        int offset = t.arriveDayOffset();
        text(card, R.id.tv_destination, offset > 0 ? t.destination + " · +" + offset + " ngày" : t.destination);
        text(card, R.id.tv_trip_date, "Ngày đi: " + Format.date(t.date));
        text(card, R.id.tv_price, Format.money(t.price));
        setBadge(card, "Ghế " + t.seat, true);
    }

    private static void bind(View card, Trip trip, Calendar date) {
        text(card, R.id.tv_operator, trip.operator);
        text(card, R.id.tv_bus_name, trip.busName);
        text(card, R.id.tv_rating, trip.ratingText());
        text(card, R.id.tv_depart_time, trip.departTime());
        text(card, R.id.tv_origin, trip.origin);
        text(card, R.id.tv_duration, trip.durationText());
        text(card, R.id.tv_arrive_time, trip.arriveTime());
        int offset = trip.arriveDayOffset();
        text(card, R.id.tv_destination, offset > 0
                ? trip.destination + " · +" + offset + " ngày"
                : trip.destination);
        text(card, R.id.tv_trip_date, "Ngày đi: " + Format.date(date));
        text(card, R.id.tv_price, Format.money(trip.price));
    }

    private static void setBadge(View card, String label, boolean mint) {
        TextView badge = card.findViewById(R.id.tv_trip_badge);
        badge.setText(label);
        badge.setBackgroundResource(mint ? R.drawable.bg_badge_mint : R.drawable.bg_badge_orange);
        badge.setTextColor(ContextCompat.getColor(card.getContext(),
                mint ? R.color.vego_primary : R.color.vego_orange_text));
    }

    private static void text(View root, int id, CharSequence value) {
        ((TextView) root.findViewById(id)).setText(value);
    }
}
