package com.vego.backend.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Web admin là trang tĩnh trong static/admin; /admin chuyển tới index.html. */
@Controller
public class AdminPageController {

    @GetMapping({"/admin", "/admin/"})
    public String index() {
        return "forward:/admin/index.html";
    }
}
