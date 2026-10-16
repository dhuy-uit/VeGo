package com.example.vego.data;

import android.os.SystemClock;

import androidx.annotation.Nullable;

import java.util.Calendar;

/**
 * Đơn đặt vé đang thực hiện, đi xuyên suốt các màn của luồng Đặt vé:
 * Tìm chuyến → Danh sách → Chi tiết → Chọn ghế → Hành khách → Xác nhận → Thanh toán.
 * Lưu tạm trong bộ nhớ ở bước prototype.
 */
public final class BookingDraft {

    /** Thời gian giữ ghế: 5 phút như thiết kế. */
    public static final long HOLD_DURATION_MS = 5 * 60 * 1000L;

    public enum PaymentMethod {
        MOMO("MoMo"), VNPAY("VNPay"), CARD("Thẻ ngân hàng");

        public final String label;

        PaymentMethod(String label) { this.label = label; }
    }

    private static final BookingDraft INSTANCE = new BookingDraft();

    public static BookingDraft get() {
        return INSTANCE;
    }

    // Bước 1: tìm chuyến
    public String origin = "TP. Hồ Chí Minh";
    public String destination = "Đà Lạt";
    public final Calendar date = Calendar.getInstance();
    public int pickupPointIndex = 0;
    @Nullable public TripFilter filter;

    // Bước 2: chuyến và ghế
    @Nullable public Trip trip;
    @Nullable public String seat;

    // Bước 3: hành khách
    public String passengerName = "";
    public String passengerPhone = "";
    public int pickupIndex = 0;
    public int dropoffIndex = 0;

    // Bước 4: giữ chỗ và thanh toán
    /** Mốc hết giờ giữ ghế (theo SystemClock.elapsedRealtime), 0 = chưa giữ. */
    private long holdUntil = 0;
    public PaymentMethod paymentMethod = PaymentMethod.MOMO;
    /** Đã hiện cảnh báo "còn 1 phút" cho lượt giữ chỗ hiện tại chưa. */
    public boolean warnedOneMinute = false;
    @Nullable public String lastTicketCode;

    private BookingDraft() {
        date.clear();
        date.set(2026, Calendar.OCTOBER, 18);
    }

    public void startHoldIfNeeded() {
        if (holdUntil == 0) {
            holdUntil = SystemClock.elapsedRealtime() + HOLD_DURATION_MS;
            warnedOneMinute = false;
        }
    }

    public long holdRemainingMs() {
        if (holdUntil == 0) return HOLD_DURATION_MS;
        return Math.max(0, holdUntil - SystemClock.elapsedRealtime());
    }

    public boolean isHolding() {
        return holdUntil != 0;
    }

    public void releaseHold() {
        holdUntil = 0;
    }

    public int total() {
        return trip == null ? 0 : trip.price;
    }

    /** Chọn chuyến mới thì bỏ ghế và lượt giữ chỗ cũ. */
    public void selectTrip(Trip t) {
        if (trip == null || !trip.id.equals(t.id)) {
            seat = null;
            releaseHold();
        }
        trip = t;
    }

    /** Mã vé dạng VG + ngày đi + số ghế, vd VG18102605. */
    public String issueTicketCode() {
        String seatDigits = seat == null ? "00" : seat.replaceAll("\\D", "");
        lastTicketCode = String.format(java.util.Locale.ROOT, "VG%02d%02d%02d%s",
                date.get(Calendar.DAY_OF_MONTH), date.get(Calendar.MONTH) + 1,
                date.get(Calendar.YEAR) % 100, seatDigits);
        return lastTicketCode;
    }
}
