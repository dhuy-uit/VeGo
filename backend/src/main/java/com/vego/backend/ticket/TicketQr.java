package com.vego.backend.ticket;

import com.vego.backend.booking.Booking;
import com.vego.backend.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Nội dung mã QR trên vé điện tử: "VEGO1|mã vé|ghế|dd/MM/yyyy|chữ ký".
 * Chữ ký HMAC-SHA256 (10 ký tự hex đầu) để nhà xe biết vé không bị làm giả khi soát vé.
 */
@Component
public class TicketQr {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final byte[] key;

    public TicketQr(AppProperties props) {
        this.key = ("qr|" + props.jwt().secret()).getBytes(StandardCharsets.UTF_8);
    }

    public String payload(Booking b) {
        String data = "VEGO1|" + b.getCode() + "|" + b.getSeatCode() + "|" + DATE.format(b.getTravelDate());
        return data + "|" + sign(data);
    }

    public boolean verify(String payload) {
        int i = payload == null ? -1 : payload.lastIndexOf('|');
        return i > 0 && sign(payload.substring(0, i)).equals(payload.substring(i + 1));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))).substring(0, 10);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
