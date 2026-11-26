package com.example.shoppos;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Map;

@Controller
public class HomeController {

    /** Route users to their natural landing page after login. */
    @GetMapping("/")
    public String home(Authentication authentication,
                       HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        Map<String, ?> inputFlash = RequestContextUtils.getInputFlashMap(request);
        if (inputFlash != null) {
            inputFlash.forEach(redirectAttributes::addFlashAttribute);
        }
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return isAdmin ? "redirect:/admin/dashboard" : "redirect:/pos";
    }
}
