package com.vego.backend.payment;

import com.vego.backend.common.ApiException;
import com.vego.backend.common.UrlBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Các địa chỉ cổng thanh toán gọi về (không cần token):
 * IPN = cổng báo ngầm server-to-server; return = trình duyệt của người dùng được chuyển về sau khi trả tiền.
 */
@RestController
@Tag(name = "Thanh toán - cổng gọi về")
public class PaymentCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackController.class);

    private final PaymentService payments;
    private final PaymentRepository paymentRepo;
    private final VnPayGateway vnpay;
    private final UrlBuilder urls;

    public PaymentCallbackController(PaymentService payments, PaymentRepository paymentRepo, VnPayGateway vnpay,
                                     UrlBuilder urls) {
        this.payments = payments;
        this.paymentRepo = paymentRepo;
        this.vnpay = vnpay;
        this.urls = urls;
    }

    // ---------- VNPay ----------

    @Operation(summary = "VNPay IPN (cấu hình trong trang quản lý merchant VNPay)")
    @GetMapping("/api/v1/payments/vnpay/ipn")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        if (!vnpay.verify(params)) {
            return Map.of("RspCode", "97", "Message", "Invalid Checksum");
        }
        PaymentService.Result r = completeVnpay(params);
        return switch (r.outcome()) {
            case APPLIED -> Map.of("RspCode", "00", "Message", "Confirm Success");
            case ALREADY_DONE -> Map.of("RspCode", "02", "Message", "Order already confirmed");
            case NOT_FOUND -> Map.of("RspCode", "01", "Message", "Order not found");
            case AMOUNT_MISMATCH -> Map.of("RspCode", "04", "Message", "Invalid amount");
        };
    }

    @Operation(summary = "VNPay chuyển người dùng về sau khi thanh toán")
    @GetMapping("/api/v1/payments/vnpay/return")
    public ResponseEntity<Void> vnpayReturn(@RequestParam Map<String, String> params) {
        if (!vnpay.verify(params)) {
            log.warn("VNPay return sai chữ ký: {}", params.get("vnp_TxnRef"));
            return redirect(null, "invalid");
        }
        // Nếu IPN chưa được cấu hình / chưa tới thì vẫn ghi nhận được kết quả (đã kiểm tra chữ ký)
        PaymentService.Result r = completeVnpay(params);
        return redirect(r.bookingCode(), r.success() ? "success" : "failed");
    }

    private PaymentService.Result completeVnpay(Map<String, String> p) {
        boolean success = "00".equals(p.get("vnp_ResponseCode")) && "00".equals(p.get("vnp_TransactionStatus"));
        long amount = parseLong(p.get("vnp_Amount")) / 100;
        return payments.complete(p.getOrDefault("vnp_TxnRef", ""), amount, success, p.get("vnp_TransactionNo"),
                p.get("vnp_ResponseCode"), success ? "Thanh toán VNPay thành công" : vnpayMessage(p.get("vnp_ResponseCode")));
    }

    private static String vnpayMessage(String code) {
        if (code == null) return "Thanh toán VNPay không thành công";
        return switch (code) {
            case "24" -> "Bạn đã hủy giao dịch";
            case "11" -> "Hết thời gian chờ thanh toán";
            case "51" -> "Tài khoản không đủ số dư";
            case "12" -> "Thẻ/Tài khoản bị khóa";
            case "13", "79" -> "Nhập sai mật khẩu/OTP quá số lần quy định";
            default -> "Thanh toán VNPay không thành công (mã " + code + ")";
        };
    }

    // ---------- Mô phỏng (demo / CI) ----------

    @Operation(summary = "Trang mô phỏng gửi kết quả (chỉ với giao dịch tạo ở chế độ mô phỏng)")
    @PostMapping("/api/v1/payments/simulated/{txnRef}")
    public Map<String, String> simulated(@PathVariable String txnRef, @RequestParam String result) {
        Payment payment = paymentRepo.findByTxnRef(txnRef)
                .filter(p -> p.getProvider() == Payment.Provider.SIMULATED)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "Không tìm thấy giao dịch mô phỏng"));
        boolean success = "success".equalsIgnoreCase(result);
        PaymentService.Result r = payments.complete(txnRef, payment.getAmount(), success, "SIM-" + txnRef,
                success ? "00" : "99", success ? "Thanh toán mô phỏng thành công" : "Giao dịch mô phỏng thất bại");
        return Map.of("redirect", resultUrl(r.bookingCode(), r.success() ? "success" : "failed"));
    }

    // ---------- Tiện ích ----------

    private ResponseEntity<Void> redirect(String bookingCode, String status) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, resultUrl(bookingCode, status))
                .build();
    }

    private String resultUrl(String bookingCode, String status) {
        String url = urls.absolute("/payment-result.html?status=" + status);
        if (bookingCode != null) url += "&booking=" + URLEncoder.encode(bookingCode, StandardCharsets.UTF_8);
        return URI.create(url).toString();
    }

    private static long parseLong(String s) {
        try {
            return s == null ? -1 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
