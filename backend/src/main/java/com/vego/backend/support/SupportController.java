package com.vego.backend.support;

import com.vego.backend.security.AuthUser;
import com.vego.backend.support.SupportDtos.ChatReply;
import com.vego.backend.support.SupportDtos.ActionResult;
import com.vego.backend.support.SupportDtos.ChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support")
@Tag(name = "Hỗ trợ AI")
public class SupportController {

    private final SupportService service;
    private final SupportActions actions;

    public SupportController(SupportService service, SupportActions actions) {
        this.service = service;
        this.actions = actions;
    }

    @Operation(summary = "Hỏi trợ lý VéGo (RAG: chỉ trả lời theo tài liệu, có ghi nguồn)")
    @PostMapping("/chat")
    public ChatReply chat(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChatRequest request) {
        return service.chat(me.id(), request);
    }

    @Operation(summary = "Khách bấm Xác nhận một thao tác trợ lý đề xuất (đặt / hủy / đổi vé); token dùng một lần, hết hạn sau 10 phút")
    @PostMapping("/actions/{token}/confirm")
    public ActionResult confirm(@AuthenticationPrincipal AuthUser me, @PathVariable String token) {
        return actions.confirm(me.id(), token);
    }

    @Operation(summary = "Khách bấm Bỏ qua một thao tác trợ lý đề xuất")
    @DeleteMapping("/actions/{token}")
    public ResponseEntity<Void> dismiss(@AuthenticationPrincipal AuthUser me, @PathVariable String token) {
        actions.dismiss(me.id(), token);
        return ResponseEntity.noContent().build();
    }
}
