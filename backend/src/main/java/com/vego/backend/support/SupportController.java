package com.vego.backend.support;

import com.vego.backend.security.AuthUser;
import com.vego.backend.support.SupportDtos.ChatReply;
import com.vego.backend.support.SupportDtos.ChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support")
@Tag(name = "Hỗ trợ AI")
public class SupportController {

    private final SupportService service;

    public SupportController(SupportService service) {
        this.service = service;
    }

    @Operation(summary = "Hỏi trợ lý VéGo (RAG: chỉ trả lời theo tài liệu, có ghi nguồn)")
    @PostMapping("/chat")
    public ChatReply chat(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChatRequest request) {
        return service.chat(me.id(), request);
    }
}
