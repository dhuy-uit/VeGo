package com.example.vego.data;

import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.example.vego.data.api.ApiModels;

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
    /** Điểm đón muốn chọn ở màn Tìm chuyến (tên bến), null = theo bến chính của chuyến. */
    @Nullable public String preferredPickup;
    @Nullable public TripFilter filter;

    // Bước 2: chuyến và ghế
    @Nullable public Trip trip;
    @Nullable public String seat;

    // Bước 3: hành khách
    public String passengerName = "";
    public String passengerPhone = "";
    /** Bến đón / trả đã chọn (tên bến), null = bến chính của chuyến. */
    @Nullable public String pickupStation;
    @Nullable public String dropoffStation;
    /** Điểm đón / trả của chuyến đang chọn (GET /trips/{id}/stops), null = chưa tải. */
    @Nullable public ApiModels.TripStops stops;
    /** Đón / trả bằng xe trung chuyển: không chọn bến, nhân viên sẽ gọi hẹn điểm. */
    public boolean pickupShuttle = false;
    public boolean dropoffShuttle = false;

    // Bước 4: giữ chỗ và thanh toán
    /** Mốc hết giờ giữ ghế (theo SystemClock.elapsedRealtime), 0 = chưa giữ. */
    private long holdUntil = 0;
    public PaymentMethod paymentMethod = PaymentMethod.MOMO;

    // Đơn trên server (POST /api/v1/bookings)
    /** Mã vé của đơn đang giữ trên server, null = chưa giữ ghế trên server. */
    @Nullable public String bookingCode;
    /** Thông tin đã gửi khi giữ ghế; đổi chuyến / ghế / hành khách thì phải giữ ghế lại. */
    @Nullable private String bookingKey;
    public int discount = 0;
    @Nullable public String voucherCode;
    @Nullable public String voucherTitle;
    /** Tổng tiền server tính (đã trừ mã giảm giá), -1 = chưa có. */
    private int serverTotal = -1;
    /** Lý do thanh toán thất bại gần nhất (hiện ở màn Thất bại). */
    @Nullable public String lastPaymentMessage;
    /** Đã hiện cảnh báo "còn 1 phút" cho lượt giữ chỗ hiện tại chưa. */
    public boolean warnedOneMinute = false;
    @Nullable public String lastTicketCode;

    private BookingDraft() {
        // Mặc định: ngày mai
        Calendar today = Calendar.getInstance();
        date.clear();
        date.set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));
        date.add(Calendar.DAY_OF_MONTH, 1);
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

    /** Nhãn điểm đón hiển thị ở Xác nhận / Vé điện tử. */
    public String pickupLabel() {
        if (pickupShuttle) return SHUTTLE_LABEL;
        if (pickupStation != null) return pickupStation;
        return trip == null ? "" : trip.pickupStation;
    }

    /** Nhãn điểm trả hiển thị ở Xác nhận / Vé điện tử. */
    public String dropoffLabel() {
        if (dropoffShuttle) return SHUTTLE_LABEL;
        if (dropoffStation != null) return dropoffStation;
        return trip == null ? "" : trip.dropoffStation;
    }

    public static final String SHUTTLE_LABEL = "Xe trung chuyển (nhân viên sẽ liên hệ)";

    public void releaseHold() {
        holdUntil = 0;
        clearBooking();
    }

    /** Bỏ thông tin đơn trên server (hết giờ, hủy, đổi chuyến, đã thanh toán xong). */
    public void clearBooking() {
        bookingCode = null;
        bookingKey = null;
        discount = 0;
        voucherCode = null;
        voucherTitle = null;
        serverTotal = -1;
        lastPaymentMessage = null;
    }

    public int fare() {
        return trip == null ? 0 : trip.price;
    }

    public int total() {
        return serverTotal >= 0 ? serverTotal : fare();
    }

    /** Khóa nhận diện đơn: chuyến + ngày + ghế + hành khách + điểm đón/trả. */
    public String currentBookingKey() {
        return (trip == null ? "" : trip.id) + "|" + Format.date(date) + "|" + seat + "|" + passengerName
                + "|" + passengerPhone + "|" + pickupLabel() + "|" + dropoffLabel();
    }

    /** Đơn trên server còn khớp với lựa chọn hiện tại không. */
    public boolean hasServerBooking() {
        return bookingCode != null && currentBookingKey().equals(bookingKey) && isHolding();
    }

    /** Cập nhật từ dữ liệu server: mã vé, tiền, mã giảm giá, thời gian giữ ghế còn lại. */
    public void applyServerBooking(ApiModels.Booking b) {
        if (!b.code.equals(bookingCode)) bookingKey = currentBookingKey();
        bookingCode = b.code;
        discount = b.discount;
        voucherCode = b.voucherCode;
        voucherTitle = b.voucherTitle;
        serverTotal = b.total;
        if (b.is("HOLDING")) {
            holdUntil = SystemClock.elapsedRealtime() + b.holdRemainingSeconds * 1000L;
            if (b.holdRemainingSeconds > 60) warnedOneMinute = false;
        }
        lastPaymentMessage = b.lastPayment != null ? b.lastPayment.message : null;
    }

    /** Chọn chuyến mới thì bỏ ghế, điểm đón / trả và lượt giữ chỗ cũ. */
    public void selectTrip(Trip t) {
        if (trip == null || !trip.id.equals(t.id)) {
            seat = null;
            pickupStation = null;
            dropoffStation = null;
            stops = null;
            releaseHold();
        }
        trip = t;
    }

    /** Mã vé: dùng mã server cấp; chưa có server thì tạo mã dạng VG + ngày đi + số ghế. */
    public String issueTicketCode() {
        if (bookingCode != null) {
            lastTicketCode = bookingCode;
            return lastTicketCode;
        }
        String seatDigits = seat == null ? "00" : seat.replaceAll("\\D", "");
        lastTicketCode = String.format(java.util.Locale.ROOT, "VG%02d%02d%02d%s",
                date.get(Calendar.DAY_OF_MONTH), date.get(Calendar.MONTH) + 1,
                date.get(Calendar.YEAR) % 100, seatDigits);
        return lastTicketCode;
    }
}
