package com.vego.backend.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vego.backend.common.ApiException;
import com.vego.backend.config.PaymentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cổng MoMo (API v2): server gọi /v2/gateway/api/create để lấy payUrl, chữ ký HMAC-SHA256.
 * Kết quả trả về qua IPN (POST JSON) và redirectUrl (GET), đều được kiểm tra chữ ký.
 */
@Component
public class MoMoGateway {

    private static final Logger log = LoggerFactory.getLogger(MoMoGateway.class);

    private final PaymentProperties.Momo config;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public MoMoGateway(PaymentProperties props, ObjectMapper json) {
        this.config = props.momo();
        this.json = json;
    }

    public boolean configured() {
        return config != null && config.configured();
    }

    public String createPayUrl(Payment payment, String orderInfo, String redirectUrl, String ipnUrl,
                               long expireMinutes) {
        String requestId = payment.getTxnRef();
        String extraData = "";
        String requestType = config.requestType();
        String raw = "accessKey=" + config.accessKey()
                + "&amount=" + payment.getAmount()
                + "&extraData=" + extraData
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + payment.getTxnRef()
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + config.partnerCode()
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + requestType;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", config.partnerCode());
        body.put("partnerName", "VeGo");
        body.put("storeId", "VeGo");
        body.put("requestId", requestId);
        body.put("amount", payment.getAmount());
        body.put("orderId", payment.getTxnRef());
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", redirectUrl);
        body.put("ipnUrl", ipnUrl);
        body.put("lang", "vi");
        body.put("requestType", requestType);
        body.put("autoCapture", true);
        body.put("extraData", extraData);
        body.put("orderExpireTime", Math.max(1, expireMinutes));
        body.put("signature", hmacSha256(raw));

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(config.endpoint()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = json.readTree(res.body());
            int resultCode = node.path("resultCode").asInt(-1);
            if (resultCode != 0 || node.path("payUrl").asText("").isEmpty()) {
                log.warn("MoMo từ chối tạo giao dịch {}: {} {}", payment.getTxnRef(), resultCode,
                        node.path("message").asText());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                        "MoMo: " + node.path("message").asText("không tạo được giao dịch"));
            }
            return node.path("payUrl").asText();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                    "Không kết nối được MoMo, vui lòng thử lại hoặc chọn phương thức khác");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", "Không kết nối được MoMo");
        }
    }

    /** Kiểm tra chữ ký kết quả MoMo gửi về (IPN hoặc tham số redirectUrl). */
    public boolean verify(Map<String, String> p) {
        String signature = p.get("signature");
        if (signature == null || !configured()) return false;
        String raw = "accessKey=" + config.accessKey()
                + "&amount=" + p.getOrDefault("amount", "")
                + "&extraData=" + p.getOrDefault("extraData", "")
                + "&message=" + p.getOrDefault("message", "")
                + "&orderId=" + p.getOrDefault("orderId", "")
                + "&orderInfo=" + p.getOrDefault("orderInfo", "")
                + "&orderType=" + p.getOrDefault("orderType", "")
                + "&partnerCode=" + p.getOrDefault("partnerCode", "")
                + "&payType=" + p.getOrDefault("payType", "")
                + "&requestId=" + p.getOrDefault("requestId", "")
                + "&responseTime=" + p.getOrDefault("responseTime", "")
                + "&resultCode=" + p.getOrDefault("resultCode", "")
                + "&transId=" + p.getOrDefault("transId", "");
        return hmacSha256(raw).equalsIgnoreCase(signature);
    }

    String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
