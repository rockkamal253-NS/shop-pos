package com.example.shoppos.user;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/account")
public class AccountController {

    private final AccountService accountService;
    private final CurrentUserService currentUserService;

    public AccountController(AccountService accountService, CurrentUserService currentUserService) {
        this.accountService = accountService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/password")
    public String showPasswordForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PasswordChangeForm());
        }
        return "account/password";
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @ModelAttribute("form") PasswordChangeForm form,
                                 BindingResult binding,
                                 RedirectAttributes redirectAttributes) {
        User user = currentUserService.getCurrent(principal);
        boolean success = accountService.changePassword(user, form, binding);
        if (!success) {
            return "account/password";
        }
        redirectAttributes.addFlashAttribute("toastSuccess", "Password changed.");
        return "redirect:/";
    }
}
