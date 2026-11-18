package com.example.shoppos.sale;

import com.example.shoppos.common.web.BusinessRuleException;
import com.example.shoppos.common.web.NotFoundException;
import com.example.shoppos.common.web.Toasts;
import com.example.shoppos.user.CurrentUserService;
import com.example.shoppos.user.Role;
import com.example.shoppos.user.User;
import com.example.shoppos.user.UserPrincipal;
import com.example.shoppos.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
public class SaleController {

    private final SaleService sales;
    private final UserRepository users;
    private final CurrentUserService currentUser;

    public SaleController(SaleService sales, UserRepository users, CurrentUserService currentUser) {
        this.sales = sales;
        this.users = users;
        this.currentUser = currentUser;
    }

    /** Printable 80mm thermal receipt. Available to both roles (cashiers print their own sales). */
    @GetMapping("/sales/{id}/receipt")
    public String receipt(@PathVariable Long id,
                          @AuthenticationPrincipal UserPrincipal principal,
                          Model model) {
        Sale sale = sales.get(id);
        if (principal == null || principal.getRole() != Role.ADMIN) {
            Long currentUserId = principal != null ? principal.getId() : null;
            if (currentUserId == null || !sale.getCashier().getId().equals(currentUserId)) {
                throw new NotFoundException("Sale not found: " + id);
            }
        }
        model.addAttribute("sale", sale);
        return "sale/receipt";
    }

    @GetMapping("/sales")
    public String list(@ModelAttribute("filter") SaleFilterForm filter,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        LocalDateTime from = filter.getFrom() != null ? filter.getFrom().atStartOfDay() : null;
        LocalDateTime to = filter.getTo() != null ? filter.getTo().plusDays(1).atStartOfDay() : null;
        Page<Sale> result = sales.search(from, to, filter.getCashierId(), page);
        model.addAttribute("page", result);
        model.addAttribute("cashiers", users.findByRoleOrderByIdAsc(Role.CASHIER));
        return "sale/list";
    }

    @GetMapping("/sales/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("sale", sales.get(id));
        return "sale/detail";
    }

    /** ADMIN only (enforced by security + service): undo a sale and restore stock. */
    @PostMapping("/sales/{id}/void")
    public String voidSale(@PathVariable Long id,
                           @AuthenticationPrincipal UserPrincipal principal,
                           RedirectAttributes ra,
                           HttpServletResponse response) {
        try {
            User admin = currentUser.getCurrent(principal);
            sales.voidSale(id, admin);
            ra.addFlashAttribute("toastSuccess", "Sale #" + id + " voided. Stock has been restored.");
        } catch (BusinessRuleException e) {
            ra.addFlashAttribute("toastError", e.getMessage());
        }
        return "redirect:/sales/" + id;
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(HttpServletRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal,
                                 Model model) {
        if (request != null) {
            model.addAttribute("currentPath", request.getRequestURI());
        }
        if (principal != null) {
            model.addAttribute("currentUserId", principal.getId());
            model.addAttribute("currentUserName", principal.getDisplayName());
            model.addAttribute("currentUserRole", principal.getRole().name());
            model.addAttribute("isAdmin", principal.getRole() == Role.ADMIN);
        } else {
            model.addAttribute("isAdmin", false);
        }
        return "error/404";
    }
}
