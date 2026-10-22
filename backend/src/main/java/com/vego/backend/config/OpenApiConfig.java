package com.vego.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Tài liệu API tự sinh tại /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vegoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("VéGo API")
                        .version("v1")
                        .description("REST API cho ứng dụng đặt vé xe khách VéGo: xác thực, hồ sơ người dùng, trang chủ."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
