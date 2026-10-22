package com.vego.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chạy toàn bộ luồng chính với PostgreSQL thật (Testcontainers). */
@SpringBootTest(properties = {
        "app.auth.expose-reset-token=true",
        "app.upload-dir=target/test-uploads"
})
@AutoConfigureMockMvc
@Testcontainers
class ApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String body(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    /** Đăng ký tài khoản mới, trả về [phone, email]. */
    private String[] register(String password) throws Exception {
        int n = SEQ.incrementAndGet();
        String phone = "09" + String.format("%08d", n);
        String email = "user" + n + "@vego.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("fullName", "Trần Văn Bình", "phone", phone,
                                "email", email, "password", password))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.givenName").value("Văn Bình"))
                .andExpect(jsonPath("$.phone").value(phone));
        return new String[]{phone, email};
    }

    private JsonNode login(String account, String password) throws Exception {
        return read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("account", account, "password", password))))
                .andExpect(status().isOk()));
    }

    @Test
    void demoUserCanLoginByFormattedPhone() throws Exception {
        JsonNode auth = login("090 123 4567", "vego123456");
        assertThat(auth.get("accessToken").asText()).isNotBlank();
        assertThat(auth.at("/user/fullName").asText()).isEqualTo("Nguyễn Minh Anh");
    }

    @Test
    void registerRejectsDuplicatesAndWeakPassword() throws Exception {
        String[] acc = register("matkhau123");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("fullName", "Ai Đó", "phone", acc[0],
                                "email", "khac@vego.test", "password", "matkhau123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("fullName", "Ai Đó", "phone", "0912000999",
                                "email", "yeu@vego.test", "password", "12345678"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void wrongPasswordIsUnauthorizedThenLocked() throws Exception {
        String[] acc = register("matkhau123");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("account", acc[1], "password", "sai-mat-khau1"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("account", acc[1], "password", "matkhau123"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void profileUpdateAvatarAndPasswordFlow() throws Exception {
        String[] acc = register("matkhau123");
        JsonNode auth = login(acc[1], "matkhau123");
        String bearer = "Bearer " + auth.get("accessToken").asText();

        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(acc[1]));

        mvc.perform(put("/api/v1/users/me").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("fullName", "Trần Văn Bình An", "phone", acc[0],
                                "email", acc[1], "birthDate", "2000-01-31"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.givenName").value("Văn Bình An"))
                .andExpect(jsonPath("$.birthDate").value("2000-01-31"));

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        JsonNode me = read(mvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(new MockMultipartFile("file", "a.png", "image/png", png))
                        .header("Authorization", bearer))
                .andExpect(status().isOk()));
        String avatarUrl = me.get("avatarUrl").asText();
        assertThat(avatarUrl).contains("/api/v1/files/avatars/");
        mvc.perform(get(avatarUrl.substring(avatarUrl.indexOf("/api/")))).andExpect(status().isOk());

        mvc.perform(put("/api/v1/users/me/password").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "sai123456", "newPassword", "MatKhau456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());
        JsonNode changed = read(mvc.perform(put("/api/v1/users/me/password").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "matkhau123", "newPassword", "MatKhau456"))))
                .andExpect(status().isOk()));
        assertThat(changed.get("refreshToken").asText()).isNotBlank();

        // refresh token cũ đã bị thu hồi khi đổi mật khẩu
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("refreshToken", auth.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        login(acc[0], "MatKhau456");
    }

    @Test
    void refreshRotatesAndDetectsReuse() throws Exception {
        String[] acc = register("matkhau123");
        JsonNode auth = login(acc[0], "matkhau123");
        String r1 = auth.get("refreshToken").asText();

        JsonNode next = read(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("refreshToken", r1))))
                .andExpect(status().isOk()));
        String r2 = next.get("refreshToken").asText();
        assertThat(r2).isNotEqualTo(r1);

        // Dùng lại r1 -> bị coi là lộ token, cả chuỗi (gồm r2) bị thu hồi
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("refreshToken", r1))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("refreshToken", r2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotAndResetPassword() throws Exception {
        String[] acc = register("matkhau123");
        JsonNode res = read(mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("account", acc[1]))))
                .andExpect(status().isAccepted()));
        String token = res.get("devResetToken").asText();

        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("token", token, "newPassword", "DatLai789"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("token", token, "newPassword", "DatLai789"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        login(acc[1], "DatLai789");

        // Tài khoản không tồn tại vẫn trả 202 (không lộ thông tin)
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("account", "khongco@vego.test"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.devResetToken").doesNotExist());
    }

    @Test
    void homeWorksForGuestAndUser() throws Exception {
        mvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(false))
                .andExpect(jsonPath("$.popularRoutes[0].destination").value("Đà Lạt"))
                .andExpect(jsonPath("$.popularRoutes[0].fromPrice").value(220000))
                .andExpect(jsonPath("$.suggestedTrip.trip.origin").value("TP. Hồ Chí Minh"))
                .andExpect(jsonPath("$.promotions[0].code").value("VEGIN15"));

        JsonNode auth = login("0901234567", "vego123456");
        mvc.perform(get("/api/v1/home").header("Authorization", "Bearer " + auth.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.greetingName").value("Minh Anh"));

        mvc.perform(get("/api/v1/home").header("Authorization", "Bearer khong-hop-le"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }
}
