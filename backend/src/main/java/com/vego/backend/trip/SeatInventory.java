package com.vego.backend.trip;

import com.vego.backend.booking.BookingRepository;
import com.vego.backend.config.BookingProperties;
import com.vego.backend.home.Trip;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Sơ đồ ghế và số ghế còn trống của một chuyến trong một ngày.
 *
 * <ul>
 *   <li>Xe giường nằm / limousine từ 20 chỗ: 2 tầng (A = tầng dưới, B = tầng trên), mỗi tầng một nửa số ghế.</li>
 *   <li>Xe ghế ngồi hoặc xe nhỏ: 1 khoang (A).</li>
 *   <li>Mỗi hàng 3 ghế, hàng cuối là phần còn lại (vd 17 ghế = 5 hàng x 3 + 1 hàng x 2).</li>
 * </ul>
 *
 * Ghế đã bán = ghế "bán ở quầy" (giả lập, cố định theo chuyến + ngày, số lượng = total_seats - seats_left)
 * cộng các đơn trong app đang giữ / đã thanh toán.
 */
@Component
public class SeatInventory {

    public record Seat(String code, boolean available) { }

    public record Deck(String code, String name, List<List<String>> rows) {
        public List<String> seats() {
            List<String> all = new ArrayList<>();
            rows.forEach(all::addAll);
            return all;
        }
    }

    private static final int SEATS_PER_ROW = 3;

    private final BookingRepository bookings;
    private final boolean presoldEnabled;
    private final Clock clock;

    public SeatInventory(BookingRepository bookings, BookingProperties props, Clock clock) {
        this.bookings = bookings;
        this.presoldEnabled = props.presoldSeats();
        this.clock = clock;
    }

    public List<Deck> layout(Trip t) {
        int total = t.getTotalSeats();
        boolean twoDecks = t.getBusType() != Trip.BusType.SEATER && total >= 20;
        if (!twoDecks) return List.of(deck("A", "Khoang hành khách", total));
        int lower = (total + 1) / 2;
        return List.of(deck("A", "Tầng dưới", lower), deck("B", "Tầng trên", total - lower));
    }

    private static Deck deck(String prefix, String name, int count) {
        List<List<String>> rows = new ArrayList<>();
        for (int n = 1; n <= count; ) {
            List<String> row = new ArrayList<>();
            for (int i = 0; i < SEATS_PER_ROW && n <= count; i++, n++) {
                row.add(String.format(Locale.ROOT, "%s%02d", prefix, n));
            }
            rows.add(List.copyOf(row));
        }
        return new Deck(prefix, name, List.copyOf(rows));
    }

    public List<String> allSeats(Trip t) {
        List<String> all = new ArrayList<>();
        for (Deck d : layout(t)) all.addAll(d.seats());
        return all;
    }

    public boolean exists(Trip t, String seat) {
        return allSeats(t).contains(seat);
    }

    /** Ghế đã bán ở quầy: chọn ngẫu nhiên nhưng cố định theo (chuyến, ngày) để lần nào xem cũng giống nhau. */
    public Set<String> presold(Trip t, LocalDate date) {
        if (!presoldEnabled) return Set.of();
        List<String> all = allSeats(t);
        int count = Math.max(0, Math.min(all.size(), t.getTotalSeats() - t.getSeatsLeft()));
        if (count == 0) return Set.of();
        Collections.shuffle(all, new Random((t.getId() + "|" + date).hashCode() * 31L + 7));
        return new LinkedHashSet<>(all.subList(0, count));
    }

    /** Ghế không chọn được: bán ở quầy + đơn đang giữ / đã thanh toán trong app. */
    public Set<String> unavailable(Trip t, LocalDate date) {
        Set<String> taken = new LinkedHashSet<>(presold(t, date));
        taken.addAll(bookings.takenSeats(t.getId(), date, clock.instant()));
        return taken;
    }

    public int seatsLeft(Trip t, LocalDate date) {
        return Math.max(0, t.getTotalSeats() - unavailable(t, date).size());
    }

    /** Số ghế trống của nhiều chuyến cùng ngày (1 truy vấn cho cả danh sách). */
    public Map<String, Integer> seatsLeft(Collection<Trip> trips, LocalDate date) {
        Map<String, Integer> result = new HashMap<>();
        if (trips.isEmpty()) return result;
        Map<String, Long> taken = new HashMap<>();
        for (Object[] row : bookings.countTaken(trips.stream().map(Trip::getId).toList(), date, clock.instant())) {
            taken.put((String) row[0], (Long) row[1]);
        }
        for (Trip t : trips) {
            int left = t.getTotalSeats() - presold(t, date).size() - taken.getOrDefault(t.getId(), 0L).intValue();
            result.put(t.getId(), Math.max(0, left));
        }
        return result;
    }
}
