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
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web admin: phân quyền, thống kê từ dữ liệu minh họa, quản lý xe / tài xế / tuyến / chuyến, lập lịch. */
@SpringBootTest(properties = {
        "app.admin-email=admin@vego.test",
        "app.admin-password=Admin12345",
        "app.booking.presold-seats=false",
        "app.upload-dir=target/test-uploads"
})
@AutoConfigureMockMvc
@Testcontainers
class AdminIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String login(String account, String password) throws Exception {
        return read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("account", account, "password", password))))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private String admin() throws Exception {
        return login("admin@vego.test", "Admin12345");
    }

    private MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String body(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    private static LocalDate today() {
        return LocalDate.now(VN);
    }

    private int idOf(JsonNode list, String field, String value) {
        for (JsonNode n : list) if (value.equals(n.get(field).asText())) return n.get("id").asInt();
        throw new AssertionError("Không thấy " + value);
    }

    @Test
    void onlyAdminCanCallAdminApi() throws Exception {
        mvc.perform(get("/api/v1/admin/vehicles")).andExpect(status().isUnauthorized());
        String customer = login("0901234567", "vego123456");
        mvc.perform(as(customer, get("/api/v1/admin/vehicles"))).andExpect(status().isForbidden());
        JsonNode me = read(mvc.perform(as(admin(), get("/api/v1/users/me"))).andExpect(status().isOk()));
        assertThat(me.get("role").asText()).isEqualTo("ADMIN");
        // Trang web admin là trang tĩnh, không cần đăng nhập
        mvc.perform(get("/admin/index.html")).andExpect(status().isOk());
    }

    @Test
    void statsUseDemoData() throws Exception {
        String token = admin();
        LocalDate yesterday = today().minusDays(1);
        JsonNode day = read(mvc.perform(as(token, get("/api/v1/admin/stats/overview")
                        .param("mode", "DAY").param("date", yesterday.toString())))
                .andExpect(status().isOk()));
        assertThat(day.get("tickets").asLong()).isPositive();
        assertThat(day.get("revenue").asLong()).isPositive();
        assertThat(day.get("fillRate").asDouble()).isBetween(10.0, 100.0);
        assertThat(day.get("includesDemo").asBoolean()).isTrue();
        assertThat(day.get("series")).hasSize(12);

        JsonNode year = read(mvc.perform(as(token, get("/api/v1/admin/stats/overview").param("mode", "YEAR")))
                .andExpect(status().isOk()));
        assertThat(year.get("series")).hasSize(12);

        JsonNode top = read(mvc.perform(as(token, get("/api/v1/admin/stats/top-routes")
                        .param("from", today().minusDays(10).toString()).param("to", today().toString())
                        .param("sort", "REVENUE")))
                .andExpect(status().isOk()));
        assertThat(top.get("items").size()).isGreaterThanOrEqualTo(4);
        assertThat(top.at("/items/0/rank").asInt()).isEqualTo(1);
        assertThat(top.at("/items/0/revenue").asLong()).isGreaterThanOrEqualTo(top.at("/items/1/revenue").asLong());
        assertThat(top.at("/highlight/tripId").asText()).isNotBlank();

        JsonNode sug = read(mvc.perform(as(token, get("/api/v1/admin/stats/suggestions")
                        .param("from", today().minusDays(10).toString())))
                .andExpect(status().isOk()));
        assertThat(sug.get("impressions").asLong()).isPositive();
        assertThat(sug.get("clicks").asLong()).isPositive();
        assertThat(sug.get("weekly")).hasSize(12);
        assertThat(sug.get("groups")).hasSize(3);

        mvc.perform(as(token, get("/api/v1/admin/activity"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void suggestionImpressionsAndClicksAreRecorded() throws Exception {
        String token = admin();
        long before = read(mvc.perform(as(token, get("/api/v1/admin/stats/suggestions")
                .param("from", today().toString())))).get("clicks").asLong();
        mvc.perform(get("/api/v1/suggestions")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/suggestions/click").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"PT2200\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/suggestions/click").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"KHONGCO\"}"))
                .andExpect(status().isNotFound());
        long after = read(mvc.perform(as(token, get("/api/v1/admin/stats/suggestions")
                .param("from", today().toString())))).get("clicks").asLong();
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void vehicleCrudAndRules() throws Exception {
        String token = admin();
        JsonNode list = read(mvc.perform(as(token, get("/api/v1/admin/vehicles").param("size", "50")))
                .andExpect(status().isOk()));
        assertThat(list.at("/summary/total").asInt()).isGreaterThanOrEqualTo(24);
        assertThat(list.at("/summary/maintenance").asInt()).isGreaterThanOrEqualTo(2);
        int xe001 = idOf(list.at("/page/items"), "code", "XE001");

        JsonNode ops = read(mvc.perform(as(token, get("/api/v1/admin/options"))).andExpect(status().isOk()));
        int pt = idOf(ops.get("operators"), "code", "PT");

        Map<String, Object> req = new HashMap<>(Map.of("plate", "51b 99988", "operatorId", pt,
                "busType", "SLEEPER", "totalSeats", 40, "status", "READY", "note", "Xe mới"));
        JsonNode created = read(mvc.perform(as(token, post("/api/v1/admin/vehicles")).content(body(req)))
                .andExpect(status().isCreated()));
        assertThat(created.at("/vehicle/plate").asText()).isEqualTo("51B-999.88");
        assertThat(created.at("/vehicle/code").asText()).startsWith("XE");
        assertThat(created.get("decks")).hasSize(2);
        int id = created.at("/vehicle/id").asInt();

        mvc.perform(as(token, post("/api/v1/admin/vehicles")).content(body(req)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.plate").exists());

        req.put("status", "MAINTENANCE");
        req.put("totalSeats", 34);
        req.put("busType", "LIMOUSINE");
        mvc.perform(as(token, put("/api/v1/admin/vehicles/" + id)).content(body(req)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vehicle.state").value("MAINTENANCE"))
                .andExpect(jsonPath("$.vehicle.typeLabel").value("Limousine giường nằm 34 chỗ"));

        mvc.perform(as(token, delete("/api/v1/admin/vehicles/" + xe001))).andExpect(status().isConflict());
        mvc.perform(as(token, delete("/api/v1/admin/vehicles/" + id))).andExpect(status().isOk());
        mvc.perform(as(token, get("/api/v1/admin/vehicles/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void driverCrudAndValidation() throws Exception {
        String token = admin();
        JsonNode list = read(mvc.perform(as(token, get("/api/v1/admin/drivers"))).andExpect(status().isOk()));
        assertThat(list.at("/summary/total").asInt()).isGreaterThanOrEqualTo(20);
        assertThat(list.at("/summary/off").asInt()).isGreaterThanOrEqualTo(2);

        Map<String, Object> req = new HashMap<>(Map.of("fullName", "Trịnh Công Lý", "phone", "0977 111 222",
                "licenseNo", "799999999999", "licenseClass", "FC", "licenseExpiry", "2031-01-01",
                "experienceYears", 3));
        mvc.perform(as(token, post("/api/v1/admin/drivers")).content(body(req)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.licenseClass").exists());
        req.put("licenseClass", "D");
        JsonNode d = read(mvc.perform(as(token, post("/api/v1/admin/drivers")).content(body(req)))
                .andExpect(status().isCreated()));
        assertThat(d.at("/driver/phone").asText()).isEqualTo("0977111222");
        assertThat(d.at("/driver/maxSeats").asInt()).isGreaterThan(40);
        int id = d.at("/driver/id").asInt();
        mvc.perform(as(token, delete("/api/v1/admin/drivers/" + id))).andExpect(status().isOk());
    }

    @Test
    void routeTripAndScheduleFlowReachesApp() throws Exception {
        String token = admin();
        JsonNode ops = read(mvc.perform(as(token, get("/api/v1/admin/options"))).andExpect(status().isOk()));
        int canTho = idOf(ops.get("stations"), "name", "Bến xe Trung tâm Cần Thơ");
        int vungTau = idOf(ops.get("stations"), "name", "Bến xe Vũng Tàu");

        // Tuyến mới Cần Thơ -> Vũng Tàu
        Map<String, Object> route = new HashMap<>(Map.of("originCity", "Cần Thơ", "destinationCity", "Vũng Tàu",
                "durationMinutes", 300, "distanceKm", 260, "originStationId", canTho,
                "destinationStationId", vungTau));
        JsonNode r = read(mvc.perform(as(token, post("/api/v1/admin/routes")).content(body(route)))
                .andExpect(status().isCreated()));
        int routeId = r.get("id").asInt();
        assertThat(r.get("code").asText()).startsWith("T");
        mvc.perform(as(token, post("/api/v1/admin/routes")).content(body(route)))
                .andExpect(status().isConflict());
        route.put("originStationId", vungTau);
        mvc.perform(as(token, put("/api/v1/admin/routes/" + routeId)).content(body(route)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.originStationId").exists());

        // Chuyến cố định 09:00 bằng xe dự phòng XE016 (Phương Trang, giường nằm 40)
        JsonNode vehicles = read(mvc.perform(as(token, get("/api/v1/admin/vehicles").param("size", "100"))));
        int xe016 = idOf(vehicles.at("/page/items"), "code", "XE016");
        int xe015 = idOf(vehicles.at("/page/items"), "code", "XE015");
        int xe017 = idOf(vehicles.at("/page/items"), "code", "XE017");
        JsonNode trip = read(mvc.perform(as(token, post("/api/v1/admin/trips")).content(body(Map.of(
                        "routeId", routeId, "departTime", "09:00", "price", 250000, "vehicleId", xe016))))
                .andExpect(status().isCreated()));
        String tripId = trip.get("id").asText();
        assertThat(tripId).startsWith("PT0900");
        assertThat(trip.get("totalSeats").asInt()).isEqualTo(40);

        LocalDate tomorrow = today().plusDays(1);
        mvc.perform(get("/api/v1/trips/search").param("origin", "Cần Thơ").param("destination", "Vũng Tàu")
                        .param("date", tomorrow.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.trips[0].id").value(tripId));

        // Lập lịch chuyến tăng cường TP.HCM -> Đà Lạt 06:15, 3 ngày nữa, xe XE015 + tài xế TX011
        int t001 = idOf(ops.get("routes"), "code", "T001");
        JsonNode drivers = read(mvc.perform(as(token, get("/api/v1/admin/drivers").param("size", "100"))));
        int tx011 = idOf(drivers.at("/page/items"), "code", "TX011");
        int tx004 = idOf(drivers.at("/page/items"), "code", "TX004");
        int tx010 = idOf(drivers.at("/page/items"), "code", "TX010");
        LocalDate day = today().plusDays(3);
        Map<String, Object> sched = new HashMap<>(Map.of("routeId", t001, "date", day.toString(),
                "departTime", "06:15", "vehicleId", xe015, "driverId", tx011, "price", 300000));
        JsonNode preview = read(mvc.perform(as(token, post("/api/v1/admin/schedules/preview")).content(body(sched)))
                .andExpect(status().isOk()));
        assertThat(preview.get("errors")).isEmpty();
        assertThat(preview.get("seats").asInt()).isEqualTo(34);

        JsonNode run = read(mvc.perform(as(token, post("/api/v1/admin/schedules")).content(body(sched)))
                .andExpect(status().isCreated()));
        String oneOff = run.get("tripId").asText();
        assertThat(run.get("oneOff").asBoolean()).isTrue();
        assertThat(run.at("/driver/code").asText()).isEqualTo("TX011");
        assertThat(run.get("departTime").asText()).isEqualTo("06:15");

        mvc.perform(get("/api/v1/trips/search").param("origin", "TP. Hồ Chí Minh").param("destination", "Đà Lạt")
                        .param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trips[?(@.id == '" + oneOff + "')]").exists());
        mvc.perform(get("/api/v1/trips/search").param("origin", "TP. Hồ Chí Minh").param("destination", "Đà Lạt")
                        .param("date", day.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trips[?(@.id == '" + oneOff + "')]").doesNotExist());
        mvc.perform(get("/api/v1/trips/" + oneOff).param("date", day.plusDays(1).toString()))
                .andExpect(status().isNotFound());

        // Trùng lịch xe, tài xế tạm nghỉ, xe bảo trì, GPLX D1 không lái được xe 34 chỗ
        sched.put("departTime", "07:00");
        sched.put("driverId", tx011);
        mvc.perform(as(token, post("/api/v1/admin/schedules")).content(body(sched)))
                .andExpect(status().isConflict());
        Map<String, Object> assign = new HashMap<>(Map.of("tripId", "PT2200", "date", day.toString(),
                "driverId", tx004));
        mvc.perform(as(token, put("/api/v1/admin/schedules/assign")).content(body(assign)))
                .andExpect(status().isConflict());
        assign.put("driverId", tx010);
        mvc.perform(as(token, put("/api/v1/admin/schedules/assign")).content(body(assign)))
                .andExpect(status().isConflict());
        assign.remove("driverId");
        assign.put("vehicleId", xe017);
        mvc.perform(as(token, put("/api/v1/admin/schedules/assign")).content(body(assign)))
                .andExpect(status().isConflict());

        // Chuyến cố định đã có: lập lịch = phân công cho chuyến đó
        Map<String, Object> existing = new HashMap<>(Map.of("routeId", t001, "date", day.toString(),
                "departTime", "22:00", "vehicleId", xe015, "driverId", tx011));
        JsonNode prev2 = read(mvc.perform(as(token, post("/api/v1/admin/schedules/preview")).content(body(existing)))
                .andExpect(status().isOk()));
        assertThat(prev2.at("/existingTrip/id").asText()).isEqualTo("PT2200");
        assertThat(prev2.get("errors")).isEmpty();
        JsonNode assigned = read(mvc.perform(as(token, post("/api/v1/admin/schedules")).content(body(existing)))
                .andExpect(status().isCreated()));
        assertThat(assigned.get("tripId").asText()).isEqualTo("PT2200");
        assertThat(assigned.at("/vehicle/code").asText()).isEqualTo("XE015");

        JsonNode runs = read(mvc.perform(as(token, get("/api/v1/admin/routes/" + t001 + "/runs")
                .param("date", day.toString()))).andExpect(status().isOk()));
        assertThat(runs.size()).isGreaterThanOrEqualTo(7);

        mvc.perform(as(token, delete("/api/v1/admin/schedules/" + oneOff + "/" + day))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/trips/" + oneOff).param("date", day.toString())).andExpect(status().isNotFound());
        mvc.perform(as(token, delete("/api/v1/admin/schedules/PT2200/" + day + "/driver")))
                .andExpect(status().isOk());

        // Tạm ngừng tuyến: app không tìm thấy nữa; tuyến còn chuyến thì không xóa được
        route.put("originStationId", canTho);
        route.put("active", false);
        mvc.perform(as(token, put("/api/v1/admin/routes/" + routeId)).content(body(route)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/trips/search").param("origin", "Cần Thơ").param("destination", "Vũng Tàu")
                        .param("date", tomorrow.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.routeFound").value(false));
        mvc.perform(as(token, delete("/api/v1/admin/routes/" + routeId))).andExpect(status().isConflict());
        mvc.perform(as(token, delete("/api/v1/admin/trips/" + tripId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xóa chuyến"));
        mvc.perform(as(token, delete("/api/v1/admin/routes/" + routeId))).andExpect(status().isOk());
    }
}
