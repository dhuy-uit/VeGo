package com.vego.backend.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "Tệp")
public class FileController {

    private final AvatarStorage avatars;

    public FileController(AvatarStorage avatars) {
        this.avatars = avatars;
    }

    @Operation(summary = "Lấy ảnh đại diện theo tên tệp (tên ngẫu nhiên, không đoán được)")
    @GetMapping("/avatars/{name}")
    public ResponseEntity<Resource> avatar(@PathVariable String name) {
        return avatars.load(name)
                .map(res -> ResponseEntity.ok()
                        .contentType(name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                        .body(res))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
