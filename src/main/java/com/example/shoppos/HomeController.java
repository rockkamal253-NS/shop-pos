package com.example.shoppos;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** Route users to their natural landing page after login. */
    @GetMapping("/")
    public String home(Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return isAdmin ? "redirect:/admin/dashboard" : "redirect:/pos";
    }
}
