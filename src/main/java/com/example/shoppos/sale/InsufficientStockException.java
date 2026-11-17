package com.example.shoppos.sale;

import java.util.List;

/** Sale rejected because one or more items lack stock. No partial writes happen. */
public class InsufficientStockException extends RuntimeException {

    private final List<String> issues;

    public InsufficientStockException(List<String> issues) {
        super("Not enough stock for: " + String.join(", ", issues));
        this.issues = List.copyOf(issues);
    }

    public List<String> getIssues() {
        return issues;
    }
}
