package com.vego.backend.booking;

import com.vego.backend.booking.dto.ApplyVoucherRequest;
import com.vego.backend.booking.dto.BookingResponse;
import com.vego.backend.booking.dto.CreateBookingRequest;
import com.vego.backend.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Đặt vé")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @Operation(summary = "Giữ ghế: tạo đơn HOLDING (hết hạn sau 5 phút nếu chưa thanh toán)")
    @PostMapping("/api/v1/bookings")
    public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal AuthUser me,
                                                  @Valid @RequestBody CreateBookingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.id(), req));
    }

    @Operation(summary = "Danh sách đơn của tôi (mới nhất trước)")
    @GetMapping("/api/v1/bookings")
    public List<BookingResponse> list(@AuthenticationPrincipal AuthUser me) {
        return service.list(me.id());
    }

    @Operation(summary = "Xem đơn theo mã vé (dùng để kiểm tra kết quả thanh toán)")
    @GetMapping("/api/v1/bookings/{code}")
    public BookingResponse get(@AuthenticationPrincipal AuthUser me, @PathVariable String code) {
        return service.get(me.id(), code);
    }

    @Operation(summary = "Áp dụng mã giảm giá cho đơn đang giữ")
    @PostMapping("/api/v1/bookings/{code}/voucher")
    public BookingResponse applyVoucher(@AuthenticationPrincipal AuthUser me, @PathVariable String code,
                                        @Valid @RequestBody ApplyVoucherRequest req) {
        return service.applyVoucher(me.id(), code, req.code());
    }

    @Operation(summary = "Bỏ mã giảm giá")
    @DeleteMapping("/api/v1/bookings/{code}/voucher")
    public BookingResponse removeVoucher(@AuthenticationPrincipal AuthUser me, @PathVariable String code) {
        return service.removeVoucher(me.id(), code);
    }

    @Operation(summary = "Hủy giữ chỗ")
    @PostMapping("/api/v1/bookings/{code}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal AuthUser me, @PathVariable String code) {
        return service.cancel(me.id(), code);
    }

    @Operation(summary = "Các ghế đã có người giữ / đặt của một chuyến trong ngày")
    @GetMapping("/api/v1/trips/{tripId}/seats")
    public Map<String, Object> seats(@PathVariable String tripId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Map.of("tripId", tripId, "date", date, "taken", service.takenSeats(tripId, date));
    }
}
