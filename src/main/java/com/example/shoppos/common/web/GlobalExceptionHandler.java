package com.example.shoppos.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    public Object handleDataIntegrity(HttpServletRequest request, HttpServletResponse response, RedirectAttributes ra) {
        String message = "This record is referenced by other data and cannot be changed or removed.";
        if (isHtmx(request)) {
            response.setHeader("HX-Reswap", "none");
            Toasts.send(response, message, "error");
            return ResponseEntity.ok().build();
        }
        ra.addFlashAttribute("toastError", message);
        return redirectBack(request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public Object handleBusinessRule(BusinessRuleException e, HttpServletRequest request, HttpServletResponse response, RedirectAttributes ra) {
        if (isHtmx(request)) {
            response.setHeader("HX-Reswap", "none");
            Toasts.send(response, e.getMessage(), "error");
            return ResponseEntity.ok().build();
        }
        ra.addFlashAttribute("toastError", e.getMessage());
        return redirectBack(request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public Object handleOptimisticLock(HttpServletRequest request, HttpServletResponse response, RedirectAttributes ra) {
        String message = "Someone else updated this record just now. Please try again.";
        if (isHtmx(request)) {
            response.setHeader("HX-Reswap", "none");
            Toasts.send(response, message, "error");
            return ResponseEntity.ok().build();
        }
        ra.addFlashAttribute("toastError", message);
        return redirectBack(request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResource(NoResourceFoundException e, HttpServletRequest request, Model model) {
        log.debug("Static resource not found: {}", request.getRequestURI());
        model.addAttribute("currentPath", request.getRequestURI());
        return "error/404";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnexpected(Exception e, HttpServletRequest request, Model model) {
        log.error("Unhandled exception", e);
        model.addAttribute("currentPath", request.getRequestURI());
        return "error/500";
    }

    private boolean isHtmx(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
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
