package com.example.shoppos.user;

import jakarta.servlet.http.HttpServletRequest;
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
    private final UserSessionService userSessionService;

    public AccountController(AccountService accountService,
                             CurrentUserService currentUserService,
                             UserSessionService userSessionService) {
        this.accountService = accountService;
        this.currentUserService = currentUserService;
        this.userSessionService = userSessionService;
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
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        User user = currentUserService.getCurrent(principal);
        boolean success = accountService.changePassword(user, form, binding);
        if (!success) {
            return "account/password";
        }
        userSessionService.expireOtherSessions(principal.getUsername(), request.getSession().getId());
        redirectAttributes.addFlashAttribute("toastSuccess", "Password changed.");
        return "redirect:/";
    }
}
