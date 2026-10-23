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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tìm chuyến, sơ đồ ghế, Vé của tôi (hủy / đánh giá), thông báo và Ví xu với PostgreSQL thật. */
@SpringBootTest(properties = {
        "app.payments.simulate=true",
        "app.upload-dir=target/test-uploads"
})
@AutoConfigureMockMvc
@Testcontainers
class TicketWalletIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger SEQ = new AtomicInteger(7000);
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Đi sau 5 ngày: hủy vé được hoàn 80%. */
    private static final LocalDate TRAVEL = LocalDate.now(VN).plusDays(5);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String login(String account, String password) throws Exception {
        return read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("account", account, "password", password))))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private String newUser() throws Exception {
        int n = SEQ.incrementAndGet();
        String phone = "07" + String.format("%08d", n);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Phạm Thu Trang", "phone", phone,
                                "email", "trang" + n + "@vego.test", "password", "matkhau123"))))
                .andExpect(status().isCreated());
        return login(phone, "matkhau123");
    }

    /** Ghế trống đầu tiên trong sơ đồ ghế. */
    private String freeSeat(String tripId) throws Exception {
        JsonNode map = read(mvc.perform(get("/api/v1/trips/" + tripId + "/seats").param("date", TRAVEL.toString()))
                .andExpect(status().isOk()));
        for (JsonNode deck : map.get("decks")) {
            for (JsonNode row : deck.get("rows")) {
                for (JsonNode seat : row) {
                    if (seat.get("available").asBoolean()) return seat.get("code").asText();
                }
            }
        }
        throw new AssertionError("Hết ghế");
    }

    /** Giữ ghế + (tùy chọn) áp mã + thanh toán mô phỏng thành công, trả về mã vé. */
    private String buyTicket(String token, String voucher) throws Exception {
        String seat = freeSeat("PT2200");
        String code = read(mvc.perform(auth(post("/api/v1/bookings"), token)
                        .content(json.writeValueAsString(Map.of("tripId", "PT2200", "travelDate", TRAVEL.toString(),
                                "seat", seat, "passengerName", "Phạm Thu Trang", "passengerPhone", "0987654321"))))
                .andExpect(status().isCreated())).get("code").asText();
        if (voucher != null) {
            mvc.perform(auth(post("/api/v1/bookings/" + code + "/voucher"), token)
                            .content("{\"code\":\"" + voucher + "\"}"))
                    .andExpect(status().isOk());
        }
        String ref = read(mvc.perform(auth(post("/api/v1/bookings/" + code + "/payments"), token)
                        .content("{\"method\":\"MOMO\"}"))
                .andExpect(status().isCreated())).get("txnRef").asText();
        mvc.perform(post("/api/v1/payments/simulated/" + ref).param("result", "success"))
                .andExpect(status().isOk());
        return code;
    }

    @Test
    void searchTripsAndSeatMap() throws Exception {
        JsonNode r = read(mvc.perform(get("/api/v1/trips/search")
                        .param("origin", "tp hcm").param("destination", "da lat").param("date", TRAVEL.toString()))
                .andExpect(status().isOk()));
        assertThat(r.get("routeFound").asBoolean()).isTrue();
        assertThat(r.get("origin").asText()).isEqualTo("TP. Hồ Chí Minh");
        assertThat(r.get("destination").asText()).isEqualTo("Đà Lạt");
        assertThat(r.get("total").asInt()).isEqualTo(6);
        assertThat(r.get("operators").toString()).contains("Phương Trang", "Thành Bưởi", "Điền Linh");

        // Lọc + sắp xếp theo giá
        JsonNode filtered = read(mvc.perform(get("/api/v1/trips/search")
                        .param("origin", "TP. Hồ Chí Minh").param("destination", "Đà Lạt")
                        .param("date", TRAVEL.toString()).param("operator", "Phương Trang")
                        .param("busType", "LIMOUSINE").param("slot", "3").param("sort", "PRICE"))
                .andExpect(status().isOk()));
        assertThat(filtered.get("total").asInt()).isEqualTo(2);   // PT2200, PT2300
        for (JsonNode t : filtered.get("trips")) assertThat(t.get("operator").asText()).isEqualTo("Phương Trang");

        // Không có tuyến
        mvc.perform(get("/api/v1/trips/search").param("origin", "Hà Giang").param("destination", "Cà Mau")
                        .param("date", TRAVEL.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeFound").value(false))
                .andExpect(jsonPath("$.total").value(0));
        // Ngày đã qua
        mvc.perform(get("/api/v1/trips/search").param("origin", "HCM").param("destination", "Đà Lạt")
                        .param("date", LocalDate.now(VN).minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATE_IN_PAST"));

        // Sơ đồ ghế xe 34 chỗ: 2 tầng x 17 ghế, ghế đã bán ở quầy = 34 - 12
        JsonNode map = read(mvc.perform(get("/api/v1/trips/PT2200/seats").param("date", TRAVEL.toString()))
                .andExpect(status().isOk()));
        assertThat(map.get("decks").size()).isEqualTo(2);
        assertThat(map.get("decks").get(0).get("rows").size()).isEqualTo(6);
        assertThat(map.get("taken").size()).isGreaterThanOrEqualTo(22);
        int searchSeats = -1;
        for (JsonNode t : r.get("trips")) if (t.get("id").asText().equals("PT2200")) searchSeats = t.get("seatsLeft").asInt();
        assertThat(searchSeats).isEqualTo(map.get("seatsLeft").asInt());

        // Ghế đã bán ở quầy không giữ được
        String token = newUser();
        String sold = map.get("taken").get(0).asText();
        mvc.perform(auth(post("/api/v1/bookings"), token)
                        .content(json.writeValueAsString(Map.of("tripId", "PT2200", "travelDate", TRAVEL.toString(),
                                "seat", sold, "passengerName", "Phạm Thu Trang", "passengerPhone", "0987654321"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_TAKEN"));
        mvc.perform(auth(post("/api/v1/bookings"), token)
                        .content(json.writeValueAsString(Map.of("tripId", "PT2200", "travelDate", TRAVEL.toString(),
                                "seat", "C40", "passengerName", "Phạm Thu Trang", "passengerPhone", "0987654321"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEAT"));

        // Xe ghế ngồi 29 chỗ: 1 khoang
        mvc.perform(get("/api/v1/trips/TB1330/seats").param("date", TRAVEL.toString()))
                .andExpect(jsonPath("$.decks.length()").value(1))
                .andExpect(jsonPath("$.totalSeats").value(29));

        mvc.perform(get("/api/v1/trips/PT2200/stops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickups[0].name").value("Bến xe Miền Đông mới"))
                .andExpect(jsonPath("$.dropoffs[0].name").value("Bến xe Đà Lạt"));
        mvc.perform(get("/api/v1/stations").param("city", "Đà Lạt"))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/trips/PT2200").param("date", TRAVEL.toString()))
                .andExpect(jsonPath("$.seatsLeft").value(map.get("seatsLeft").asInt()));
    }

    @Test
    void walletCheckInGameAndVoucher() throws Exception {
        String token = newUser();
        // Quà chào mừng 500 xu + thông báo chào mừng
        mvc.perform(auth(get("/api/v1/wallet"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500))
                .andExpect(jsonPath("$.gameTurnsLeft").value(2))
                .andExpect(jsonPath("$.history[0].source").value("WELCOME"));
        mvc.perform(auth(get("/api/v1/notifications"), token))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("PROMO"));

        // Điểm danh: lần đầu +20, lần 2 báo đã điểm danh
        mvc.perform(auth(get("/api/v1/wallet/check-in"), token))
                .andExpect(jsonPath("$.checkedInToday").value(false))
                .andExpect(jsonPath("$.todayIndex").value(0))
                .andExpect(jsonPath("$.days.length()").value(7));
        mvc.perform(auth(post("/api/v1/wallet/check-in"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reward").value(20))
                .andExpect(jsonPath("$.status.checkedInToday").value(true))
                .andExpect(jsonPath("$.status.streak").value(1))
                .andExpect(jsonPath("$.status.balance").value(520));
        mvc.perform(auth(post("/api/v1/wallet/check-in"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"));

        // Game: 2 lượt / ngày, điểm bị giới hạn theo thời gian chơi
        long play1 = read(mvc.perform(auth(post("/api/v1/wallet/game/start"), token))
                .andExpect(status().isOk())).get("playId").asLong();
        JsonNode result = read(mvc.perform(auth(post("/api/v1/wallet/game/" + play1 + "/finish"), token)
                        .content("{\"score\":150}"))
                .andExpect(status().isOk()));
        assertThat(result.get("coins").asInt()).isEqualTo(15);
        assertThat(result.get("newBest").asBoolean()).isTrue();
        assertThat(result.get("balance").asInt()).isEqualTo(535);
        mvc.perform(auth(post("/api/v1/wallet/game/" + play1 + "/finish"), token).content("{\"score\":150}"))
                .andExpect(status().isConflict());
        long play2 = read(mvc.perform(auth(post("/api/v1/wallet/game/start"), token))
                .andExpect(jsonPath("$.turnsLeft").value(0))).get("playId").asLong();
        JsonNode cheat = read(mvc.perform(auth(post("/api/v1/wallet/game/" + play2 + "/finish"), token)
                        .content("{\"score\":999999}"))
                .andExpect(status().isOk()));
        assertThat(cheat.get("score").asInt()).isLessThan(999999);
        assertThat(cheat.get("coins").asInt()).isBetween(0, 100);
        int balance = cheat.get("balance").asInt();
        assertThat(balance).isEqualTo(535 + cheat.get("coins").asInt());
        mvc.perform(auth(post("/api/v1/wallet/game/start"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_TURNS_LEFT"));

        // Đổi voucher: V50 thiếu xu, V20 đủ xu
        mvc.perform(auth(post("/api/v1/wallet/vouchers/V50/redeem"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_COINS"));
        JsonNode redeemed = read(mvc.perform(auth(post("/api/v1/wallet/vouchers/V20/redeem"), token))
                .andExpect(status().isOk()));
        String voucher = redeemed.get("voucher").get("code").asText();
        assertThat(voucher).startsWith("XU20K-");
        assertThat(redeemed.get("balance").asInt()).isEqualTo(balance - 500);
        mvc.perform(auth(get("/api/v1/wallet/vouchers"), token))
                .andExpect(jsonPath("$.offers[0].id").value("V20"))
                .andExpect(jsonPath("$.offers[0].owned").value(1))
                .andExpect(jsonPath("$.mine[0].status").value("AVAILABLE"));
        // Voucher hiện trong gợi ý mã ở màn Thanh toán
        mvc.perform(auth(get("/api/v1/vouchers"), token))
                .andExpect(jsonPath("$[0].code").value(voucher))
                .andExpect(jsonPath("$[0].source").value("WALLET"));

        // Dùng voucher khi thanh toán: giảm 20.000đ, sau đó voucher thành USED, cộng xu theo số tiền đã trả
        String code = buyTicket(token, voucher);
        JsonNode ticket = read(mvc.perform(auth(get("/api/v1/tickets/" + code), token)).andExpect(status().isOk()));
        assertThat(ticket.get("status").asText()).isEqualTo("UPCOMING");
        assertThat(ticket.get("discount").asInt()).isEqualTo(20000);
        assertThat(ticket.get("total").asInt()).isEqualTo(300000);
        assertThat(ticket.get("coinsEarned").asInt()).isEqualTo(300);
        assertThat(ticket.get("qrData").asText()).startsWith("VEGO1|" + code + "|");
        mvc.perform(auth(get("/api/v1/wallet/vouchers"), token))
                .andExpect(jsonPath("$.mine[0].status").value("USED"));
        mvc.perform(auth(get("/api/v1/wallet"), token))
                .andExpect(jsonPath("$.balance").value(balance - 500 + 300))
                .andExpect(jsonPath("$.history[0].source").value("BOOKING"));

        // Dùng lại voucher đã dùng -> lỗi
        String seat = freeSeat("PT2200");
        String code2 = read(mvc.perform(auth(post("/api/v1/bookings"), token)
                        .content(json.writeValueAsString(Map.of("tripId", "PT2200", "travelDate", TRAVEL.toString(),
                                "seat", seat, "passengerName", "Phạm Thu Trang", "passengerPhone", "0987654321"))))
                .andExpect(status().isCreated())).get("code").asText();
        mvc.perform(auth(post("/api/v1/bookings/" + code2 + "/voucher"), token).content("{\"code\":\"" + voucher + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VOUCHER_USED"));
    }

    @Test
    void ticketsCancelAndNotifications() throws Exception {
        String token = newUser();
        String code = buyTicket(token, null);

        JsonNode list = read(mvc.perform(auth(get("/api/v1/tickets"), token)).andExpect(status().isOk()));
        assertThat(list.size()).isEqualTo(1);
        JsonNode t = list.get(0);
        assertThat(t.get("code").asText()).isEqualTo(code);
        assertThat(t.get("cancellable").asBoolean()).isTrue();
        assertThat(t.get("refundPercent").asInt()).isEqualTo(80);
        assertThat(t.get("refundAmount").asInt()).isEqualTo(256000);
        assertThat(t.get("paymentMethodLabel").asText()).isEqualTo("MoMo");
        assertThat(t.get("canReview").asBoolean()).isFalse();

        // Thông báo "Vé đã được xác nhận" + chào mừng
        JsonNode inbox = read(mvc.perform(auth(get("/api/v1/notifications"), token)));
        assertThat(inbox.get("unreadCount").asInt()).isEqualTo(2);
        assertThat(inbox.get("items").get(0).get("ticketCode").asText()).isEqualTo(code);
        long id = inbox.get("items").get(0).get("id").asLong();
        mvc.perform(auth(post("/api/v1/notifications/" + id + "/read"), token))
                .andExpect(jsonPath("$.read").value(true));
        mvc.perform(auth(get("/api/v1/notifications/unread-count"), token))
                .andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(auth(post("/api/v1/notifications/read-all"), token)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/notifications/unread-count"), token))
                .andExpect(jsonPath("$.unreadCount").value(0));

        // Chưa đi -> chưa đánh giá được
        mvc.perform(auth(post("/api/v1/tickets/" + code + "/review"), token).content("{\"rating\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));

        // Hủy vé: hoàn 80%, thu hồi xu, ghế mở bán lại
        String seat = t.get("seat").asText();
        mvc.perform(auth(post("/api/v1/tickets/" + code + "/cancel"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refundAmount").value(256000))
                .andExpect(jsonPath("$.cancellable").value(false));
        mvc.perform(auth(post("/api/v1/tickets/" + code + "/cancel"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_CANCELLED"));
        mvc.perform(auth(get("/api/v1/wallet"), token))
                .andExpect(jsonPath("$.balance").value(500))
                .andExpect(jsonPath("$.history[0].source").value("REFUND"));
        JsonNode map = read(mvc.perform(get("/api/v1/trips/PT2200/seats").param("date", TRAVEL.toString())));
        List<String> taken = new ArrayList<>();
        map.get("taken").forEach(x -> taken.add(x.asText()));
        assertThat(taken).doesNotContain(seat);
        mvc.perform(auth(get("/api/v1/notifications"), token))
                .andExpect(jsonPath("$.items[0].title").value("Đã hủy vé " + code));

        // Người khác không xem được vé
        String other = newUser();
        mvc.perform(auth(get("/api/v1/tickets/" + code), other)).andExpect(status().isNotFound());
    }

    @Test
    void demoUserHistoryReviewAndSuggestions() throws Exception {
        String token = login("0901234567", "vego123456");
        JsonNode list = read(mvc.perform(auth(get("/api/v1/tickets"), token)).andExpect(status().isOk()));
        String completed = null;
        int upcoming = 0, cancelled = 0;
        for (JsonNode t : list) {
            switch (t.get("status").asText()) {
                case "COMPLETED" -> completed = t.get("code").asText();
                case "UPCOMING" -> upcoming++;
                case "CANCELLED" -> cancelled++;
                default -> { }
            }
        }
        assertThat(completed).isNotNull();
        assertThat(upcoming).isEqualTo(1);
        assertThat(cancelled).isEqualTo(1);
        mvc.perform(auth(get("/api/v1/wallet"), token)).andExpect(jsonPath("$.balance").value(1250));
        mvc.perform(auth(get("/api/v1/wallet/check-in"), token))
                .andExpect(jsonPath("$.streak").value(3))
                .andExpect(jsonPath("$.todayIndex").value(3));

        // Đánh giá chuyến đã đi
        mvc.perform(auth(post("/api/v1/tickets/" + completed + "/review"), token)
                        .content("{\"rating\":6}"))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/v1/tickets/" + completed + "/review"), token)
                        .content("{\"rating\":4,\"tags\":[\"clean\",\"ONTIME\"],\"comment\":\"Xe sạch, tài xế vui vẻ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.rating").value(4))
                .andExpect(jsonPath("$.review.tags[0]").value("CLEAN"));
        mvc.perform(auth(get("/api/v1/notifications"), token))
                .andExpect(jsonPath("$.items[0].kind").value("REVIEW"));
        // Đã đi rồi thì không hủy được
        mvc.perform(auth(post("/api/v1/tickets/" + completed + "/cancel"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_DEPARTED"));

        // Gợi ý dựa trên chuyến đã đi (Phương Trang, buổi tối, TP.HCM -> Đà Lạt)
        mvc.perform(auth(get("/api/v1/suggestions"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basedOn.operator").value("Phương Trang"))
                .andExpect(jsonPath("$.basedOn.slot").value("buổi tối"))
                .andExpect(jsonPath("$.main.operator").value("Phương Trang"))
                .andExpect(jsonPath("$.main.destination").value("Đà Lạt"));
        // Khách chưa đăng nhập: gợi ý chung
        mvc.perform(get("/api/v1/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.main.id").exists());
    }
}
