package com.example.shoppos.common.web;

import com.example.shoppos.user.Role;
import com.example.shoppos.user.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentUserAdvice {

    @Value("${app.shop-name}")
    private String shopName;

    /**
     * Exposes the current user, role flags and the request path (used for sidebar
     * highlighting) to every template, so templates never need the security dialect.
     */
    @ModelAttribute
    public void addCommonAttributes(Model model, Authentication authentication, HttpServletRequest request) {
        model.addAttribute("currentPath", request.getRequestURI());
        model.addAttribute("shopName", shopName);
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            model.addAttribute("currentUserId", principal.getId());
            model.addAttribute("currentUserName", principal.getDisplayName());
            model.addAttribute("currentUserRole", principal.getRole().name());
            model.addAttribute("isAdmin", principal.getRole() == Role.ADMIN);
        } else {
            model.addAttribute("isAdmin", false);
        }
    }
}
