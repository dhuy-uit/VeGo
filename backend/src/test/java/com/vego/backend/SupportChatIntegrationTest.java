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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API trợ lý khi KHÔNG có khóa Gemini: trả lời theo tài liệu, dữ liệu riêng lấy từ CSDL. */
@SpringBootTest(properties = {"app.rag.api-key=", "app.admin-demo.enabled=false", "app.upload-dir=target/test-uploads"})
@AutoConfigureMockMvc
@Testcontainers
class SupportChatIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("account", "0901234567", "password", "vego123456"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).get("accessToken").asText();
    }

    private JsonNode ask(String token, String message) throws Exception {
        String body = mvc.perform(post("/api/v1/support/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("message", message))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    @Test
    void answersFromDocumentsAndUserData() throws Exception {
        String token = token();

        JsonNode policy = ask(token, "Chính sách hủy vé & hoàn tiền");
        assertThat(policy.get("mode").asText()).isEqualTo("DOCS");
        assertThat(policy.get("source").asText()).contains("hủy vé");

        JsonNode stops = ask(token, "điểm đón tại Đà Lạt");
        assertThat(stops.get("source").asText()).contains("điểm đón");

        JsonNode tickets = ask(token, "Kiểm tra vé của tôi");
        assertThat(tickets.get("mode").asText()).isEqualTo("RULE");
        assertThat(tickets.get("tickets").size()).isPositive();

        JsonNode unknown = ask(token, "công thức nấu phở bò");
        assertThat(unknown.get("mode").asText()).isEqualTo("NONE");
        assertThat(unknown.get("escalate").asBoolean()).isTrue();
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(post("/api/v1/support/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"xin chào\"}"))
                .andExpect(status().isUnauthorized());
    }
}
