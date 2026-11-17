package com.example.shoppos.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/**
 * Global fallback handling: turns constraint conflicts, rule violations and
 * concurrent-edit conflicts into friendly redirect toasts, and everything
 * unexpected into the styled 500 page. 404s are handled by @ResponseStatus
 * on {@link NotFoundException} (renders error/404).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrity(HttpServletRequest request, RedirectAttributes ra) {
        ra.addFlashAttribute("toastError", "This record is referenced by other data and cannot be changed or removed.");
        return redirectBack(request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public String handleBusinessRule(BusinessRuleException e, HttpServletRequest request, RedirectAttributes ra) {
        ra.addFlashAttribute("toastError", e.getMessage());
        return redirectBack(request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public String handleOptimisticLock(HttpServletRequest request, RedirectAttributes ra) {
        ra.addFlashAttribute("toastError", "Someone else updated this record just now. Please try again.");
        return redirectBack(request);
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return "error/500";
    }

    private String redirectBack(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                String path = uri.getPath();
                if (path != null) {
                    return "redirect:" + path + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
                }
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return "redirect:/";
    }
}
