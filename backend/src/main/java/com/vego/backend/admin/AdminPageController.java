package com.vego.backend.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Web admin là trang tĩnh trong static/admin. /admin chuyển hướng sang /admin/ (có dấu /) để các đường dẫn
 * tương đối (admin.css, core.js, logo.webp) trỏ đúng thư mục; /admin/ trả về index.html.
 */
@Controller
public class AdminPageController {

    @GetMapping("/admin")
    public String redirect() {
        return "redirect:/admin/";
    }

    @GetMapping("/admin/")
    public String index() {
        return "forward:/admin/index.html";
    }
}
