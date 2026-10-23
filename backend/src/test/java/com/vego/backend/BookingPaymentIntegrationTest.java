package com.vego.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Đặt vé, mã giảm giá và thanh toán VNPay (IPN có chữ ký) với PostgreSQL thật. */
@SpringBootTest(properties = {
        "app.payments.simulate=false",
        "app.booking.presold-seats=false",
        "app.payments.vnpay.tmn-code=TESTTMN1",
        "app.payments.vnpay.hash-secret=TESTSECRETTESTSECRETTESTSECRET12",
        "app.upload-dir=target/test-uploads"
})
@AutoConfigureMockMvc
@Testcontainers
class BookingPaymentIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String SECRET = "TESTSECRETTESTSECRETTESTSECRET12";
    private static final AtomicInteger SEQ = new AtomicInteger(5000);
    private static final LocalDate TOMORROW = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    /** Đăng ký + đăng nhập một người dùng mới, trả về access token. */
    private String newUser() throws Exception {
        int n = SEQ.incrementAndGet();
        String phone = "08" + String.format("%08d", n);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Lê Văn Khách", "phone", phone,
                                "email", "khach" + n + "@vego.test", "password", "matkhau123"))))
                .andExpect(status().isCreated());
        return read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("account", phone, "password", "matkhau123"))))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private JsonNode book(String token, String seat) throws Exception {
        return read(mvc.perform(auth(post("/api/v1/bookings"), token)
                        .content(json.writeValueAsString(Map.of("tripId", "PT2200", "travelDate", TOMORROW.toString(),
                                "seat", seat, "passengerName", "Lê Văn Khách", "passengerPhone", "0912 345 678"))))
                .andExpect(status().isCreated()));
    }

    @Test
    void holdSeatAndPreventDoubleBooking() throws Exception {
        String a = newUser();
        String b = newUser();
        JsonNode booking = book(a, "a07");
        assertThat(booking.get("status").asText()).isEqualTo("HOLDING");
        assertThat(booking.get("seat").asText()).isEqualTo("A07");
        assertThat(booking.get("total").asInt()).isEqualTo(320000);
        assertThat(booking.get("holdRemainingSeconds").asLong()).isBetween(250L, 300L);

        mvc.perform(auth(post("/api/v1/bookings"), b)
                        .content(json.writeValueAsString(Map.of("tripId", "PT2200", "travelDate", TOMORROW.toString(),
                                "seat", "A07", "passengerName", "Người Khác", "passengerPhone", "0912345679"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_TAKEN"));

        mvc.perform(get("/api/v1/trips/PT2200/seats").param("date", TOMORROW.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taken[0]").value("A07"));

        // Người khác không xem được đơn
        mvc.perform(auth(get("/api/v1/bookings/" + booking.get("code").asText()), b))
                .andExpect(status().isNotFound());

        // Hủy giữ chỗ -> ghế được giải phóng
        mvc.perform(auth(post("/api/v1/bookings/" + booking.get("code").asText() + "/cancel"), a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        book(b, "A07");
    }

    @Test
    void vouchers() throws Exception {
        String token = newUser();
        String code = book(token, "A08").get("code").asText();
        String path = "/api/v1/bookings/" + code + "/voucher";

        mvc.perform(auth(post(path), token).content("{\"code\":\"vegin15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voucherCode").value("VEGIN15"))
                .andExpect(jsonPath("$.discount").value(48000))
                .andExpect(jsonPath("$.total").value(272000));
        mvc.perform(auth(post(path), token).content("{\"code\":\"BIENXANH20\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VOUCHER_NOT_APPLICABLE"));
        mvc.perform(auth(post(path), token).content("{\"code\":\"KHONGCO\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(post(path), token).content("{\"code\":\"CHAOBANMOI\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discount").value(50000));
        mvc.perform(auth(delete(path), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(320000));
    }

    @Test
    void vnpayIpnMarksBookingPaid() throws Exception {
        String token = newUser();
        String code = book(token, "B03").get("code").asText();
        mvc.perform(auth(post("/api/v1/bookings/" + code + "/voucher"), token).content("{\"code\":\"VEGO10\"}"))
                .andExpect(jsonPath("$.total").value(290000));

        JsonNode pay = read(mvc.perform(auth(post("/api/v1/bookings/" + code + "/payments"), token)
                        .content("{\"method\":\"VNPAY\"}"))
                .andExpect(status().isCreated()));
        assertThat(pay.get("provider").asText()).isEqualTo("VNPAY");
        assertThat(pay.get("payUrl").asText()).contains("vnp_TmnCode=TESTTMN1").contains("vnp_Amount=29000000")
                .contains("vnp_SecureHash=");
        String txnRef = pay.get("txnRef").asText();

        Map<String, String> ipn = new TreeMap<>(Map.of(
                "vnp_TmnCode", "TESTTMN1", "vnp_Amount", "29000000", "vnp_TxnRef", txnRef,
                "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00", "vnp_TransactionNo", "14600000",
                "vnp_BankCode", "NCB", "vnp_OrderInfo", "Thanh toan ve VeGo " + code, "vnp_PayDate", "20261024100000"));

        // Sai chữ ký -> 97
        mvc.perform(get("/api/v1/payments/vnpay/ipn").params(toParams(ipn, "deadbeef")))
                .andExpect(jsonPath("$.RspCode").value("97"));
        // Đúng chữ ký -> 00, đơn chuyển PAID
        mvc.perform(get("/api/v1/payments/vnpay/ipn").params(toParams(ipn, sign(ipn))))
                .andExpect(jsonPath("$.RspCode").value("00"));
        mvc.perform(auth(get("/api/v1/bookings/" + code), token))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value("VNPAY"))
                .andExpect(jsonPath("$.lastPayment.status").value("SUCCESS"));
        // Gọi lại -> 02 (đã xác nhận), không xử lý 2 lần
        mvc.perform(get("/api/v1/payments/vnpay/ipn").params(toParams(ipn, sign(ipn))))
                .andExpect(jsonPath("$.RspCode").value("02"));
        // Đơn đã thanh toán không đổi mã giảm giá / không thanh toán lại
        mvc.perform(auth(post("/api/v1/bookings/" + code + "/payments"), token).content("{\"method\":\"CARD\"}"))
                .andExpect(status().isConflict());
    }

    private static String sign(Map<String, String> sorted) throws Exception {
        String data = sorted.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static org.springframework.util.MultiValueMap<String, String> toParams(Map<String, String> p, String hash) {
        org.springframework.util.LinkedMultiValueMap<String, String> m = new org.springframework.util.LinkedMultiValueMap<>();
        p.forEach(m::add);
        m.add("vnp_SecureHash", hash);
        return m;
    }
}
