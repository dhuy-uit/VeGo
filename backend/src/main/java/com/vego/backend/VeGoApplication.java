package com.vego.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** REST API cho ứng dụng đặt vé xe khách VéGo. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class VeGoApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeGoApplication.class, args);
    }
}
