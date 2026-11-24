package com.example.shoppos.user;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.common.web.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final Logger audit = LoggerFactory.getLogger("shoppos.audit");

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public String listUsers(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        model.addAttribute("users", userAdminService.listUsers());
        model.addAttribute("roles", Role.values());
        model.addAttribute("currentUserId", principal != null ? principal.getId() : null);
        return "admin/users";
    }

    @PostMapping
    public String createUser(@AuthenticationPrincipal UserPrincipal principal,
                             @RequestParam(value = "username", required = false) String username,
                             @RequestParam(value = "displayName", required = false) String displayName,
                             @RequestParam(value = "password", required = false) String password,
                             @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                             @RequestParam(value = "role", required = false) Role role,
                             RedirectAttributes redirectAttributes) {
        if (!Objects.equals(password, confirmPassword)) {
            throw new BusinessRuleException("Passwords do not match.");
        }
        User created = userAdminService.createUser(username, displayName, password, role);
        String actor = LogSanitizer.safe(principal != null ? principal.getUsername() : null);
        String target = LogSanitizer.safe(created.getUsername());
        audit.info("Admin action: '{}' created user '{}' with role {}", actor, target, role);
        redirectAttributes.addFlashAttribute("toastSuccess", "User created.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/password")
    public String resetPassword(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable("id") Long id,
                                @RequestParam(value = "newPassword", required = false) String newPassword,
                                @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                                RedirectAttributes redirectAttributes) {
        if (!Objects.equals(newPassword, confirmPassword)) {
            throw new BusinessRuleException("Passwords do not match.");
        }
        userAdminService.resetPassword(principal.getId(), id, newPassword);
        String actor = LogSanitizer.safe(principal != null ? principal.getUsername() : null);
        audit.info("Admin action: '{}' reset the password of user id {}", actor, id);
        redirectAttributes.addFlashAttribute("toastSuccess", "Password updated.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/enabled")
    public String setEnabled(@AuthenticationPrincipal UserPrincipal principal,
                             @PathVariable("id") Long id,
                             @RequestParam("enabled") boolean enabled,
                             RedirectAttributes redirectAttributes) {
        userAdminService.setEnabled(principal.getId(), id, enabled);
        String actor = LogSanitizer.safe(principal != null ? principal.getUsername() : null);
        if (enabled) {
            audit.info("Admin action: '{}' enabled user id {}", actor, id);
        } else {
            audit.info("Admin action: '{}' disabled user id {}", actor, id);
        }
        redirectAttributes.addFlashAttribute("toastSuccess", enabled ? "User enabled." : "User disabled.");
        return "redirect:/admin/users";
    }
}
