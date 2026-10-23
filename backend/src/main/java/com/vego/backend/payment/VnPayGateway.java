package com.vego.backend.payment;

import com.vego.backend.config.PaymentProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Cổng VNPay (API 2.1.0): tạo URL thanh toán có chữ ký HMAC-SHA512 và kiểm tra chữ ký khi VNPay gọi về.
 * Server không cần gọi sang VNPay để tạo giao dịch — chỉ ký URL rồi app mở URL đó.
 */
@Component
public class VnPayGateway {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** Giờ dùng trong vnp_CreateDate / vnp_ExpireDate là giờ Việt Nam (GMT+7). */
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentProperties.Vnpay config;

    public VnPayGateway(PaymentProperties props) {
        this.config = props.vnpay();
    }

    public boolean configured() {
        return config != null && config.configured();
    }

    public String createPayUrl(Payment payment, String orderInfo, String returnUrl, String clientIp,
                               Instant createdAt, Instant expiresAt) {
        Map<String, String> p = new TreeMap<>();
        p.put("vnp_Version", "2.1.0");
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", config.tmnCode());
        p.put("vnp_Amount", String.valueOf((long) payment.getAmount() * 100));   // VNPay tính theo đơn vị x100
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_TxnRef", payment.getTxnRef());
        p.put("vnp_OrderInfo", orderInfo);
        p.put("vnp_OrderType", "other");
        p.put("vnp_Locale", "vn");
        p.put("vnp_ReturnUrl", returnUrl);
        p.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        p.put("vnp_CreateDate", TIME.format(createdAt.atZone(VN)));
        p.put("vnp_ExpireDate", TIME.format(expiresAt.atZone(VN)));
        // Thẻ ngân hàng: vào thẳng danh sách ngân hàng nội địa (thẻ test NCB)
        if (payment.getMethod() == Payment.Method.CARD) p.put("vnp_BankCode", "VNBANK");

        String query = canonical(p);
        return config.payUrl() + "?" + query + "&vnp_SecureHash=" + hmacSha512(query);
    }

    /** Kiểm tra chữ ký các tham số vnp_* mà VNPay gửi về (return URL / IPN). */
    public boolean verify(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        if (received == null || !configured()) return false;
        Map<String, String> fields = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")
                    && v != null && !v.isEmpty()) {
                fields.put(k, v);
            }
        });
        return hmacSha512(canonical(fields)).equalsIgnoreCase(received);
    }

    /** Chuỗi ký: sắp xếp theo tên, giá trị mã hóa URL (khoảng trắng thành '+'), nối bằng '&'. */
    static String canonical(Map<String, String> sorted) {
        return sorted.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
    }

    String hmacSha512(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(config.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
